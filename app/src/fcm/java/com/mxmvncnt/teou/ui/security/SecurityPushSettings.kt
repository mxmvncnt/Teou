package com.mxmvncnt.teou.ui.security

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import com.mxmvncnt.teou.R
import com.mxmvncnt.teou.databinding.SecurityPushSettingsBinding
import com.mxmvncnt.teou.push.fcm.FcmStore

object SecurityPushSettings {
    fun bind(container: ViewGroup) {
        val context = container.context
        val binding = SecurityPushSettingsBinding.inflate(LayoutInflater.from(context), container, true)
        val store = FcmStore(context)
        binding.etFcmRelayUrl.setText(store.relayUrl)
        binding.btnSaveFcmRelay.setOnClickListener {
            val input = binding.etFcmRelayUrl.text.toString().trim()
            val url = FcmStore.normalizeRelayUrl(input)
            if (input.isNotEmpty() && url == null) {
                binding.fcmRelayUrlInput.error = context.getString(R.string.fcm_relay_url_invalid)
            } else {
                binding.fcmRelayUrlInput.error = null
                store.relayUrl = url
                binding.etFcmRelayUrl.setText(url.orEmpty())
                Toast.makeText(context, R.string.fcm_relay_url_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
