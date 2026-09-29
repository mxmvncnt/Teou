package com.mxmvncnt.teou.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.mxmvncnt.teou.*
import com.mxmvncnt.teou.R
import com.mxmvncnt.teou.app.ThemeManager
import com.mxmvncnt.teou.data.PrefsManager
import com.mxmvncnt.teou.databinding.LocationLogFragmentBinding

class LocationLogFragment : Fragment() {

    private var _binding: LocationLogFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LocationLogFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadLogs()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadLogs() {
        val prefs = PrefsManager(requireContext())
        val container = binding.locationLogContainer
        container.removeAllViews()

        val logs = prefs.getLocationLogs().filter { it.type == "incoming" || it.type == "incoming_denied" }
        val dateFormat = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())

        if (logs.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = getString(R.string.location_log_empty)
                textSize = 14f
                setTextColor(ThemeManager.color(
                    requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant
                ))
                setPadding(0, 16, 0, 16)
            }
            container.addView(empty)
        } else {
            logs.take(50).forEach { entry ->
                val date = dateFormat.format(java.util.Date(entry.timestamp))
                val label = if (entry.type == "incoming_denied") {
                    getString(R.string.location_log_denied, entry.name)
                } else {
                    "\u2B07 ${getString(R.string.location_log_incoming, entry.name)}"
                }
                val tv = TextView(requireContext()).apply {
                    text = "$label\n     $date"
                    textSize = 14f
                    setPadding(0, 8, 0, 8)
                }
                container.addView(tv)
            }
        }
    }
}
