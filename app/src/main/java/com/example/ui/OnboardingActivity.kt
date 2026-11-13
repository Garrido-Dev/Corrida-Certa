package com.example.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.data.SettingsRepository
import com.example.databinding.ActivityOnboardingBinding
import com.example.service.AccessibilityStatusHelper
import com.example.service.MonitoringNotificationHelper
import com.example.service.OverlayPermissionHelper
import kotlinx.coroutines.launch

/**
 * Onboarding em 3 etapas:
 * 1. O que é o Rota Pro
 * 2. Como ativar a Acessibilidade
 * 3. Como ativar o Overlay flutuante (e notificações no Android 13+)
 */
class OnboardingActivity : AppCompatActivity() {

    companion object {
        private const val TOTAL_STEPS = 3
        private const val EXTRA_LAUNCH_MAIN_ON_FINISH = "extra_launch_main_on_finish"

        fun createIntent(
            context: Context,
            launchMainOnFinish: Boolean = true
        ): Intent {
            return Intent(context, OnboardingActivity::class.java).apply {
                putExtra(EXTRA_LAUNCH_MAIN_ON_FINISH, launchMainOnFinish)
            }
        }
    }

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var settingsRepository: SettingsRepository
    private var currentStep = 0

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissionBadges()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val msgRes = if (granted) {
            if (AccessibilityStatusHelper.isServiceEnabled(this)) {
                MonitoringNotificationHelper.showMonitoringNotification(this)
            }
            R.string.msg_notifications_granted
        } else {
            R.string.msg_notifications_denied
        }
        Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
        refreshPermissionBadges()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepository = SettingsRepository(applicationContext)
        applyEdgeToEdgeInsets()
        setupListeners()
        setupBackNavigation()
        renderStep()
        refreshPermissionBadges()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionBadges()
    }

    private fun applyEdgeToEdgeInsets() {
        val baseHorizontal = resources.getDimensionPixelSize(R.dimen.spacing_medium_fallback)
        ViewCompat.setOnApplyWindowInsetsListener(binding.onboardingContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = baseHorizontal + systemBars.left,
                top = baseHorizontal + systemBars.top,
                right = baseHorizontal + systemBars.right,
                bottom = baseHorizontal + systemBars.bottom
            )
            insets
        }
    }

    private fun setupListeners() {
        binding.btnOnboardingSkip.setOnClickListener {
            completeOnboarding()
        }

        binding.btnOnboardingBack.setOnClickListener {
            if (currentStep > 0) {
                currentStep--
                renderStep()
            }
        }

        binding.btnOnboardingNext.setOnClickListener {
            if (currentStep < TOTAL_STEPS - 1) {
                currentStep++
                renderStep()
            } else {
                completeOnboarding()
            }
        }

        binding.btnOnboardingOpenAccessibility.setOnClickListener {
            try {
                startActivity(AccessibilityStatusHelper.createAccessibilitySettingsIntent())
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(
                    this,
                    R.string.error_open_accessibility_settings,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnOnboardingOpenOverlay.setOnClickListener {
            try {
                overlayPermissionLauncher.launch(
                    OverlayPermissionHelper.createOverlayPermissionIntent(this)
                )
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(
                    this,
                    R.string.error_open_overlay_settings,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnOnboardingAllowNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentStep > 0) {
                        currentStep--
                        renderStep()
                    } else {
                        completeOnboarding()
                    }
                }
            }
        )
    }

    private fun renderStep() {
        binding.viewFlipperOnboarding.displayedChild = currentStep
        binding.tvOnboardingStepIndicator.text = getString(
            R.string.onboarding_step_indicator_format,
            currentStep + 1
        )

        val activeColor = ContextCompat.getColor(this, R.color.md_theme_secondary)
        val inactiveColor = ContextCompat.getColor(this, R.color.md_theme_outlineVariant)
        binding.viewIndicatorStep1.setBackgroundColor(if (currentStep >= 0) activeColor else inactiveColor)
        binding.viewIndicatorStep2.setBackgroundColor(if (currentStep >= 1) activeColor else inactiveColor)
        binding.viewIndicatorStep3.setBackgroundColor(if (currentStep >= 2) activeColor else inactiveColor)

        binding.btnOnboardingBack.visibility = if (currentStep == 0) View.INVISIBLE else View.VISIBLE
        binding.btnOnboardingSkip.visibility = if (currentStep == TOTAL_STEPS - 1) View.INVISIBLE else View.VISIBLE
        binding.btnOnboardingNext.setText(
            if (currentStep == TOTAL_STEPS - 1) {
                R.string.action_onboarding_finish
            } else {
                R.string.action_onboarding_next
            }
        )
    }

    private fun refreshPermissionBadges() {
        val accessibilityActive = AccessibilityStatusHelper.isServiceEnabled(this)
        styleStatusCard(
            granted = accessibilityActive,
            card = binding.cardOnboardingAccessibilityStatus,
            icon = binding.ivOnboardingAccessibilityBadge,
            label = binding.tvOnboardingAccessibilityStatus,
            okTextRes = R.string.onboarding_step2_status_ok,
            pendingTextRes = R.string.onboarding_step2_status_pending,
            pendingIconRes = R.drawable.ic_accessibility
        )

        val overlayGranted = OverlayPermissionHelper.hasOverlayPermission(this)
        styleStatusCard(
            granted = overlayGranted,
            card = binding.cardOnboardingOverlayStatus,
            icon = binding.ivOnboardingOverlayBadge,
            label = binding.tvOnboardingOverlayStatus,
            okTextRes = R.string.onboarding_step3_status_ok,
            pendingTextRes = R.string.onboarding_step3_status_pending,
            pendingIconRes = R.drawable.ic_layers
        )

        val showNotifSection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !MonitoringNotificationHelper.hasNotificationPermission(this)
        binding.tvOnboardingNotificationHint.visibility = if (showNotifSection) View.VISIBLE else View.GONE
        binding.btnOnboardingAllowNotifications.visibility = if (showNotifSection) View.VISIBLE else View.GONE
    }

    private fun styleStatusCard(
        granted: Boolean,
        card: com.google.android.material.card.MaterialCardView,
        icon: android.widget.ImageView,
        label: android.widget.TextView,
        okTextRes: Int,
        pendingTextRes: Int,
        pendingIconRes: Int
    ) {
        val bgRes = if (granted) R.color.verdict_accept_bg else R.color.verdict_caution_bg
        val accentRes = if (granted) R.color.verdict_accept_accent else R.color.verdict_caution_accent
        val textRes = if (granted) R.color.verdict_accept_text else R.color.verdict_caution_text

        val textColor = ContextCompat.getColor(this, textRes)
        card.setCardBackgroundColor(ContextCompat.getColor(this, bgRes))
        card.strokeColor = ContextCompat.getColor(this, accentRes)
        icon.setImageResource(if (granted) R.drawable.ic_check_circle else pendingIconRes)
        icon.setColorFilter(textColor)
        label.setTextColor(textColor)
        label.setText(if (granted) okTextRes else pendingTextRes)
    }

    private fun completeOnboarding() {
        lifecycleScope.launch {
            runCatching {
                settingsRepository.setOnboardingCompleted(true)
            }
            val launchMain = intent.getBooleanExtra(EXTRA_LAUNCH_MAIN_ON_FINISH, true)
            if (launchMain) {
                startActivity(
                    Intent(this@OnboardingActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
            }
            finish()
        }
    }
}
