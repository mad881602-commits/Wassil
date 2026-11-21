package com.example.files

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.FileCategory
import com.example.model.InstalledAppInfo
import com.example.model.ShareableFile
import com.example.security.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.sin

class FileSelectionManager(private val context: Context) {

    private val sampleFilesDir: File
        get() = File(context.filesDir, "sample_source_files").apply { mkdirs() }

    val receivedFilesDir: File
        get() = File(context.filesDir, "received_files").apply { mkdirs() }

    /**
     * Queries all launchable & user-installed apps on the phone so the user can share installed apps directly.
     */
    suspend fun getInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = runCatching { pm.queryIntentActivities(mainIntent, 0) }.getOrDefault(emptyList())
        val appInfoMap = linkedMapOf<String, ApplicationInfo>()

        for (ri in resolveInfos) {
            val ai = ri.activityInfo?.applicationInfo ?: continue
            appInfoMap[ai.packageName] = ai
        }
        // Also include installed packages from getInstalledApplications
        val installedApps = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
        for (ai in installedApps) {
            val isUserApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isUserApp || pm.getLaunchIntentForPackage(ai.packageName) != null) {
                appInfoMap.putIfAbsent(ai.packageName, ai)
            }
        }

        val result = mutableListOf<InstalledAppInfo>()
        for ((pkg, appInfo) in appInfoMap) {
            val sourcePath = appInfo.sourceDir ?: continue
            val apkFile = File(sourcePath)
            if (!apkFile.exists() || !apkFile.canRead()) continue

            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                .getOrDefault(pkg)
                .trim()
                .ifEmpty { pkg }

            val iconBitmap = runCatching {
                drawableToBitmap(pm.getApplicationIcon(appInfo), 96)
            }.getOrNull()

            result.add(
                InstalledAppInfo(
                    packageName = pkg,
                    appName = label,
                    apkFilePath = apkFile.absolutePath,
                    sizeBytes = apkFile.length(),
                    iconBitmap = iconBitmap
                )
            )
        }

        // Put non-system / user apps first, then alphabetical
        result.sortedWith(
            compareBy<InstalledAppInfo> { it.packageName.startsWith("com.android.") || it.packageName.startsWith("android") }
                .thenBy { it.appName.lowercase() }
        )
    }

    suspend fun resolveInstalledAppToShareable(app: InstalledAppInfo): ShareableFile? = withContext(Dispatchers.IO) {
        try {
            val apkFile = File(app.apkFilePath)
            if (!apkFile.exists()) return@withContext null
            val sha256 = SecurityUtils.computeSha256(apkFile)
            val cleanAppName = SecurityUtils.sanitizeFileName(app.appName)
            val finalName = if (cleanAppName.endsWith(".apk", ignoreCase = true)) {
                cleanAppName
            } else {
                "$cleanAppName.apk"
            }
            ShareableFile(
                id = UUID.randomUUID().toString(),
                name = finalName,
                sizeBytes = apkFile.length(),
                mimeType = "application/vnd.android.package-archive",
                category = FileCategory.APK,
                sha256 = sha256,
                uriString = null,
                localFilePath = apkFile.absolutePath,
                appIconBitmap = app.iconBitmap
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable, maxPx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, maxPx, maxPx, true)
        }
        val bitmap = Bitmap.createBitmap(maxPx, maxPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Resolves an Android content:// URI into a ShareableFile.
     */
    suspend fun resolveUriToShareableFile(uri: Uri, overrideCategory: FileCategory? = null): ShareableFile? =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                var displayName = "file_${System.currentTimeMillis()}"
                var sizeBytes = 0L

                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) {
                            displayName = cursor.getString(nameIdx) ?: displayName
                        }
                        if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
                            sizeBytes = cursor.getLong(sizeIdx)
                        }
                    }
                }

                val mimeType = resolver.getType(uri) ?: SecurityUtils.resolveMimeType(displayName)
                val sha256 = resolver.openInputStream(uri)?.use { stream ->
                    SecurityUtils.computeSha256(stream)
                } ?: return@withContext null

                if (sizeBytes <= 0L) {
                    var counted = 0L
                    resolver.openInputStream(uri)?.use { stream ->
                        val buf = ByteArray(16 * 1024)
                        var r: Int
                        while (stream.read(buf).also { r = it } != -1) {
                            counted += r
                        }
                    }
                    sizeBytes = counted
                }

                val category = overrideCategory ?: SecurityUtils.detectCategory(displayName, mimeType)

                ShareableFile(
                    id = UUID.randomUUID().toString(),
                    name = SecurityUtils.sanitizeFileName(displayName),
                    sizeBytes = sizeBytes,
                    mimeType = mimeType,
                    category = category,
                    sha256 = sha256,
                    uriString = uri.toString(),
                    localFilePath = null
                )
            } catch (_: Exception) {
                null
            }
        }

    suspend fun resolveLocalFileToShareable(file: File, overrideCategory: FileCategory? = null): ShareableFile =
        withContext(Dispatchers.IO) {
            val mimeType = SecurityUtils.resolveMimeType(file.name)
            val category = overrideCategory ?: SecurityUtils.detectCategory(file.name, mimeType)
            val sha256 = SecurityUtils.computeSha256(file)
            ShareableFile(
                id = UUID.randomUUID().toString(),
                name = file.name,
                sizeBytes = file.length(),
                mimeType = mimeType,
                category = category,
                sha256 = sha256,
                uriString = null,
                localFilePath = file.absolutePath
            )
        }

    fun openShareableInputStream(file: ShareableFile): InputStream? {
        file.localFilePath?.let { path ->
            val f = File(path)
            if (f.exists()) return FileInputStream(f)
        }
        file.uriString?.let { uriStr ->
            return context.contentResolver.openInputStream(Uri.parse(uriStr))
        }
        return null
    }

    // Internal helpers used by automated unit tests
    suspend fun createRealSamplePhoto(index: Int = 1): ShareableFile = withContext(Dispatchers.IO) {
        val file = File(sampleFilesDir, "WASSIL_Photo_${index}_${System.currentTimeMillis() % 10000}.png")
        val width = 800
        val height = 600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            Color.rgb(10, 88, 202),
            Color.rgb(2, 132, 199),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        resolveLocalFileToShareable(file, FileCategory.PHOTO)
    }

    suspend fun createRealSamplePdf(): ShareableFile = withContext(Dispatchers.IO) {
        val file = File(sampleFilesDir, "WASSIL_Document_${System.currentTimeMillis() % 10000}.pdf")
        val contentStream = "BT\n/F1 18 Tf\n50 760 Td\n(WASSIL File Transfer) Tj\nET\n"
        val pdfContent = buildString {
            append("%PDF-1.4\n")
            append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
            append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
            append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n")
            append("4 0 obj\n<< /Length ${contentStream.length} >>\nstream\n")
            append(contentStream)
            append("endstream\nendobj\n")
            append("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")
            append("trailer\n<< /Root 1 0 R /Size 6 >>\n%%EOF\n")
        }
        FileOutputStream(file).use { out ->
            out.write(pdfContent.toByteArray(Charsets.US_ASCII))
        }
        resolveLocalFileToShareable(file, FileCategory.DOCUMENT)
    }

    suspend fun createRealSampleAudio(): ShareableFile = withContext(Dispatchers.IO) {
        val file = File(sampleFilesDir, "WASSIL_Audio_${System.currentTimeMillis() % 10000}.wav")
        val sampleRate = 22050
        val numSamples = sampleRate * 2
        val pcmData = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val sample = (sin(2.0 * Math.PI * 440.0 * t) * 0.5)
            val shortVal = (sample * Short.MAX_VALUE).toInt().toShort()
            pcmData[i * 2] = (shortVal.toInt() and 0xFF).toByte()
            pcmData[i * 2 + 1] = ((shortVal.toInt() shr 8) and 0xFF).toByte()
        }
        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + pcmData.size)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1)
            header.putShort(1)
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2)
            header.putShort(2)
            header.putShort(16)
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(pcmData.size)
            out.write(header.array())
            out.write(pcmData)
        }
        resolveLocalFileToShareable(file, FileCategory.MUSIC)
    }

    suspend fun createRealSampleVideo(): ShareableFile = withContext(Dispatchers.IO) {
        val file = File(sampleFilesDir, "WASSIL_Video_${System.currentTimeMillis() % 10000}.mp4")
        FileOutputStream(file).use { out ->
            val ftyp = byteArrayOf(
                0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70,
                0x69, 0x73, 0x6F, 0x6D, 0x00, 0x00, 0x02, 0x00,
                0x69, 0x73, 0x6F, 0x6D, 0x69, 0x73, 0x6F, 0x32
            )
            out.write(ftyp)
            val frameChunk = ByteArray(32 * 1024) { idx -> ((idx * 31 + 17) and 0xFF).toByte() }
            repeat(16) { out.write(frameChunk) }
        }
        resolveLocalFileToShareable(file, FileCategory.VIDEO)
    }

    suspend fun createRealSampleZip(): ShareableFile = withContext(Dispatchers.IO) {
        val file = File(sampleFilesDir, "WASSIL_Archive_${System.currentTimeMillis() % 10000}.zip")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            for (i in 1..3) {
                zos.putNextEntry(ZipEntry("file_$i.txt"))
                zos.write("WASSIL Archive File #$i\n".repeat(120).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        resolveLocalFileToShareable(file, FileCategory.ARCHIVE)
    }

    suspend fun createRealSampleApk(): ShareableFile = withContext(Dispatchers.IO) {
        val file = File(sampleFilesDir, "WASSIL_App_${System.currentTimeMillis() % 10000}.apk")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zos.write("Manifest-Version: 1.0\n".toByteArray())
            zos.closeEntry()
        }
        resolveLocalFileToShareable(file, FileCategory.APK)
    }

    suspend fun createRealLargeFile(sizeMb: Int = 4): ShareableFile = withContext(Dispatchers.IO) {
        val file = File(sampleFilesDir, "WASSIL_LargeFile_${sizeMb}MB_${System.currentTimeMillis() % 10000}.bin")
        val chunk = ByteArray(64 * 1024)
        val totalChunks = sizeMb * 16
        FileOutputStream(file).use { out ->
            for (c in 0 until totalChunks) {
                for (i in chunk.indices) {
                    chunk[i] = ((i xor c * 13) and 0xFF).toByte()
                }
                out.write(chunk)
            }
        }
        resolveLocalFileToShareable(file, FileCategory.OTHER)
    }
}
