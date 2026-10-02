package com.mxmvncnt.teou.ui.security

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.Toast
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.mxmvncnt.teou.R
import com.mxmvncnt.teou.databinding.FcmSecurityFragmentBinding
import com.mxmvncnt.teou.push.fcm.FcmStore
import com.mxmvncnt.teou.messaging.MessageAuth
import com.mxmvncnt.teou.messaging.WebPushCrypto
import com.mxmvncnt.teou.messaging.EncryptionKeyStore
import com.mxmvncnt.teou.messaging.IdentityKeyStore
import com.mxmvncnt.teou.messaging.PushTransport
import com.mxmvncnt.teou.push.unifiedpush.PairPayload
import com.mxmvncnt.teou.push.unifiedpush.UnifiedPushPairing
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class SecurityFragment : Fragment(R.layout.fcm_security_fragment) {
    private var binding: FcmSecurityFragmentBinding? = null
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "fcm_token" || key == "fcm_relay_url") activity?.runOnUiThread { refreshStatus() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FcmSecurityFragmentBinding.bind(view)
        this.binding = binding
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
        binding.btnShowMyQr.setOnClickListener { showPairing() }
    }

    override fun onResume() {
        super.onResume()
        requireContext().getSharedPreferences("teou_prefs", 0).registerOnSharedPreferenceChangeListener(settingsListener)
        refreshStatus()
    }

    override fun onPause() {
        requireContext().getSharedPreferences("teou_prefs", 0).unregisterOnSharedPreferenceChangeListener(settingsListener)
        super.onPause()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun refreshStatus() {
        val store = FcmStore(requireContext())
        val ready = !store.relayUrl.isNullOrBlank() && !store.token.isNullOrBlank()
        binding?.btnShowMyQr?.isEnabled = ready
        binding?.tvPairingStatus?.setText(if (ready) R.string.fcm_pairing_ready else R.string.p2p_block_not_registered)
    }

    private fun showPairing() {
        val ctx = requireContext()
        val store = FcmStore(ctx)
        val relay = store.relayUrl?.takeIf { it.isNotBlank() } ?: return
        val token = store.token?.takeIf { it.isNotBlank() } ?: return
        val currentBinding = binding ?: return
        Thread {
            try {
                val keys = EncryptionKeyStore.getOrCreate(ctx)
                val identity = IdentityKeyStore.idPub()
                val pairing = PairPayload("", keys.p256dh, keys.authSecret, WebPushCrypto.b64enc(identity),
                    PushTransport.FCM, relay, token)
                val payload = UnifiedPushPairing.encode(pairing)
                val link = UnifiedPushPairing.linkFor(payload)
                val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512)
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
                for (x in 0 until 512) for (y in 0 until 512) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
                activity?.runOnUiThread {
                    if (binding !== currentBinding) return@runOnUiThread
                    val pad = (16 * resources.displayMetrics.density).toInt()
                    val content = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(pad, pad, pad, pad)
                        addView(ImageView(ctx).apply { setImageBitmap(bitmap); adjustViewBounds = true })
                        addView(TextView(ctx).apply { setText(R.string.p2p_my_qr_hint) })
                        addView(TextView(ctx).apply {
                            text = getString(R.string.p2p_peer_fingerprint, MessageAuth.fingerprint(identity))
                            setTextIsSelectable(true)
                        })
                    }
                    MaterialAlertDialogBuilder(ctx).setTitle(R.string.p2p_my_qr_title).setView(content)
                        .setNeutralButton(R.string.pair_copy_link) { _, _ ->
                            ctx.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("pairing", link))
                            Toast.makeText(ctx, R.string.pair_link_copied, Toast.LENGTH_SHORT).show()
                        }
                        .setPositiveButton(R.string.btn_close, null).show()
                }
            } catch (_: Exception) {
                activity?.runOnUiThread {
                    if (binding === currentBinding) Toast.makeText(ctx, R.string.fcm_pairing_failed, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
