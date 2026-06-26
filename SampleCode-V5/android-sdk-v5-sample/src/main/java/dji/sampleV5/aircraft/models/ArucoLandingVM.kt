package dji.sampleV5.aircraft.models

import android.view.Surface
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sampleV5.aircraft.aruco.ArucoAlignController
import dji.sampleV5.aircraft.aruco.ArucoDetection
import dji.sampleV5.aircraft.aruco.ArucoGuidance
import dji.sampleV5.aircraft.aruco.ArucoGuidanceController
import dji.sampleV5.aircraft.aruco.OpenCvArucoDetector
import dji.sdk.keyvalue.key.GimbalKey
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.flightcontroller.FlightCoordinateSystem
import dji.sdk.keyvalue.value.flightcontroller.RollPitchControlMode
import dji.sdk.keyvalue.value.flightcontroller.VerticalControlMode
import dji.sdk.keyvalue.value.flightcontroller.VirtualStickFlightControlParam
import dji.sdk.keyvalue.value.flightcontroller.YawControlMode
import dji.sdk.keyvalue.value.gimbal.GimbalResetType
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.et.action
import dji.v5.et.create
import dji.v5.et.listen
import dji.v5.manager.KeyManager
import dji.v5.manager.aircraft.virtualstick.VirtualStickManager
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ICameraStreamManager
import dji.v5.manager.interfaces.ICameraStreamManager.AvailableCameraUpdatedListener
import dji.v5.manager.interfaces.ICameraStreamManager.CameraFrameListener
import dji.v5.utils.common.DJIExecutor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ArucoLandingVM : DJIViewModel(), AvailableCameraUpdatedListener {

    enum class AutoLandState {
        IDLE,
        ALIGN_ONLY,
        DESCENDING,
        FINAL_AUTO_LANDING
    }

    private val detector = OpenCvArucoDetector(targetMarkerId = 1)
    private val guidanceController = ArucoGuidanceController()
    private val alignController = ArucoAlignController()
    private val visualControlLock = Any()
    private val _availableCameraListData = MutableLiveData<List<ComponentIndexType>>(emptyList())
    private val _selectedCamera = MutableLiveData(ComponentIndexType.UNKNOWN)
    private val _detection = MutableLiveData<ArucoDetection>()
    private val _guidance = MutableLiveData(guidanceController.calculate(null))
    private val _status = MutableLiveData("Idle. Select Start Detection after video appears.")
    private val _autoAlignEnabled = MutableLiveData(false)
    private val _autoLandState = MutableLiveData(AutoLandState.IDLE)

    private var frameListener: CameraFrameListener? = null
    private var isDetecting = false
    private var isProcessingFrame = false
    private var lastProcessTime = 0L
    @Volatile private var latestDetection: ArucoDetection? = null
    @Volatile private var lastDetectionTime = 0L
    @Volatile private var autoAlignRunning = false
    @Volatile private var autoLandRunning = false
    @Volatile private var virtualStickEnabled = false
    @Volatile private var currentAutoLandState = AutoLandState.IDLE
    @Volatile private var alignedSinceTime = 0L
    @Volatile private var latestAltitude = Double.NaN
    @Volatile private var landingConfirmationNeeded = false
    @Volatile private var landingConfirmSent = false
    private var alignExecutor: ScheduledExecutorService? = null
    private var surface: Surface? = null

    init {
        MediaDataCenter.getInstance().cameraStreamManager.addAvailableCameraUpdatedListener(this)
        FlightControllerKey.KeyAltitude.create().listen(this) { altitude ->
            altitude?.let { latestAltitude = it }
        }
        FlightControllerKey.KeyIsLandingConfirmationNeeded.create().listen(this) { needed ->
            landingConfirmationNeeded = needed == true
            if (landingConfirmationNeeded && currentAutoLandState == AutoLandState.FINAL_AUTO_LANDING) {
                confirmFinalLandingIfNeeded()
            }
        }
    }

    fun putCameraStreamSurface(surface: Surface, width: Int, height: Int) {
        this.surface = surface
        val cameraIndex = selectedCamera.value ?: ComponentIndexType.UNKNOWN
        if (cameraIndex != ComponentIndexType.UNKNOWN) {
            MediaDataCenter.getInstance().cameraStreamManager.putCameraStreamSurface(
                cameraIndex,
                surface,
                width,
                height,
                ICameraStreamManager.ScaleType.FIX_XY
            )
        }
    }

    fun removeCameraStreamSurface(surface: Surface) {
        if (this.surface == surface) {
            this.surface = null
        }
        MediaDataCenter.getInstance().cameraStreamManager.removeCameraStreamSurface(surface)
    }

    fun selectCamera(cameraIndex: ComponentIndexType) {
        if (_selectedCamera.value == cameraIndex) return
        if (autoAlignRunning || autoLandRunning || virtualStickEnabled) {
            stopVisualControl()
        }
        stopDetection()
        _selectedCamera.postValue(cameraIndex)
        surface?.let {
            MediaDataCenter.getInstance().cameraStreamManager.putCameraStreamSurface(
                cameraIndex,
                it,
                1280,
                720,
                ICameraStreamManager.ScaleType.FIX_XY
            )
        }
        _status.postValue("Selected camera: ${cameraIndex.name}")
    }

    fun startDetection(): Boolean {
        lookDownGimbal()
        val cameraIndex = selectedCamera.value ?: ComponentIndexType.UNKNOWN
        if (cameraIndex == ComponentIndexType.UNKNOWN) {
            _status.postValue("No camera stream is available yet.")
            return false
        }
        if (isDetecting) return true
        isDetecting = true
        val listener = CameraFrameListener { frameData, offset, length, width, height, format ->
            if (!isDetecting || format != ICameraStreamManager.FrameFormat.NV21) return@CameraFrameListener
            val now = System.currentTimeMillis()
            if (isProcessingFrame || now - lastProcessTime < 500L) return@CameraFrameListener
            lastProcessTime = now
            isProcessingFrame = true
            val copied = frameData.copyOfRange(offset, offset + length)
            DJIExecutor.getExecutor().execute {
                try {
                    val result = detector.detectNv21(copied, 0, copied.size, width, height)
                    latestDetection = result
                    if (result.visible) {
                        lastDetectionTime = System.currentTimeMillis()
                    }
                    _detection.postValue(result)
                    _guidance.postValue(guidanceController.calculate(result))
                    if (result.visible) {
                        _status.postValue(
                            "Detected ID ${result.markerId}: errX=${"%.3f".format(result.normalizedErrorX)}, errY=${"%.3f".format(result.normalizedErrorY)}, rot=${"%.1f".format(result.rotationDegrees)}, conf=${"%.2f".format(result.confidence)}"
                        )
                    } else {
                        _status.postValue("Detecting... ArUco ID 1 not found")
                    }
                } catch (e: Exception) {
                    _status.postValue("Detection error: ${e.message}")
                } finally {
                    isProcessingFrame = false
                }
            }
        }
        frameListener = listener
        MediaDataCenter.getInstance().cameraStreamManager.addFrameListener(
            cameraIndex,
            ICameraStreamManager.FrameFormat.NV21,
            listener
        )
        _status.postValue("Detection started on ${cameraIndex.name}. No flight control command is sent.")
        return true
    }

    fun startAutoAlign() {
        if (autoAlignRunning || autoLandRunning) return
        if (!startDetection()) return
        _status.postValue("Requesting Virtual Stick control. Make sure RC mode is Normal/P mode, not Sport/Cine/Tripod.")
        VirtualStickManager.getInstance().enableVirtualStick(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                virtualStickEnabled = true
                autoAlignRunning = true
                setAutoLandState(AutoLandState.ALIGN_ONLY)
                VirtualStickManager.getInstance().setVirtualStickAdvancedModeEnabled(true)
                _autoAlignEnabled.postValue(true)
                _status.postValue("Auto align enabled. Horizontal control only; descent is disabled.")
                startAlignLoop()
            }

            override fun onFailure(error: IDJIError) {
                autoAlignRunning = false
                virtualStickEnabled = false
                setAutoLandState(AutoLandState.IDLE)
                _autoAlignEnabled.postValue(false)
                _status.postValue("Enable virtual stick failed: $error\n请确认遥控器档位在 Normal/P 模式，不能是 Sport/Cine/Tripod；并确认飞机已起飞且未靠近限飞区/限远边界。")
            }
        })
    }

    fun startAutoLand() {
        if (autoAlignRunning || autoLandRunning) return
        if (!startDetection()) return
        alignedSinceTime = 0L
        landingConfirmSent = false
        _status.postValue("Requesting Virtual Stick control for conservative Auto Land.")
        VirtualStickManager.getInstance().enableVirtualStick(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                virtualStickEnabled = true
                autoAlignRunning = true
                autoLandRunning = true
                setAutoLandState(AutoLandState.ALIGN_ONLY)
                VirtualStickManager.getInstance().setVirtualStickAdvancedModeEnabled(true)
                _autoAlignEnabled.postValue(true)
                _status.postValue("Auto land enabled. Aligning before descent.")
                startAlignLoop()
            }

            override fun onFailure(error: IDJIError) {
                autoAlignRunning = false
                autoLandRunning = false
                virtualStickEnabled = false
                alignedSinceTime = 0L
                landingConfirmSent = false
                setAutoLandState(AutoLandState.IDLE)
                _autoAlignEnabled.postValue(false)
                _status.postValue("Enable virtual stick failed: $error\n请确认遥控器档位在 Normal/P 模式，不能是 Sport/Cine/Tripod；并确认飞机已起飞且未靠近限飞区/限远边界。")
            }
        })
    }

    fun stopAutoAlign() {
        stopVisualControl()
    }

    fun stopAll() {
        stopVisualControl()
        stopDetection()
    }

    private fun stopVisualControl() {
        val shouldDisableVirtualStick = virtualStickEnabled || autoAlignRunning || autoLandRunning || alignExecutor != null
        if (!shouldDisableVirtualStick) {
            _autoAlignEnabled.postValue(false)
            autoAlignRunning = false
            autoLandRunning = false
            alignedSinceTime = 0L
            landingConfirmSent = false
            setAutoLandState(AutoLandState.IDLE)
            return
        }
        synchronized(visualControlLock) {
            autoAlignRunning = false
            autoLandRunning = false
            alignedSinceTime = 0L
            landingConfirmSent = false
            setAutoLandState(AutoLandState.IDLE)
            _autoAlignEnabled.postValue(false)
            alignExecutor?.shutdownNow()
            alignExecutor = null
        }
        if (virtualStickEnabled) {
            sendVirtualStickParam(0.0, 0.0, 0.0, 0.0)
            VirtualStickManager.getInstance().setVirtualStickAdvancedModeEnabled(false)
        }
        VirtualStickManager.getInstance().disableVirtualStick(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                virtualStickEnabled = false
                _status.postValue("Visual control stopped. Virtual stick disabled.")
            }

            override fun onFailure(error: IDJIError) {
                virtualStickEnabled = false
                _status.postValue("Visual control stopped, but disable virtual stick failed: $error")
            }
        })
    }

    private fun startAlignLoop() {
        alignExecutor?.shutdownNow()
        alignExecutor = Executors.newSingleThreadScheduledExecutor()
        alignExecutor?.scheduleAtFixedRate({
            if (!autoAlignRunning || !virtualStickEnabled) return@scheduleAtFixedRate
            if (!autoLandRunning && currentAutoLandState != AutoLandState.ALIGN_ONLY) {
                setAutoLandState(AutoLandState.ALIGN_ONLY)
            }
            val markerFresh = System.currentTimeMillis() - lastDetectionTime < 900L
            if (!markerFresh) {
                alignedSinceTime = 0L
                sendVirtualStickParam(0.0, 0.0, 0.0, 0.0)
                if (autoLandRunning) setAutoLandState(AutoLandState.ALIGN_ONLY)
                _status.postValue("${currentAutoLandState.name}: marker lost. Hovering. alt=${formatAltitude()}")
                return@scheduleAtFixedRate
            }
            val command = alignController.calculate(latestDetection)
            val verticalVelocity = if (autoLandRunning) updateAutoLandDescent(command) else 0.0
            if (currentAutoLandState == AutoLandState.FINAL_AUTO_LANDING) return@scheduleAtFixedRate
            sendVirtualStickParam(command.pitchVelocity, command.rollVelocity, verticalVelocity, command.yawRate)
            _status.postValue(
                "${currentAutoLandState.name}: ${command.reason}, pitch=${"%.2f".format(command.pitchVelocity)}, roll=${"%.2f".format(command.rollVelocity)}, yaw=${"%.1f".format(command.yawRate)}deg/s, vertical=${"%.2f".format(verticalVelocity)}, alt=${formatAltitude()}"
            )
        }, 0L, 100L, TimeUnit.MILLISECONDS)
    }

    private fun updateAutoLandDescent(command: dji.sampleV5.aircraft.aruco.ArucoAlignCommand): Double {
        if (latestAltitude.isFinite() && latestAltitude <= FINAL_AUTO_LANDING_HEIGHT_M) {
            enterFinalAutoLanding()
            return 0.0
        }

        val now = System.currentTimeMillis()
        if (!command.aligned) {
            alignedSinceTime = 0L
            setAutoLandState(AutoLandState.ALIGN_ONLY)
            return 0.0
        }

        if (alignedSinceTime == 0L) {
            alignedSinceTime = now
            setAutoLandState(AutoLandState.ALIGN_ONLY)
            return 0.0
        }

        if (now - alignedSinceTime < ALIGN_STABLE_TIME_MS) {
            setAutoLandState(AutoLandState.ALIGN_ONLY)
            return 0.0
        }

        setAutoLandState(AutoLandState.DESCENDING)
        return DESCENT_VELOCITY_MPS
    }

    private fun enterFinalAutoLanding() {
        synchronized(visualControlLock) {
            if (!autoLandRunning || currentAutoLandState == AutoLandState.FINAL_AUTO_LANDING) return
            setAutoLandState(AutoLandState.FINAL_AUTO_LANDING)
            autoLandRunning = false
            autoAlignRunning = false
            alignedSinceTime = 0L
            landingConfirmSent = false
            _autoAlignEnabled.postValue(false)
            alignExecutor?.shutdown()
            alignExecutor = null
        }
        sendVirtualStickParam(0.0, 0.0, 0.0, 0.0)
        stopDetection()
        if (virtualStickEnabled) {
            VirtualStickManager.getInstance().setVirtualStickAdvancedModeEnabled(false)
            VirtualStickManager.getInstance().disableVirtualStick(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    virtualStickEnabled = false
                    startDjiAutoLanding()
                }

                override fun onFailure(error: IDJIError) {
                    virtualStickEnabled = false
                    _status.postValue("FINAL_AUTO_LANDING: disable virtual stick failed: $error")
                    startDjiAutoLanding()
                }
            })
        } else {
            startDjiAutoLanding()
        }
    }

    private fun startDjiAutoLanding() {
        FlightControllerKey.KeyStartAutoLanding.create().action(
            { _: EmptyMsg? ->
                _status.postValue("FINAL_AUTO_LANDING: DJI auto landing started.")
                confirmFinalLandingIfNeeded()
            },
            { error: IDJIError ->
                _status.postValue("FINAL_AUTO_LANDING: DJI auto landing failed: $error")
                virtualStickEnabled = false
                setAutoLandState(AutoLandState.IDLE)
            }
        )
    }

    private fun confirmFinalLandingIfNeeded() {
        if (!landingConfirmationNeeded || landingConfirmSent) return
        landingConfirmSent = true
        FlightControllerKey.KeyConfirmLanding.create().action(
            { _: EmptyMsg? ->
                _status.postValue("FINAL_AUTO_LANDING: landing confirmation sent.")
            },
            { error: IDJIError ->
                landingConfirmSent = false
                _status.postValue("FINAL_AUTO_LANDING: landing confirmation failed: $error")
            }
        )
    }

    private fun setAutoLandState(state: AutoLandState) {
        if (currentAutoLandState == state) return
        currentAutoLandState = state
        _autoLandState.postValue(state)
    }

    private fun formatAltitude(): String {
        return if (latestAltitude.isFinite()) "%.2fm".format(latestAltitude) else "unknown"
    }

    private fun sendVirtualStickParam(pitch: Double, roll: Double, vertical: Double, yaw: Double) {
        val param = VirtualStickFlightControlParam().apply {
            rollPitchCoordinateSystem = FlightCoordinateSystem.BODY
            rollPitchControlMode = RollPitchControlMode.VELOCITY
            verticalControlMode = VerticalControlMode.VELOCITY
            yawControlMode = YawControlMode.ANGULAR_VELOCITY
            setPitch(pitch)
            setRoll(roll)
            setVerticalThrottle(vertical)
            setYaw(yaw)
        }
        VirtualStickManager.getInstance().sendVirtualStickAdvancedParam(param)
    }

    fun lookDownGimbal() {
        GimbalKey.KeyGimbalReset.create(ComponentIndexType.LEFT_OR_MAIN).action(
            GimbalResetType.PITCH_UP_OR_DOWN_WITH_YAW_CENTER,
            { _: EmptyMsg? ->
                _status.postValue("Gimbal look-down command succeeded. Ready for ArUco detection.")
            },
            { error: IDJIError ->
                _status.postValue("Gimbal look-down command failed: $error")
            }
        )
    }

    fun stopDetection() {
        isDetecting = false
        frameListener?.let {
            MediaDataCenter.getInstance().cameraStreamManager.removeFrameListener(it)
        }
        frameListener = null
        isProcessingFrame = false
        latestDetection = null
        lastDetectionTime = 0L
        _status.postValue("Detection stopped. No flight control command is sent.")
        _guidance.postValue(guidanceController.calculate(null))
    }

    override fun onAvailableCameraUpdated(availableCameraList: List<ComponentIndexType>) {
        _availableCameraListData.postValue(availableCameraList)
        if ((_selectedCamera.value ?: ComponentIndexType.UNKNOWN) == ComponentIndexType.UNKNOWN && availableCameraList.isNotEmpty()) {
            _selectedCamera.postValue(availableCameraList.first())
            _status.postValue("Selected camera: ${availableCameraList.first().name}")
        }
    }

    override fun onCameraStreamEnableUpdate(cameraStreamEnableMap: MutableMap<ComponentIndexType, Boolean>) {
        // Not used by this page.
    }

    override fun onCleared() {
        stopAll()
        KeyManager.getInstance().cancelListen(this)
        MediaDataCenter.getInstance().cameraStreamManager.removeAvailableCameraUpdatedListener(this)
        super.onCleared()
    }

    val availableCameraListData: LiveData<List<ComponentIndexType>>
        get() = _availableCameraListData

    val selectedCamera: LiveData<ComponentIndexType>
        get() = _selectedCamera

    val detection: LiveData<ArucoDetection>
        get() = _detection

    val guidance: LiveData<ArucoGuidance>
        get() = _guidance

    val autoAlignEnabled: LiveData<Boolean>
        get() = _autoAlignEnabled

    val autoLandState: LiveData<AutoLandState>
        get() = _autoLandState

    val status: LiveData<String>
        get() = _status

    companion object {
        private const val ALIGN_STABLE_TIME_MS = 1200L
        private const val DESCENT_VELOCITY_MPS = -0.08
        private const val FINAL_AUTO_LANDING_HEIGHT_M = 0.6
    }
}
