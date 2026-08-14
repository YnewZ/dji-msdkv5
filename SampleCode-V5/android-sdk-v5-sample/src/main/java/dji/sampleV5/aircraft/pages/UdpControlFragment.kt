package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.viewModels
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.databinding.FragUdpControlPageBinding
import dji.sampleV5.aircraft.models.UdpControlVM

class UdpControlFragment : DJIFragment() {

    private var binding: FragUdpControlPageBinding? = null
    private val udpControlVM: UdpControlVM by viewModels()
    private val messageList = ArrayList<String>()
    private lateinit var messageAdapter: ArrayAdapter<String>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragUdpControlPageBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        messageAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, messageList)
        binding?.messageListview?.adapter = messageAdapter

        udpControlVM.serverStatusLiveData.observe(viewLifecycleOwner) { status ->
            binding?.tvServerStatus?.text = status
        }

        udpControlVM.controlStatusLiveData.observe(viewLifecycleOwner) { status ->
            binding?.tvControlStatus?.text = status
            binding?.btnToggleServer?.text = if (udpControlVM.isControlEnabled()) {
                getString(R.string.udp_btn_stop)
            } else {
                getString(R.string.udp_btn_start)
            }
        }

        udpControlVM.receiveMessageLiveData.observe(viewLifecycleOwner) { msg ->
            messageList.add(msg)
            if (messageList.size > 100) {
                messageList.removeAt(0)
            }
            messageAdapter.notifyDataSetChanged()
            binding?.messageListview?.setSelection(messageList.size - 1)
        }

        udpControlVM.startUdpListening()

        binding?.btnToggleServer?.setOnClickListener {
            if (udpControlVM.isControlEnabled()) {
                udpControlVM.stopUdpControl()
            } else {
                udpControlVM.startUdpControl()
            }
        }
    }

    override fun onDestroyView() {
        udpControlVM.stopAll()
        super.onDestroyView()
        binding = null
    }
}
