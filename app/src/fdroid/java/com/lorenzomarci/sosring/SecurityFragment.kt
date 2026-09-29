package com.lorenzomarci.sosring

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lorenzomarci.sosring.databinding.FragmentSecurityBinding
import org.unifiedpush.android.connector.UnifiedPush

class SecurityFragment : Fragment() {

    private var _binding: FragmentSecurityBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrefsManager

    private val attemptsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        activity?.runOnUiThread { if (_binding != null) refreshRequests() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSecurityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())
        binding.btnShowMyQr.setOnClickListener { showMyQrDialog() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshPeerList()
        FollowerAttempts(requireContext()).register(attemptsListener)
    }

    override fun onPause() {
        FollowerAttempts(requireContext()).unregister(attemptsListener)
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refreshStatus() {
        val registered = !UnifiedPushStore(requireContext()).endpointUrl.isNullOrBlank()
        binding.tvP2pStatus.text = getString(
            if (registered) R.string.p2p_status_registered else R.string.p2p_status_waiting
        )
        binding.btnShowMyQr.isEnabled = registered
        val noDistributor = UnifiedPush.getDistributors(requireContext()).isEmpty()
        binding.cardNoDistributor.visibility = if (noDistributor) View.VISIBLE else View.GONE
    }

    private fun showMyQrDialog() {
        val store = UnifiedPushStore(requireContext())
        val endpoint = store.endpointUrl
        val pubKey = store.pubKey
        val auth = store.auth
        if (endpoint.isNullOrBlank() || pubKey.isNullOrBlank() || auth.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.p2p_status_waiting), Toast.LENGTH_LONG).show()
            return
        }
        Thread {
            val idPubBytes = IdentityKeyStore.idPub()
            val idPub = WebPushCrypto.b64enc(idPubBytes)
            val fingerprint = MessageAuth.fingerprint(idPubBytes)
            val payload = UnifiedPushPairing.encode(PairPayload(endpoint, pubKey, auth, idPub))
            val bitmap = generateQrCode(payload)
            activity?.runOnUiThread {
                val ctx = context ?: return@runOnUiThread
                if (_binding == null) return@runOnUiThread
                val pad = (16 * resources.displayMetrics.density).toInt()
                val image = ImageView(ctx).apply {
                    setImageBitmap(bitmap)
                    setPadding(pad, pad, pad, pad)
                }
                val hint = TextView(ctx).apply {
                    text = getString(R.string.p2p_my_qr_hint)
                    setPadding(pad, 0, pad, pad)
                }
                val fingerprintLabel = TextView(ctx).apply {
                    text = getString(R.string.p2p_fingerprint_label)
                    setPadding(pad, 0, pad, 0)
                }
                val fingerprintValue = TextView(ctx).apply {
                    text = fingerprint
                    typeface = android.graphics.Typeface.MONOSPACE
                    setPadding(pad, 0, pad, pad)
                }
                val container = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(image)
                    addView(hint)
                    addView(fingerprintLabel)
                    addView(fingerprintValue)
                }
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.p2p_my_qr_title)
                    .setView(container)
                    .setNeutralButton(R.string.pair_copy_link) { _, _ ->
                        val clipboard = ctx.getSystemService(android.content.ClipboardManager::class.java)
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("pairing", UnifiedPushPairing.linkFor(payload))
                        )
                        Toast.makeText(ctx, getString(R.string.pair_link_copied), Toast.LENGTH_SHORT).show()
                    }
                    .setPositiveButton(R.string.btn_close, null)
                    .show()
            }
        }.start()
    }

    private fun refreshPeerList() {
        val container = binding.peersContainer
        container.removeAllViews()
        val peers = PeerStore(requireContext()).all()
        val contacts = prefs.getContacts()
        binding.tvPeersEmpty.visibility = if (peers.isEmpty()) View.VISIBLE else View.GONE
        if (peers.isNotEmpty()) {
            val (followers, others) = peers.partition { peer ->
                contacts.firstOrNull { PhoneUtils.matches(it.number, peer.number) }?.locationEnabled == true
            }
            container.addView(sectionHeader(getString(R.string.followers_title)))
            if (followers.isEmpty()) {
                container.addView(sectionEmpty(getString(R.string.followers_empty)))
            } else {
                followers.forEach { addPeerRow(container, it, contacts) }
            }
            if (others.isNotEmpty()) {
                container.addView(sectionHeader(getString(R.string.paired_off_title)))
                others.forEach { addPeerRow(container, it, contacts) }
            }
        }
        refreshRequests()
    }

    private fun sectionHeader(text: String): TextView {
        val density = resources.displayMetrics.density
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.color(
                requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant
            ))
            setPadding(0, (12 * density).toInt(), 0, (2 * density).toInt())
        }
    }

    private fun sectionEmpty(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 13f
            setTextColor(ThemeManager.color(
                requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant
            ))
            setPadding(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
        }
    }

    private fun addPeerRow(container: LinearLayout, peer: Peer, contacts: List<VipContact>) {
        val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_peer, container, false)
        val peerName = contacts.firstOrNull { PhoneUtils.matches(it.number, peer.number) }?.name
            ?: fingerprintOf(peer.idPub)
        row.findViewById<TextView>(R.id.tvPeerNumber).text = peerName
        row.findViewById<TextView>(R.id.tvPeerFingerprint).text =
            getString(R.string.p2p_peer_fingerprint, fingerprintOf(peer.idPub))
        row.findViewById<View>(R.id.btnRemovePeer).setOnClickListener { confirmRemovePeer(peer) }
        val locationEnabled = contacts.firstOrNull { PhoneUtils.matches(it.number, peer.number) }?.locationEnabled ?: false
        val locationSwitch = row.findViewById<MaterialSwitch>(R.id.swPeerLocation)
        locationSwitch.setOnCheckedChangeListener(null)
        locationSwitch.isChecked = locationEnabled
        locationSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.updateContactLocationEnabled(peer.number, isChecked)
            refreshPeerList()
        }
        container.addView(row)
    }

    private fun refreshRequests() {
        val container = binding.requestsContainer
        container.removeAllViews()
        val attempts = FollowerAttempts(requireContext()).all()
        binding.tvRequestsEmpty.visibility = if (attempts.isEmpty()) View.VISIBLE else View.GONE
        val dateFormat = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
        attempts.forEach { attempt ->
            val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_request, container, false)
            row.findViewById<TextView>(R.id.tvRequestFingerprint).text = fingerprintOf(attempt.idPub)
            row.findViewById<TextView>(R.id.tvRequestSubtitle).text = getString(
                R.string.request_subtitle,
                attempt.count,
                dateFormat.format(java.util.Date(attempt.lastSeen))
            )
            row.findViewById<View>(R.id.btnDismissRequest).setOnClickListener {
                FollowerAttempts(requireContext()).remove(attempt.idPub)
                refreshRequests()
            }
            container.addView(row)
        }
    }

    private fun confirmRemovePeer(peer: Peer) {
        val peerName = prefs.getContacts()
            .firstOrNull { PhoneUtils.matches(it.number, peer.number) }?.name
            ?: fingerprintOf(peer.idPub)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.p2p_peer_remove_title)
            .setMessage(getString(R.string.p2p_peer_remove_msg, peerName))
            .setPositiveButton(R.string.btn_remove) { _, _ ->
                PeerStore(requireContext()).remove(peer.number)
                refreshPeerList()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun fingerprintOf(idPubB64: String): String {
        return try {
            MessageAuth.fingerprint(WebPushCrypto.b64dec(idPubB64))
        } catch (e: Exception) {
            idPubB64
        }
    }

    private fun generateQrCode(content: String): Bitmap {
        val writer = QRCodeWriter()
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val width = matrix.width
        val height = matrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
