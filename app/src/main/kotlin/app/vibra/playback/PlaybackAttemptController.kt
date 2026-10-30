/*
 * Vibra (2026) — one user-visible result for a complete playback attempt.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.playback

import androidx.media3.common.PlaybackException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PlaybackStage { DIRECT, REFRESHED, ANONYMOUS, INVIDIOUS, PIPED }
enum class PlaybackAttemptPhase { IDLE, LOADING, RECOVERING, PLAYING, FAILED }

data class PlaybackFailure(val code: Int, val message: String, val exception: PlaybackException? = null)

data class PlaybackAttempt(
    val id: Long = 0,
    val mediaId: String? = null,
    val stage: PlaybackStage = PlaybackStage.DIRECT,
    val phase: PlaybackAttemptPhase = PlaybackAttemptPhase.IDLE,
    val deadlineMs: Long? = null,
    val error: PlaybackFailure? = null,
) {
    val isLoading: Boolean get() = phase == PlaybackAttemptPhase.LOADING || phase == PlaybackAttemptPhase.RECOVERING
}

/** Owns attempt identity, the shared retry budget and the latched final error. */
@Singleton
class PlaybackAttemptController @Inject constructor() {
    private val mutableState = MutableStateFlow(PlaybackAttempt())
    val state = mutableState.asStateFlow()
    private var sequence = 0L
    @Volatile private var debugFailures: Set<PlaybackStage> = emptySet()
    @Volatile private var debugTimeout = false

    /** Debug fixture only; release builds ignore fault injection entirely. */
    fun configureDebugFailures(stages: Set<PlaybackStage>, timeout: Boolean = false) {
        if (!moe.rukamori.archivetune.BuildConfig.DEBUG) return
        debugFailures = stages.toSet()
        debugTimeout = timeout
    }

    internal suspend fun beforeResolution(stage: PlaybackStage) {
        if (!moe.rukamori.archivetune.BuildConfig.DEBUG) return
        if (debugTimeout && stage == PlaybackStage.REFRESHED) kotlinx.coroutines.delay(60_000L)
        if (stage in debugFailures) throw java.io.IOException("Simulated playback source failure")
    }

    @Synchronized
    fun begin(mediaId: String?): PlaybackAttempt = PlaybackAttempt(
        id = ++sequence,
        mediaId = mediaId,
        phase = if (mediaId == null) PlaybackAttemptPhase.IDLE else PlaybackAttemptPhase.LOADING,
    ).also { mutableState.value = it }

    @Synchronized
    fun current(mediaId: String): PlaybackAttempt? = state.value.takeIf { it.mediaId == mediaId }

    @Synchronized
    fun isCurrent(id: Long, mediaId: String): Boolean = state.value.let {
        it.id == id && it.mediaId == mediaId && it.phase != PlaybackAttemptPhase.IDLE && it.phase != PlaybackAttemptPhase.FAILED
    }

    @Synchronized
    fun advance(id: Long, mediaId: String, signedIn: Boolean, nowMs: Long): PlaybackAttempt? {
        val previous = current(mediaId)?.takeIf { isCurrent(id, mediaId) } ?: return null
        if (previous.deadlineMs?.let { nowMs >= it } == true) return null
        val next = when (previous.stage) {
            PlaybackStage.DIRECT -> PlaybackStage.REFRESHED
            PlaybackStage.REFRESHED -> if (signedIn) PlaybackStage.ANONYMOUS else PlaybackStage.INVIDIOUS
            PlaybackStage.ANONYMOUS -> PlaybackStage.INVIDIOUS
            PlaybackStage.INVIDIOUS -> PlaybackStage.PIPED
            PlaybackStage.PIPED -> return null
        }
        return previous.copy(
            stage = next,
            phase = PlaybackAttemptPhase.RECOVERING,
            deadlineMs = previous.deadlineMs ?: (nowMs + RECOVERY_TIMEOUT_MS),
            error = null,
        ).also { mutableState.value = it }
    }

    @Synchronized
    fun ready(id: Long, mediaId: String) {
        val attempt = current(mediaId)?.takeIf { isCurrent(id, mediaId) } ?: return
        mutableState.value = attempt.copy(phase = PlaybackAttemptPhase.PLAYING, deadlineMs = null)
    }

    @Synchronized
    fun fail(id: Long, mediaId: String, error: PlaybackFailure) {
        val attempt = current(mediaId)?.takeIf { isCurrent(id, mediaId) } ?: return
        mutableState.value = attempt.copy(phase = PlaybackAttemptPhase.FAILED, deadlineMs = null, error = error)
    }

    fun fail(id: Long, mediaId: String, error: PlaybackException) =
        fail(id, mediaId, PlaybackFailure(error.errorCode, error.message.orEmpty(), error))

    @Synchronized
    fun dismiss() {
        mutableState.value = state.value.copy(error = null)
    }

    @Synchronized
    fun cancel() {
        mutableState.value = PlaybackAttempt(id = ++sequence)
    }

    companion object {
        const val RECOVERY_TIMEOUT_MS = 30_000L
    }
}
