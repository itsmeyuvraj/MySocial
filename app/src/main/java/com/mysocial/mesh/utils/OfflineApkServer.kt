package com.mysocial.mesh.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight, zero-dependency Kotlin ServerSocket HTTP server.
 * Serves the installed base APK (context.applicationInfo.sourceDir) over local Wi-Fi / Hotspot
 * to allow friends to download the app with zero internet.
 */
class OfflineApkServer(private val context: Context) {
    private val tag = "OfflineApkServer"
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)

    var localIp: String = "127.0.0.1"
        private set

    var port: Int = 8080
        private set

    val apkDownloadUrl: String
        get() = "http://$localIp:$port/mysocial.apk"

    suspend fun start(preferredPort: Int = 8080): Boolean = withContext(Dispatchers.IO) {
        if (isRunning.get()) return@withContext true

        localIp = findLocalIpAddress() ?: "127.0.0.1"
        val apkFile = File(context.applicationInfo.sourceDir)
        if (!apkFile.exists() || !apkFile.canRead()) {
            Log.e(tag, "Source APK cannot be read at ${apkFile.absolutePath}")
            return@withContext false
        }

        try {
            // Try preferredPort, if busy fallback to ephemeral port 0
            val socket = try {
                ServerSocket(preferredPort)
            } catch (_: Exception) {
                ServerSocket(0)
            }
            serverSocket = socket
            port = socket.localPort
            isRunning.set(true)

            Thread({
                listenLoop(socket, apkFile)
            }, "OfflineApkServerThread").start()

            Log.i(tag, "Offline APK Server started at $apkDownloadUrl (APK size: ${apkFile.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start OfflineApkServer: ${e.message}", e)
            false
        }
    }

    private fun listenLoop(server: ServerSocket, apkFile: File) {
        while (isRunning.get() && !server.isClosed) {
            try {
                val client = server.accept()
                Thread({
                    handleClient(client, apkFile)
                }, "ApkClientHandler").start()
            } catch (_: Exception) {
                break
            }
        }
    }

    private fun handleClient(socket: Socket, apkFile: File) {
        try {
            socket.use { s ->
                val reader = s.getInputStream().bufferedReader()
                val requestLine = reader.readLine() ?: return
                // Drain remaining request headers
                while (true) {
                    val header = reader.readLine()
                    if (header.isNullOrBlank()) break
                }

                val out = BufferedOutputStream(s.getOutputStream())
                val parts = requestLine.split(" ")
                val path = parts.getOrNull(1) ?: "/"

                if (path == "/mysocial.apk" || path == "/" || path.startsWith("/mysocial.apk?")) {
                    val fileLength = apkFile.length()
                    val header = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: application/vnd.android.package-archive\r\n")
                        append("Content-Length: $fileLength\r\n")
                        append("Content-Disposition: attachment; filename=\"mysocial.apk\"\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    out.write(header.toByteArray(Charsets.UTF_8))
                    FileInputStream(apkFile).use { fis ->
                        val buffer = ByteArray(65536)
                        var read: Int
                        while (fis.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                        }
                    }
                    out.flush()
                } else {
                    val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    out.write(notFound.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Client connection finished: ${e.message}")
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            try {
                serverSocket?.close()
            } catch (_: Exception) {}
            serverSocket = null
            Log.i(tag, "Offline APK Server stopped")
        }
    }

    companion object {
        fun findLocalIpAddress(): String? {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
                var fallbackIp: String? = null
                for (iface in interfaces) {
                    if (iface.isLoopback || !iface.isUp) continue
                    for (addr in iface.inetAddresses) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val host = addr.hostAddress ?: continue
                            // Prioritize hotspot/wlan interfaces
                            if (iface.name.startsWith("wlan") || iface.name.startsWith("ap") || iface.name.startsWith("swlan")) {
                                return host
                            }
                            if (fallbackIp == null) {
                                fallbackIp = host
                            }
                        }
                    }
                }
                return fallbackIp
            } catch (_: Exception) {
                return null
            }
        }
    }
}

