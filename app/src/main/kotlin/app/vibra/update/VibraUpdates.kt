/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.SystemClock
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.BuildConfig
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  UPDATES — VIBRA keeps itself up to date without getting in the way
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  1. Check   – whenever the app comes to the foreground (at most once an hour), read the
 *               latest release from BuildConfig.VIBRA_UPDATE_URL.
 *  2. Fetch   – a newer version is downloaded in the background and verified: the SHA-256
 *               GitHub declares, then package name, signing key and versionCode.
 *  3. Install – never under the user's fingers and never during music: once the app has
 *               been in the background and silent for [QuietPeriod], Android installs it
 *               without asking (Android 12+, after "install unknown apps" was allowed
 *               once). When Android has to ask, a dialog offers the update while the app
 *               is open, and the system confirmation follows a tap.
 *  4. After   – an update that happened while the user was away is announced by a
 *               notification from the new version.
 *
 *  Errors are silent: the next start simply tries again.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */
object VibraUpdates {
    sealed interface State {
        data object Idle : State

        data class Downloading(
            val release: VibraRelease,
        ) : State

        /** Downloaded and verified, waiting for the right moment to install. */
        data class Ready(
            val release: VibraRelease,
            val apk: File,
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _prompt = MutableStateFlow<VibraRelease?>(null)

    /** The release the update dialog should offer, or null when there is nothing to ask. */
    val prompt: StateFlow<VibraRelease?> = _prompt.asStateFlow()

    private lateinit var app: Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var checkJob: Job? = null
    private var installJob: Job? = null
    private var lastCheckAt: Long? = null
    private var foreground = false
    private var playing = false
    private var backgroundAt = 0L
    private var stoppedAt = 0L

    /** The user closed the dialog or Android's confirmation: don't ask again until the next start. */
    private var declined = false

    /** Android asked for confirmation on a silent attempt: the dialog has to ask. */
    private var confirmationRequired = false

    /** Android's own confirmation is on screen: the dialog stays away. */
    private var confirming = false

    /** Starts watching the app's lifecycle. Does nothing when no update source is configured. */
    fun start(application: Application) {
        if (BuildConfig.VIBRA_UPDATE_URL.isBlank()) return
        app = application
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> onForeground()
                    Lifecycle.Event.ON_STOP -> onBackground()
                    else -> Unit
                }
            },
        )
    }

    /** Fed by the playback service: an update never interrupts the music. */
    fun onPlaybackChanged(isPlaying: Boolean) {
        if (!::app.isInitialized || playing == isPlaying) return
        playing = isPlaying
        if (!isPlaying) stoppedAt = SystemClock.elapsedRealtime()
        scheduleInstall()
    }

    /** "Update" in the dialog: hands the update to Android, which shows its confirmation. */
    fun installNow() {
        val ready = state.value as? State.Ready ?: return
        confirming = true
        _prompt.value = null
        commit(ready, silent = false)
    }

    /** "Later" in the dialog. */
    fun postpone() {
        declined = true
        refreshPrompt()
    }

    private fun onForeground() {
        foreground = true
        installJob?.cancel()
        refreshPrompt()
        val now = SystemClock.elapsedRealtime()
        if (checkJob?.isActive == true || lastCheckAt?.let { now - it < CheckInterval } == true) return
        lastCheckAt = now
        checkJob = scope.launch { check() }
    }

    private fun onBackground() {
        foreground = false
        backgroundAt = SystemClock.elapsedRealtime()
        refreshPrompt()
        scheduleInstall()
    }

    private suspend fun check() {
        val release =
            try {
                withContext(Dispatchers.IO) { VibraReleases.parseLatest(read(BuildConfig.VIBRA_UPDATE_URL)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.d("Update check failed: %s", error.javaClass.simpleName)
                null
            } ?: return
        val dir = updatesDir(app)
        val prefs = prefs(app)
        if (!VibraReleases.isNewer(release.versionName, BuildConfig.VERSION_NAME) ||
            release.versionName == prefs.getString(KeySkipped, null)
        ) {
            withContext(Dispatchers.IO) { dir.deleteRecursively() }
            return
        }
        val apk = File(dir, "VIBRA-${release.versionName}.apk")
        if (state.value !is State.Ready) _state.value = State.Downloading(release)
        val installable =
            try {
                withContext(Dispatchers.IO) {
                    dir.mkdirs()
                    dir.listFiles()?.filter { it != apk }?.forEach(File::delete)
                    if (!apk.exists()) download(release, apk)
                    VibraInstaller.isInstallable(app, apk)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.d("Update download failed: %s", error.javaClass.simpleName)
                _state.value = State.Idle
                return
            }
        if (!installable) {
            // Not a newer build signed with our key: don't download it again at every start.
            Timber.d("Update %s is not installable over this build", release.versionName)
            apk.delete()
            prefs.edit { putString(KeySkipped, release.versionName) }
            _state.value = State.Idle
            return
        }
        _state.value = State.Ready(release, apk)
        refreshPrompt()
        scheduleInstall()
    }

    private fun refreshPrompt() {
        val ready = state.value as? State.Ready
        _prompt.value =
            ready?.release?.takeIf {
                foreground && !declined && !confirming && (confirmationRequired || !VibraInstaller.canInstallSilently(app))
            }
    }

    /** Installs silently once the app has been in the background, with no music, for [QuietPeriod]. */
    private fun scheduleInstall() {
        installJob?.cancel()
        val ready = state.value as? State.Ready ?: return
        if (foreground || playing || confirmationRequired || !VibraInstaller.canInstallSilently(app)) return
        installJob =
            scope.launch {
                delay(QuietPeriod - (SystemClock.elapsedRealtime() - maxOf(backgroundAt, stoppedAt)))
                commit(ready, silent = true)
            }
    }

    private fun commit(
        ready: State.Ready,
        silent: Boolean,
    ) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Read by the new version to say it was updated (the user wasn't looking).
                    prefs(app).edit(commit = true) {
                        if (silent) putString(KeyUpdatedSilently, ready.release.versionName) else remove(KeyUpdatedSilently)
                    }
                    VibraInstaller.install(app, ready.apk, silent)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.d("Update install failed: %s", error.javaClass.simpleName)
                confirming = false
            }
        }
    }

    internal fun onInstallStatus(
        context: Context,
        status: Int,
        confirmation: Intent?,
    ) {
        if (status != PackageInstaller.STATUS_SUCCESS) prefs(context).edit { remove(KeyUpdatedSilently) }
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) confirming = false
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                if (foreground && confirmation != null) {
                    context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    confirmationRequired = true
                }
            PackageInstaller.STATUS_FAILURE_ABORTED -> declined = true
            else -> {
                Timber.d("Update install refused: status %d", status)
                (state.value as? State.Ready)?.let { ready ->
                    ready.apk.delete()
                    prefs(context).edit { putString(KeySkipped, ready.release.versionName) }
                }
                _state.value = State.Idle
            }
        }
        if (::app.isInitialized) refreshPrompt()
    }

    /** First broadcast of the new version: tidy up, and announce an update the user didn't see. */
    internal fun onUpdated(context: Context) {
        val prefs = prefs(context)
        val silently = prefs.getString(KeyUpdatedSilently, null)
        prefs.edit { remove(KeyUpdatedSilently) }
        updatesDir(context).deleteRecursively()
        if (silently != null) VibraInstaller.notifyUpdated(context)
    }

    private fun read(url: String): String =
        open(url, accept = "application/vnd.github+json").run {
            try {
                inputStream.bufferedReader().use { it.readText() }
            } finally {
                disconnect()
            }
        }

    private fun download(
        release: VibraRelease,
        apk: File,
    ) {
        val part = File(apk.path + ".part")
        val sha256 = MessageDigest.getInstance("SHA-256")
        try {
            open(release.apkUrl).run {
                try {
                    inputStream.use { input ->
                        part.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                sha256.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                } finally {
                    disconnect()
                }
            }
            if (release.apkSize >= 0 && part.length() != release.apkSize) throw IOException("Size mismatch")
            val digest = sha256.digest().joinToString("") { "%02x".format(it) }
            if (release.sha256 != null && digest != release.sha256) throw IOException("Digest mismatch")
            if (!part.renameTo(apk)) throw IOException("Rename failed")
        } finally {
            part.delete()
        }
    }

    private fun open(
        url: String,
        accept: String? = null,
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            accept?.let { setRequestProperty("Accept", it) }
            setRequestProperty("User-Agent", "VIBRA/${BuildConfig.VERSION_NAME}")
            if (responseCode !in 200..299) {
                disconnect()
                throw IOException("HTTP $responseCode")
            }
        }

    private fun updatesDir(context: Context) = File(context.noBackupFilesDir, "updates")

    private fun prefs(context: Context) = context.getSharedPreferences("vibra_updates", Context.MODE_PRIVATE)

    private const val CheckInterval = 60 * 60 * 1000L
    private const val QuietPeriod = 30 * 1000L
    private const val KeySkipped = "skipped_version"
    private const val KeyUpdatedSilently = "updated_silently"
}
