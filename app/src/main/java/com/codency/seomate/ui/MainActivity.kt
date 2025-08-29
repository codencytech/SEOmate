package com.codency.seomate.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.codency.seomate.R
import com.codency.seomate.databinding.ActivityMainBinding
import com.codency.seomate.databinding.LayoutCustomBottomNavBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var customNavBinding: LayoutCustomBottomNavBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val customNavView: View = binding.root.findViewById(R.id.custom_bottom_bar)
        customNavBinding = LayoutCustomBottomNavBinding.bind(customNavView)
        Log.d("MainActivity", "Custom Nav Bar found: ${customNavBinding.root != null}")

        // Initialize NavController safely
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            ?: return.also { Log.e("MainActivity", "NavHostFragment not found!") }

        navController = navHostFragment.navController

        setupCustomBottomNav()

        // Hide bottom nav when on LoginFragment
        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d("MainActivity", "Navigated to ${destination.label}")
            if (destination.id == R.id.loginFragment) {
                customNavBinding.root.visibility = View.GONE
            } else {
                customNavBinding.root.visibility = View.VISIBLE
                updateCustomNavSelection(destination.id)
            }
        }
    }

    private fun setupCustomBottomNav() {
        // Set click listeners for each item
        customNavBinding.homeItem.setOnClickListener {
            navigateToFragment(R.id.homeFragment)
        }
        customNavBinding.reportItem.setOnClickListener {
            navigateToFragment(R.id.reportFragment)
        }
        customNavBinding.settingsItem.setOnClickListener {
            navigateToFragment(R.id.settingsFragment)
        }
    }

    private fun navigateToFragment(fragmentId: Int) {
        val currentDest = navController.currentDestination?.id
        if (currentDest != fragmentId) {
            navController.navigate(fragmentId)
        }
    }

    private fun updateCustomNavSelection(destinationId: Int) {
        // Reset all items to inactive state
        setItemInactive(customNavBinding.homeItem, customNavBinding.homeIcon, customNavBinding.homeActiveIndicator, R.drawable.ic_home)
        setItemInactive(customNavBinding.reportItem, customNavBinding.reportIcon, customNavBinding.reportActiveIndicator, R.drawable.ic_report)
        setItemInactive(customNavBinding.settingsItem, customNavBinding.settingIcon, customNavBinding.settingActiveIndicator, R.drawable.ic_settings)

        // Set the active item based on the current destination
        when (destinationId) {
            R.id.homeFragment -> {
                setItemActive(customNavBinding.homeItem, customNavBinding.homeIcon, customNavBinding.homeActiveIndicator, R.drawable.ic_home)
            }
            R.id.reportFragment -> {
                setItemActive(customNavBinding.reportItem, customNavBinding.reportIcon, customNavBinding.reportActiveIndicator, R.drawable.ic_report)
            }
            R.id.settingsFragment -> {
                setItemActive(customNavBinding.settingsItem, customNavBinding.settingIcon, customNavBinding.settingActiveIndicator, R.drawable.ic_settings)
            }
        }
    }

    private fun setItemInactive(itemView: View, iconView: View, indicatorView: View, outlineIconId: Int) {
        if (iconView is androidx.appcompat.widget.AppCompatImageView) {
            iconView.setImageResource(outlineIconId)
            iconView.setColorFilter(ContextCompat.getColor(this, R.color.nav_inactive_color))
        }
        indicatorView.visibility = View.INVISIBLE
    }

    private fun setItemActive(itemView: View, iconView: View, indicatorView: View, solidIconId: Int) {
        if (iconView is androidx.appcompat.widget.AppCompatImageView) {
            iconView.setImageResource(solidIconId)
            iconView.setColorFilter(ContextCompat.getColor(this, R.color.nav_active_color))
        }
        indicatorView.visibility = View.VISIBLE
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        val currentDestination = navController.currentDestination?.id
        if (currentDestination == R.id.homeFragment) {
            moveTaskToBack(true)
        } else {
            super.onBackPressed()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
