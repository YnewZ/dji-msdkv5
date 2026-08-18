package dji.sampleV5.aircraft.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dji.sampleV5.aircraft.control.WaypointCaptureClient
import org.json.JSONObject

class WaypointCaptureVM : DJIViewModel() {
    private var client: WaypointCaptureClient? = null

    private val _connectionStatus = MutableLiveData("未连接")
    private val _lastResponse = MutableLiveData("暂无采集结果")
    private val _connected = MutableLiveData(false)

    val connectionStatus: LiveData<String>
        get() = _connectionStatus

    val lastResponse: LiveData<String>
        get() = _lastResponse

    val connected: LiveData<Boolean>
        get() = _connected

    fun connect(host: String, port: Int) {
        disconnect()
        client = WaypointCaptureClient(
            onStatus = { status ->
                _connectionStatus.postValue(status)
                _connected.postValue(client?.isConnected() == true)
            },
            onResponse = { response ->
                _lastResponse.postValue(formatResponse(response))
                _connectionStatus.postValue("收到机载端返回: ${response.optString("type")}")
                _connected.postValue(client?.isConnected() == true)
            }
        )
        client?.connect(host.trim(), port)
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        _connected.postValue(false)
    }

    fun captureWaypoint() {
        val currentClient = client
        if (currentClient?.isConnected() != true) {
            _connectionStatus.postValue("未连接机载计算机")
            _connected.postValue(false)
            return
        }
        val requestId = System.currentTimeMillis().toString()
        _lastResponse.postValue("采集请求已发送，等待机载计算机保存结果...\nrequest_id=$requestId")
        currentClient.sendCaptureRequest(requestId)
    }

    fun ping() {
        val currentClient = client
        if (currentClient?.isConnected() != true) {
            _connectionStatus.postValue("未连接机载计算机")
            _connected.postValue(false)
            return
        }
        currentClient.ping(System.currentTimeMillis().toString())
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }

    private fun formatResponse(response: JSONObject): String {
        val success = response.optBoolean("success", false)
        if (!success) {
            return "失败: ${response.optString("error", "UNKNOWN")}\n${response.optString("message")}".trim()
        }
        if (response.optString("type") == "pong") {
            return "通信正常: pong"
        }
        return "保存成功，航点已写入机载端文件\n" +
                "index=${response.optInt("index")}\n" +
                "x=${response.optDouble("x")}\n" +
                "y=${response.optDouble("y")}\n" +
                "z=${response.optDouble("z")}\n" +
                "yaw=${response.optDouble("yaw")}\n" +
                "file=${response.optString("file")}".trim()
    }
}
