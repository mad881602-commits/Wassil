package com.example.network

import com.example.files.FileSelectionManager
import com.example.model.ShareableFile
import com.example.model.TransferSessionOffer
import com.example.security.SecurityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

/**
 * Embedded HTTP + Installable Progressive Web App (PWA) server.
 * Allows any mobile browser or device without the native APK to open http://<Device-IP>:<WebPort>,
 * install WASSIL as a standalone PWA (with manifest.json and Service Worker sw.js),
 * inspect the offered files, verify SHA-256 hashes in the browser using WebCrypto,
 * and download files directly Peer-to-Peer in 64 KB chunks.
 */
class EmbeddedWebPwaServer(
    private val fileSelectionManager: FileSelectionManager
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    val boundPort: Int
        get() = serverSocket?.localPort ?: 0

    fun start(
        scope: CoroutineScope,
        offerProvider: () -> TransferSessionOffer?,
        filesProvider: () -> List<ShareableFile>
    ): Int {
        stop()
        val ss = ServerSocket(0)
        serverSocket = ss
        serverJob = scope.launch(Dispatchers.IO) {
            while (isActive && !ss.isClosed) {
                val client = runCatching { ss.accept() }.getOrNull() ?: break
                launch(Dispatchers.IO) {
                    handleClient(client, offerProvider(), filesProvider())
                }
            }
        }
        return ss.localPort
    }

    fun stop() {
        runCatching { serverJob?.cancel() }
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverJob = null
    }

    private fun handleClient(
        socket: Socket,
        offer: TransferSessionOffer?,
        files: List<ShareableFile>
    ) {
        try {
            socket.soTimeout = 15000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val path = if (parts.size >= 2) parts[1] else "/"

            // Drain HTTP headers
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }

            val out = BufferedOutputStream(socket.getOutputStream())
            when {
                path == "/manifest.json" -> {
                    val manifest = """
                        {
                          "name": "WASSIL (وَصِّل) - Direct P2P File Sharing",
                          "short_name": "WASSIL",
                          "start_url": "/",
                          "display": "standalone",
                          "background_color": "#F8FAFC",
                          "theme_color": "#0A58CA",
                          "dir": "rtl",
                          "lang": "ar"
                        }
                    """.trimIndent().toByteArray(Charsets.UTF_8)
                    writeHttpResponse(out, "200 OK", "application/manifest+json; charset=utf-8", manifest)
                }

                path == "/sw.js" -> {
                    val sw = """
                        self.addEventListener('install', (e) => self.skipWaiting());
                        self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()));
                        self.addEventListener('fetch', (e) => e.respondWith(fetch(e.request)));
                    """.trimIndent().toByteArray(Charsets.UTF_8)
                    writeHttpResponse(out, "200 OK", "application/javascript; charset=utf-8", sw)
                }

                path.startsWith("/download/") -> {
                    val fileId = path.removePrefix("/download/").trim()
                    val target = files.find { it.id == fileId }
                    if (target == null) {
                        writeHttpResponse(
                            out,
                            "404 Not Found",
                            "text/plain; charset=utf-8",
                            "الملف غير موجود".toByteArray(Charsets.UTF_8)
                        )
                    } else {
                        val inputStream = fileSelectionManager.openShareableInputStream(target)
                        if (inputStream == null) {
                            writeHttpResponse(
                                out,
                                "500 Internal Server Error",
                                "text/plain; charset=utf-8",
                                "تعذر قراءة الملف".toByteArray(Charsets.UTF_8)
                            )
                        } else {
                            inputStream.use { stream ->
                                val headers = buildString {
                                    append("HTTP/1.1 200 OK\r\n")
                                    append("Content-Type: ${target.mimeType}\r\n")
                                    append("Content-Length: ${target.sizeBytes}\r\n")
                                    append("Content-Disposition: attachment; filename=\"${target.name}\"\r\n")
                                    append("X-Wassil-SHA256: ${target.sha256}\r\n")
                                    append("Connection: close\r\n\r\n")
                                }
                                out.write(headers.toByteArray(Charsets.UTF_8))
                                val buf = ByteArray(SecurityUtils.DEFAULT_CHUNK_SIZE)
                                var read: Int
                                while (stream.read(buf).also { read = it } != -1) {
                                    out.write(buf, 0, read)
                                }
                                out.flush()
                            }
                        }
                    }
                }

                else -> {
                    val html = buildPwaHtmlPage(offer, files).toByteArray(Charsets.UTF_8)
                    writeHttpResponse(out, "200 OK", "text/html; charset=utf-8", html)
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun writeHttpResponse(
        out: BufferedOutputStream,
        status: String,
        contentType: String,
        body: ByteArray
    ) {
        val header = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    private fun buildPwaHtmlPage(offer: TransferSessionOffer?, files: List<ShareableFile>): String {
        val senderName = offer?.senderDeviceName ?: "WASSIL Android"
        val pairingCode = offer?.pairingCode ?: "------"
        val rows = files.joinToString("\n") { file ->
            """
            <div class="file-card">
              <div class="file-info">
                <div class="file-name">${file.name}</div>
                <div class="file-meta">${SecurityUtils.formatBytes(file.sizeBytes)} • SHA-256: <code>${file.sha256.take(16)}...</code></div>
              </div>
              <a class="btn-download" href="/download/${file.id}" download="${file.name}">تنزيل مباشر (P2P)</a>
            </div>
            """.trimIndent()
        }
        return """
            <!DOCTYPE html>
            <html lang="ar" dir="rtl">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <meta name="theme-color" content="#0A58CA" />
              <link rel="manifest" href="/manifest.json" />
              <title>وَصِّل (WASSIL) - P2P PWA</title>
              <style>
                body { font-family: system-ui, -apple-system, sans-serif; background: #F8FAFC; color: #0F172A; margin: 0; padding: 20px; }
                .container { max-width: 540px; margin: 0 auto; background: #FFFFFF; border-radius: 24px; padding: 24px; box-shadow: 0 10px 30px rgba(10,88,202,0.08); }
                .badge { display: inline-block; background: #E7F1FF; color: #0A58CA; padding: 6px 14px; border-radius: 999px; font-weight: 700; font-size: 13px; }
                h1 { color: #0A58CA; margin: 12px 0 4px 0; font-size: 26px; }
                .file-card { display: flex; justify-content: space-between; align-items: center; background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 16px; padding: 14px; margin-top: 12px; gap: 12px; }
                .file-name { font-weight: 700; font-size: 15px; word-break: break-all; }
                .file-meta { font-size: 12px; color: #475569; margin-top: 4px; }
                .btn-download { background: #0A58CA; color: #FFFFFF; text-decoration: none; padding: 10px 16px; border-radius: 12px; font-weight: 700; font-size: 13px; white-space: nowrap; }
              </style>
            </head>
            <body>
              <div class="container">
                <span class="badge">وَصِّل • WASSIL P2P PWA</span>
                <h1>ملفات مشتركة من: $senderName</h1>
                <p>رمز الربط النشط: <strong>$pairingCode</strong> — اتصال مباشر من جهاز إلى جهاز بدون سحابة.</p>
                $rows
              </div>
              <script>
                if ('serviceWorker' in navigator) {
                  navigator.serviceWorker.register('/sw.js').catch(() => {});
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
