package dji.sampleV5.aircraft.control

import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

class WaypointCaptureClient(
    private val onStatus: (String) -> Unit,
    private val onResponse: (JSONObject) -> Unit
) {
    @Volatile
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private var receiveThread: Thread? = null

    @Volatile
    private var running = false

    fun connect(host: String, port: Int, timeoutMs: Int = 3000) {
        if (running) return
        running = true
        onStatus("正在连接 $host:$port ...")
        thread(name = "WaypointCaptureConnectThread") {
            try {
                val connectedSocket = Socket()
                connectedSocket.connect(InetSocketAddress(host, port), timeoutMs)
                socket = connectedSocket
                reader = BufferedReader(InputStreamReader(connectedSocket.getInputStream(), Charsets.UTF_8))
                writer = BufferedWriter(OutputStreamWriter(connectedSocket.getOutputStream(), Charsets.UTF_8))
                onStatus("已连接 $host:$port")
                startReceiveLoop()
            } catch (e: Exception) {
                running = false
                closeResources()
                onStatus("连接失败: ${e.message}")
            }
        }
    }

    fun disconnect() {
        running = false
        closeResources()
        onStatus("已断开")
    }

    fun sendCaptureRequest(requestId: String) {
        val request = JSONObject()
            .put("type", "capture_waypoint")
            .put("request_id", requestId)
        send(request)
    }

    fun ping(requestId: String) {
        val request = JSONObject()
            .put("type", "ping")
            .put("request_id", requestId)
        send(request)
    }

    fun isConnected(): Boolean {
        val currentSocket = socket
        return running && currentSocket != null && currentSocket.isConnected && !currentSocket.isClosed
    }

    private fun send(request: JSONObject) {
        thread(name = "WaypointCaptureSendThread") {
            try {
                val currentWriter = writer ?: throw IllegalStateException("not connected")
                currentWriter.write(request.toString())
                currentWriter.newLine()
                currentWriter.flush()
                onStatus("已发送: ${request.optString("type")}")
            } catch (e: Exception) {
                onStatus("发送失败: ${e.message}")
            }
        }
    }

    private fun startReceiveLoop() {
        receiveThread = thread(name = "WaypointCaptureReceiveThread") {
            try {
                while (running) {
                    val line = reader?.readLine() ?: break
                    onResponse(JSONObject(line))
                }
            } catch (e: Exception) {
                if (running) onStatus("接收异常: ${e.message}")
            } finally {
                val wasRunning = running
                running = false
                closeResources()
                if (wasRunning) onStatus("机载端已关闭连接，请重新建立通信")
            }
        }
    }

    private fun closeResources() {
        try {
            reader?.close()
        } catch (_: Exception) {
        }
        try {
            writer?.close()
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        reader = null
        writer = null
        socket = null
    }
}
