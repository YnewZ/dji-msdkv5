package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.databinding.FragCustomFeaturesPageBinding

class CustomFeaturesFragment : DJIFragment() {

    private var binding: FragCustomFeaturesPageBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragCustomFeaturesPageBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.btnOpenArucoLanding?.setOnClickListener {
            findNavController().navigate(R.id.action_open_aruco_landing_page)
        }
        binding?.btnOpenUdpControl?.setOnClickListener {
            findNavController().navigate(R.id.action_open_udp_control_page)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
