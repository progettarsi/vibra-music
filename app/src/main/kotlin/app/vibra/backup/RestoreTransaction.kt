/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.backup

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Replaces the library database in a way that a failed restore cannot damage it.
 *
 * Backup files are first written to a staging folder and checked there; the live files are not
 * touched until that succeeds. The swap only renames files. The previous database is kept aside
 * until the new one has opened, and a marker makes a swap cut short by a crash undo itself at the
 * next start, before the database is opened.
 */
class RestoreTransaction(
    root: File,
    private val liveDir: File,
    private val databaseName: String,
) {
    private val stagingDir = File(root, "staging")
    private val rollbackDir = File(root, "rollback")
    private val commitMarker = File(root, "commit-in-progress")
    private val installedMarker = File(root, "old-files-moved")
    private val fileNames = DATABASE_SUFFIXES.map { databaseName + it }
    private var swapStarted = false

    val stagedDatabase: File get() = File(stagingDir, databaseName)

    /** The folder for everything being restored; [begin] empties it. */
    val staging: File get() = stagingDir

    fun begin() {
        stagingDir.deleteRecursively()
        if (!stagingDir.mkdirs()) throw IOException("Cannot create the restore folder")
    }

    /** Copies one backup entry into staging. A failure here leaves the live database untouched. */
    fun stage(
        name: String,
        input: InputStream,
    ) {
        File(stagingDir, name).outputStream().use { input.copyTo(it) }
    }

    /** True when [stagedDatabase] starts with the SQLite header; anything else is not a database. */
    fun stagedDatabaseHasSqliteHeader(): Boolean {
        val file = stagedDatabase
        if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
        val header = ByteArray(SQLITE_HEADER.size)
        file.inputStream().use { if (it.read(header) != header.size) return false }
        return header.contentEquals(SQLITE_HEADER)
    }

    /** Puts the staged database in place. On failure the previous files are back when it throws. */
    fun commit() {
        check(stagedDatabase.isFile) { "No staged database" }
        if (rollbackDir.exists() || commitMarker.exists() || installedMarker.exists()) throw IOException("A restore is already pending")
        if (!rollbackDir.mkdirs()) throw IOException("Cannot create the rollback folder")
        if (!commitMarker.createNewFile()) throw IOException("A restore is already in progress")
        swapStarted = true
        try {
            fileNames.forEach { name -> move(File(liveDir, name), File(rollbackDir, name)) }
            if (!installedMarker.createNewFile()) throw IOException("Cannot mark the database swap")
            fileNames.forEach { name -> move(File(stagingDir, name), File(liveDir, name)) }
        } catch (e: IOException) {
            putPreviousBack()
            throw e
        }
        if (!commitMarker.delete()) throw IOException("Cannot complete the restore")
    }

    /**
     * Runs before the database opens. Undoes an interrupted swap, and returns true when a restored
     * database is opening for the first time: the caller then reports back with [revert] or [finish].
     */
    fun recover(): Boolean {
        if (commitMarker.exists()) {
            putPreviousBack()
            return false
        }
        stagingDir.deleteRecursively()
        return rollbackDir.exists()
    }

    /** The restored database did not open: the previous one comes back. */
    fun revert() = putPreviousBack()

    /** A later step of this same restore failed. A swap that never started has nothing of its own to undo. */
    fun undoSwap() {
        if (swapStarted) putPreviousBack()
    }

    /** The restored database opened: the previous one can go. */
    fun finish() {
        if (installedMarker.exists() && !installedMarker.delete()) throw IOException("Cannot finish the restore")
        rollbackDir.deleteRecursively()
    }

    fun discardStaging() {
        stagingDir.deleteRecursively()
    }

    private fun putPreviousBack() {
        // Until every old file has moved, files still in liveDir belong to the original DB.
        // Keep rollback copies until all replacements succeed, so recovery can itself be retried.
        val swapped = installedMarker.exists()
        fileNames.forEach { name ->
            val previous = File(rollbackDir, name)
            val live = File(liveDir, name)
            if (previous.exists()) {
                val replacement = File(liveDir, "$name.restore")
                previous.copyTo(replacement, overwrite = true)
                if (live.exists() && !live.delete()) throw IOException("Cannot replace $name")
                move(replacement, live)
            } else if (swapped && live.exists() && !live.delete()) {
                throw IOException("Cannot remove restored $name")
            }
        }
        if (installedMarker.exists() && !installedMarker.delete()) throw IOException("Cannot clear the swap marker")
        if (commitMarker.exists() && !commitMarker.delete()) throw IOException("Cannot clear the restore marker")
        rollbackDir.deleteRecursively()
        stagingDir.deleteRecursively()
    }

    private fun move(
        from: File,
        to: File,
    ) {
        if (from.exists() && !from.renameTo(to)) throw IOException("Cannot move ${from.name}")
    }

    companion object {
        /** Each is a database file only under its own suffix; "-shm" is rebuilt by SQLite and never restored. */
        val DATABASE_SUFFIXES = listOf("", "-wal", "-shm", "-journal")

        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray()

        fun forApp(
            context: Context,
            databaseName: String,
        ) = RestoreTransaction(
            root = File(context.filesDir, "vibra-restore"),
            liveDir = context.getDatabasePath(databaseName).parentFile!!,
            databaseName = databaseName,
        )
    }
}
