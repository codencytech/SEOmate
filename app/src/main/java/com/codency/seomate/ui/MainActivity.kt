package com.codency.seomate.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.codency.seomate.R
import com.codency.seomate.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var shouldCheckAuthState = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize NavController safely
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            ?: return.also { Log.e("MainActivity", "NavHostFragment not found!") }

        navController = navHostFragment.navController

        // Setup bottom navigation
        binding.bottomNavigationView.setupWithNavController(navController)

        // Hide bottom nav when on LoginFragment
        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d("MainActivity", "Navigated to ${destination.label}")
            if (destination.id == R.id.loginFragment) {
                binding.bottomNavigationView.visibility = View.GONE
            } else {
                binding.bottomNavigationView.visibility = View.VISIBLE
            }
        }

        // Always check auth state when activity is created
        checkAuthState()
    }

    override fun onResume() {
        super.onResume()
        // Check auth state when app comes to foreground
        checkAuthState()
    }

    private fun checkAuthState() {
        if (!shouldCheckAuthState) return

        binding.root.post {
            val currentUser = FirebaseAuth.getInstance().currentUser
            val currentDest = navController.currentDestination?.id

            Log.d("MainActivity", "Auth Check - User: $currentUser, Current destination: $currentDest")

            if (currentUser != null) {
                // User is logged in
                if (currentDest == R.id.loginFragment) {
                    // Navigate to home if on login screen
                    navigateToHome()
                }
                // If already on home or other fragment, do nothing
            } else {
                // User is not logged in
                if (currentDest != R.id.loginFragment && currentDest != null) {
                    // Navigate to login if not already there
                    navigateToLogin()
                }
            }

            shouldCheckAuthState = false
        }
    }

    private fun navigateToHome() {
        try {
            val navOptions = androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.loginFragment, true)
                .build()

            navController.navigate(R.id.action_loginFragment_to_homeFragment, null, navOptions)
            Log.d("MainActivity", "Navigated to HomeFragment")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error navigating to HomeFragment", e)
            // If action doesn't exist, try basic navigation
            try {
                navController.navigate(R.id.homeFragment)
            } catch (e2: Exception) {
                Log.e("MainActivity", "Fallback navigation also failed", e2)
            }
        }
    }

    private fun navigateToLogin() {
        try {
            val navOptions = androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.homeFragment, true)
                .build()

            navController.navigate(R.id.loginFragment, null, navOptions)
            Log.d("MainActivity", "Navigated to LoginFragment")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error navigating to LoginFragment", e)
            // If basic navigation fails, recreate activity
            recreate()
        }
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        val currentDestination = navController.currentDestination?.id

        // If we're on HomeFragment, minimize the app instead of going back to login
        if (currentDestination == R.id.homeFragment) {
            moveTaskToBack(true)
        } else {
            super.onBackPressed()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    // Call this method from fragments when they want to trigger auth check
    fun requestAuthCheck() {
        shouldCheckAuthState = true
    }
}