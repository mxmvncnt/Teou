package com.mxmvncnt.teou

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.mxmvncnt.teou.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val toolbarHeight = binding.toolbar.layoutParams.height
        val toolbarTop = binding.toolbar.paddingTop
        val contentBottom = binding.fragmentContainer.paddingBottom
        val drawerHeader = binding.navigationView.getHeaderView(0)
        val drawerHeaderTop = drawerHeader.paddingTop
        val drawerBottom = binding.navigationView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.updatePadding(top = toolbarTop + bars.top)
            binding.toolbar.updateLayoutParams { height = toolbarHeight + bars.top }
            binding.fragmentContainer.updatePadding(bottom = contentBottom + bars.bottom)
            drawerHeader.updatePadding(top = drawerHeaderTop + bars.top)
            binding.navigationView.updatePadding(bottom = drawerBottom + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    return
                }
                val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                if (current !is HomeFragment) {
                    loadFragment(HomeFragment(), getString(R.string.nav_home))
                    binding.navigationView.setCheckedItem(R.id.nav_home)
                } else {
                    finish()
                }
            }
        })

        handlePairingLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePairingLink(intent)
    }

    private fun handlePairingLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        if (data.scheme != UnifiedPushPairing.LINK_SCHEME || data.host != "pair") return
        val linkText = intent.dataString ?: return
        loadFragment(HomeFragment.withPairing(linkText), getString(R.string.nav_home))
        binding.navigationView.setCheckedItem(R.id.nav_home)
    }

    override fun onResume() {
        super.onResume()
        Push.ensureRegistered(this)
    }

    private fun loadFragment(fragment: androidx.fragment.app.Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        supportActionBar?.title = title
    }

}
