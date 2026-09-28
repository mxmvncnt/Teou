package com.lorenzomarci.sosring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.lorenzomarci.sosring.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var toggle: ActionBarDrawerToggle

    private val contactsUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (current is HomeFragment) {
                current.onResume()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.nav_home, R.string.nav_home
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment(), getString(R.string.nav_home))
                R.id.nav_location_log -> loadFragment(LocationLogFragment(), getString(R.string.nav_location_log))
                R.id.nav_security -> loadFragment(SecurityFragment(), getString(R.string.security_title))
            }
            menuItem.isChecked = true
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        loadFragment(HomeFragment(), getString(R.string.nav_home))
        binding.navigationView.setCheckedItem(R.id.nav_home)

    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            contactsUpdatedReceiver,
            IntentFilter(Push.ACTION_CONTACTS_UPDATED)
        )
        Push.ensureRegistered(this)
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(contactsUpdatedReceiver)
    }

    private fun loadFragment(fragment: androidx.fragment.app.Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        supportActionBar?.title = title
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (current !is HomeFragment) {
                loadFragment(HomeFragment(), getString(R.string.nav_home))
                binding.navigationView.setCheckedItem(R.id.nav_home)
            } else {
                super.onBackPressed()
            }
        }
    }
}
