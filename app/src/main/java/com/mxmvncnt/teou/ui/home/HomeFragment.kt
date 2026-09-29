package com.mxmvncnt.teou.ui.home

import android.Manifest
import android.app.Activity
import android.content.SharedPreferences
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.mxmvncnt.teou.*
import com.mxmvncnt.teou.R
import com.mxmvncnt.teou.ui.scanner.QrScannerActivity
import com.mxmvncnt.teou.app.ThemeManager
import com.mxmvncnt.teou.data.PrefsManager
import com.mxmvncnt.teou.data.VipContact
import com.mxmvncnt.teou.push.Push
import com.mxmvncnt.teou.util.PhoneUtils
import com.mxmvncnt.teou.messaging.*
import com.mxmvncnt.teou.location.*
import com.mxmvncnt.teou.push.unifiedpush.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.mxmvncnt.teou.databinding.HomeFragmentBinding
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

class HomeFragment : Fragment() {
    private var _binding: HomeFragmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrefsManager
    private var map: MapLibreMap? = null
    private var lastCentered: List<Pair<String, Long>> = emptyList()
    private var deviceLocation: Location? = null
    private var deviceHelper: LocationHelper? = null
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val cooldownRefresh = Runnable { if (_binding != null) refresh() }
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (_binding != null) { refresh(); updateDevicePin() }
            refreshHandler.postDelayed(this, 60_000L)
        }
    }
    private val storeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        activity?.runOnUiThread { if (_binding != null) refresh() }
    }
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { if (_binding != null) { updatePermissions(); refresh(); updateDevicePin() } }
    private val qrScanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val content = result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_TEXT)
        if (result.resultCode == Activity.RESULT_OK && !content.isNullOrBlank()) {
            handlePairingText(content.trim())
        }
    }
    private val sheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            val expanded = newState == BottomSheetBehavior.STATE_EXPANDED
            val collapsed = newState == BottomSheetBehavior.STATE_COLLAPSED
            when (newState) {
                BottomSheetBehavior.STATE_COLLAPSED ->
                    _binding?.fabAdd?.translationY = -BottomSheetBehavior.from(bottomSheet).peekHeight.toFloat()
                BottomSheetBehavior.STATE_HALF_EXPANDED,
                BottomSheetBehavior.STATE_EXPANDED -> _binding?.fabAdd?.translationY = 0f
            }
            _binding?.fabAdd?.apply {
                setImageResource(if (collapsed) R.drawable.ic_target else R.drawable.ic_plus)
                contentDescription = getString(if (collapsed) R.string.center_on_device else R.string.add_contact)
            }
            _binding?.sheetHeader?.contentDescription = getString(
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> R.string.collapse_contacts_sheet
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> R.string.expand_contacts_fully
                    else -> R.string.expand_contacts_sheet
                }
            )
            val accessibility = if (expanded || newState == BottomSheetBehavior.STATE_HALF_EXPANDED)
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            _binding?.sheetScroll?.importantForAccessibility = accessibility
            _binding?.btnPermissions?.importantForAccessibility = accessibility
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            val behavior = BottomSheetBehavior.from(bottomSheet)
            val parentHeight = (bottomSheet.parent as? View)?.height ?: return
            val peek = behavior.peekHeight
            val distanceToHalf = (parentHeight - peek - parentHeight * (1f - behavior.halfExpandedRatio))
                .coerceAtLeast(1f)
            val progress = ((parentHeight - peek - bottomSheet.top) / distanceToHalf).coerceIn(0f, 1f)
            _binding?.fabAdd?.translationY = -peek * (1f - progress)
            // Move the whole map at half the sheet's speed, and stop once past the midway point.
            val halfTop = (parentHeight * (1f - behavior.halfExpandedRatio)).toInt()
            val collapsedTop = parentHeight - peek
            _binding?.mapView?.translationY = 0.5f * (bottomSheet.top.coerceAtLeast(halfTop) - collapsedTop)
        }
    }

    private data class Pin(val contact: VipContact, val location: ReceivedLocation, val unreachable: Boolean)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        _binding = HomeFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = PrefsManager(requireContext())
        val sheet = BottomSheetBehavior.from(binding.contactsSheet)
        sheet.isFitToContents = false
        sheet.halfExpandedRatio = 0.5f
        sheet.addBottomSheetCallback(sheetCallback)
        sheet.state = BottomSheetBehavior.STATE_COLLAPSED
        sheetCallback.onStateChanged(binding.contactsSheet, sheet.state)
        binding.sheetHeader.setOnClickListener {
            sheet.state = when (sheet.state) {
                BottomSheetBehavior.STATE_COLLAPSED -> BottomSheetBehavior.STATE_HALF_EXPANDED
                BottomSheetBehavior.STATE_HALF_EXPANDED -> BottomSheetBehavior.STATE_EXPANDED
                else -> BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        binding.fabAdd.setOnClickListener {
            if (BottomSheetBehavior.from(binding.contactsSheet).state == BottomSheetBehavior.STATE_COLLAPSED) {
                centerOnDevice()
            } else {
                showAddOptions()
            }
        }
        binding.btnPermissions.setOnClickListener {
            when {
                hasLocation() && !hasBackgroundLocation() -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${requireContext().packageName}")))
                else -> permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
        val mapView = binding.mapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { loadedMap ->
            if (_binding?.mapView !== mapView) return@getMapAsync
            map = loadedMap
            loadedMap.setStyle(Style.Builder().fromUri(getString(R.string.map_style_url))) { style ->
                if (_binding?.mapView !== mapView) return@setStyle
                val context = requireContext()
                for ((sourceId, colors) in listOf(
                    ACTIVE to Pair(
                        ThemeManager.color(context, androidx.appcompat.R.attr.colorPrimary),
                        ThemeManager.color(context, com.google.android.material.R.attr.colorOnPrimary)
                    ),
                    UNREACHABLE to Pair(
                        ThemeManager.color(context, com.google.android.material.R.attr.colorSurfaceVariant),
                        ThemeManager.color(context, com.google.android.material.R.attr.colorOnSurfaceVariant)
                    )
                )) {
                    style.addSource(GeoJsonSource(sourceId, FeatureCollection.fromFeatures(emptyArray<Feature>())))
                    style.addLayer(CircleLayer("$sourceId-pins", sourceId).withProperties(
                        circleRadius(12f), circleColor(colors.first), circleStrokeColor(colors.second), circleStrokeWidth(2f)))
                    style.addLayer(SymbolLayer("$sourceId-labels", sourceId).withProperties(
                        textField(get("initial")), textFont(arrayOf("Open Sans Regular")),
                        textSize(14f), textColor(colors.second)))
                }
                style.addSource(GeoJsonSource(DEVICE, FeatureCollection.fromFeatures(emptyArray<Feature>())))
                style.addLayer(CircleLayer("$DEVICE-pin", DEVICE).withProperties(
                    circleRadius(9f), circleColor(ThemeManager.color(context, com.google.android.material.R.attr.colorTertiary)),
                    circleStrokeColor(ThemeManager.color(context, com.google.android.material.R.attr.colorOnTertiary)),
                    circleStrokeWidth(2f)))
                refresh()
                updateDevicePin()
            }
        }
        arguments?.getString(ARG_PAIRING)?.let { linkText ->
            arguments?.remove(ARG_PAIRING)
            view.post { if (_binding != null) handlePairingText(linkText) }
        }
    }

    override fun onStart() { super.onStart(); _binding?.mapView?.onStart() }

    override fun onResume() {
        super.onResume()
        updatePermissions()
        _binding?.mapView?.onResume()
        ReceivedLocationStore(requireContext()).register(storeListener)
        refreshHandler.post(refreshRunnable)
        updateDevicePin()
        refreshContactLocations()
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(cooldownRefresh)
        refreshHandler.removeCallbacks(refreshRunnable)
        ReceivedLocationStore(requireContext()).unregister(storeListener)
        deviceHelper?.stop()
        deviceHelper = null
        _binding?.mapView?.onPause()
        super.onPause()
    }

    override fun onStop() { _binding?.mapView?.onStop(); super.onStop() }

    override fun onDestroyView() {
        refreshHandler.removeCallbacks(cooldownRefresh)
        BottomSheetBehavior.from(binding.contactsSheet).removeBottomSheetCallback(sheetCallback)
        deviceHelper?.stop()
        deviceHelper = null
        binding.mapView.onDestroy()
        map = null
        deviceLocation = null
        lastCentered = emptyList()
        _binding = null
        super.onDestroyView()
    }

    override fun onLowMemory() { super.onLowMemory(); _binding?.mapView?.onLowMemory() }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.mapView?.onSaveInstanceState(outState)
    }

    private fun hasLocation(): Boolean {
        val context = requireContext()
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocation(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun updatePermissions() {
        val missing = when {
            !hasLocation() -> R.string.location_permission_request
            !hasBackgroundLocation() -> R.string.location_bg_perm_needed
            else -> null
        }
        binding.btnPermissions.visibility = if (missing == null) View.GONE else View.VISIBLE
        if (missing != null) binding.btnPermissions.setText(missing)
    }

    private fun requestLocation(contact: VipContact) {
        val context = requireContext()
        val block = Push.locationBlock(context, contact)
        if (block != null) Toast.makeText(context, block, Toast.LENGTH_LONG).show()
        else if (Push.requestLocation(context, contact)) {
            Toast.makeText(context, getString(R.string.location_request_sent, contact.name), Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshContactLocations() {
        val context = context ?: return
        prefs.getContacts().forEach { contact ->
            if (Push.canRequestLocation(context, contact.number)) Push.requestLocation(context, contact)
        }
    }

    private fun centerOnDevice() {
        if (!hasLocation()) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        val context = context ?: return
        LocationHelper(context).requestSingleFix(object : LocationHelper.Callback {
            override fun onLocationReady(location: Location) {
                if (_binding == null) return
                deviceLocation = location
                updateDeviceSource()
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude), 15.0))
            }

            override fun onLocationFailed() {
                val ctx = context ?: return
                Toast.makeText(ctx, getString(R.string.device_location_unavailable), Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateDevicePin() {
        if (!hasLocation()) return
        val context = context ?: return
        deviceHelper?.stop()
        val helper = LocationHelper(context)
        deviceHelper = helper
        helper.requestSingleFix(object : LocationHelper.Callback {
            override fun onLocationReady(location: Location) {
                if (_binding == null) return
                deviceLocation = location
                updateDeviceSource()
            }

            override fun onLocationFailed() = Unit
        })
    }

    private fun updateDeviceSource() {
        val style = map?.style ?: return
        val loc = deviceLocation
        val features = if (loc == null) emptyArray<Feature>()
        else arrayOf(Feature.fromGeometry(Point.fromLngLat(loc.longitude, loc.latitude)))
        try {
            style.getSourceAs<GeoJsonSource>(DEVICE)?.setGeoJson(FeatureCollection.fromFeatures(features))
        } catch (_: Exception) { }
    }

    private fun editContact(contact: VipContact?, pendingPayload: PairPayload? = null) {
        val view = layoutInflater.inflate(R.layout.add_contact_dialog, null)
        val name = view.findViewById<EditText>(R.id.etDialogName)
        val locationSharing = view.findViewById<MaterialSwitch>(R.id.swDialogLocationSharing)
        name.setText(contact?.name.orEmpty())
        locationSharing.visibility = if (contact == null) View.VISIBLE else View.GONE
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (contact == null) R.string.add_choice_title else R.string.edit_contact_title)
            .setView(view)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val n = name.text.toString().trim()
                val contacts = prefs.getContacts().toMutableList()
                if (n.isBlank() || contacts.any { it.number != contact?.number && it.name.equals(n, ignoreCase = true) }) {
                    Toast.makeText(requireContext(), R.string.contact_invalid_input, Toast.LENGTH_LONG).show()
                } else if (contact == null && pendingPayload != null) {
                    pairNewContact(n, pendingPayload, locationSharing.isChecked)
                } else if (contact == null) {
                    contacts.add(VipContact(n, java.util.UUID.randomUUID().toString(), locationSharing.isChecked))
                    prefs.saveContacts(contacts)
                    refresh()
                } else {
                    val position = contacts.indexOfFirst { it.number == contact.number }
                    if (position < 0) return@setPositiveButton
                    contacts[position] = contact.copy(name = n)
                    prefs.saveContacts(contacts)
                    refresh()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null).show()
    }

    private fun deleteContact(contact: VipContact) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.remove_contact_title)
            .setMessage(getString(R.string.remove_contact_msg, contact.name))
            .setPositiveButton(R.string.btn_remove) { _, _ ->
                PeerStore(requireContext()).remove(contact.number)
                prefs.saveContacts(prefs.getContacts().filterNot { it.number == contact.number })
                refresh()
            }
            .setNegativeButton(R.string.btn_cancel, null).show()
    }

    private fun showAddOptions() {
        MaterialAlertDialogBuilder(requireContext())
            .setItems(arrayOf(getString(R.string.pair_scan), getString(R.string.pair_paste))) { _, which ->
                if (which == 0) {
                    qrScanLauncher.launch(Intent(requireContext(), QrScannerActivity::class.java))
                } else {
                    showPasteDialog()
                }
            }
            .show()
    }

    private fun showPasteDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.pair_paste_hint)
            minLines = 3
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pair_paste)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> handlePairingText(input.text.toString()) }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun handlePairingText(raw: String) {
        val payload = UnifiedPushPairing.decode(raw.trim())
        val idPub = payload?.idPub
        if (payload == null || idPub.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.p2p_pair_invalid, Toast.LENGTH_LONG).show()
            return
        }
        editContact(null, payload)
    }

    private fun pairNewContact(name: String, payload: PairPayload, locationEnabled: Boolean) {
        val idPub = payload.idPub ?: return
        val duplicateOwner = PeerStore(requireContext()).all().firstOrNull { it.idPub == idPub }
        if (duplicateOwner == null) {
            savePairedContact(name, payload, locationEnabled)
            return
        }
        val ownerName = prefs.getContacts()
            .firstOrNull { PhoneUtils.matches(it.number, duplicateOwner.number) }?.name
            ?: fingerprintOf(idPub)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.p2p_duplicate_identity_title)
            .setMessage(getString(R.string.p2p_duplicate_identity_msg, ownerName, name, fingerprintOf(idPub)))
            .setPositiveButton(R.string.p2p_duplicate_identity_confirm) { _, _ ->
                savePairedContact(name, payload, locationEnabled)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun savePairedContact(name: String, payload: PairPayload, locationEnabled: Boolean) {
        val idPub = payload.idPub ?: return
        val contacts = prefs.getContacts().toMutableList()
        if (contacts.any { it.name.equals(name, ignoreCase = true) }) {
            Toast.makeText(requireContext(), R.string.contact_invalid_input, Toast.LENGTH_LONG).show()
            return
        }
        val id = java.util.UUID.randomUUID().toString()
        val contact = VipContact(name, id, locationEnabled)
        contacts.add(contact)
        prefs.saveContacts(contacts)
        PeerStore(requireContext()).save(
            Peer(number = id, endpoint = payload.endpoint, p256dh = payload.p256dh, auth = payload.auth, idPub = idPub)
        )
        Toast.makeText(requireContext(), getString(R.string.p2p_pair_saved, name), Toast.LENGTH_SHORT).show()
        refresh()
        requestLocation(contact)
    }

    private fun fingerprintOf(idPubB64: String): String {
        return try {
            MessageAuth.fingerprint(WebPushCrypto.b64dec(idPubB64))
        } catch (e: Exception) {
            idPubB64
        }
    }

    private fun currentPins(): List<Pin> {
        val context = context ?: return emptyList()
        val peers = PeerStore(context).all().associateBy { PhoneUtils.normalize(it.number) }
        val store = ReceivedLocationStore(context)
        val now = System.currentTimeMillis()
        return prefs.getContacts().mapNotNull { contact ->
            val peer = peers[PhoneUtils.normalize(contact.number)] ?: return@mapNotNull null
            val location = store.get(peer) ?: return@mapNotNull null
            Pin(contact, location, store.unreachable(peer, location, now))
        }
    }

    private fun centerOnPins(pins: List<Pin>) {
        if (pins.isEmpty()) return
        val positions = pins.map { it.contact.number to it.location.receivedAt }
        if (positions == lastCentered) return
        lastCentered = positions
        val bounds = LatLngBounds.Builder()
        pins.forEach { bounds.include(LatLng(it.location.lat, it.location.lon)) }
        val mapView = binding.mapView
        mapView.post {
            if (_binding?.mapView !== mapView) return@post
            val camera = if (pins.size == 1) {
                CameraUpdateFactory.newLatLngZoom(LatLng(pins[0].location.lat, pins[0].location.lon), 13.0)
            } else {
                CameraUpdateFactory.newLatLngBounds(bounds.build(), 80)
            }
            map?.animateCamera(camera, 650)
        }
    }

    private fun refresh() {
        val context = context ?: return
        refreshHandler.removeCallbacks(cooldownRefresh)
        val now = System.currentTimeMillis()
        val pins = currentPins()
        val contacts = prefs.getContacts()
        val peers = PeerStore(context).all().associateBy { PhoneUtils.normalize(it.number) }
        val store = ReceivedLocationStore(context)
        var nextCooldownExpiry: Long? = null
        val pinByNumber = pins.associateBy { PhoneUtils.normalize(it.contact.number) }

        val scrollY = binding.sheetScroll.scrollY
        binding.locationSummary.removeAllViews()
        if (contacts.isEmpty()) {
            binding.locationSummary.addView(TextView(context).apply {
                setText(R.string.map_no_contacts)
                setTextColor(ThemeManager.color(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
        }
        contacts.forEach { contact ->
            val peer = peers[PhoneUtils.normalize(contact.number)]
            val cooldown = peer?.let { store.requestCooldownRemaining(it, now) } ?: 0L
            if (cooldown > 0L) nextCooldownExpiry = minOf(nextCooldownExpiry ?: cooldown, cooldown)
            val pin = pinByNumber[PhoneUtils.normalize(contact.number)]
            val row = layoutInflater.inflate(R.layout.home_contact_item, binding.locationSummary, false)
            val name = row.findViewById<TextView>(R.id.tvMapContactName)
            val lastSeen = row.findViewById<TextView>(R.id.tvMapLastSeen)
            name.text = contact.name
            if (pin == null) {
                name.setTextColor(ThemeManager.color(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
                val paired = peers[PhoneUtils.normalize(contact.number)] != null
                lastSeen.text = getString(R.string.last_seen_never) + "\n" +
                    getString(if (paired) R.string.map_request_first_hint else R.string.map_pair_first_hint)
                row.findViewById<View>(R.id.mapContactText).apply {
                    contentDescription = "${contact.name}, ${lastSeen.text}"
                    setOnClickListener { requestLocation(contact) }
                }
            } else {
                val minutes = ((now - pin.location.receivedAt).coerceAtLeast(0L) / 60_000L)
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                lastSeen.text = resources.getQuantityString(R.plurals.last_seen_minutes, minutes, minutes)
                if (pin.unreachable) name.setTextColor(
                    ThemeManager.color(context, com.google.android.material.R.attr.colorOnSurfaceVariant)
                )
            }
            row.findViewById<View>(R.id.btnMapRefresh).apply {
                contentDescription = getString(R.string.map_refresh_contact, contact.name)
                isEnabled = Push.canRequestLocation(context, contact.number)
                alpha = if (isEnabled) 1f else 0.35f
                setOnClickListener { requestLocation(contact) }
                isClickable = cooldown == 0L
                importantForAccessibility = if (cooldown > 0L) View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            }
            row.findViewById<View>(R.id.mapRefreshAction).apply {
                setOnClickListener(if (cooldown > 0L) View.OnClickListener { requestLocation(contact) } else null)
                contentDescription = if (cooldown > 0L) getString(R.string.map_refresh_contact, contact.name) + ", " +
                    getString(R.string.location_request_cooldown, (cooldown + 999L) / 1_000L) else null
            }
            row.findViewById<View>(R.id.btnMapLocate).apply {
                contentDescription = getString(R.string.map_find_contact, contact.name)
                isEnabled = pin != null
                alpha = if (pin == null) 0.35f else 1f
                setOnClickListener {
                    BottomSheetBehavior.from(binding.contactsSheet).state = BottomSheetBehavior.STATE_COLLAPSED
                    if (pin != null) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        LatLng(pin.location.lat, pin.location.lon), 15.0))
                }
            }
            row.findViewById<View>(R.id.btnMapMore).setOnClickListener { anchor ->
                PopupMenu(context, anchor).apply {
                    menuInflater.inflate(R.menu.vip_row_menu, menu)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_edit -> { editContact(contact); true }
                            R.id.action_delete -> { deleteContact(contact); true }
                            else -> false
                        }
                    }
                    show()
                }
            }
            binding.locationSummary.addView(row)
        }
        nextCooldownExpiry?.let { refreshHandler.postDelayed(cooldownRefresh, it) }
        binding.sheetScroll.post {
            if (_binding != null) binding.sheetScroll.scrollTo(0, scrollY)
        }

        val style = map?.style ?: return
        for ((source, selected) in listOf(ACTIVE to pins.filterNot { it.unreachable },
                UNREACHABLE to pins.filter { it.unreachable })) {
            val features = selected.map { pin ->
                Feature.fromGeometry(Point.fromLngLat(pin.location.lon, pin.location.lat)).apply {
                    addStringProperty("initial", pin.contact.name.trim().firstOrNull()?.uppercase() ?: "?")
                }
            }
            style.getSourceAs<GeoJsonSource>(source)?.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
        }

        centerOnPins(pins)
    }

    companion object {
        private const val ARG_PAIRING = "pairing_link"

        fun withPairing(linkText: String) = HomeFragment().apply {
            arguments = Bundle().apply { putString(ARG_PAIRING, linkText) }
        }

        private const val ACTIVE = "active-contacts"
        private const val UNREACHABLE = "unreachable-contacts"
        private const val DEVICE = "device-location"
    }
}
