package com.codency.seomate.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.codency.seomate.R
import com.codency.seomate.ui.home.HomeFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {

    private lateinit var titleText: TextView
    private lateinit var seochart: ImageView
    private lateinit var loginButton: Button
    private lateinit var signupText: TextView
    private lateinit var bottomSheetContainer: FrameLayout

    private var isSheetVisible = false

    // FirebaseAuth instance
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false)

        // Find views
        titleText = view.findViewById(R.id.letstart)
        seochart = view.findViewById(R.id.seochart)
        loginButton = view.findViewById(R.id.loginbtn)
        signupText = view.findViewById(R.id.tvSignup)
        bottomSheetContainer = view.findViewById(R.id.bottomSheetContainer)

        // Initialize FirebaseAuth
        auth = FirebaseAuth.getInstance()

        // Login button -> show login card
        loginButton.setOnClickListener {
            showBottomSheet(R.layout.login_bottom_sheet)
        }

        // Signup text -> show signup card
        signupText.setOnClickListener {
            showBottomSheet(R.layout.signup_bottom_sheet)
        }

        return view
    }

    private fun showBottomSheet(layoutRes: Int) {
        if (isSheetVisible) return

        bottomSheetContainer.removeAllViews()

        // Inflate card and force it to bottom
        val card = layoutInflater.inflate(layoutRes, bottomSheetContainer, false)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.BOTTOM
        card.layoutParams = params
        bottomSheetContainer.addView(card)

        bottomSheetContainer.visibility = View.VISIBLE
        isSheetVisible = true

        // Animate fade out of title, login, signup
        titleText.animate().alpha(0f).setDuration(300).start()
        loginButton.animate().alpha(0f).setDuration(300).start()
        signupText.animate().alpha(0f).setDuration(300).start()

        // Move + shrink seochart
        bottomSheetContainer.post {
            val cardHeight = bottomSheetContainer.height
            seochart.animate()
                .translationY(-cardHeight.toFloat() / 3)
                .scaleX(0.7f)
                .scaleY(0.7f)
                .setDuration(300)
                .start()
        }

        // Slide card up
        bottomSheetContainer.translationY = bottomSheetContainer.height.toFloat()
        bottomSheetContainer.animate().translationY(0f).setDuration(300).start()

        // Handle optional close button inside card
        card.findViewById<View>(R.id.closeBtn)?.setOnClickListener {
            hideBottomSheet()
        }

        // ✅ Handle login
        if (layoutRes == R.layout.login_bottom_sheet) {
            val emailField = card.findViewById<TextInputEditText>(R.id.editEmail)
            val passField = card.findViewById<TextInputEditText>(R.id.editPassword)
            val loginBtn = card.findViewById<View>(R.id.btnLogin)

            loginBtn.setOnClickListener {
                val email = emailField.text?.toString()?.trim() ?: ""
                val password = passField.text?.toString()?.trim() ?: ""

                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter email and password", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }


                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(requireActivity()) { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            if (user != null && user.isEmailVerified) {
                                Toast.makeText(requireContext(), "Login successful", Toast.LENGTH_SHORT).show()
                                hideBottomSheet()

                                // Notify MainActivity to check auth state
                                (activity as? MainActivity)?.requestAuthCheck()

                                // Navigate to home
                                val navOptions = androidx.navigation.NavOptions.Builder()
                                    .setPopUpTo(R.id.loginFragment, true)
                                    .build()

                                findNavController().navigate(R.id.action_loginFragment_to_homeFragment, null, navOptions)
                            } else {
                                Toast.makeText(requireContext(), "Please verify your email before logging in", Toast.LENGTH_LONG).show()
                                auth.signOut()
                            }
                        } else {
                            Toast.makeText(requireContext(), "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }

        // Handle signup with email verification
        if (layoutRes == R.layout.signup_bottom_sheet) {
            val emailField = card.findViewById<TextInputEditText>(R.id.editSignupEmail)
            val passField = card.findViewById<TextInputEditText>(R.id.editSignupPassword)
            val confirmField = card.findViewById<TextInputEditText>(R.id.editSignupConfirm)
            val signupBtn = card.findViewById<View>(R.id.btnCreate)
            val verificationMsg = card.findViewById<TextView>(R.id.tvVerificationMsg)

            // Dynamic message TextView
            val verificationMessage = TextView(requireContext()).apply {
                visibility = View.GONE
                textSize = 14f
                setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                setPadding(0, 16, 0, 0)
            }
            (card as ViewGroup).addView(verificationMessage)

            signupBtn.setOnClickListener {
                val email = emailField.text?.toString()?.trim() ?: ""
                val password = passField.text?.toString()?.trim() ?: ""
                val confirmPassword = confirmField.text?.toString()?.trim() ?: ""

                if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                    Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (password != confirmPassword) {
                    Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(requireActivity()) { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            user?.sendEmailVerification()?.addOnCompleteListener { emailTask ->
                                if (emailTask.isSuccessful) {
                                    verificationMsg.visibility = View.VISIBLE
                                } else {
                                    Toast.makeText(requireContext(), "Failed to send verification email.", Toast.LENGTH_LONG).show()
                                }
                            }
                            // Sign out user until verified
                            auth.signOut()
                        } else {
                            Toast.makeText(requireContext(), "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }
    }

    private fun hideBottomSheet() {
        if (!isSheetVisible) return

        bottomSheetContainer.animate()
            .translationY(bottomSheetContainer.height.toFloat())
            .setDuration(300)
            .withEndAction {
                bottomSheetContainer.visibility = View.GONE
                bottomSheetContainer.removeAllViews()
            }.start()

        seochart.animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .start()

        titleText.animate().alpha(1f).setDuration(300).start()
        loginButton.animate().alpha(1f).setDuration(300).start()
        signupText.animate().alpha(1f).setDuration(300).start()

        isSheetVisible = false
    }
}
