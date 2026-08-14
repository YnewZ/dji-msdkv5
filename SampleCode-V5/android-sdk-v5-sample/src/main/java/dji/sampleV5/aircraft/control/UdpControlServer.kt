package dji.sampleV5.aircraft.control

import dji.v5.utils.common.LogUtils
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.concurrent.thread

class UdpControlServer(
    private val port: Int,
    private val onPacket: (ByteArray, String, Int) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val tag = "UdpControlServer"

    @Volatile
    private var running = false
    private var socket: DatagramSocket? = null
    private var serverThread: Thread? = null

    fun start() {
        if (running) return
        running = true
        onStatus("启动中...")
        serverThread = thread(name = "UdpControlServerThread") {
            try {
                socket = DatagramSocket(port)
                onStatus("监听中: $port")
                LogUtils.i(tag, "UDP control server started on port $port")
                val buffer = ByteArray(1024)
                while (running) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    val data = packet.data.copyOf(packet.length)
                    onPacket(data, packet.address.hostAddress ?: "unknown", packet.port)
                }
            } catch (e: Exception) {
                if (running) {
                    val message = "异常: ${e.message}"
                    onStatus(message)
                    LogUtils.e(tag, message)
                }
            } finally {
                socket?.close()
                socket = null
                running = false
            }
        }
    }

    fun stop() {
        running = false
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        serverThread = null
        onStatus("已停止")
        LogUtils.i(tag, "UDP control server stopped")
    }

    fun isRunning(): Boolean = running
}
