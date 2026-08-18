package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.databinding.FragIndoorFlightLiveBinding
import dji.sdk.keyvalue.key.BatteryKey
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.et.create
import dji.v5.et.get
import dji.v5.et.listen
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ICameraStreamManager

class IndoorFlightLiveFragment : DJIFragment() {

    private var binding: FragIndoorFlightLiveBinding? = null
    private val cameraStreamManager = MediaDataCenter.getInstance().cameraStreamManager

    private var cameraIndex = ComponentIndexType.LEFT_OR_MAIN
    private var streamSurface: Surface? = null
    private var streamWidth = 0
    private var streamHeight = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragIndoorFlightLiveBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initCameraStream()
        initActions()
        initBatteryInfo()
    }

    override fun onDestroyView() {
        removeCameraStreamSurface()
        binding = null
        super.onDestroyView()
    }

    private fun initCameraStream() {
        binding?.svIndoorCameraStream?.holder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                streamSurface = holder.surface
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                streamSurface = holder.surface
                streamWidth = width
                streamHeight = height
                putCameraStreamSurface()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                removeCameraStreamSurface(holder.surface)
                streamSurface = null
                streamWidth = 0
                streamHeight = 0
            }
        })
    }

    private fun initActions() {
        binding?.btnIndoorLiveWaypointCapture?.setOnClickListener {
            findNavController().navigate(R.id.action_indoor_live_to_waypoint_capture_page)
        }
        binding?.btnIndoorLiveArucoLanding?.setOnClickListener {
            findNavController().navigate(R.id.action_indoor_live_to_aruco_landing_page)
        }
        binding?.btnIndoorLiveUdpControl?.setOnClickListener {
            findNavController().navigate(R.id.action_indoor_live_to_udp_control_page)
        }
    }

    private fun initBatteryInfo() {
        BatteryKey.KeyChargeRemainingInPercent.create().get(
            onSuccess = { updateBatteryPercent(it) },
            onFailure = { updateBatteryPercent(null) }
        )
        BatteryKey.KeyChargeRemainingInPercent.create().listen(this) { percent ->
            updateBatteryPercent(percent)
        }
    }

    private fun putCameraStreamSurface() {
        val surface = streamSurface ?: return
        if (streamWidth <= 0 || streamHeight <= 0) return
        cameraStreamManager.putCameraStreamSurface(
            cameraIndex,
            surface,
            streamWidth,
            streamHeight,
            ICameraStreamManager.ScaleType.CENTER_CROP
        )
    }

    private fun removeCameraStreamSurface(surface: Surface? = streamSurface) {
        surface?.let { cameraStreamManager.removeCameraStreamSurface(it) }
    }

    private fun updateBatteryPercent(percent: Int?) {
        mainHandler.post {
            binding?.viewIndoorBattery?.setPercent(percent)
            binding?.tvIndoorBatteryPercent?.text = percent?.let { "${it.coerceIn(0, 100)}%" } ?: "--%"
        }
    }
}
