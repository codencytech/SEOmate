package com.codency.seomate.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.codency.seomate.R
import com.google.firebase.auth.FirebaseAuth

class SplashFragment : Fragment() {

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide bottom nav on splash
        (activity as? MainActivity)?.findViewById<View>(R.id.custom_bottom_bar)?.visibility = View.GONE


        auth = FirebaseAuth.getInstance()

        // Add a small delay so splash looks natural (optional)
        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = auth.currentUser
            if (currentUser != null) {
                // User is already logged in -> go to Home
                findNavController().navigate(R.id.action_splashFragment_to_homeFragment)
            } else {
                // User not logged in -> go to Login
                findNavController().navigate(R.id.action_splashFragment_to_loginFragment)
            }
        }, 3000) // 1 second delay, adjust as you like
    }
}
