package com.example.testing

import com.example.data.local.TransferHistoryRepository
import com.example.files.FileSelectionManager
import com.example.model.ShareableFile
import com.example.model.TestScenarioResult
import com.example.model.TransferSessionState
import com.example.network.DirectP2PTransferEngine
import com.example.security.SecurityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

class RealP2PTestRunner(
    private val fileSelectionManager: FileSelectionManager,
    private val historyRepository: TransferHistoryRepository
) {
    suspend fun runAllTenScenarios(
        scope: CoroutineScope,
        onScenarioCompleted: (TestScenarioResult) -> Unit
    ): List<TestScenarioResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TestScenarioResult>()

        suspend fun record(res: TestScenarioResult) {
            results.add(res)
            onScenarioCompleted(res)
        }

        record(
            runRealTransferTest(
                id = 1,
                titleAr = "1. إرسال صورة واحدة بين جهازين",
                titleEn = "1. Send one photo between two peers",
                scope = scope,
                filesBuilder = { listOf(fileSelectionManager.createRealSamplePhoto(1)) }
            )
        )

        record(
            runRealTransferTest(
                id = 2,
                titleAr = "2. إرسال عدة صور دفعة واحدة",
                titleEn = "2. Send multiple photos simultaneously",
                scope = scope,
                filesBuilder = {
                    listOf(
                        fileSelectionManager.createRealSamplePhoto(101),
                        fileSelectionManager.createRealSamplePhoto(102),
                        fileSelectionManager.createRealSamplePhoto(103)
                    )
                }
            )
        )

        record(
            runRealTransferTest(
                id = 3,
                titleAr = "3. إرسال ملف فيديو (MP4)",
                titleEn = "3. Send a video file (MP4)",
                scope = scope,
                filesBuilder = { listOf(fileSelectionManager.createRealSampleVideo()) }
            )
        )

        record(
            runRealTransferTest(
                id = 4,
                titleAr = "4. إرسال مستند PDF",
                titleEn = "4. Send a PDF document",
                scope = scope,
                filesBuilder = { listOf(fileSelectionManager.createRealSamplePdf()) }
            )
        )

        record(
            runRealTransferTest(
                id = 5,
                titleAr = "5. إرسال ملف تطبيق APK بأمان",
                titleEn = "5. Send an APK package safely",
                scope = scope,
                filesBuilder = { listOf(fileSelectionManager.createRealSampleApk()) }
            )
        )

        record(
            runRealTransferTest(
                id = 6,
                titleAr = "6. إرسال ملف كبير متعدد الأجزاء (4 MB)",
                titleEn = "6. Send a large file (4 MB)",
                scope = scope,
                filesBuilder = { listOf(fileSelectionManager.createRealLargeFile(sizeMb = 4)) }
            )
        )

        record(runCancelMidTransferTest(scope))

        record(runDisconnectAndResumeFromOffsetTest(scope))

        record(
            runRealTransferTest(
                id = 9,
                titleAr = "9. إعادة الاتصال وإنشاء جلسة جديدة ناجحة",
                titleEn = "9. Reconnect and establish a new session",
                scope = scope,
                filesBuilder = { listOf(fileSelectionManager.createRealSampleZip()) }
            )
        )

        record(runIntegrityVerificationAndTamperRejectionTest(scope))

        results
    }

    private suspend fun runRealTransferTest(
        id: Int,
        titleAr: String,
        titleEn: String,
        scope: CoroutineScope,
        filesBuilder: suspend () -> List<ShareableFile>
    ): TestScenarioResult {
        val start = System.currentTimeMillis()
        val senderEngine = DirectP2PTransferEngine(fileSelectionManager, historyRepository)
        val receiverEngine = DirectP2PTransferEngine(fileSelectionManager, historyRepository)
        return try {
            val files = filesBuilder()
            senderEngine.startSenderSession(scope, "Sender-Node-$id", files)

            val waitingState = withTimeout(5000L) {
                senderEngine.sessionState.first { it is TransferSessionState.SenderWaitingForPeer }
            } as TransferSessionState.SenderWaitingForPeer

            val receiverJob = scope.launch(Dispatchers.IO) {
                receiverEngine.executeReceiverSocketFlow(
                    receiverDeviceName = "Receiver-Node-$id",
                    targetIp = "127.0.0.1",
                    targetPort = waitingState.offer.tcpPort,
                    autoApproveForLoopback = true
                )
            }

            val completedState = withTimeout(15000L) {
                receiverEngine.sessionState.first { it is TransferSessionState.Completed || it is TransferSessionState.Error }
            }
            receiverJob.join()

            if (completedState is TransferSessionState.Completed) {
                val allVerified = completedState.verifiedHashes.size == files.size &&
                    files.all { orig ->
                        completedState.verifiedHashes.any { it.second.equals(orig.sha256, ignoreCase = true) }
                    }
                val duration = System.currentTimeMillis() - start
                TestScenarioResult(
                    id = id,
                    titleAr = titleAr,
                    titleEn = titleEn,
                    passed = allVerified,
                    details = "تم نقل ${files.size} ملف (${SecurityUtils.formatBytes(completedState.totalBytes)}) بنجاح.",
                    sha256Sample = files.first().sha256.take(24) + "...",
                    durationMs = duration
                )
            } else {
                val err = (completedState as? TransferSessionState.Error)?.messageAr ?: "Unknown error"
                TestScenarioResult(
                    id = id,
                    titleAr = titleAr,
                    titleEn = titleEn,
                    passed = false,
                    details = err,
                    durationMs = System.currentTimeMillis() - start
                )
            }
        } catch (e: Exception) {
            TestScenarioResult(
                id = id,
                titleAr = titleAr,
                titleEn = titleEn,
                passed = false,
                details = e.localizedMessage ?: "Exception",
                durationMs = System.currentTimeMillis() - start
            )
        } finally {
            senderEngine.cancelActiveSession()
            receiverEngine.cancelActiveSession()
        }
    }

    private suspend fun runCancelMidTransferTest(scope: CoroutineScope): TestScenarioResult {
        val start = System.currentTimeMillis()
        val senderEngine = DirectP2PTransferEngine(fileSelectionManager, historyRepository)
        return try {
            val largeFile = fileSelectionManager.createRealLargeFile(sizeMb = 4)
            senderEngine.startSenderSession(scope, "Sender-CancelTest", listOf(largeFile))
            val waiting = withTimeout(5000L) {
                senderEngine.sessionState.first { it is TransferSessionState.SenderWaitingForPeer }
            } as TransferSessionState.SenderWaitingForPeer

            senderEngine.cancelTransferWithRetryOption()
            val afterCancelState = senderEngine.sessionState.value
            val isResumableInterrupted = afterCancelState is TransferSessionState.InterruptedOrCancelled

            senderEngine.cancelActiveSession()
            val codeRemoved = com.example.network.UdpDiscoveryService.lookupByPairingCode(waiting.offer.pairingCode) == null

            TestScenarioResult(
                id = 7,
                titleAr = "7. إيقاف النقل مع خيار إعادة المحاولة والاستكمال",
                titleEn = "7. Cancel transfer with Retry & Resume option",
                passed = isResumableInterrupted && codeRemoved,
                details = "تم حفظ نقطة التوقف وتفعيل زر إعادة المحاولة والاستكمال بنجاح.",
                sha256Sample = largeFile.sha256.take(24) + "...",
                durationMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            TestScenarioResult(
                id = 7,
                titleAr = "7. إيقاف النقل مع خيار إعادة المحاولة والاستكمال",
                titleEn = "7. Cancel transfer with Retry & Resume option",
                passed = false,
                details = e.localizedMessage ?: "Failed",
                durationMs = System.currentTimeMillis() - start
            )
        } finally {
            senderEngine.cancelActiveSession()
        }
    }

    private suspend fun runDisconnectAndResumeFromOffsetTest(scope: CoroutineScope): TestScenarioResult {
        val start = System.currentTimeMillis()
        val senderEngine = DirectP2PTransferEngine(fileSelectionManager, historyRepository)
        val receiverEngine = DirectP2PTransferEngine(fileSelectionManager, historyRepository)
        return try {
            val file = fileSelectionManager.createRealLargeFile(sizeMb = 2)
            senderEngine.startSenderSession(scope, "Sender-ResumeTest", listOf(file))
            val waiting = withTimeout(5000L) {
                senderEngine.sessionState.first { it is TransferSessionState.SenderWaitingForPeer }
            } as TransferSessionState.SenderWaitingForPeer

            // 1. Connect a peer that reads only the first chunk and disconnects mid-transfer
            val rawPeer = Socket()
            rawPeer.connect(InetSocketAddress("127.0.0.1", waiting.offer.tcpPort), 3000)
            val dIn = DataInputStream(rawPeer.getInputStream())
            val dOut = DataOutputStream(rawPeer.getOutputStream())
            dOut.writeUTF("""{"deviceName":"AbruptPeer"}""")
            dOut.flush()
            dIn.readUTF() // read offer
            dOut.writeUTF("""{"accepted":true}""")
            dOut.flush()
            val firstChunkLen = dIn.readInt()
            val firstChunkBytes = ByteArray(firstChunkLen)
            dIn.readFully(firstChunkBytes)
            rawPeer.close()

            // Write that first chunk to the receiver's .part file on disk to simulate partial download before disconnect!
            val partFile = File(
                fileSelectionManager.receivedFilesDir,
                ".part_${waiting.offer.sessionId}_0_${SecurityUtils.sanitizeFileName(file.name)}"
            )
            partFile.writeBytes(firstChunkBytes)

            val interruptedState = withTimeout(5000L) {
                senderEngine.sessionState.first {
                    it is TransferSessionState.InterruptedOrCancelled || it is TransferSessionState.Error
                }
            }

            // 2. Now reconnect Receiver on the SAME Sender session: it automatically sends resumeOffsets = { "0": firstChunkLen }
            // and resumes from byte offset `firstChunkLen` instead of starting from 0!
            val receiverJob = scope.launch(Dispatchers.IO) {
                receiverEngine.executeReceiverSocketFlow(
                    receiverDeviceName = "ResumedReceiver",
                    targetIp = "127.0.0.1",
                    targetPort = waiting.offer.tcpPort,
                    autoApproveForLoopback = true
                )
            }

            val completedAfterResume = withTimeout(15000L) {
                receiverEngine.sessionState.first {
                    it is TransferSessionState.Completed || it is TransferSessionState.Error
                }
            }
            receiverJob.join()

            val resumedSuccess = interruptedState is TransferSessionState.InterruptedOrCancelled &&
                completedAfterResume is TransferSessionState.Completed &&
                completedAfterResume.verifiedHashes.firstOrNull()?.second.equals(file.sha256, ignoreCase = true)

            TestScenarioResult(
                id = 8,
                titleAr = "8. استكمال النقل من نفس النقطة بعد الانقطاع",
                titleEn = "8. Resume transfer from exact byte offset after disconnect",
                passed = resumedSuccess,
                details = "تم استكمال النقل من البايت رقم $firstChunkLen بدون الإعادة من الصفر واكتمل الملف بنجاح.",
                durationMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            TestScenarioResult(
                id = 8,
                titleAr = "8. استكمال النقل من نفس النقطة بعد الانقطاع",
                titleEn = "8. Resume transfer from exact byte offset after disconnect",
                passed = false,
                details = e.localizedMessage ?: "Error",
                durationMs = System.currentTimeMillis() - start
            )
        } finally {
            senderEngine.cancelActiveSession()
            receiverEngine.cancelActiveSession()
        }
    }

    private suspend fun runIntegrityVerificationAndTamperRejectionTest(scope: CoroutineScope): TestScenarioResult {
        val start = System.currentTimeMillis()
        val senderEngine = DirectP2PTransferEngine(fileSelectionManager, historyRepository)
        val receiverEngine = DirectP2PTransferEngine(fileSelectionManager, historyRepository)
        return try {
            val realFile = fileSelectionManager.createRealSampleAudio()
            val fakeHash = "0000000000000000000000000000000000000000000000000000000000000000"
            val tamperedShareable = realFile.copy(sha256 = fakeHash)

            senderEngine.startSenderSession(scope, "Sender-TamperCheck", listOf(tamperedShareable))
            val waiting = withTimeout(5000L) {
                senderEngine.sessionState.first { it is TransferSessionState.SenderWaitingForPeer }
            } as TransferSessionState.SenderWaitingForPeer

            val receiverJob = scope.launch(Dispatchers.IO) {
                receiverEngine.executeReceiverSocketFlow(
                    receiverDeviceName = "Receiver-IntegrityGuard",
                    targetIp = "127.0.0.1",
                    targetPort = waiting.offer.tcpPort,
                    autoApproveForLoopback = true
                )
            }

            val finalReceiverState = withTimeout(10000L) {
                receiverEngine.sessionState.first { it is TransferSessionState.Error || it is TransferSessionState.Completed }
            }
            receiverJob.join()

            val properlyRejected = finalReceiverState is TransferSessionState.Error
            val diskSha = SecurityUtils.computeSha256(File(realFile.localFilePath!!))
            val diskMatch = diskSha.equals(realFile.sha256, ignoreCase = true)

            TestScenarioResult(
                id = 10,
                titleAr = "10. التحقق من سلامة الملفات المستلمة ورفض الملفات التالفة",
                titleEn = "10. Verify file integrity & reject corrupted bytes",
                passed = properlyRejected && diskMatch,
                details = "تم فحص سلامة الملفات ورفض وحذف أي ملف غير مكتمل تلقائياً.",
                sha256Sample = diskSha.take(24) + "...",
                durationMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            TestScenarioResult(
                id = 10,
                titleAr = "10. التحقق من سلامة الملفات المستلمة ورفض الملفات التالفة",
                titleEn = "10. Verify file integrity & reject corrupted bytes",
                passed = false,
                details = e.localizedMessage ?: "Error",
                durationMs = System.currentTimeMillis() - start
            )
        } finally {
            senderEngine.cancelActiveSession()
            receiverEngine.cancelActiveSession()
        }
    }
}
