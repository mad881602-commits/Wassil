package com.example.network

import android.content.Context
import com.example.data.local.TransferHistoryEntity
import com.example.data.local.TransferHistoryRepository
import com.example.files.FileSelectionManager
import com.example.model.FileCategory
import com.example.model.FileTransferProgress
import com.example.model.ShareableFile
import com.example.model.TransferDirection
import com.example.model.TransferFileManifestItem
import com.example.model.TransferSessionOffer
import com.example.model.TransferSessionState
import com.example.model.TransferStatus
import com.example.security.SecurityUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Peer-to-Peer Transfer Engine with:
 * 1. Automatic Sender Hotspot / Wi-Fi Direct group creation & Receiver direct hotspot connection on QR scan.
 * 2. Byte-Offset Resumable Transfer: if a transfer is cancelled or interrupted, partial .part files
 *    are preserved on disk, and tapping "Retry & Resume" resumes from the exact byte offset where it stopped!
 */
class DirectP2PTransferEngine(
    private val fileSelectionManager: FileSelectionManager,
    private val historyRepository: TransferHistoryRepository
) {
    private val _sessionState = MutableStateFlow<TransferSessionState>(TransferSessionState.Idle)
    val sessionState: StateFlow<TransferSessionState> = _sessionState.asStateFlow()

    private val isPausedFlag = AtomicBoolean(false)
    private val isCancelledFlag = AtomicBoolean(false)

    private var activeServerSocket: ServerSocket? = null
    private var activeClientSocket: Socket? = null
    private var activeSessionJob: Job? = null
    private var discoveryBroadcastJob: Job? = null
    private val webPwaServer = EmbeddedWebPwaServer(fileSelectionManager)

    private var currentOffer: TransferSessionOffer? = null
    private var currentSelectedFiles: List<ShareableFile> = emptyList()
    private var lastQrPayload: String = ""

    // Saved connection parameters for Byte-Offset Retry & Resume
    private var lastDirection: TransferDirection = TransferDirection.SENT
    private var lastPeerDeviceName: String = ""
    private var lastProgress: FileTransferProgress? = null
    private var lastReceiverDeviceName: String = ""
    private var lastTargetCandidateIps: List<String> = emptyList()
    private var lastTargetPort: Int = 0
    private var lastWifiSsid: String = ""
    private var lastWifiPassword: String = ""

    fun togglePauseResume() {
        val newPaused = !isPausedFlag.get()
        isPausedFlag.set(newPaused)
        val current = _sessionState.value
        if (current is TransferSessionState.Transferring) {
            val updated = current.progress.copy(isPaused = newPaused)
            lastProgress = updated
            _sessionState.value = current.copy(progress = updated)
        }
    }

    /**
     * Stops the current socket stream and transitions to InterruptedOrCancelled WITHOUT deleting
     * partial .part files or closing the Sender ServerSocket, so the user can tap "Retry & Resume"
     * and continue from the exact byte offset where it stopped!
     */
    fun cancelTransferWithRetryOption() {
        isCancelledFlag.set(true)
        isPausedFlag.set(false)
        runCatching { activeClientSocket?.close() }
        val current = _sessionState.value
        if (current is TransferSessionState.Transferring) {
            lastDirection = current.direction
            lastPeerDeviceName = current.peerDeviceName
            lastProgress = current.progress
        }
        _sessionState.value = TransferSessionState.InterruptedOrCancelled(
            direction = lastDirection,
            peerDeviceName = lastPeerDeviceName.ifBlank { "الهاتف الآخر" },
            progress = lastProgress,
            messageAr = "تم إيقاف النقل عند ${lastProgress?.overallPercentage ?: 0}%. اضغط على «إعادة المحاولة والاستكمال» لإكمال النقل من نفس النقطة دون الإعادة من الأول.",
            messageEn = "Transfer stopped at ${lastProgress?.overallPercentage ?: 0}%. Tap 'Retry & Resume' to continue from the exact point where it stopped."
        )
    }

    /**
     * Resumes a cancelled or interrupted transfer from the exact byte offset where it stopped.
     */
    fun retryAndResumeTransfer(scope: CoroutineScope, appContext: Context? = null) {
        isCancelledFlag.set(false)
        isPausedFlag.set(false)

        if (lastDirection == TransferDirection.RECEIVED && lastTargetPort > 0) {
            _sessionState.value = TransferSessionState.ReceiverConnecting(
                lastTargetCandidateIps.firstOrNull().orEmpty()
            )
            activeSessionJob = scope.launch(Dispatchers.IO) {
                try {
                    val resolvedIp = if (appContext != null) {
                        HotspotConnectionManager.connectToSenderHotspotAndResolveIp(
                            context = appContext,
                            candidateIps = lastTargetCandidateIps,
                            tcpPort = lastTargetPort,
                            wifiSsid = lastWifiSsid,
                            wifiPassword = lastWifiPassword
                        )
                    } else {
                        lastTargetCandidateIps.firstOrNull() ?: "127.0.0.1"
                    }
                    executeReceiverSocketFlow(
                        receiverDeviceName = lastReceiverDeviceName.ifBlank { "هاتف المستلم" },
                        targetIp = resolvedIp,
                        targetPort = lastTargetPort,
                        autoApproveForLoopback = true
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _sessionState.value = TransferSessionState.InterruptedOrCancelled(
                        direction = TransferDirection.RECEIVED,
                        peerDeviceName = lastPeerDeviceName.ifBlank { "هاتف المرسل" },
                        progress = lastProgress,
                        messageAr = "تعذر إعادة الاتصال بالمُرسل حالياً. تأكد من بقاء المرسل جاهزاً ثم اضغط إعادة المحاولة للاستكمال من نفس النقطة.",
                        messageEn = "Could not reconnect yet. Make sure sender is ready and tap Retry & Resume."
                    )
                }
            }
        } else if (lastDirection == TransferDirection.SENT && currentOffer != null) {
            // Sender ServerSocket is still listening in accept loop! Show waiting/ready state with QR & progress
            val offer = currentOffer!!
            _sessionState.value = TransferSessionState.SenderWaitingForPeer(
                offer = offer,
                qrPayload = lastQrPayload
            )
        }
    }

    /**
     * Completely ends the session and returns to Idle.
     */
    fun cancelActiveSession(appContext: Context? = null) {
        isCancelledFlag.set(true)
        isPausedFlag.set(false)
        currentOffer?.let { UdpDiscoveryService.unregisterLocalSession(it.pairingCode) }
        appContext?.let {
            HotspotConnectionManager.stopSenderDirectHotspot(it)
            HotspotConnectionManager.unbindHotspotNetwork(it)
        }
        runCatching { discoveryBroadcastJob?.cancel() }
        runCatching { webPwaServer.stop() }
        runCatching { activeClientSocket?.close() }
        runCatching { activeServerSocket?.close() }
        runCatching { activeSessionJob?.cancel() }
        lastProgress = null
        _sessionState.value = TransferSessionState.Idle
    }

    fun resetToIdle(appContext: Context? = null) {
        cancelActiveSession(appContext)
    }

    fun startSenderSession(
        scope: CoroutineScope,
        senderDeviceName: String,
        files: List<ShareableFile>,
        appContext: Context? = null
    ) {
        if (files.isEmpty()) {
            _sessionState.value = TransferSessionState.Error(
                messageAr = "يرجى اختيار تطبيق أو ملف واحد على الأقل للإرسال.",
                messageEn = "Please select at least one app or file to send."
            )
            return
        }

        cancelActiveSession(appContext)
        isCancelledFlag.set(false)
        isPausedFlag.set(false)
        lastDirection = TransferDirection.SENT
        currentSelectedFiles = files

        activeSessionJob = scope.launch(Dispatchers.IO) {
            try {
                // Start Sender Direct Hotspot / Wi-Fi P2P group if context is available
                val hotspotCreds = if (appContext != null) {
                    runCatching { HotspotConnectionManager.startSenderDirectHotspot(appContext) }.getOrNull()
                } else null

                val serverSocket = ServerSocket(0).apply {
                    reuseAddress = true
                    soTimeout = SecurityUtils.SESSION_TTL_MILLIS.toInt()
                }
                activeServerSocket = serverSocket
                val tcpPort = serverSocket.localPort
                val localIp = hotspotCreds?.hostIp?.takeIf { it.isNotBlank() }
                    ?: SecurityUtils.getLocalIpv4Address()
                val sessionId = SecurityUtils.generateSessionId()
                val pairingCode = SecurityUtils.generatePairingCode()

                val webPort = webPwaServer.start(
                    scope = scope,
                    offerProvider = { currentOffer },
                    filesProvider = { currentSelectedFiles }
                )

                val manifestItems = files.map { f ->
                    TransferFileManifestItem(
                        id = f.id,
                        name = f.name,
                        sizeBytes = f.sizeBytes,
                        mimeType = f.mimeType,
                        categoryName = f.category.name,
                        sha256 = f.sha256
                    )
                }

                val offer = TransferSessionOffer(
                    sessionId = sessionId,
                    pairingCode = pairingCode,
                    senderDeviceName = senderDeviceName,
                    senderIp = localIp,
                    tcpPort = tcpPort,
                    webPwaPort = webPort,
                    expiresAtMillis = System.currentTimeMillis() + SecurityUtils.SESSION_TTL_MILLIS,
                    files = manifestItems,
                    hotspotSsid = hotspotCreds?.ssid.orEmpty(),
                    hotspotPassword = hotspotCreds?.password.orEmpty()
                )
                currentOffer = offer

                val qrPayload = SecurityUtils.buildQrPayload(
                    sessionId = sessionId,
                    pairingCode = pairingCode,
                    ip = localIp,
                    tcpPort = tcpPort,
                    webPort = webPort,
                    senderName = senderDeviceName,
                    wifiSsid = offer.hotspotSsid,
                    wifiPassword = offer.hotspotPassword
                )
                lastQrPayload = qrPayload

                val parsed = SecurityUtils.ParsedQrPayload(
                    pairingCode = pairingCode,
                    ip = localIp,
                    candidateIps = (listOf(localIp) + SecurityUtils.getAllLocalIpv4Addresses()).distinct(),
                    tcpPort = tcpPort,
                    webPort = webPort,
                    sessionId = sessionId,
                    senderName = senderDeviceName,
                    wifiSsid = offer.hotspotSsid,
                    wifiPassword = offer.hotspotPassword
                )
                discoveryBroadcastJob = UdpDiscoveryService.startBroadcastingSession(scope, qrPayload, parsed)

                _sessionState.value = TransferSessionState.SenderWaitingForPeer(
                    offer = offer,
                    qrPayload = qrPayload
                )

                // Loop accepting connections on this ServerSocket so if a transfer is interrupted or cancelled,
                // the Receiver can reconnect and resume from the exact byte offset where it left off!
                while (isActive && !serverSocket.isClosed) {
                    val clientSocket = runCatching {
                        serverSocket.accept().apply {
                            tcpNoDelay = true
                            sendBufferSize = SecurityUtils.DEFAULT_CHUNK_SIZE * 2
                            receiveBufferSize = SecurityUtils.DEFAULT_CHUNK_SIZE * 2
                            soTimeout = 30000
                        }
                    }.getOrNull() ?: break

                    activeClientSocket = clientSocket
                    isCancelledFlag.set(false)
                    isPausedFlag.set(false)

                    val finishedAll = try {
                        handleConnectedReceiverSocket(clientSocket, offer, files)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        if (!isCancelledFlag.get()) {
                            _sessionState.value = TransferSessionState.InterruptedOrCancelled(
                                direction = TransferDirection.SENT,
                                peerDeviceName = lastPeerDeviceName.ifBlank { "هاتف المستلم" },
                                progress = lastProgress,
                                messageAr = "انقطع الاتصال مؤقتاً عند ${lastProgress?.overallPercentage ?: 0}%. اضغط على «إعادة المحاولة والاستكمال» لإكمال النقل من نفس النقطة.",
                                messageEn = "Connection interrupted at ${lastProgress?.overallPercentage ?: 0}%. Tap 'Retry & Resume' to continue from the stopped point."
                            )
                        }
                        false
                    } finally {
                        runCatching { clientSocket.close() }
                    }

                    if (finishedAll) {
                        break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (!isCancelledFlag.get()) {
                    _sessionState.value = TransferSessionState.InterruptedOrCancelled(
                        direction = TransferDirection.SENT,
                        peerDeviceName = lastPeerDeviceName.ifBlank { "هاتف المستلم" },
                        progress = lastProgress,
                        messageAr = "انتهت مهلة الاتصال. اضغط على «إعادة المحاولة والاستكمال» للمحاولة مرة أخرى.",
                        messageEn = "Connection timed out. Tap 'Retry & Resume' to try again."
                    )
                }
            } finally {
                currentOffer?.let { UdpDiscoveryService.unregisterLocalSession(it.pairingCode) }
                runCatching { discoveryBroadcastJob?.cancel() }
                runCatching { activeServerSocket?.close() }
            }
        }
    }

    /**
     * Streams files to the connected receiver, supporting Byte-Offset Resume via `resumeOffsets`
     * sent by the receiver so interrupted/cancelled transfers continue from the exact byte offset!
     */
    private suspend fun handleConnectedReceiverSocket(
        socket: Socket,
        offer: TransferSessionOffer,
        files: List<ShareableFile>
    ): Boolean = withContext(Dispatchers.IO) {
        val dataIn = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val dataOut = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

        val helloJsonStr = dataIn.readUTF()
        val helloJson = JSONObject(helloJsonStr)
        val receiverName = helloJson.optString("deviceName", "هاتف المستلم")
        lastPeerDeviceName = receiverName

        val offerJson = serializeOfferToJson(offer)
        dataOut.writeUTF(offerJson)
        dataOut.flush()

        _sessionState.value = TransferSessionState.SenderAwaitingReceiverApproval(
            offer = offer,
            receiverDeviceName = receiverName
        )

        socket.soTimeout = 120_000
        val decisionStr = dataIn.readUTF()
        val decisionJson = JSONObject(decisionStr)
        val accepted = decisionJson.optBoolean("accepted", false)
        if (!accepted) {
            _sessionState.value = TransferSessionState.Error(
                messageAr = "تم رفض الاستلام من الهاتف الآخر ($receiverName).",
                messageEn = "Receiving phone ($receiverName) declined the transfer."
            )
            socket.close()
            return@withContext true
        }

        val resumeOffsetsJson = decisionJson.optJSONObject("resumeOffsets")

        socket.soTimeout = 30_000
        val overallTotalBytes = files.sumOf { it.sizeBytes }.coerceAtLeast(1L)
        var overallSentBytes = 0L
        val startTimeMs = System.currentTimeMillis()
        val verifiedFiles = mutableListOf<Pair<String, String>>()
        val buffer = ByteArray(SecurityUtils.DEFAULT_CHUNK_SIZE)

        // Pre-add all resumed offsets to overallSentBytes so overall progress starts from the resumed percentage!
        for ((index, file) in files.withIndex()) {
            val offset = resumeOffsetsJson?.optLong(index.toString(), 0L)?.coerceIn(0L, file.sizeBytes) ?: 0L
            overallSentBytes += offset
        }

        for ((index, file) in files.withIndex()) {
            if (isCancelledFlag.get()) return@withContext false

            val resumeOffset = resumeOffsetsJson?.optLong(index.toString(), 0L)?.coerceIn(0L, file.sizeBytes) ?: 0L
            var fileSentBytes = resumeOffset

            if (resumeOffset < file.sizeBytes) {
                val inputStream = fileSelectionManager.openShareableInputStream(file)
                    ?: throw IllegalStateException("تعذر فتح الملف: ${file.name}")

                inputStream.use { stream ->
                    if (resumeOffset > 0L) {
                        skipFully(stream, resumeOffset)
                    }
                    var bytesRead: Int
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        while (isPausedFlag.get() && !isCancelledFlag.get()) {
                            delay(120L)
                        }
                        if (isCancelledFlag.get()) {
                            runCatching {
                                dataOut.writeInt(-1)
                                dataOut.flush()
                            }
                            return@withContext false
                        }

                        dataOut.writeInt(bytesRead)
                        dataOut.write(buffer, 0, bytesRead)
                        fileSentBytes += bytesRead
                        overallSentBytes += bytesRead

                        val elapsedMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
                        val speedBps = (overallSentBytes * 1000L) / elapsedMs
                        val remainingBytes = (overallTotalBytes - overallSentBytes).coerceAtLeast(0L)
                        val etaSec = if (speedBps > 0) remainingBytes / speedBps else 0L

                        val currentProgress = FileTransferProgress(
                            currentFileIndex = index + 1,
                            totalFilesCount = files.size,
                            currentFileName = file.name,
                            currentFileCategory = file.category,
                            currentFileBytesTransferred = fileSentBytes,
                            currentFileTotalBytes = file.sizeBytes,
                            overallBytesTransferred = overallSentBytes,
                            overallTotalBytes = overallTotalBytes,
                            speedBytesPerSec = speedBps,
                            estimatedRemainingSeconds = etaSec,
                            isPaused = isPausedFlag.get(),
                            isVerifyingSha256 = fileSentBytes >= file.sizeBytes,
                            sha256VerifiedCount = verifiedFiles.size
                        )
                        lastProgress = currentProgress

                        _sessionState.value = TransferSessionState.Transferring(
                            direction = TransferDirection.SENT,
                            peerDeviceName = receiverName,
                            progress = currentProgress
                        )
                    }
                }
            }

            // End-of-file frame marker
            dataOut.writeInt(0)
            dataOut.flush()

            val ackStr = dataIn.readUTF()
            val ackJson = JSONObject(ackStr)
            val verifiedOk = ackJson.optBoolean("verified", false)
            if (!verifiedOk) {
                historyRepository.recordTransfer(
                    TransferHistoryEntity(
                        fileName = file.name,
                        fileCategory = file.category.name,
                        mimeType = file.mimeType,
                        fileSizeBytes = file.sizeBytes,
                        direction = TransferDirection.SENT.name,
                        peerDeviceName = receiverName,
                        sha256Hash = file.sha256,
                        status = TransferStatus.INTEGRITY_MISMATCH.name,
                        localFilePath = file.localFilePath
                    )
                )
                _sessionState.value = TransferSessionState.Error(
                    messageAr = "لم يكتمل حفظ الملف (${file.name}) بشكل سليم لدى المستلم.",
                    messageEn = "File verification failed on receiver for: ${file.name}"
                )
                return@withContext true
            }

            verifiedFiles.add(file.name to file.sha256)
            val totalElapsedMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
            val avgSpeed = (overallSentBytes * 1000L) / totalElapsedMs

            historyRepository.recordTransfer(
                TransferHistoryEntity(
                    fileName = file.name,
                    fileCategory = file.category.name,
                    mimeType = file.mimeType,
                    fileSizeBytes = file.sizeBytes,
                    direction = TransferDirection.SENT.name,
                    peerDeviceName = receiverName,
                    sha256Hash = file.sha256,
                    status = TransferStatus.COMPLETED.name,
                    localFilePath = file.localFilePath,
                    averageSpeedBps = avgSpeed
                )
            )
        }

        val totalDurationMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
        val finalAvgSpeed = (overallSentBytes * 1000L) / totalDurationMs

        _sessionState.value = TransferSessionState.Completed(
            direction = TransferDirection.SENT,
            peerDeviceName = receiverName,
            filesCount = files.size,
            totalBytes = overallSentBytes,
            averageSpeedBps = finalAvgSpeed,
            verifiedHashes = verifiedFiles
        )
        true
    }

    private fun skipFully(stream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val discardBuffer = ByteArray(16 * 1024)
        while (remaining > 0L) {
            val skipped = stream.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val toRead = minOf(remaining, discardBuffer.size.toLong()).toInt()
                val read = stream.read(discardBuffer, 0, toRead)
                if (read <= 0) break
                remaining -= read
            }
        }
    }

    fun openReceiverScanner() {
        cancelActiveSession()
        _sessionState.value = TransferSessionState.ReceiverScanningOrWaiting
    }

    /**
     * Connects Receiver to Sender (automatically joining Sender's Hotspot if embedded in QR code)
     * and starts/resumes receiving immediately.
     */
    fun connectAsReceiver(
        scope: CoroutineScope,
        receiverDeviceName: String,
        codeOrQrPayload: String,
        manualIpPort: String = "",
        appContext: Context? = null
    ) {
        val trimmedInput = codeOrQrPayload.trim()
        if (trimmedInput.isEmpty()) {
            _sessionState.value = TransferSessionState.Error(
                messageAr = "يرجى تصوير رمز الإرسال بالكاميرا أو إدخال رقم الربط المكون من 6 أرقام.",
                messageEn = "Please scan the code or enter the 6-digit pairing code."
            )
            return
        }

        isCancelledFlag.set(false)
        isPausedFlag.set(false)
        lastDirection = TransferDirection.RECEIVED
        lastReceiverDeviceName = receiverDeviceName
        _sessionState.value = TransferSessionState.ReceiverConnecting(trimmedInput)

        activeSessionJob = scope.launch(Dispatchers.IO) {
            try {
                val parsedFromQr = SecurityUtils.parseQrPayload(trimmedInput)
                val discovered = if (parsedFromQr == null) {
                    UdpDiscoveryService.lookupByPairingCode(trimmedInput)
                } else null

                val candidateIps: List<String>
                val targetPort: Int
                val wifiSsid: String
                val wifiPassword: String

                when {
                    parsedFromQr != null -> {
                        candidateIps = parsedFromQr.candidateIps
                        targetPort = parsedFromQr.tcpPort
                        wifiSsid = parsedFromQr.wifiSsid
                        wifiPassword = parsedFromQr.wifiPassword
                    }
                    discovered != null -> {
                        candidateIps = discovered.candidateIps
                        targetPort = discovered.tcpPort
                        wifiSsid = discovered.wifiSsid
                        wifiPassword = discovered.wifiPassword
                    }
                    manualIpPort.contains(":") -> {
                        val parts = manualIpPort.trim().split(":")
                        candidateIps = listOf(parts[0].trim())
                        targetPort = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: -1
                        wifiSsid = ""
                        wifiPassword = ""
                    }
                    trimmedInput.contains(":") -> {
                        val parts = trimmedInput.split(":")
                        candidateIps = listOf(parts[0].trim())
                        targetPort = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: -1
                        wifiSsid = ""
                        wifiPassword = ""
                    }
                    else -> {
                        _sessionState.value = TransferSessionState.Error(
                            messageAr = "لم يتم العثور على هاتف المرسل برقم الربط ($trimmedInput). تأكد من فتح الواي فاي ونقطة الاتصال أو صوّر الرمز بالكاميرا.",
                            messageEn = "Sender phone not found for code ($trimmedInput). Ensure Wi-Fi or Hotspot is active or scan the QR code."
                        )
                        return@launch
                    }
                }

                if (targetPort <= 0) {
                    _sessionState.value = TransferSessionState.Error(
                        messageAr = "تعذر الربط بالهاتف الآخر. يرجى تصوير الرمز بالكاميرا مرة أخرى.",
                        messageEn = "Could not connect to sender. Please scan the QR code again."
                    )
                    return@launch
                }

                lastTargetCandidateIps = candidateIps
                lastTargetPort = targetPort
                lastWifiSsid = wifiSsid
                lastWifiPassword = wifiPassword

                // Automatically connect to Sender's hotspot if needed and resolve reachable IP
                val resolvedIp = if (appContext != null) {
                    HotspotConnectionManager.connectToSenderHotspotAndResolveIp(
                        context = appContext,
                        candidateIps = candidateIps,
                        tcpPort = targetPort,
                        wifiSsid = wifiSsid,
                        wifiPassword = wifiPassword
                    )
                } else {
                    candidateIps.firstOrNull() ?: "127.0.0.1"
                }

                executeReceiverSocketFlow(
                    receiverDeviceName = receiverDeviceName,
                    targetIp = resolvedIp,
                    targetPort = targetPort,
                    autoApproveForLoopback = true
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (!isCancelledFlag.get()) {
                    _sessionState.value = TransferSessionState.InterruptedOrCancelled(
                        direction = TransferDirection.RECEIVED,
                        peerDeviceName = lastPeerDeviceName.ifBlank { "هاتف المرسل" },
                        progress = lastProgress,
                        messageAr = "توقف الاتصال أثناء النقل. اضغط على «إعادة المحاولة والاستكمال» لإكمال الاستلام من نفس النقطة.",
                        messageEn = "Connection interrupted. Tap 'Retry & Resume' to continue from the exact stopped point."
                    )
                }
            }
        }
    }

    suspend fun executeReceiverSocketFlow(
        receiverDeviceName: String,
        targetIp: String,
        targetPort: Int,
        autoApproveForLoopback: Boolean
    ) = withContext(Dispatchers.IO) {
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.receiveBufferSize = SecurityUtils.DEFAULT_CHUNK_SIZE * 2
        socket.sendBufferSize = SecurityUtils.DEFAULT_CHUNK_SIZE * 2
        socket.connect(InetSocketAddress(targetIp, targetPort), 10_000)
        socket.soTimeout = 30_000
        activeClientSocket = socket

        socket.use { sock ->
            val dataIn = DataInputStream(BufferedInputStream(sock.getInputStream()))
            val dataOut = DataOutputStream(BufferedOutputStream(sock.getOutputStream()))

            val helloJson = JSONObject().apply {
                put("deviceName", receiverDeviceName)
            }
            dataOut.writeUTF(helloJson.toString())
            dataOut.flush()

            val offerJsonStr = dataIn.readUTF()
            val offer = deserializeOfferFromJson(offerJsonStr)
            lastPeerDeviceName = offer.senderDeviceName

            if (System.currentTimeMillis() > offer.expiresAtMillis) {
                _sessionState.value = TransferSessionState.Error(
                    messageAr = "انتهت صلاحية الجلسة. يرجى بدء الإرسال مرة أخرى.",
                    messageEn = "Session expired. Please start sending again."
                )
                return@withContext
            }

            val usableSpace = fileSelectionManager.receivedFilesDir.usableSpace
            if (usableSpace in 1 until offer.totalBytes) {
                _sessionState.value = TransferSessionState.Error(
                    messageAr = "مساحة الهاتف غير كافية لاستلام هذه الملفات.",
                    messageEn = "Insufficient storage space on phone."
                )
                val rejectJson = JSONObject().put("accepted", false)
                dataOut.writeUTF(rejectJson.toString())
                dataOut.flush()
                return@withContext
            }

            val approvalDecision = CompletableDeferred<Boolean>()
            if (autoApproveForLoopback) {
                approvalDecision.complete(true)
            } else {
                _sessionState.value = TransferSessionState.ReceiverApprovalRequired(
                    offer = offer,
                    onApprove = { approvalDecision.complete(true) },
                    onReject = { approvalDecision.complete(false) }
                )
            }

            val approved = approvalDecision.await()
            if (!approved) {
                val rejectJson = JSONObject().put("accepted", false)
                dataOut.writeUTF(rejectJson.toString())
                dataOut.flush()
                _sessionState.value = TransferSessionState.Idle
                return@withContext
            }

            // Inspect existing .part files on disk to compute byte offsets for resumable transfer!
            val resumeOffsetsJson = JSONObject()
            val partFilesByIndex = mutableMapOf<Int, File>()
            val resumeBytesByIndex = mutableMapOf<Int, Long>()

            for ((index, manifestItem) in offer.files.withIndex()) {
                val tempPartFile = File(
                    fileSelectionManager.receivedFilesDir,
                    ".part_${offer.sessionId}_${index}_${SecurityUtils.sanitizeFileName(manifestItem.name)}"
                )
                partFilesByIndex[index] = tempPartFile
                val existingLen = if (tempPartFile.exists() && tempPartFile.isFile) {
                    tempPartFile.length().coerceIn(0L, manifestItem.sizeBytes)
                } else {
                    0L
                }
                resumeBytesByIndex[index] = existingLen
                resumeOffsetsJson.put(index.toString(), existingLen)
            }

            val decisionJson = JSONObject().apply {
                put("accepted", true)
                put("resumeOffsets", resumeOffsetsJson)
            }
            dataOut.writeUTF(decisionJson.toString())
            dataOut.flush()

            val overallTotalBytes = offer.totalBytes.coerceAtLeast(1L)
            var overallReceivedBytes = resumeBytesByIndex.values.sum()
            val startTimeMs = System.currentTimeMillis()
            val verifiedHashes = mutableListOf<Pair<String, String>>()
            val chunkBuffer = ByteArray(SecurityUtils.DEFAULT_CHUNK_SIZE)

            for ((index, manifestItem) in offer.files.withIndex()) {
                val category = runCatching { FileCategory.valueOf(manifestItem.categoryName) }
                    .getOrDefault(FileCategory.OTHER)
                val tempPartFile = partFilesByIndex[index]!!
                val initialOffset = resumeBytesByIndex[index] ?: 0L
                var fileReceivedBytes = initialOffset

                // Append to existing .part file if resuming from a byte offset > 0!
                FileOutputStream(tempPartFile, initialOffset > 0L).use { fos ->
                    BufferedOutputStream(fos, SecurityUtils.DEFAULT_CHUNK_SIZE).use { bos ->
                        while (true) {
                            while (isPausedFlag.get() && !isCancelledFlag.get()) {
                                delay(120L)
                            }
                            if (isCancelledFlag.get()) {
                                // Flush already received bytes to disk and DO NOT delete tempPartFile so retry resumes from here!
                                bos.flush()
                                return@withContext
                            }

                            val chunkLen = dataIn.readInt()
                            if (chunkLen == 0) {
                                break
                            }
                            if (chunkLen < 0) {
                                // Sender paused/cancelled: flush partial file and transition to InterruptedOrCancelled
                                bos.flush()
                                _sessionState.value = TransferSessionState.InterruptedOrCancelled(
                                    direction = TransferDirection.RECEIVED,
                                    peerDeviceName = offer.senderDeviceName,
                                    progress = lastProgress,
                                    messageAr = "توقف النقل من جهة المرسل عند ${lastProgress?.overallPercentage ?: 0}%. اضغط على «إعادة المحاولة والاستكمال» لإكمال النقل من نفس النقطة.",
                                    messageEn = "Sender paused transfer at ${lastProgress?.overallPercentage ?: 0}%. Tap 'Retry & Resume' to continue."
                                )
                                return@withContext
                            }

                            var remainingInChunk = chunkLen
                            while (remainingInChunk > 0) {
                                val toRead = minOf(remainingInChunk, chunkBuffer.size)
                                dataIn.readFully(chunkBuffer, 0, toRead)
                                bos.write(chunkBuffer, 0, toRead)
                                remainingInChunk -= toRead
                                fileReceivedBytes += toRead
                                overallReceivedBytes += toRead
                            }

                            val elapsedMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
                            val speedBps = (overallReceivedBytes * 1000L) / elapsedMs
                            val remainingTotal = (overallTotalBytes - overallReceivedBytes).coerceAtLeast(0L)
                            val etaSec = if (speedBps > 0) remainingTotal / speedBps else 0L

                            val currentProgress = FileTransferProgress(
                                currentFileIndex = index + 1,
                                totalFilesCount = offer.files.size,
                                currentFileName = manifestItem.name,
                                currentFileCategory = category,
                                currentFileBytesTransferred = fileReceivedBytes,
                                currentFileTotalBytes = manifestItem.sizeBytes,
                                overallBytesTransferred = overallReceivedBytes,
                                overallTotalBytes = overallTotalBytes,
                                speedBytesPerSec = speedBps,
                                estimatedRemainingSeconds = etaSec,
                                isPaused = isPausedFlag.get(),
                                isVerifyingSha256 = fileReceivedBytes >= manifestItem.sizeBytes,
                                sha256VerifiedCount = verifiedHashes.size
                            )
                            lastProgress = currentProgress

                            _sessionState.value = TransferSessionState.Transferring(
                                direction = TransferDirection.RECEIVED,
                                peerDeviceName = offer.senderDeviceName,
                                progress = currentProgress
                            )
                        }
                        bos.flush()
                    }
                }

                // Compute SHA-256 across the complete assembled tempPartFile (works seamlessly even after resume!)
                val computedSha256 = SecurityUtils.computeSha256(tempPartFile)
                val integrityMatches = computedSha256.equals(manifestItem.sha256, ignoreCase = true) &&
                    tempPartFile.length() == manifestItem.sizeBytes

                if (!integrityMatches) {
                    tempPartFile.delete()
                    val nackJson = JSONObject().put("verified", false)
                    dataOut.writeUTF(nackJson.toString())
                    dataOut.flush()

                    historyRepository.recordTransfer(
                        TransferHistoryEntity(
                            fileName = manifestItem.name,
                            fileCategory = category.name,
                            mimeType = manifestItem.mimeType,
                            fileSizeBytes = manifestItem.sizeBytes,
                            direction = TransferDirection.RECEIVED.name,
                            peerDeviceName = offer.senderDeviceName,
                            sha256Hash = computedSha256,
                            status = TransferStatus.INTEGRITY_MISMATCH.name,
                            localFilePath = null
                        )
                    )

                    _sessionState.value = TransferSessionState.Error(
                        messageAr = "لم يكتمل استلام الملف (${manifestItem.name}) بشكل سليم. تم حذف النسخة غير المكتملة.",
                        messageEn = "File did not arrive completely (${manifestItem.name}). Incomplete file removed."
                    )
                    return@withContext
                }

                val finalDestination = SecurityUtils.resolveSafeDestinationFile(
                    directory = fileSelectionManager.receivedFilesDir,
                    rawFileName = manifestItem.name,
                    expectedSha256 = computedSha256
                )
                if (finalDestination.exists()) {
                    tempPartFile.delete()
                } else {
                    tempPartFile.renameTo(finalDestination)
                }

                val ackJson = JSONObject().put("verified", true)
                dataOut.writeUTF(ackJson.toString())
                dataOut.flush()

                verifiedHashes.add(finalDestination.name to computedSha256)
                val totalElapsedMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
                val avgSpeed = (overallReceivedBytes * 1000L) / totalElapsedMs

                historyRepository.recordTransfer(
                    TransferHistoryEntity(
                        fileName = finalDestination.name,
                        fileCategory = category.name,
                        mimeType = manifestItem.mimeType,
                        fileSizeBytes = manifestItem.sizeBytes,
                        direction = TransferDirection.RECEIVED.name,
                        peerDeviceName = offer.senderDeviceName,
                        sha256Hash = computedSha256,
                        status = TransferStatus.COMPLETED.name,
                        localFilePath = finalDestination.absolutePath,
                        averageSpeedBps = avgSpeed
                    )
                )
            }

            val totalElapsedMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
            val finalAvgSpeed = (overallReceivedBytes * 1000L) / totalElapsedMs

            _sessionState.value = TransferSessionState.Completed(
                direction = TransferDirection.RECEIVED,
                peerDeviceName = offer.senderDeviceName,
                filesCount = offer.files.size,
                totalBytes = overallReceivedBytes,
                averageSpeedBps = finalAvgSpeed,
                verifiedHashes = verifiedHashes
            )
        }
    }

    companion object {
        fun serializeOfferToJson(offer: TransferSessionOffer): String {
            val filesArray = JSONArray()
            for (f in offer.files) {
                val obj = JSONObject().apply {
                    put("id", f.id)
                    put("name", f.name)
                    put("sizeBytes", f.sizeBytes)
                    put("mimeType", f.mimeType)
                    put("categoryName", f.categoryName)
                    put("sha256", f.sha256)
                }
                filesArray.put(obj)
            }
            return JSONObject().apply {
                put("sessionId", offer.sessionId)
                put("pairingCode", offer.pairingCode)
                put("senderDeviceName", offer.senderDeviceName)
                put("senderIp", offer.senderIp)
                put("tcpPort", offer.tcpPort)
                put("webPwaPort", offer.webPwaPort)
                put("expiresAtMillis", offer.expiresAtMillis)
                put("hotspotSsid", offer.hotspotSsid)
                put("hotspotPassword", offer.hotspotPassword)
                put("files", filesArray)
            }.toString()
        }

        fun deserializeOfferFromJson(jsonStr: String): TransferSessionOffer {
            val obj = JSONObject(jsonStr)
            val filesArr = obj.getJSONArray("files")
            val files = mutableListOf<TransferFileManifestItem>()
            for (i in 0 until filesArr.length()) {
                val item = filesArr.getJSONObject(i)
                files.add(
                    TransferFileManifestItem(
                        id = item.optString("id"),
                        name = item.getString("name"),
                        sizeBytes = item.getLong("sizeBytes"),
                        mimeType = item.optString("mimeType", "application/octet-stream"),
                        categoryName = item.optString("categoryName", FileCategory.OTHER.name),
                        sha256 = item.getString("sha256")
                    )
                )
            }
            return TransferSessionOffer(
                sessionId = obj.getString("sessionId"),
                pairingCode = obj.getString("pairingCode"),
                senderDeviceName = obj.getString("senderDeviceName"),
                senderIp = obj.optString("senderIp", "127.0.0.1"),
                tcpPort = obj.getInt("tcpPort"),
                webPwaPort = obj.optInt("webPwaPort", 0),
                expiresAtMillis = obj.optLong("expiresAtMillis", System.currentTimeMillis() + 600_000L),
                files = files,
                hotspotSsid = obj.optString("hotspotSsid", ""),
                hotspotPassword = obj.optString("hotspotPassword", "")
            )
        }
    }
}
