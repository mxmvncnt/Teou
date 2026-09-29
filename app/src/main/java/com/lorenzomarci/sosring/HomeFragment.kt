package com.lorenzomarci.sosring

import android.Manifest
import android.content.SharedPreferences
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lorenzomarci.sosring.databinding.FragmentHomeBinding
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
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrefsManager
    private var map: MapLibreMap? = null
    private var lastCentered: List<Pair<String, Long>> = emptyList()
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (_binding != null) refresh()
            refreshHandler.postDelayed(this, 60_000L)
        }
    }
    private val storeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        activity?.runOnUiThread { if (_binding != null) refresh() }
    }
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { if (_binding != null) updatePermissions() }
    private val sheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            val expanded = newState == BottomSheetBehavior.STATE_EXPANDED
            _binding?.fabAdd?.visibility = if (expanded) View.VISIBLE else View.GONE
            _binding?.sheetHeader?.contentDescription = getString(
                if (expanded) R.string.collapse_contacts_sheet else R.string.expand_contacts_sheet
            )
            val accessibility = if (expanded) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            _binding?.sheetScroll?.importantForAccessibility = accessibility
            _binding?.btnPermissions?.importantForAccessibility = accessibility
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
    }

    private data class Pin(val contact: VipContact, val location: ReceivedLocation, val unreachable: Boolean)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = PrefsManager(requireContext())
        val sheet = BottomSheetBehavior.from(binding.contactsSheet)
        sheet.addBottomSheetCallback(sheetCallback)
        sheet.state = BottomSheetBehavior.STATE_COLLAPSED
        sheetCallback.onStateChanged(binding.contactsSheet, sheet.state)
        binding.sheetHeader.setOnClickListener {
            sheet.state = if (sheet.state == BottomSheetBehavior.STATE_EXPANDED) {
                BottomSheetBehavior.STATE_COLLAPSED
            } else {
                BottomSheetBehavior.STATE_EXPANDED
            }
        }
        binding.fabAdd.setOnClickListener { editContact(null) }
        binding.btnPermissions.setOnClickListener {
            when {
                hasLocation() && !hasBackgroundLocation() -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${requireContext().packageName}")))
                Build.VERSION.SDK_INT >= 33 && !hasNotifications() ->
                    permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                else -> permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
        val mapView = binding.mapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { loadedMap ->
            if (_binding?.mapView !== mapView) return@getMapAsync
            map = loadedMap
            loadedMap.setStyle(Style.Builder().fromJson(OSM_STYLE)) { style ->
                if (_binding?.mapView !== mapView) return@setStyle
                for ((sourceId, color) in listOf(ACTIVE to Color.rgb(21, 101, 192), UNREACHABLE to Color.GRAY)) {
                    style.addSource(GeoJsonSource(sourceId, FeatureCollection.fromFeatures(emptyArray<Feature>())))
                    style.addLayer(CircleLayer("$sourceId-pins", sourceId).withProperties(
                        circleRadius(9f), circleColor(color), circleStrokeColor(Color.WHITE), circleStrokeWidth(2f)))
                    style.addLayer(SymbolLayer("$sourceId-labels", sourceId).withProperties(
                        textField(get("label")), textSize(13f), textColor(color), textOffset(arrayOf(0f, 1.6f))))
                }
                refresh()
            }
        }
    }

    override fun onStart() { super.onStart(); _binding?.mapView?.onStart() }

    override fun onResume() {
        super.onResume()
        updatePermissions()
        _binding?.mapView?.onResume()
        ReceivedLocationStore(requireContext()).register(storeListener)
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshRunnable)
        ReceivedLocationStore(requireContext()).unregister(storeListener)
        _binding?.mapView?.onPause()
        super.onPause()
    }

    override fun onStop() { _binding?.mapView?.onStop(); super.onStop() }

    override fun onDestroyView() {
        BottomSheetBehavior.from(binding.contactsSheet).removeBottomSheetCallback(sheetCallback)
        binding.mapView.onDestroy()
        map = null
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

    private fun hasNotifications(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun updatePermissions() {
        val missing = when {
            !hasLocation() -> R.string.location_permission_request
            !hasBackgroundLocation() -> R.string.location_bg_perm_needed
            !hasNotifications() -> R.string.notification_permission_request
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

    private fun editContact(contact: VipContact?) {
        val view = layoutInflater.inflate(R.layout.dialog_add_number, null)
        val name = view.findViewById<EditText>(R.id.etDialogName)
        val number = view.findViewById<EditText>(R.id.etDialogNumber)
        name.setText(contact?.name.orEmpty())
        number.setText(contact?.number.orEmpty())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (contact == null) R.string.add_choice_title else R.string.edit_contact_title)
            .setView(view)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val n = name.text.toString().trim()
                val p = number.text.toString().trim()
                val contacts = prefs.getContacts().toMutableList()
                if (n.isBlank() || p.length <= 3 || contacts.any { it.number != contact?.number && PhoneUtils.matches(it.number, p) }) {
                    Toast.makeText(requireContext(), R.string.contact_invalid_input, Toast.LENGTH_LONG).show()
                } else {
                    if (contact == null) contacts.add(VipContact(n, p))
                    else {
                        val position = contacts.indexOfFirst { it.number == contact.number }
                        if (position < 0) return@setPositiveButton
                        if (PhoneUtils.normalize(contact.number) != PhoneUtils.normalize(p)) {
                            PeerStore(requireContext()).remove(contact.number)
                        }
                        contacts[position] = contact.copy(name = n, number = p)
                    }
                    prefs.saveContacts(contacts)
                    refresh()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null).show()
    }

    private fun deleteContact(contact: VipContact) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.remove_contact_title)
            .setMessage(getString(R.string.remove_contact_msg, contact.name, contact.number))
            .setPositiveButton(R.string.btn_remove) { _, _ ->
                PeerStore(requireContext()).remove(contact.number)
                prefs.saveContacts(prefs.getContacts().filterNot { it.number == contact.number })
                refresh()
            }
            .setNegativeButton(R.string.btn_cancel, null).show()
    }

    private fun refresh() {
        val context = context ?: return
        val peers = PeerStore(context).all().associateBy { PhoneUtils.normalize(it.number) }
        val store = ReceivedLocationStore(context)
        val now = System.currentTimeMillis()
        val contacts = prefs.getContacts()
        val pins = contacts.mapNotNull { contact ->
            val peer = peers[PhoneUtils.normalize(contact.number)] ?: return@mapNotNull null
            val location = store.get(peer) ?: return@mapNotNull null
            Pin(contact, location, store.unreachable(peer, location, now))
        }
        val pinByNumber = pins.associateBy { PhoneUtils.normalize(it.contact.number) }

        val scrollY = binding.sheetScroll.scrollY
        binding.locationSummary.removeAllViews()
        if (contacts.isEmpty()) {
            binding.locationSummary.addView(TextView(context).apply {
                setText(R.string.map_no_contacts)
                setTextColor(ContextCompat.getColor(context, R.color.ink_secondary))
            })
        }
        contacts.forEach { contact ->
            val pin = pinByNumber[PhoneUtils.normalize(contact.number)]
            val row = layoutInflater.inflate(R.layout.item_map_contact, binding.locationSummary, false)
            val name = row.findViewById<TextView>(R.id.tvMapContactName)
            val lastSeen = row.findViewById<TextView>(R.id.tvMapLastSeen)
            name.text = contact.name
            if (pin == null) {
                name.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
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
                if (pin.unreachable) name.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            }
            row.findViewById<ImageButton>(R.id.btnMapRefresh).apply {
                contentDescription = getString(R.string.map_refresh_contact, contact.name)
                isEnabled = pin != null
                alpha = if (pin == null) 0.35f else 1f
                setOnClickListener { requestLocation(contact) }
            }
            row.findViewById<ImageButton>(R.id.btnMapLocate).apply {
                contentDescription = getString(R.string.map_find_contact, contact.name)
                isEnabled = pin != null
                alpha = if (pin == null) 0.35f else 1f
                setOnClickListener {
                    BottomSheetBehavior.from(binding.contactsSheet).state = BottomSheetBehavior.STATE_COLLAPSED
                    if (pin != null) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        LatLng(pin.location.lat, pin.location.lon), 15.0))
                }
            }
            row.findViewById<ImageButton>(R.id.btnMapMore).setOnClickListener { anchor ->
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
        binding.sheetScroll.post {
            if (_binding != null) binding.sheetScroll.scrollTo(0, scrollY)
        }

        val style = map?.style ?: return
        for ((source, selected) in listOf(ACTIVE to pins.filterNot { it.unreachable },
                UNREACHABLE to pins.filter { it.unreachable })) {
            val features = selected.map { pin ->
                Feature.fromGeometry(Point.fromLngLat(pin.location.lon, pin.location.lat)).apply {
                    addStringProperty("label", pin.contact.name)
                }
            }
            style.getSourceAs<GeoJsonSource>(source)?.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
        }

        val positions = pins.map { it.contact.number to it.location.receivedAt }
        if (pins.isEmpty() || positions == lastCentered) return
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
            map?.animateCamera(camera)
        }
    }

    companion object {
        private const val ACTIVE = "active-contacts"
        private const val UNREACHABLE = "unreachable-contacts"
        private const val OSM_STYLE = """{
            "version": 8,
            "glyphs": "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf",
            "sources": {"osm": {"type": "raster", "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                "tileSize": 256, "attribution": "© OpenStreetMap contributors"}},
            "layers": [{"id": "osm", "type": "raster", "source": "osm"}]
        }"""
    }
}
