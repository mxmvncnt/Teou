package com.lorenzomarci.sosring

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lorenzomarci.sosring.databinding.FragmentContactsBinding

class ContactsFragment : Fragment() {
    private var _binding: FragmentContactsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrefsManager
    private lateinit var adapter: VipNumbersAdapter
    private val contacts = mutableListOf<VipContact>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { updatePermissions() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = PrefsManager(requireContext())
        adapter = VipNumbersAdapter(
            onEdit = { position, contact -> editContact(position, contact) },
            onDelete = { position -> deleteContact(position) },
            onTrackTap = { contact ->
                val block = Push.locationBlock(requireContext(), contact)
                if (block != null) Toast.makeText(requireContext(), block, Toast.LENGTH_LONG).show()
                else if (Push.requestLocation(requireContext(), contact)) {
                    Toast.makeText(requireContext(), getString(R.string.location_request_sent, contact.name), Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.rvContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvContacts.adapter = adapter
        binding.fabAdd.setOnClickListener { editContact(null, null) }
        binding.btnPermissions.setOnClickListener {
            if (hasLocation() && !hasBackgroundLocation()) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${requireContext().packageName}")))
            } else {
                val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
                permissionLauncher.launch(permissions.toTypedArray())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissions()
        contacts.clear()
        contacts.addAll(prefs.getContacts())
        adapter.submitList(contacts.toList())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun hasLocation() = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocation() = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    private fun updatePermissions() {
        binding.btnPermissions.text = when {
            !hasLocation() -> getString(R.string.location_permission_request)
            !hasBackgroundLocation() -> getString(R.string.location_bg_perm_needed)
            else -> getString(R.string.status_granted)
        }
        binding.btnPermissions.isEnabled = !hasLocation() || !hasBackgroundLocation()
    }

    private fun editContact(position: Int?, contact: VipContact?) {
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
                if (n.isBlank() || p.length <= 3 || contacts.any { it !== contact && PhoneUtils.matches(it.number, p) }) {
                    Toast.makeText(requireContext(), R.string.contact_invalid_input, Toast.LENGTH_LONG).show()
                } else {
                    if (position == null) contacts.add(VipContact(n, p))
                    else {
                        if (PhoneUtils.normalize(contact!!.number) != PhoneUtils.normalize(p)) {
                            PeerStore(requireContext()).remove(contact.number)
                        }
                        contacts[position] = contact.copy(name = n, number = p)
                    }
                    saveContacts()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null).show()
    }

    private fun deleteContact(position: Int) {
        val contact = contacts[position]
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.remove_contact_title)
            .setMessage(getString(R.string.remove_contact_msg, contact.name, contact.number))
            .setPositiveButton(R.string.btn_remove) { _, _ ->
                PeerStore(requireContext()).remove(contact.number)
                contacts.removeAt(position)
                saveContacts()
            }
            .setNegativeButton(R.string.btn_cancel, null).show()
    }

    private fun saveContacts() {
        prefs.saveContacts(contacts)
        adapter.submitList(contacts.toList())
    }
}
