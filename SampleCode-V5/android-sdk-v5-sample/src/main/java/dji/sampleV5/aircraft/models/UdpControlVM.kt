package dji.sampleV5.aircraft.models

import androidx.lifecycle.MutableLiveData
import dji.sdk.keyvalue.value.flightcontroller.FlightCoordinateSystem
import dji.sdk.keyvalue.value.flightcontroller.RollPitchControlMode
import dji.sdk.keyvalue.value.flightcontroller.VerticalControlMode
import dji.sdk.keyvalue.value.flightcontroller.VirtualStickFlightControlParam
import dji.sdk.keyvalue.value.flightcontroller.YawControlMode
import dji.sampleV5.aircraft.control.UdpControlServer
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.virtualstick.VirtualStickManager
import dji.v5.utils.common.LogUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Timer
import java.util.TimerTask

class UdpControlVM : DJIViewModel() {
    private val tag = "UdpControlVM"
    private var udpServer: UdpControlServer? = null
    private var timeoutTimer: Timer? = null

    @Volatile
    private var lastPacketTime = 0L

    @Volatile
    private var virtualStickEnabled = false

    @Volatile
    private var controlEnabled = false

    val serverStatusLiveData = MutableLiveData("未启动")
    val controlStatusLiveData = MutableLiveData("虚拟摇杆未启动")
    val receiveMessageLiveData = MutableLiveData<String>()

    fun startUdpListening(port: Int = 9999) {
        if (udpServer?.isRunning() == true) return
        startServer(port)
        controlStatusLiveData.postValue("仅监听显示，不发送飞控指令")
    }

    fun startUdpControl() {
        if (controlEnabled) return
        serverStatusLiveData.postValue("准备虚拟摇杆...")
        VirtualStickManager.getInstance().enableVirtualStick(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                virtualStickEnabled = true
                controlEnabled = true
                VirtualStickManager.getInstance().setVirtualStickAdvancedModeEnabled(true)
                controlStatusLiveData.postValue("虚拟摇杆已启动，Advanced Mode 已开启")
                startTimeoutWatchdog()
            }

            override fun onFailure(error: IDJIError) {
                controlStatusLiveData.postValue("虚拟摇杆启动失败: $error")
                serverStatusLiveData.postValue("未启动")
            }
        })
    }

    fun stopUdpControl() {
        controlEnabled = false
        stopTimeoutWatchdog()
        sendZeroParam()
        VirtualStickManager.getInstance().setVirtualStickAdvancedModeEnabled(false)
        if (virtualStickEnabled) {
            VirtualStickManager.getInstance().disableVirtualStick(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    virtualStickEnabled = false
                    controlStatusLiveData.postValue("虚拟摇杆已关闭")
                }

                override fun onFailure(error: IDJIError) {
                    controlStatusLiveData.postValue("虚拟摇杆关闭失败: $error")
                }
            })
        } else {
            controlStatusLiveData.postValue("虚拟摇杆未启动")
        }
    }

    fun stopAll() {
        stopUdpControl()
        udpServer?.stop()
        udpServer = null
    }

    fun isServerRunning(): Boolean = udpServer?.isRunning() == true

    fun isControlEnabled(): Boolean = controlEnabled

    private fun startServer(port: Int) {
        lastPacketTime = System.currentTimeMillis()
        udpServer = UdpControlServer(
            port = port,
            onPacket = { data, host, senderPort -> handlePacket(data, host, senderPort) },
            onStatus = { status -> serverStatusLiveData.postValue(status) }
        )
        udpServer?.start()
    }

    private fun handlePacket(data: ByteArray, host: String, senderPort: Int) {
        if (data.size != 24) {
            receiveMessageLiveData.postValue("${timeNow()} 来自 $host:$senderPort，长度 ${data.size}，已忽略")
            return
        }
        val floats = parseLittleEndianFloats(data)
        lastPacketTime = System.currentTimeMillis()
        if (controlEnabled) {
            val param = buildVirtualStickParam(floats)
            VirtualStickManager.getInstance().sendVirtualStickAdvancedParam(param)
        }
        val mode = if (controlEnabled) "已发送飞控" else "仅显示"
        val msg = "${timeNow()} [$mode] 来自 $host:$senderPort，roll=${floats[0]}, pitch=${floats[1]}, vertical=${floats[2]}, yaw=${floats[5]}"
        receiveMessageLiveData.postValue(msg)
        LogUtils.i(tag, msg)
    }

    private fun parseLittleEndianFloats(bytes: ByteArray): FloatArray {
        val result = FloatArray(6)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in result.indices) {
            result[i] = buffer.float
        }
        return result
    }

    private fun buildVirtualStickParam(floats: FloatArray): VirtualStickFlightControlParam {
        return VirtualStickFlightControlParam().apply {
            rollPitchCoordinateSystem = FlightCoordinateSystem.BODY
            rollPitchControlMode = RollPitchControlMode.VELOCITY
            yawControlMode = YawControlMode.ANGULAR_VELOCITY
            verticalControlMode = VerticalControlMode.VELOCITY
            setRoll(floats[0].toDouble())
            setPitch(floats[1].toDouble())
            setVerticalThrottle(floats[2].toDouble())
            setYaw(floats[5].toDouble())
        }
    }

    private fun sendZeroParam() {
        val zeroParam = VirtualStickFlightControlParam().apply {
            rollPitchCoordinateSystem = FlightCoordinateSystem.BODY
            rollPitchControlMode = RollPitchControlMode.VELOCITY
            yawControlMode = YawControlMode.ANGULAR_VELOCITY
            verticalControlMode = VerticalControlMode.VELOCITY
            setRoll(0.0)
            setPitch(0.0)
            setVerticalThrottle(0.0)
            setYaw(0.0)
        }
        VirtualStickManager.getInstance().sendVirtualStickAdvancedParam(zeroParam)
    }

    private fun startTimeoutWatchdog() {
        stopTimeoutWatchdog()
        timeoutTimer = Timer("UdpControlTimeoutTimer").apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (controlEnabled && udpServer?.isRunning() == true && System.currentTimeMillis() - lastPacketTime > 500L) {
                        sendZeroParam()
                        controlStatusLiveData.postValue("UDP 超时，已发送零速度")
                    }
                }
            }, 500L, 200L)
        }
    }

    private fun stopTimeoutWatchdog() {
        timeoutTimer?.cancel()
        timeoutTimer = null
    }

    private fun timeNow(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(Date())

    override fun onCleared() {
        stopAll()
        super.onCleared()
    }
}
