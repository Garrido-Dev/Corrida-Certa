package com.example.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.HistoryRepository
import com.example.data.SettingsRepository
import com.example.databinding.ActivityMainBinding
import com.example.model.RideAnalysis
import com.example.model.RideOffer
import com.example.model.RideVerdict
import com.example.service.AccessibilityStatusHelper
import com.example.service.DetectedRideAnalysis
import com.example.service.MonitoringNotificationHelper
import com.example.service.OverlayPermissionHelper
import com.example.service.OverlayService
import com.example.service.RideMonitorState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Tela principal do Rota Pro: simulador manual, status da Acessibilidade e controle do Overlay flutuante.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_FROM_OVERLAY = "extra_from_overlay"

        fun createFromOverlayIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_FROM_OVERLAY, true)
            }
        }
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsRepository: SettingsRepository
    private var isSplashCheckComplete = false

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(
            settingsRepository = settingsRepository,
            historyRepository = HistoryRepository(applicationContext)
        )
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateOverlayPermissionStatus()
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
        updateNotificationBanner()
    }

    private val currencyFormat: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !isSplashCheckComplete }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settingsRepository = SettingsRepository(applicationContext)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyEdgeToEdgeInsets()
        binding.tvPhaseBadge.text = getString(R.string.phase_badge, BuildConfig.VERSION_NAME)
        setupListeners()
        observeUiState()
        handleOverlayIntentIfPresent(intent)
        checkFirstLaunchOnboarding()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOverlayIntentIfPresent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        updateOverlayPermissionStatus()
        updateNotificationBanner()
    }

    private fun checkFirstLaunchOnboarding() {
        lifecycleScope.launch {
            val fromOverlay = intent?.getBooleanExtra(EXTRA_FROM_OVERLAY, false) == true
            val completed = runCatching {
                settingsRepository.onboardingCompletedFlow.first()
            }.getOrDefault(true)
            isSplashCheckComplete = true
            if (!fromOverlay && !completed) {
                startActivity(OnboardingActivity.createIntent(this@MainActivity, launchMainOnFinish = true))
            }
        }
    }

    private fun handleOverlayIntentIfPresent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_FROM_OVERLAY, false) != true) return
        val detected = RideMonitorState.detectedRide.value ?: return
        populateOfferFields(detected.offer)
        triggerAnalysis()
    }

    private fun applyEdgeToEdgeInsets() {
        val baseHorizontal = resources.getDimensionPixelSize(R.dimen.spacing_medium_fallback)
        ViewCompat.setOnApplyWindowInsetsListener(binding.contentContainer) { view, insets ->
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
        binding.tvPhaseBadge.setOnClickListener {
            startActivity(AboutActivity.createIntent(this))
        }
        binding.btnOpenOnboarding.setOnClickListener {
            startActivity(OnboardingActivity.createIntent(this, launchMainOnFinish = false))
        }
        binding.btnTopSettings.setOnClickListener {
            startActivity(SettingsActivity.createIntent(this, SettingsSection.VEHICLE_COSTS))
        }
        binding.btnGrantNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        binding.btnEditVehicleCosts.setOnClickListener {
            startActivity(SettingsActivity.createIntent(this, SettingsSection.VEHICLE_COSTS))
        }
        binding.btnEditGoals.setOnClickListener {
            startActivity(SettingsActivity.createIntent(this, SettingsSection.GOALS))
        }
        binding.btnOpenAccessibilityGuide.setOnClickListener {
            startActivity(AccessibilityGuideActivity.createIntent(this))
        }
        binding.btnOpenHistory.setOnClickListener {
            startActivity(HistoryActivity.createIntent(this, HistoryTab.LIST))
        }
        binding.btnOpenStats.setOnClickListener {
            startActivity(HistoryActivity.createIntent(this, HistoryTab.STATS))
        }
        binding.btnOverlayPermission.setOnClickListener {
            requestOverlayPermission()
        }
        binding.btnTestOverlay.setOnClickListener {
            previewCurrentRideInOverlay()
        }

        binding.btnAnalyze.setOnClickListener {
            triggerAnalysis()
        }

        binding.btnClear.setOnClickListener {
            binding.etFare.text?.clear()
            binding.etPickupKm.text?.clear()
            binding.etPickupMin.text?.clear()
            binding.etTripKm.text?.clear()
            binding.etTripMin.text?.clear()
            viewModel.clearAnalysis()
        }

        binding.chipPresetAccept.setOnClickListener { applyPreset(RidePreset.ACCEPT) }
        binding.chipPresetCaution.setOnClickListener { applyPreset(RidePreset.CAUTION) }
        binding.chipPresetReject.setOnClickListener { applyPreset(RidePreset.REJECT) }
        binding.chipPresetLoss.setOnClickListener { applyPreset(RidePreset.LOSS) }
    }

    private fun requestOverlayPermission() {
        try {
            overlayPermissionLauncher.launch(
                OverlayPermissionHelper.createOverlayPermissionIntent(this)
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_open_overlay_settings, Toast.LENGTH_SHORT).show()
        }
    }

    private fun previewCurrentRideInOverlay() {
        if (!OverlayPermissionHelper.hasOverlayPermission(this)) {
            Toast.makeText(this, R.string.msg_overlay_permission_required, Toast.LENGTH_SHORT).show()
            requestOverlayPermission()
            return
        }

        val allBlank = listOf(
            binding.etFare.text?.toString(),
            binding.etPickupKm.text?.toString(),
            binding.etPickupMin.text?.toString(),
            binding.etTripKm.text?.toString(),
            binding.etTripMin.text?.toString()
        ).all { it.isNullOrBlank() }

        if (allBlank) {
            applyPreset(RidePreset.ACCEPT)
        } else {
            triggerAnalysis()
            val errors = viewModel.uiState.value.fieldErrors
            val hasFieldError = listOf(
                errors.fareError,
                errors.pickupKmError,
                errors.pickupMinError,
                errors.tripKmError,
                errors.tripMinError
            ).any { it != null }
            if (hasFieldError) {
                Toast.makeText(this, R.string.msg_fill_valid_ride_for_overlay, Toast.LENGTH_SHORT).show()
                return
            }
        }

        val finalAnalysis = viewModel.uiState.value.analysis ?: return
        val offer = buildOfferFromInputsOrPreset()

        RideMonitorState.updateDetectedRide(
            DetectedRideAnalysis(
                app = null,
                offer = offer,
                analysis = finalAnalysis
            )
        )
        OverlayService.startOrUpdate(this)
    }

    private fun buildOfferFromInputsOrPreset(): RideOffer {
        fun parse(text: String?): Double? = text?.trim()?.replace(',', '.')?.toDoubleOrNull()
        val fare = parse(binding.etFare.text?.toString())
        val pickupKm = parse(binding.etPickupKm.text?.toString())
        val pickupMin = parse(binding.etPickupMin.text?.toString())
        val tripKm = parse(binding.etTripKm.text?.toString())
        val tripMin = parse(binding.etTripMin.text?.toString())
        return if (fare != null && pickupKm != null && pickupMin != null && tripKm != null && tripMin != null) {
            RideOffer(fare, pickupKm, pickupMin, tripKm, tripMin)
        } else {
            viewModel.getPresetOffer(RidePreset.ACCEPT)
        }
    }

    private fun applyPreset(preset: RidePreset) {
        val offer = viewModel.getPresetOffer(preset)
        populateOfferFields(offer)
        triggerAnalysis()
    }

    private fun populateOfferFields(offer: RideOffer) {
        binding.etFare.setText(String.format(Locale.US, "%.2f", offer.fare))
        binding.etPickupKm.setText(String.format(Locale.US, "%.1f", offer.pickupKm))
        binding.etPickupMin.setText(String.format(Locale.US, "%.0f", offer.pickupMin))
        binding.etTripKm.setText(String.format(Locale.US, "%.1f", offer.tripKm))
        binding.etTripMin.setText(String.format(Locale.US, "%.0f", offer.tripMin))
    }

    private fun triggerAnalysis() {
        viewModel.analyzeManualInput(
            fareText = binding.etFare.text?.toString().orEmpty(),
            pickupKmText = binding.etPickupKm.text?.toString().orEmpty(),
            pickupMinText = binding.etPickupMin.text?.toString().orEmpty(),
            tripKmText = binding.etTripKm.text?.toString().orEmpty(),
            tripMinText = binding.etTripMin.text?.toString().orEmpty(),
            sourceAppName = getString(R.string.history_simulator_app_name)
        )
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.tvCostPerKmInfo.text = getString(
                            R.string.settings_cost_per_km_format,
                            currencyFormat.format(state.settings.costPerKm)
                        )
                        binding.tvTargetsSummaryInfo.text = getString(
                            R.string.settings_targets_summary_format,
                            currencyFormat.format(state.settings.targetPerKm),
                            currencyFormat.format(state.settings.targetPerHour)
                        )
                        renderDailyGoal(state.dailyGoalProgress)
                        renderFieldErrors(state.fieldErrors)
                        renderAnalysis(state.analysis)
                    }
                }
                launch {
                    RideMonitorState.isServiceConnected.collect {
                        updateAccessibilityStatus()
                    }
                }
            }
        }
    }

    private fun updateAccessibilityStatus() {
        val isActive = AccessibilityStatusHelper.isServiceEnabled(this)
        val accentRes = if (isActive) R.color.verdict_accept_accent else R.color.verdict_caution_accent
        val accentColor = ContextCompat.getColor(this, accentRes)
        binding.iconMainAccessibilityContainer.setBackgroundColor(accentColor)
        binding.tvMainAccessibilityStatus.setTextColor(accentColor)
        binding.tvMainAccessibilityStatus.setText(
            if (isActive) R.string.accessibility_status_active else R.string.accessibility_status_inactive
        )
        if (isActive && MonitoringNotificationHelper.hasNotificationPermission(this)) {
            MonitoringNotificationHelper.showMonitoringNotification(this)
        }
    }

    private fun updateNotificationBanner() {
        val needsNotifPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !MonitoringNotificationHelper.hasNotificationPermission(this)
        binding.cardNotificationPermission.visibility = if (needsNotifPermission) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun updateOverlayPermissionStatus() {
        val granted = OverlayPermissionHelper.hasOverlayPermission(this)
        val accentRes = if (granted) R.color.verdict_accept_accent else R.color.verdict_caution_accent
        val accentColor = ContextCompat.getColor(this, accentRes)
        binding.iconMainOverlayContainer.setBackgroundColor(accentColor)
        binding.tvMainOverlayStatus.setTextColor(accentColor)
        binding.tvMainOverlayStatus.setText(
            if (granted) R.string.overlay_status_granted else R.string.overlay_status_denied
        )
    }

    private fun renderDailyGoal(progress: com.example.engine.DailyGoalProgress) {
        binding.tvDailyGoalProgress.text = getString(
            R.string.daily_goal_progress_format,
            currencyFormat.format(progress.todayProfit),
            currencyFormat.format(progress.dailyGoal),
            progress.progressPercent
        )
        binding.progressDailyGoal.progress = progress.progressPercent
        val indicatorColor = ContextCompat.getColor(
            this,
            if (progress.isGoalReached) R.color.verdict_accept_accent else R.color.md_theme_secondary
        )
        binding.progressDailyGoal.setIndicatorColor(indicatorColor)
        binding.tvDailyGoalProgress.setTextColor(indicatorColor)

        binding.tvDailyGoalSubtitle.text = if (progress.isGoalReached) {
            getString(R.string.daily_goal_reached)
        } else {
            getString(
                R.string.daily_goal_remaining_format,
                currencyFormat.format(progress.remainingAmount)
            )
        }
    }

    private fun renderFieldErrors(errors: FormFieldErrors) {
        binding.tilFare.error = errors.fareError?.let(::getString)
        binding.tilPickupKm.error = errors.pickupKmError?.let(::getString)
        binding.tilPickupMin.error = errors.pickupMinError?.let(::getString)
        binding.tilTripKm.error = errors.tripKmError?.let(::getString)
        binding.tilTripMin.error = errors.tripMinError?.let(::getString)
    }

    private fun renderAnalysis(analysis: RideAnalysis?) {
        if (analysis == null) {
            applyVerdictTheme(
                bgColorRes = R.color.verdict_idle_bg,
                accentColorRes = R.color.verdict_idle_accent,
                textColorRes = R.color.verdict_idle_text,
                iconRes = R.drawable.ic_traffic_idle,
                title = getString(R.string.verdict_idle_title),
                description = getString(R.string.verdict_idle_desc)
            )
            val placeholder = getString(R.string.metric_placeholder)
            binding.tvGrossPerKm.text = placeholder
            binding.tvGrossPerHour.text = placeholder
            binding.tvProfit.text = placeholder
            binding.tvNetPerKm.text = placeholder
            binding.tvSummaryDetails.visibility = View.GONE
            return
        }

        when (analysis.verdict) {
            RideVerdict.ACCEPT -> applyVerdictTheme(
                bgColorRes = R.color.verdict_accept_bg,
                accentColorRes = R.color.verdict_accept_accent,
                textColorRes = R.color.verdict_accept_text,
                iconRes = R.drawable.ic_traffic_accept,
                title = getString(R.string.verdict_accept_title),
                description = getString(R.string.verdict_accept_desc)
            )
            RideVerdict.CAUTION -> applyVerdictTheme(
                bgColorRes = R.color.verdict_caution_bg,
                accentColorRes = R.color.verdict_caution_accent,
                textColorRes = R.color.verdict_caution_text,
                iconRes = R.drawable.ic_traffic_caution,
                title = getString(R.string.verdict_caution_title),
                description = getString(R.string.verdict_caution_desc)
            )
            RideVerdict.REJECT -> {
                val descRes = if (analysis.profit <= 0.0) {
                    R.string.verdict_loss_desc
                } else {
                    R.string.verdict_reject_desc
                }
                applyVerdictTheme(
                    bgColorRes = R.color.verdict_reject_bg,
                    accentColorRes = R.color.verdict_reject_accent,
                    textColorRes = R.color.verdict_reject_text,
                    iconRes = R.drawable.ic_traffic_reject,
                    title = getString(R.string.verdict_reject_title),
                    description = getString(descRes)
                )
            }
        }

        binding.tvGrossPerKm.text = currencyFormat.format(analysis.grossPerKm)
        binding.tvGrossPerHour.text = currencyFormat.format(analysis.grossPerHour)
        binding.tvProfit.text = currencyFormat.format(analysis.profit)
        binding.tvNetPerKm.text = currencyFormat.format(analysis.netPerKm)
        binding.tvSummaryDetails.visibility = View.VISIBLE
        binding.tvSummaryDetails.text = getString(
            R.string.metric_summary_format,
            analysis.totalKm,
            analysis.totalMin,
            currencyFormat.format(analysis.totalCost)
        )
    }

    private fun applyVerdictTheme(
        bgColorRes: Int,
        accentColorRes: Int,
        textColorRes: Int,
        iconRes: Int,
        title: String,
        description: String
    ) {
        val bgColor = ContextCompat.getColor(this, bgColorRes)
        val accentColor = ContextCompat.getColor(this, accentColorRes)
        val textColor = ContextCompat.getColor(this, textColorRes)

        binding.cardVerdict.setCardBackgroundColor(bgColor)
        binding.cardVerdict.strokeColor = accentColor
        binding.iconVerdictContainer.setBackgroundColor(accentColor)
        binding.ivVerdictIcon.setImageResource(iconRes)

        binding.tvVerdictTitle.text = title
        binding.tvVerdictTitle.setTextColor(textColor)
        binding.tvVerdictDescription.text = description
        binding.tvVerdictDescription.setTextColor(textColor)

        binding.tvGrossPerKm.setTextColor(textColor)
        binding.tvGrossPerHour.setTextColor(textColor)
        binding.tvProfit.setTextColor(textColor)
        binding.tvNetPerKm.setTextColor(textColor)
        binding.tvSummaryDetails.setTextColor(textColor)
    }
}
