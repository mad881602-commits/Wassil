package com.example.model

import android.graphics.Bitmap

enum class FileCategory(val labelAr: String, val labelEn: String) {
    PHOTO("صورة", "Photo"),
    VIDEO("فيديو", "Video"),
    MUSIC("موسيقى", "Audio"),
    DOCUMENT("مستند", "Document"),
    APK("تطبيق", "App"),
    ARCHIVE("ملف مضغوط", "Archive"),
    OTHER("ملف", "File")
}

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val apkFilePath: String,
    val sizeBytes: Long,
    val iconBitmap: Bitmap? = null
)

data class ShareableFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val category: FileCategory,
    val sha256: String,
    val uriString: String? = null,
    val localFilePath: String? = null,
    val appIconBitmap: Bitmap? = null
)

data class TransferFileManifestItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val categoryName: String,
    val sha256: String
)

data class TransferSessionOffer(
    val sessionId: String,
    val pairingCode: String,
    val senderDeviceName: String,
    val senderIp: String,
    val tcpPort: Int,
    val webPwaPort: Int,
    val expiresAtMillis: Long,
    val files: List<TransferFileManifestItem>,
    val hotspotSsid: String = "",
    val hotspotPassword: String = ""
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
    val hasApkFile: Boolean get() = files.any {
        it.categoryName == FileCategory.APK.name || it.name.endsWith(".apk", ignoreCase = true)
    }
}

enum class TransferDirection {
    SENT,
    RECEIVED
}

enum class TransferStatus {
    COMPLETED,
    CANCELLED,
    FAILED,
    INTEGRITY_MISMATCH
}

data class FileTransferProgress(
    val currentFileIndex: Int,
    val totalFilesCount: Int,
    val currentFileName: String,
    val currentFileCategory: FileCategory,
    val currentFileBytesTransferred: Long,
    val currentFileTotalBytes: Long,
    val overallBytesTransferred: Long,
    val overallTotalBytes: Long,
    val speedBytesPerSec: Long,
    val estimatedRemainingSeconds: Long,
    val isPaused: Boolean = false,
    val isVerifyingSha256: Boolean = false,
    val sha256VerifiedCount: Int = 0
) {
    val overallPercentage: Int
        get() = if (overallTotalBytes <= 0L) 100
        else ((overallBytesTransferred * 100L) / overallTotalBytes).toInt().coerceIn(0, 100)

    val currentFilePercentage: Int
        get() = if (currentFileTotalBytes <= 0L) 100
        else ((currentFileBytesTransferred * 100L) / currentFileTotalBytes).toInt().coerceIn(0, 100)
}

sealed class TransferSessionState {
    object Idle : TransferSessionState()
    data class SenderWaitingForPeer(val offer: TransferSessionOffer, val qrPayload: String) : TransferSessionState()
    data class SenderAwaitingReceiverApproval(val offer: TransferSessionOffer, val receiverDeviceName: String) : TransferSessionState()
    object ReceiverScanningOrWaiting : TransferSessionState()
    data class ReceiverConnecting(val targetCodeOrEndpoint: String) : TransferSessionState()
    data class ReceiverApprovalRequired(
        val offer: TransferSessionOffer,
        val onApprove: () -> Unit,
        val onReject: () -> Unit
    ) : TransferSessionState()
    data class Transferring(
        val direction: TransferDirection,
        val peerDeviceName: String,
        val progress: FileTransferProgress
    ) : TransferSessionState()
    data class InterruptedOrCancelled(
        val direction: TransferDirection,
        val peerDeviceName: String,
        val progress: FileTransferProgress?,
        val messageAr: String,
        val messageEn: String
    ) : TransferSessionState()
    data class Completed(
        val direction: TransferDirection,
        val peerDeviceName: String,
        val filesCount: Int,
        val totalBytes: Long,
        val averageSpeedBps: Long,
        val verifiedHashes: List<Pair<String, String>>
    ) : TransferSessionState()
    data class Error(val messageAr: String, val messageEn: String) : TransferSessionState()
}

data class TestScenarioResult(
    val id: Int,
    val titleAr: String,
    val titleEn: String,
    val passed: Boolean,
    val details: String,
    val sha256Sample: String = "",
    val durationMs: Long = 0L
)
