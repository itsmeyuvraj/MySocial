package com.mysocial.mesh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mysocial.mesh.update.PeerApkUpdateManager
import com.mysocial.mesh.update.PeerUpdateInfo
import com.mysocial.mesh.utils.OfflineApkServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeerApkUpdateManagerTest {

    private lateinit var context: Context
    private lateinit var apkFile: File
    private var server: OfflineApkServer? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val dummyDir = File(context.cacheDir, "mock_source_dir")
        dummyDir.mkdirs()
        apkFile = File(dummyDir, "mock_base.apk")
        apkFile.writeBytes(ByteArray(1024) { 0x42 }) // 1KB dummy content
        context.applicationInfo.sourceDir = apkFile.absolutePath
    }

    @After
    fun tearDown() {
        server?.stop()
        if (::apkFile.isInitialized && apkFile.exists()) {
            apkFile.delete()
        }
    }

    @Test
    fun testOfflineApkServerStartsAndServesApk() = runBlocking {
        server = OfflineApkServer(context)
        val started = server!!.start(0)
        assertTrue("Server should start successfully", started)
        val port = server!!.port
        assertTrue("Server port should be assigned > 0", port > 0)

        // Make an HTTP GET request to /mysocial.apk
        val socket = Socket("127.0.0.1", port)
        val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()))
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        writer.print("GET /mysocial.apk HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
        writer.flush()

        val statusLine = reader.readLine()
        assertEquals("HTTP/1.1 200 OK", statusLine)

        var contentLength = -1
        var line: String? = reader.readLine()
        while (!line.isNullOrEmpty()) {
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(":").trim().toInt()
            }
            line = reader.readLine()
        }

        assertEquals(apkFile.length().toInt(), contentLength)
        socket.close()
    }

    @Test
    fun testOfflineApkServerReturns404ForInvalidPath() = runBlocking {
        server = OfflineApkServer(context)
        server!!.start(0)
        val port = server!!.port

        val socket = Socket("127.0.0.1", port)
        val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()))
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        writer.print("GET /unknown-file.txt HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
        writer.flush()

        val statusLine = reader.readLine()
        assertEquals("HTTP/1.1 404 Not Found", statusLine)
        socket.close()
    }

    @Test
    fun testOfflineApkServerHeadRequest() = runBlocking {
        server = OfflineApkServer(context)
        server!!.start(0)
        val port = server!!.port

        val socket = Socket("127.0.0.1", port)
        val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()))
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        writer.print("HEAD /mysocial.apk HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
        writer.flush()

        val statusLine = reader.readLine()
        assertEquals("HTTP/1.1 200 OK", statusLine)

        var contentLength = -1
        var line: String? = reader.readLine()
        while (!line.isNullOrEmpty()) {
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(":").trim().toInt()
            }
            line = reader.readLine()
        }

        assertEquals(apkFile.length().toInt(), contentLength)
        val body = reader.readText()
        socket.close()

        assertTrue("HEAD request must return no body", body.isEmpty())
    }

    @Test
    fun testPeerVersionDiscoveredIgnoresOlderOrEqualBuilds() {
        val manager = PeerApkUpdateManager.getInstance(context)
        val currentVer = manager.currentVersionCode

        // Discovery of older build
        manager.onPeerVersionDiscovered(
            peerId = "peer_old",
            peerName = "Old Peer",
            remoteHost = "127.0.0.1",
            remotePort = 8080,
            remoteVersionCode = currentVer - 1,
            remoteVersionName = "1.0.old"
        )
        assertNull("Older build must not trigger availableUpdate", manager.availableUpdate.value)

        // Discovery of equal build
        manager.onPeerVersionDiscovered(
            peerId = "peer_equal",
            peerName = "Equal Peer",
            remoteHost = "127.0.0.1",
            remotePort = 8080,
            remoteVersionCode = currentVer,
            remoteVersionName = "1.0.current"
        )
        assertNull("Equal build must not trigger availableUpdate", manager.availableUpdate.value)
    }

    @Test
    fun testPeerUpdateInfoProperties() {
        val dummyFile = File(context.cacheDir, "test.apk")
        val info = PeerUpdateInfo(
            peerId = "peer_test",
            peerName = "Test Peer",
            remoteVersionCode = 100L,
            remoteVersionName = "v2.0.0",
            apkFile = dummyFile,
            fileSizeBytes = 1024L
        )
        assertEquals("peer_test", info.peerId)
        assertEquals("Test Peer", info.peerName)
        assertEquals(100L, info.remoteVersionCode)
        assertEquals("v2.0.0", info.remoteVersionName)
        assertEquals(1024L, info.fileSizeBytes)
    }

    @Test
    fun testDismissUpdateResetsState() {
        val manager = PeerApkUpdateManager.getInstance(context)
        manager.dismissUpdate()
        assertNull("Dismissing update should ensure availableUpdate is null", manager.availableUpdate.value)
    }
}
