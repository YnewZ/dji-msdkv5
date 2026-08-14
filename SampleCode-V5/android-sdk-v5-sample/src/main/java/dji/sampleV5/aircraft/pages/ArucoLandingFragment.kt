package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.graphics.Color
import androidx.fragment.app.viewModels
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.aruco.ArucoOverlayView
import dji.sampleV5.aircraft.models.ArucoLandingVM
import dji.v5.ux.core.util.ToastUtils

class ArucoLandingFragment : DJIFragment() {

    private val viewModel: ArucoLandingVM by viewModels()
    private lateinit var cameraSurfaceView: SurfaceView
    private lateinit var overlayView: ArucoOverlayView
    private lateinit var statusText: TextView
    private lateinit var guidanceText: TextView
    private lateinit var autoAlignButton: Button
    private lateinit var autoLandButton: Button
    private lateinit var profileSpinner: Spinner
    private var profileSelectionReady = false
    private var surface: Surface? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_aruco_landing_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraSurfaceView = view.findViewById(R.id.sv_camera)
        overlayView = view.findViewById(R.id.aruco_overlay)
        statusText = view.findViewById(R.id.tv_status)
        guidanceText = view.findViewById(R.id.tv_guidance)
        autoAlignButton = view.findViewById(R.id.btn_auto_align)
        autoLandButton = view.findViewById(R.id.btn_auto_land)
        profileSpinner = view.findViewById(R.id.sp_profile)
        initProfileSpinner()
        view.findViewById<Button>(R.id.btn_gimbal_down).setOnClickListener {
            viewModel.lookDownGimbal()
            ToastUtils.showToast("Sending gimbal look-down command.")
        }
        view.findViewById<Button>(R.id.btn_start_detection).setOnClickListener {
            viewModel.startDetection()
            ToastUtils.showToast("Aruco detection started. Flight control is disabled.")
        }
        autoAlignButton.setOnClickListener {
            viewModel.startAutoAlign()
            ToastUtils.showToast("Auto align requested. Be ready to press STOP or use RC sticks.")
        }
        autoLandButton.setOnClickListener {
            viewModel.startAutoLand()
            ToastUtils.showToast("Auto land requested. Be ready to press STOP or use RC sticks.")
        }
        view.findViewById<Button>(R.id.btn_stop_detection).setOnClickListener {
            viewModel.stopAll()
        }
        cameraSurfaceView.holder.addCallback(surfaceCallback)
        initObservers()
    }

    override fun onDestroyView() {
        viewModel.stopAll()
        surface?.let { viewModel.removeCameraStreamSurface(it) }
        surface = null
        super.onDestroyView()
    }

    override fun updateTitle() {
        msdkInfoVm.mainTitle.value = "Aruco Landing"
    }

    private fun initObservers() {
        viewModel.availableCameraListData.observe(viewLifecycleOwner) { cameraList ->
            if (cameraList.isNotEmpty()) {
                viewModel.selectCamera(cameraList.first())
            }
        }
        viewModel.detection.observe(viewLifecycleOwner) { detection ->
            overlayView.updateDetection(detection)
        }
        viewModel.status.observe(viewLifecycleOwner) { status ->
            statusText.text = status
        }
        viewModel.autoAlignEnabled.observe(viewLifecycleOwner) { enabled ->
            autoAlignButton.text = if (enabled) "Aligning..." else "Auto Align"
            guidanceText.setBackgroundColor(if (enabled) Color.argb(190, 80, 0, 0) else Color.argb(170, 0, 0, 0))
        }
        viewModel.autoLandState.observe(viewLifecycleOwner) { state ->
            autoLandButton.text = if (state == ArucoLandingVM.AutoLandState.IDLE) "Auto Land" else state.name
        }
        viewModel.selectedProfile.observe(viewLifecycleOwner) { profile ->
            val index = viewModel.profiles.indexOf(profile)
            if (index >= 0 && profileSpinner.selectedItemPosition != index) {
                profileSpinner.setSelection(index)
            }
        }
        viewModel.guidance.observe(viewLifecycleOwner) { guidance ->
            guidanceText.text = guidance.instruction
            guidanceText.setTextColor(
                when {
                    guidance.canDescend -> Color.GREEN
                    guidance.aligned -> Color.YELLOW
                    else -> Color.WHITE
                }
            )
            statusText.text = guidance.detail
        }
    }

    private fun initProfileSpinner() {
        val profileNames = viewModel.profiles.map { it.displayName }
        profileSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, profileNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!profileSelectionReady) {
                    profileSelectionReady = true
                    return
                }
                viewModel.selectProfile(viewModel.profiles[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            surface = holder.surface
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            surface = holder.surface
            viewModel.putCameraStreamSurface(holder.surface, width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            viewModel.removeCameraStreamSurface(holder.surface)
            surface = null
        }
    }
}
