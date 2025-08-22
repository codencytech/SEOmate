package com.codency.seomate.ui

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

        // Delay navigation check to avoid crash
        binding.root.post {
            val currentUser = FirebaseAuth.getInstance().currentUser
            Log.d("MainActivity", "Current user: $currentUser")

            val currentDest = navController.currentDestination?.id
            Log.d("MainActivity", "Current destination ID: $currentDest")

            if (currentUser == null) {
                // Not logged in → only navigate if not already on LoginFragment
                if (currentDest != R.id.loginFragment) {
                    try {
                        navController.navigate(R.id.loginFragment)
                        Log.d("MainActivity", "Navigated to LoginFragment")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error navigating to LoginFragment", e)
                    }
                }
            } else {
                // Logged in → only navigate if not already on HomeFragment
                if (currentDest != R.id.homeFragment) {
                    try {
                        navController.navigate(R.id.homeFragment)
                        Log.d("MainActivity", "Navigated to HomeFragment")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error navigating to HomeFragment", e)
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
