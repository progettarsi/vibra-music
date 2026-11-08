/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class RestoreTransactionTest {
    @get:Rule val folder = TemporaryFolder()

    private lateinit var live: File
    private lateinit var root: File
    private lateinit var transaction: RestoreTransaction
    private val oldDb = sqlite("old")
    private val newDb = sqlite("new")

    private fun sqlite(body: String) = "SQLite format 3\u0000$body".toByteArray()

    private fun liveFile(suffix: String = "") = File(live, "song.db$suffix")

    @Before fun setUp() {
        live = folder.newFolder("databases")
        root = File(folder.root, "restore")
        transaction = RestoreTransaction(root, live, "song.db")
        liveFile().writeBytes(oldDb)
        liveFile("-wal").writeBytes(byteArrayOf(1, 2, 3))
        transaction.begin()
    }

    private fun assertOriginalIntact() {
        assertArrayEquals(oldDb, liveFile().readBytes())
        assertArrayEquals(byteArrayOf(1, 2, 3), liveFile("-wal").readBytes())
    }

    @Test fun streamFailingHalfwayLeavesLiveDatabaseUntouched() {
        val failing =
            object : InputStream() {
                private var left = 10

                override fun read(): Int {
                    if (left == 0) throw IOException("disk full")
                    left--
                    return 7
                }
            }
        assertThrows(IOException::class.java) { transaction.stage("song.db", failing) }
        transaction.discardStaging()
        assertOriginalIntact()
    }

    @Test fun contentThatIsNotSqliteIsRecognisedBeforeTheSwap() {
        transaction.stage("song.db", ByteArrayInputStream("not a database, just text".toByteArray()))
        assertFalse(transaction.stagedDatabaseHasSqliteHeader())
        assertOriginalIntact()
    }

    @Test fun truncatedHeaderIsRejected() {
        transaction.stage("song.db", ByteArrayInputStream("SQLite".toByteArray()))
        assertFalse(transaction.stagedDatabaseHasSqliteHeader())
    }

    @Test fun commitSwapsFilesAndClearsOldSidecars() {
        transaction.stage("song.db", ByteArrayInputStream(newDb))
        assertTrue(transaction.stagedDatabaseHasSqliteHeader())
        transaction.commit()
        assertArrayEquals(newDb, liveFile().readBytes())
        assertFalse(liveFile("-wal").exists())
    }

    @Test fun aRefusedSwapDoesNotRollBackToAnOlderLeftover() {
        File(root, "rollback").mkdirs()
        File(root, "rollback/song.db").writeBytes(sqlite("older"))
        transaction.stage("song.db", ByteArrayInputStream(newDb))
        assertThrows(IOException::class.java) { transaction.commit() }
        transaction.undoSwap()
        assertOriginalIntact()
    }

    @Test fun aSecondSwapIsRefusedWhileOneIsInProgress() {
        transaction.stage("song.db", ByteArrayInputStream(newDb))
        File(root, "commit-in-progress").createNewFile()
        assertThrows(IOException::class.java) { transaction.commit() }
        assertOriginalIntact()
    }

    @Test fun crashDuringSwapIsUndoneAtNextStart() {
        // State left by a process killed after the old files moved and before the new ones did.
        File(root, "rollback").mkdirs()
        liveFile().renameTo(File(root, "rollback/song.db"))
        liveFile("-wal").renameTo(File(root, "rollback/song.db-wal"))
        File(root, "commit-in-progress").createNewFile()

        assertFalse(RestoreTransaction(root, live, "song.db").recover())
        assertOriginalIntact()
        assertFalse(File(root, "commit-in-progress").exists())
    }

    @Test fun crashBeforeAnyOldFileMovesPreservesTheOriginal() {
        File(root, "rollback").mkdirs()
        File(root, "commit-in-progress").createNewFile()
        assertFalse(transaction.recover())
        assertOriginalIntact()
    }

    @Test fun crashAfterOnlyTheDatabaseMovedPreservesItsUnmovedWal() {
        File(root, "rollback").mkdirs()
        assertTrue(liveFile().renameTo(File(root, "rollback/song.db")))
        File(root, "commit-in-progress").createNewFile()
        assertFalse(transaction.recover())
        assertOriginalIntact()
    }

    @Test fun recoveryCanBeRepeatedAfterItRestoredOnlyOneFile() {
        transaction.stage("song.db", ByteArrayInputStream(newDb))
        transaction.commit()
        File(root, "rollback/song.db").copyTo(liveFile(), overwrite = true)
        File(root, "commit-in-progress").createNewFile()
        assertFalse(transaction.recover())
        assertOriginalIntact()
    }

    @Test fun aPendingSuccessfulSwapCannotLoseItsRollbackToAnotherCommit() {
        transaction.stage("song.db", ByteArrayInputStream(newDb))
        transaction.commit()
        transaction.begin()
        transaction.stage("song.db", ByteArrayInputStream(sqlite("third")))
        assertThrows(IOException::class.java) { transaction.commit() }
        transaction.revert()
        assertOriginalIntact()
    }

    @Test fun restoredDatabaseThatDoesNotOpenIsReverted() {
        transaction.stage("song.db", ByteArrayInputStream(newDb))
        transaction.commit()

        val nextStart = RestoreTransaction(root, live, "song.db")
        assertTrue(nextStart.recover())
        nextStart.revert()
        assertOriginalIntact()
        assertFalse(nextStart.recover())
    }

    @Test fun previousDatabaseIsKeptUntilTheRestoredOneOpens() {
        transaction.stage("song.db", ByteArrayInputStream(newDb))
        transaction.commit()
        assertArrayEquals(oldDb, File(root, "rollback/song.db").readBytes())

        val nextStart = RestoreTransaction(root, live, "song.db")
        assertTrue(nextStart.recover())
        nextStart.finish()
        assertFalse(File(root, "rollback").exists())
        assertArrayEquals(newDb, liveFile().readBytes())
        assertEquals(false, nextStart.recover())
    }
}
