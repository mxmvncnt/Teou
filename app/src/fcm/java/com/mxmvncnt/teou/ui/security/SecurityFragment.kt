package com.mxmvncnt.teou.ui.security

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.mxmvncnt.teou.R
import com.mxmvncnt.teou.databinding.FcmSecurityFragmentBinding
import com.mxmvncnt.teou.push.fcm.FcmStore

class SecurityFragment : Fragment(R.layout.fcm_security_fragment) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FcmSecurityFragmentBinding.bind(view)
        val store = FcmStore(requireContext())
        binding.etFcmRelayUrl.setText(store.relayUrl)
        binding.btnSaveFcmRelay.setOnClickListener {
            val input = binding.etFcmRelayUrl.text.toString().trim()
            val url = FcmStore.normalizeRelayUrl(input)
            if (input.isNotEmpty() && url == null) {
                binding.fcmRelayUrlInput.error = getString(R.string.fcm_relay_url_invalid)
            } else {
                binding.fcmRelayUrlInput.error = null
                store.relayUrl = url
                binding.etFcmRelayUrl.setText(url.orEmpty())
                Toast.makeText(requireContext(), R.string.fcm_relay_url_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
