package com.lorenzomarci.sosring

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
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

    private data class Pin(val contact: VipContact, val location: ReceivedLocation, val unreachable: Boolean)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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

    private fun refresh() {
        val context = context ?: return
        val peers = PeerStore(context)
        val store = ReceivedLocationStore(context)
        val now = System.currentTimeMillis()
        val pins = PrefsManager(context).getContacts().mapNotNull { contact ->
            val peer = peers.get(contact.number) ?: return@mapNotNull null
            val location = store.get(peer) ?: return@mapNotNull null
            Pin(contact, location, store.unreachable(peer, location, now))
        }

        val scrollY = binding.sheetScroll.scrollY
        binding.locationSummary.removeAllViews()
        if (pins.isEmpty()) {
            binding.locationSummary.addView(TextView(context).apply {
                setText(R.string.home_map_placeholder)
                setTextColor(ContextCompat.getColor(context, R.color.ink_secondary))
            })
        }
        pins.forEach { pin ->
            val minutes = ((now - pin.location.receivedAt).coerceAtLeast(0L) / 60_000L)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val row = layoutInflater.inflate(R.layout.item_map_contact, binding.locationSummary, false)
            row.alpha = if (pin.unreachable) 0.5f else 1f
            row.findViewById<TextView>(R.id.tvMapContactName).text = pin.contact.name
            row.findViewById<TextView>(R.id.tvMapLastSeen).text =
                resources.getQuantityString(R.plurals.last_seen_minutes, minutes, minutes)
            row.findViewById<ImageButton>(R.id.btnMapRefresh).apply {
                contentDescription = getString(R.string.map_refresh_contact, pin.contact.name)
                setOnClickListener {
                    val block = Push.locationBlock(context, pin.contact)
                    if (block != null) Toast.makeText(context, block, Toast.LENGTH_LONG).show()
                    else if (Push.requestLocation(context, pin.contact)) {
                        Toast.makeText(context, getString(R.string.location_request_sent, pin.contact.name), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            row.findViewById<ImageButton>(R.id.btnMapLocate).apply {
                contentDescription = getString(R.string.map_find_contact, pin.contact.name)
                setOnClickListener {
                    BottomSheetBehavior.from(binding.contactsSheet).state = BottomSheetBehavior.STATE_COLLAPSED
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        LatLng(pin.location.lat, pin.location.lon), 15.0))
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
