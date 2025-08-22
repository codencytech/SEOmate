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
import androidx.fragment.app.Fragment
import com.codency.seomate.R

class LoginFragment : Fragment() {

    private lateinit var titleText: TextView
    private lateinit var seochart: ImageView
    private lateinit var loginButton: Button
    private lateinit var signupText: TextView
    private lateinit var bottomSheetContainer: FrameLayout

    private var isSheetVisible = false

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
    }

    private fun hideBottomSheet() {
        if (!isSheetVisible) return

        // Slide card down
        bottomSheetContainer.animate()
            .translationY(bottomSheetContainer.height.toFloat())
            .setDuration(300)
            .withEndAction {
                bottomSheetContainer.visibility = View.GONE
                bottomSheetContainer.removeAllViews()
            }.start()

        // Restore seochart (position + size)
        seochart.animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .start()

        // Fade views back in
        titleText.animate().alpha(1f).setDuration(300).start()
        loginButton.animate().alpha(1f).setDuration(300).start()
        signupText.animate().alpha(1f).setDuration(300).start()

        isSheetVisible = false
    }
}
