package com.example.stockflow.ui.getstarted

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.example.stockflow.R
import com.example.stockflow.data.local.SessionStore
import com.example.stockflow.databinding.ActivityGetStartedBinding
import com.example.stockflow.ui.common.SystemBars
import com.example.stockflow.ui.login.LoginActivity
import kotlin.math.abs

/**
 * First-launch 3-slide onboarding.
 * Shown only while [SessionStore.hasSeenGetStarted] is false.
 *
 * Slides:
 * 0 — Welcome (gstarted)
 * 1 — Inventory (gstarted_2)
 * 2 — Run your business (gstarted_3)
 */
class GetStartedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGetStartedBinding
    private lateinit var sessionStore: SessionStore
    private lateinit var gestureDetector: GestureDetector

    /** Current onboarding page index (0..2). */
    private var currentSlide = 0

    private data class Slide(
        val imageRes: Int,
        val titleRes: Int,
        val descriptionRes: Int,
        val isFinal: Boolean
    )

    private val slides = listOf(
        Slide(
            imageRes = R.drawable.gstarted,
            titleRes = R.string.onboarding_slide1_title,
            descriptionRes = R.string.onboarding_slide1_description,
            isFinal = false
        ),
        Slide(
            imageRes = R.drawable.gstarted_2,
            titleRes = R.string.onboarding_slide2_title,
            descriptionRes = R.string.onboarding_slide2_description,
            isFinal = false
        ),
        Slide(
            imageRes = R.drawable.gstarted_3,
            titleRes = R.string.onboarding_slide3_title,
            descriptionRes = R.string.onboarding_slide3_description,
            isFinal = true
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionStore = SessionStore(this)

        // Safety: if opened after the flag is already set, skip to Login.
        if (sessionStore.hasSeenGetStarted()) {
            goToLogin(clearSelf = true)
            return
        }

        binding = ActivityGetStartedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.apply(this, binding.root)

        currentSlide = savedInstanceState?.getInt(KEY_SLIDE, 0) ?: 0
        bindSlide(currentSlide, animate = false)

        binding.btnPrimary.setOnClickListener {
            onPrimaryClicked()
        }

        // Swipe between slides without blocking button taps (no ViewPager dependency).
        gestureDetector = GestureDetector(this, SwipeGestureListener())
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::gestureDetector.isInitialized) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SLIDE, currentSlide)
    }

    private fun onPrimaryClicked() {
        if (currentSlide < slides.lastIndex) {
            showSlide(currentSlide + 1)
        } else {
            completeOnboarding()
        }
    }

    private fun completeOnboarding() {
        // Persist so Get Started never shows again until app data is cleared.
        sessionStore.markGetStartedSeen()
        goToLogin(clearSelf = true)
    }

    private fun showSlide(index: Int) {
        if (index !in slides.indices || index == currentSlide) return
        currentSlide = index
        bindSlide(currentSlide, animate = true)
    }

    private fun bindSlide(index: Int, animate: Boolean) {
        val slide = slides[index]
        val dots = listOf(binding.dot0, binding.dot1, binding.dot2)

        val applyContent = {
            binding.onboardingImage.setImageResource(slide.imageRes)
            binding.titleText.setText(slide.titleRes)
            binding.descriptionText.setText(slide.descriptionRes)
            binding.btnPrimary.setText(
                if (slide.isFinal) R.string.lets_get_started else R.string.onboarding_next
            )
            dots.forEachIndexed { i, dot ->
                dot.setBackgroundResource(
                    if (i == index) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive
                )
            }
        }

        if (!animate) {
            applyContent()
            return
        }

        // Subtle cross-fade between slides (platform animation only).
        val contentViews = listOf(
            binding.onboardingImage,
            binding.titleText,
            binding.descriptionText
        )
        contentViews.forEach { it.animate().cancel() }
        contentViews[0].animate()
            .alpha(0f)
            .setDuration(120L)
            .withEndAction {
                applyContent()
                contentViews.forEach { view ->
                    view.alpha = 0f
                    view.animate().alpha(1f).setDuration(160L).start()
                }
            }
            .start()
        contentViews.drop(1).forEach { view ->
            view.animate().alpha(0f).setDuration(120L).start()
        }
    }

    private fun goToLogin(clearSelf: Boolean) {
        startActivity(Intent(this, LoginActivity::class.java))
        if (clearSelf) {
            finish()
        }
    }

    private inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (abs(dx) < SWIPE_MIN_DISTANCE || abs(dx) < abs(dy)) return false
            if (abs(velocityX) < SWIPE_MIN_VELOCITY) return false

            if (dx < 0) {
                // Swipe left → next slide
                if (currentSlide < slides.lastIndex) showSlide(currentSlide + 1)
            } else {
                // Swipe right → previous slide
                if (currentSlide > 0) showSlide(currentSlide - 1)
            }
            return true
        }
    }

    companion object {
        private const val KEY_SLIDE = "onboarding_slide"
        private const val SWIPE_MIN_DISTANCE = 100
        private const val SWIPE_MIN_VELOCITY = 200
    }
}
