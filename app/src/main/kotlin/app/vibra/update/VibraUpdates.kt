/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.update

import app.vibra.power.PowerSave
import app.vibra.power.PowerSaveLevel
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
 *  1. Check   – whenever the app is opened, and every [CheckInterval] while it stays open,
 *               read the latest release from BuildConfig.VIBRA_UPDATE_URL. The playback
 *               service keeps the process alive for days, so "opened" is the foreground,
 *               not the process start. Repeated reads are conditional (ETag): GitHub
 *               answers 304 and doesn't count them against its rate limit.
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
    private var watchJob: Job? = null
    private var installJob: Job? = null
    private var lastCheckAt: Long? = null
    private var foreground = false
    private var playing = false
    private var backgroundAt = 0L
    private var stoppedAt = 0L

    /** When the user closed the dialog or Android's confirmation: ask again after [AskAgainAfter]. */
    private var declinedAt: Long? = null

    /** The last answer from the update source, reused when it replies 304 Not Modified. */
    @Volatile private var latest: Latest? = null

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
        declinedAt = SystemClock.elapsedRealtime()
        refreshPrompt()
    }

    private fun onForeground() {
        foreground = true
        installJob?.cancel()
        refreshPrompt()
        watchJob?.cancel()
        watchJob =
            scope.launch {
                while (true) {
                    val wait = lastCheckAt?.let { CheckInterval - (SystemClock.elapsedRealtime() - it) } ?: 0L
                    // Battery saver at its maximum: the check on opening the app stays, the
                    // periodic re-checks while it is open don't.
                    val saving = PowerSave.level.value == PowerSaveLevel.MAX
                    if (wait > 0) {
                        if (saving) break
                        delay(wait)
                        continue
                    }
                    lastCheckAt = SystemClock.elapsedRealtime()
                    // Not tied to the foreground: a download goes on when the user leaves.
                    if (checkJob?.isActive != true) checkJob = scope.launch { check() }
                    if (saving) break
                }
            }
    }

    private fun onBackground() {
        foreground = false
        watchJob?.cancel()
        backgroundAt = SystemClock.elapsedRealtime()
        refreshPrompt()
        scheduleInstall()
    }

    private suspend fun check() {
        val release =
            try {
                withContext(Dispatchers.IO) { VibraReleases.parseLatest(readLatest()) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.d("Update check failed: %s", error.javaClass.simpleName)
                null
            } ?: return
        Timber.d("Update check: latest %s", release.versionName)
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
                foreground && !confirming &&
                    declinedAt?.let { at -> SystemClock.elapsedRealtime() - at < AskAgainAfter } != true &&
                    (confirmationRequired || !VibraInstaller.canInstallSilently(app))
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
            PackageInstaller.STATUS_FAILURE_ABORTED -> declinedAt = SystemClock.elapsedRealtime()
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

    private fun readLatest(): String {
        val cached = latest
        return open(BuildConfig.VIBRA_UPDATE_URL, accept = "application/vnd.github+json", etag = cached?.etag).run {
            try {
                if (cached != null && responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) return cached.body
                inputStream.bufferedReader().use { it.readText() }.also { body ->
                    latest = getHeaderField("ETag")?.let { Latest(it, body) }
                }
            } finally {
                disconnect()
            }
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
        etag: String? = null,
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            accept?.let { setRequestProperty("Accept", it) }
            etag?.let { setRequestProperty("If-None-Match", it) }
            setRequestProperty("User-Agent", "VIBRA/${BuildConfig.VERSION_NAME}")
            val notModified = etag != null && responseCode == HttpURLConnection.HTTP_NOT_MODIFIED
            if (responseCode !in 200..299 && !notModified) {
                disconnect()
                throw IOException("HTTP $responseCode")
            }
        }

    private fun updatesDir(context: Context) = File(context.noBackupFilesDir, "updates")

    private fun prefs(context: Context) = context.getSharedPreferences("vibra_updates", Context.MODE_PRIVATE)

    private class Latest(
        val etag: String,
        val body: String,
    )

    private const val CheckInterval = 15 * 60 * 1000L
    private const val AskAgainAfter = 4 * 60 * 60 * 1000L
    private const val QuietPeriod = 30 * 1000L
    private const val KeySkipped = "skipped_version"
    private const val KeyUpdatedSilently = "updated_silently"
}
