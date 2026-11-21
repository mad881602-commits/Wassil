package com.example.security

import com.example.model.FileCategory
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object SecurityUtils {
    private val secureRandom = SecureRandom()
    const val DEFAULT_CHUNK_SIZE = 64 * 1024 // 64 KB chunks
    const val SESSION_TTL_MILLIS = 15 * 60 * 1000L // 15 minutes session expiration

    fun generateSessionId(): String {
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generatePairingCode(): String {
        val code = 100000 + secureRandom.nextInt(900000)
        return code.toString()
    }

    fun computeSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_CHUNK_SIZE)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeSha256(file: File): String {
        FileInputStream(file).use { fis ->
            return computeSha256(fis)
        }
    }

    fun sanitizeFileName(rawName: String): String {
        val stripped = rawName
            .replace("\\", "_")
            .replace("/", "_")
            .replace("..", "_")
            .replace(Regex("[\\x00-\\x1F]"), "")
            .trim()
        return if (stripped.isEmpty()) "received_file_${System.currentTimeMillis()}" else stripped
    }

    fun resolveSafeDestinationFile(directory: File, rawFileName: String, expectedSha256: String? = null): File {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val cleanName = sanitizeFileName(rawFileName)
        val initialCandidate = File(directory, cleanName)
        if (!initialCandidate.exists()) {
            return initialCandidate
        }
        if (expectedSha256 != null && initialCandidate.isFile) {
            val existingHash = runCatching { computeSha256(initialCandidate) }.getOrNull()
            if (existingHash.equals(expectedSha256, ignoreCase = true)) {
                return initialCandidate
            }
        }
        val dotIndex = cleanName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) cleanName.substring(0, dotIndex) else cleanName
        val ext = if (dotIndex > 0) cleanName.substring(dotIndex) else ""
        var counter = 1
        while (true) {
            val candidate = File(directory, "$baseName ($counter)$ext")
            if (!candidate.exists()) {
                return candidate
            }
            if (expectedSha256 != null && candidate.isFile) {
                val existingHash = runCatching { computeSha256(candidate) }.getOrNull()
                if (existingHash.equals(expectedSha256, ignoreCase = true)) {
                    return candidate
                }
            }
            counter++
        }
    }

    fun detectCategory(fileName: String, mimeType: String): FileCategory {
        val lowerName = fileName.lowercase(Locale.ROOT)
        val lowerMime = mimeType.lowercase(Locale.ROOT)
        return when {
            lowerName.endsWith(".apk") || lowerMime.contains("android.package-archive") -> FileCategory.APK
            lowerMime.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif") -> FileCategory.PHOTO
            lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") ||
                lowerName.endsWith(".mov") || lowerName.endsWith(".webm") -> FileCategory.VIDEO
            lowerMime.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") ||
                lowerName.endsWith(".flac") || lowerName.endsWith(".m4a") || lowerName.endsWith(".ogg") -> FileCategory.MUSIC
            lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") ||
                lowerName.endsWith(".tar") || lowerName.endsWith(".gz") || lowerMime.contains("zip") -> FileCategory.ARCHIVE
            lowerName.endsWith(".pdf") || lowerName.endsWith(".doc") || lowerName.endsWith(".docx") ||
                lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx") || lowerName.endsWith(".txt") ||
                lowerName.endsWith(".ppt") || lowerName.endsWith(".pptx") || lowerMime.startsWith("text/") ||
                lowerMime.contains("pdf") || lowerMime.contains("document") || lowerMime.contains("sheet") -> FileCategory.DOCUMENT
            else -> FileCategory.OTHER
        }
    }

    fun resolveMimeType(fileName: String): String {
        val lower = fileName.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".wav") -> "audio/wav"
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            lower.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            lower.endsWith(".txt") -> "text/plain"
            lower.endsWith(".apk") -> "application/vnd.android.package-archive"
            lower.endsWith(".zip") -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(4)
        val unit = arrayOf("KB", "MB", "GB", "TB")[exp - 1]
        val value = bytes / 1024.0.pow(exp.toDouble())
        return String.format(Locale.US, "%.2f %s", value, unit)
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 KB/s"
        return "${formatBytes(bytesPerSec)}/s"
    }

    fun formatDurationSeconds(seconds: Long, isArabic: Boolean): String {
        if (seconds <= 0L) return if (isArabic) "أقل من ثانية" else "< 1s"
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) {
            if (isArabic) "${mins}د ${secs}ث" else "${mins}m ${secs}s"
        } else {
            if (isArabic) "${secs} ثانية" else "${secs}s"
        }
    }

    /**
     * Returns all active IPv4 addresses on the device, prioritizing Hotspot / Wi-Fi Direct
     * interfaces (ap, wlan, p2p, rndis) so the receiver can reach the sender directly on its hotspot!
     */
    fun getAllLocalIpv4Addresses(): List<String> {
        val addresses = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            // Sort interfaces so ap / p2p / hotspot interfaces come first
            val sortedInterfaces = interfaces.sortedBy { intf ->
                val name = intf.name.lowercase(Locale.ROOT)
                when {
                    name.contains("ap") || name.contains("p2p") || name.contains("wlan1") || name.contains("swlan") -> 0
                    name.contains("wlan") -> 1
                    else -> 2
                }
            }
            for (intf in sortedInterfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (!host.startsWith("169.254") && !addresses.contains(host)) {
                            addresses.add(host)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        if (addresses.isEmpty()) {
            addresses.add("127.0.0.1")
        }
        return addresses
    }

    fun getLocalIpv4Address(): String {
        return getAllLocalIpv4Addresses().firstOrNull() ?: "127.0.0.1"
    }

    fun buildQrPayload(
        sessionId: String,
        pairingCode: String,
        ip: String,
        tcpPort: Int,
        webPort: Int,
        senderName: String,
        wifiSsid: String = "",
        wifiPassword: String = ""
    ): String {
        val safeSender = senderName.replace("|", " ").trim()
        val safeSsid = wifiSsid.replace("|", "").trim()
        val safePass = wifiPassword.replace("|", "").trim()
        val allIps = (listOf(ip) + getAllLocalIpv4Addresses()).distinct().joinToString(",")
        return "WASSIL|v1|$pairingCode|$allIps|$tcpPort|$webPort|$sessionId|$safeSender|$safeSsid|$safePass"
    }

    data class ParsedQrPayload(
        val pairingCode: String,
        val ip: String,
        val candidateIps: List<String>,
        val tcpPort: Int,
        val webPort: Int,
        val sessionId: String,
        val senderName: String,
        val wifiSsid: String = "",
        val wifiPassword: String = ""
    )

    fun parseQrPayload(raw: String): ParsedQrPayload? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("WASSIL|v1|")) {
            val parts = trimmed.split("|")
            if (parts.size >= 8) {
                val code = parts[2]
                val rawIpField = parts[3]
                val candidateIps = rawIpField.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val primaryIp = candidateIps.firstOrNull() ?: return null
                val tcpPort = parts[4].toIntOrNull() ?: return null
                val webPort = parts[5].toIntOrNull() ?: 0
                val sessionId = parts[6]
                val senderName = parts[7]
                val wifiSsid = parts.getOrNull(8).orEmpty()
                val wifiPassword = parts.getOrNull(9).orEmpty()
                return ParsedQrPayload(
                    pairingCode = code,
                    ip = primaryIp,
                    candidateIps = candidateIps,
                    tcpPort = tcpPort,
                    webPort = webPort,
                    sessionId = sessionId,
                    senderName = senderName,
                    wifiSsid = wifiSsid,
                    wifiPassword = wifiPassword
                )
            }
        }
        return null
    }
}
