package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Context
import androidx.fragment.app.viewModels
import dji.sampleV5.aircraft.databinding.FragWaypointCapturePageBinding
import dji.sampleV5.aircraft.models.WaypointCaptureVM
import dji.v5.ux.core.util.ToastUtils

class WaypointCaptureFragment : DJIFragment() {
    companion object {
        private const val PREF_NAME = "waypoint_capture_prefs"
        private const val PREF_WAYPOINT_ONBOARD_HOST = "pref_waypoint_onboard_host"
        private const val PREF_WAYPOINT_ONBOARD_PORT = "pref_waypoint_onboard_port"
        private const val DEFAULT_ONBOARD_HOST = "192.168.1.20"
        private const val DEFAULT_ONBOARD_PORT = 9001
    }

    private val viewModel: WaypointCaptureVM by viewModels()
    private var binding: FragWaypointCapturePageBinding? = null
    private var restoredConfig = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragWaypointCapturePageBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        restoreWaypointConfig()
        initListeners()
        initObservers()
        connectWithCurrentConfig()
    }

    private fun initListeners() {
        val configWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (restoredConfig) saveCurrentConfigIfValid()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding?.etOnboardHost?.addTextChangedListener(configWatcher)
        binding?.etOnboardPort?.addTextChangedListener(configWatcher)
        binding?.btnPingWaypointServer?.setOnClickListener {
            viewModel.ping()
        }
        binding?.btnCaptureWaypoint?.setOnClickListener {
            viewModel.captureWaypoint()
        }
    }

    private fun restoreWaypointConfig() {
        val prefs = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val host = prefs.getString(PREF_WAYPOINT_ONBOARD_HOST, DEFAULT_ONBOARD_HOST).orEmpty()
        val port = prefs.getInt(PREF_WAYPOINT_ONBOARD_PORT, DEFAULT_ONBOARD_PORT)
        binding?.etOnboardHost?.setText(host)
        binding?.etOnboardPort?.setText(port.toString())
        restoredConfig = true
    }

    private fun saveWaypointConfig(host: String, port: Int) {
        requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_WAYPOINT_ONBOARD_HOST, host)
            .putInt(PREF_WAYPOINT_ONBOARD_PORT, port)
            .apply()
    }

    private fun connectWithCurrentConfig() {
        val host = binding?.etOnboardHost?.text?.toString()?.trim().orEmpty()
        val port = binding?.etOnboardPort?.text?.toString()?.trim()?.toIntOrNull()
        if (host.isEmpty() || port == null || port <= 0 || port > 65535) {
            ToastUtils.showToast("请输入有效的机载计算机 IP 和端口")
            return
        }
        saveWaypointConfig(host, port)
        viewModel.connect(host, port)
    }

    private fun saveCurrentConfigIfValid() {
        val host = binding?.etOnboardHost?.text?.toString()?.trim().orEmpty()
        val port = binding?.etOnboardPort?.text?.toString()?.trim()?.toIntOrNull()
        if (host.isNotEmpty() && port != null && port > 0 && port <= 65535) {
            saveWaypointConfig(host, port)
        }
    }

    private fun initObservers() {
        viewModel.connectionStatus.observe(viewLifecycleOwner) {
            binding?.tvWaypointConnectionStatus?.text = formatConnectionStatus(it)
        }
        viewModel.lastResponse.observe(viewLifecycleOwner) {
            binding?.tvWaypointLastResponse?.text = it
        }
        viewModel.connected.observe(viewLifecycleOwner) { connected ->
            binding?.btnCaptureWaypoint?.isEnabled = connected
            binding?.btnPingWaypointServer?.isEnabled = connected
        }
    }

    private fun formatConnectionStatus(status: String): String {
        return when {
            status.contains("已连接") || status.contains("通信正常") || status.contains("收到机载端返回") -> "通信正常"
            status.contains("正在连接") -> "正在连接..."
            status.contains("失败") || status.contains("异常") || status.contains("关闭连接") || status.contains("未连接") -> "通信失败"
            else -> status
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
