/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.update

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import java.io.File

/**
 * Hands a downloaded update to Android's [PackageInstaller].
 *
 * Android 12+ lets an app update itself without asking, once the user has allowed VIBRA to
 * install apps ("install unknown apps"): that is the silent path, used while the app is in
 * the background. Otherwise Android shows its own confirmation, which only makes sense
 * while the user is looking at the app — [VibraUpdates] decides when.
 */
internal object VibraInstaller {
    /** True when Android will install an update without showing anything. */
    fun canInstallSilently(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context.packageManager.canRequestPackageInstalls()

    /**
     * True when [apk] is a newer build of this very app, signed with the same key. Android
     * would refuse anything else anyway; checking first avoids asking the user for nothing.
     */
    fun isInstallable(
        context: Context,
        apk: File,
    ): Boolean {
        val pm = context.packageManager
        val archive = pm.archiveInfo(apk) ?: return false
        val installed = runCatching { pm.packageInfo(context.packageName) }.getOrNull() ?: return false
        return archive.packageName == context.packageName &&
            PackageInfoCompat.getLongVersionCode(archive) > PackageInfoCompat.getLongVersionCode(installed) &&
            archive.signers() == installed.signers() &&
            archive.signers().isNotEmpty()
    }

    /** Writes [apk] into an install session and commits it; the outcome reaches [VibraInstallReceiver]. */
    fun install(
        context: Context,
        apk: File,
        silent: Boolean,
    ) {
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
                if (silent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("vibra.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val callback =
                    PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        Intent(context, VibraInstallReceiver::class.java).setAction(ActionInstallStatus),
                        // Mutable: the installer adds the status extras to this explicit intent.
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                session.commit(callback.intentSender)
            }
        } catch (error: Exception) {
            installer.abandonSession(sessionId)
            throw error
        }
    }

    /** "VIBRA was updated" — only after an update that happened while the user was away. */
    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers POST_NOTIFICATIONS
    fun notifyUpdated(context: Context) {
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    ChannelId,
                    context.getString(R.string.vibra_update_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val open =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
            }
        val notification =
            NotificationCompat
                .Builder(context, ChannelId)
                .setSmallIcon(R.drawable.small_icon)
                .setContentTitle(context.getString(R.string.vibra_update_done_title))
                .setContentText(context.getString(R.string.vibra_update_done_text, BuildConfig.VERSION_NAME))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        notifications.notify(NotificationId, notification)
    }

    private fun PackageManager.archiveInfo(apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNATURES)
        }

    private fun PackageManager.packageInfo(packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }

    private fun PackageInfo.signers(): Set<String> {
        val signatures =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                signatures
            }
        return signatures.orEmpty().mapTo(HashSet()) { it.toCharsString() }
    }

    const val ActionInstallStatus = "vibra.music.com.action.UPDATE_INSTALL_STATUS"
    private const val ChannelId = "vibra_updates"
    private const val NotificationId = 0x0b1b
}

/**
 * Receives the install session's outcome and, after an update, the first broadcast of the
 * new version ([Intent.ACTION_MY_PACKAGE_REPLACED]).
 */
class VibraInstallReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> VibraUpdates.onUpdated(context)
            VibraInstaller.ActionInstallStatus ->
                VibraUpdates.onInstallStatus(
                    context = context,
                    status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE),
                    confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java),
                )
        }
    }
}
