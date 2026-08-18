package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.databinding.FragIndoorControlHomeBinding

class IndoorControlHomeFragment : DJIFragment() {

    private var binding: FragIndoorControlHomeBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragIndoorControlHomeBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.btnIndoorBack?.setOnClickListener {
            requireActivity().finish()
        }
        binding?.btnOpenIndoorArucoLanding?.setOnClickListener {
            findNavController().navigate(R.id.action_open_indoor_aruco_landing_page)
        }
        binding?.btnOpenIndoorUdpControl?.setOnClickListener {
            findNavController().navigate(R.id.action_open_indoor_udp_control_page)
        }
        binding?.btnOpenWaypointCapture?.setOnClickListener {
            findNavController().navigate(R.id.action_open_waypoint_capture_page)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
