package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TransferHistoryDatabase
import com.example.data.local.TransferHistoryRepository
import com.example.files.FileSelectionManager
import com.example.security.SecurityUtils
import com.example.testing.RealP2PTestRunner
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: TransferHistoryDatabase
    private lateinit var repository: TransferHistoryRepository
    private lateinit var fileSelectionManager: FileSelectionManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TransferHistoryDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = TransferHistoryRepository(database.historyDao())
        fileSelectionManager = FileSelectionManager(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun verifySha256AndQrPayloadParsing() {
        val sampleBytes = "WASSIL Direct P2P Transfer Integrity Test".toByteArray(Charsets.UTF_8)
        val sha1 = SecurityUtils.computeSha256(ByteArrayInputStream(sampleBytes))
        val sha2 = SecurityUtils.computeSha256(ByteArrayInputStream(sampleBytes))
        assertEquals(64, sha1.length)
        assertEquals(sha1, sha2)

        val qr = SecurityUtils.buildQrPayload(
            sessionId = "abc123session",
            pairingCode = "482915",
            ip = "192.168.1.25",
            tcpPort = 54123,
            webPort = 54124,
            senderName = "Hossam's Phone"
        )
        val parsed = SecurityUtils.parseQrPayload(qr)
        assertTrue(parsed != null)
        assertEquals("482915", parsed?.pairingCode)
        assertEquals("192.168.1.25", parsed?.ip)
        assertEquals(54123, parsed?.tcpPort)
        assertEquals("Hossam's Phone", parsed?.senderName)
    }

    @Test
    fun verifyAllTenRealP2PTransferScenarios() = runBlocking {
        val runner = RealP2PTestRunner(fileSelectionManager, repository)
        val results = runner.runAllTenScenarios(this) {}
        assertEquals(10, results.size)
        results.forEach { scenario ->
            assertTrue(
                "Scenario ${scenario.id} (${scenario.titleEn}) failed: ${scenario.details}",
                scenario.passed
            )
        }
    }
}
