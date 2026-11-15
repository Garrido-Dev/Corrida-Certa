package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.data.SettingsRepository
import com.example.databinding.ActivityDiagnosticBinding
import com.example.engine.RideAnalyzer
import com.example.model.DriverSettings
import com.example.model.PaymentMethod
import com.example.model.RideAlertType
import com.example.model.RideVerdict
import com.example.parser.RideParserRegistry
import com.example.parser.SupportedApp
import com.example.service.DiagnosticCaptureStatus
import com.example.service.RideMonitorState
import com.example.service.VoiceAlertHelper
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela de diagnóstico offline para inspecionar o status da última leitura do serviço de acessibilidade
 * e testar em tempo real como UberParser, App99Parser e InDriveParser interpretam textos de ofertas.
 */
class DiagnosticActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, DiagnosticActivity::class.java)
        }
    }

    private lateinit var binding: ActivityDiagnosticBinding
    private val parserRegistry = RideParserRegistry()
    private val rideAnalyzer = RideAnalyzer()
    private var selectedApp: SupportedApp = SupportedApp.UBER
    private var currentSettings: DriverSettings = DriverSettings()
    private var voiceAlertHelper: VoiceAlertHelper? = null

    private val currencyFormat: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
    }
    private val timeFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("HH:mm:ss", Locale.forLanguageTag("pt-BR"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDiagnosticBinding.inflate(layoutInflater)
        setContentView(binding.root)

        voiceAlertHelper = VoiceAlertHelper(applicationContext) {
            runOnUiThread {
                Toast.makeText(this, R.string.diagnostic_voice_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
        applyEdgeToEdgeInsets()
        setupListeners()
        observeState()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceAlertHelper?.shutdown()
        voiceAlertHelper = null
    }

    private fun applyEdgeToEdgeInsets() {
        val basePadding = resources.getDimensionPixelSize(R.dimen.spacing_medium_fallback)
        ViewCompat.setOnApplyWindowInsetsListener(binding.diagnosticContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }
        binding.diagnosticScrollContent.setPadding(basePadding, basePadding, basePadding, basePadding)
    }

    private fun setupListeners() {
        binding.toolbarDiagnostic.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.toggleDiagnosticApp.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedApp = when (checkedId) {
                R.id.btnDiag99 -> SupportedApp.APP_99
                R.id.btnDiagInDrive -> SupportedApp.INDRIVE
                else -> SupportedApp.UBER
            }
        }

        binding.btnDiagLoadSample.setOnClickListener {
            binding.etDiagnosticRawText.setText(sampleTextFor(selectedApp))
            runParserTest()
        }

        binding.btnDiagTestVoice.setOnClickListener {
            voiceAlertHelper?.speakText(getString(R.string.tts_test_phrase))
        }

        binding.btnDiagClear.setOnClickListener {
            binding.etDiagnosticRawText.text?.clear()
            resetResultCard()
        }

        binding.btnDiagRunTest.setOnClickListener {
            runParserTest()
        }
    }

    private fun observeState() {
        val settingsRepository = SettingsRepository(applicationContext)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    settingsRepository.settingsFlow.collect { settings ->
                        currentSettings = settings
                    }
                }
                launch {
                    RideMonitorState.diagnosticStatus.collect { status ->
                        renderLiveStatus(status)
                    }
                }
            }
        }
    }

    private fun renderLiveStatus(status: DiagnosticCaptureStatus?) {
        if (status == null) {
            binding.tvDiagnosticLiveStatus.setText(R.string.diagnostic_live_status_empty)
            return
        }
        val appLabel = status.app?.displayName ?: status.packageName
        val timeText = timeFormat.format(Date(status.updatedAtMillis))
        val outcome = if (status.parsedSuccessfully) {
            status.summary ?: getString(R.string.diagnostic_live_ok)
        } else {
            getString(R.string.diagnostic_live_miss)
        }
        binding.tvDiagnosticLiveStatus.text = getString(
            R.string.diagnostic_live_status_format,
            appLabel,
            status.packageName,
            outcome,
            timeText
        )
    }

    private fun runParserTest() {
        val raw = binding.etDiagnosticRawText.text?.toString().orEmpty()
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            resetResultCard()
            return
        }

        val targetPackage = selectedApp.packageNames.first()
        val parsed = parserRegistry.parse(targetPackage, lines)
        if (parsed == null) {
            styleResultCard(
                bgRes = R.color.verdict_reject_bg,
                accentRes = R.color.verdict_reject_accent,
                textRes = R.color.verdict_reject_text
            )
            binding.tvDiagnosticResult.text = getString(
                R.string.diagnostic_result_failed,
                selectedApp.displayName
            )
            return
        }

        val analysis = rideAnalyzer.analyze(parsed.offer, currentSettings)
        val (bgRes, accentRes, textRes, verdictLabel) = when (analysis.verdict) {
            RideVerdict.ACCEPT -> QuadResultStyle(
                R.color.verdict_accept_bg,
                R.color.verdict_accept_accent,
                R.color.verdict_accept_text,
                getString(R.string.overlay_verdict_accept)
            )
            RideVerdict.CAUTION -> QuadResultStyle(
                R.color.verdict_caution_bg,
                R.color.verdict_caution_accent,
                R.color.verdict_caution_text,
                getString(R.string.overlay_verdict_caution)
            )
            RideVerdict.REJECT -> QuadResultStyle(
                R.color.verdict_reject_bg,
                R.color.verdict_reject_accent,
                R.color.verdict_reject_text,
                getString(R.string.overlay_verdict_reject)
            )
        }

        styleResultCard(bgRes, accentRes, textRes)
        val baseText = getString(
            R.string.diagnostic_result_success_format,
            parsed.app.displayName,
            verdictLabel,
            currencyFormat.format(parsed.offer.fare),
            parsed.offer.pickupKm,
            parsed.offer.pickupMin,
            parsed.offer.tripKm,
            parsed.offer.tripMin,
            currencyFormat.format(analysis.grossPerKm),
            currencyFormat.format(analysis.grossPerHour),
            currencyFormat.format(analysis.profit)
        )
        val ratingText = parsed.offer.passengerRating?.let {
            getString(R.string.badge_rating_format, it)
        } ?: getString(R.string.diagnostic_meta_none)
        val paymentText = when (parsed.offer.paymentMethod) {
            PaymentMethod.CASH -> getString(R.string.alert_cash_payment)
            PaymentMethod.DIGITAL -> getString(R.string.badge_digital_payment)
            PaymentMethod.UNKNOWN -> getString(R.string.diagnostic_meta_none)
        }
        val stopText = if (parsed.offer.hasExtraStop) {
            getString(R.string.diagnostic_yes)
        } else {
            getString(R.string.diagnostic_no)
        }
        val routeText = parsed.offer.routeSummary ?: getString(R.string.diagnostic_meta_none)
        val alertsText = if (analysis.alerts.isEmpty()) {
            getString(R.string.diagnostic_meta_none)
        } else {
            analysis.alerts.joinToString(", ") { alert ->
                when (alert) {
                    RideAlertType.BLOCKED_AREA -> getString(R.string.alert_blocked_area)
                    RideAlertType.BELOW_MIN_FARE -> getString(R.string.alert_below_min_fare)
                    RideAlertType.LONG_PICKUP -> getString(R.string.alert_long_pickup)
                    RideAlertType.LOW_RATING -> getString(R.string.alert_low_rating)
                    RideAlertType.CASH_PAYMENT -> getString(R.string.alert_cash_payment)
                    RideAlertType.EXTRA_STOP -> getString(R.string.alert_extra_stop)
                }
            }
        }
        val metaText = getString(
            R.string.diagnostic_meta_format,
            ratingText,
            paymentText,
            stopText,
            routeText,
            alertsText
        )
        binding.tvDiagnosticResult.text = baseText + metaText
    }

    private fun resetResultCard() {
        styleResultCard(
            bgRes = R.color.verdict_idle_bg,
            accentRes = R.color.verdict_idle_accent,
            textRes = R.color.verdict_idle_text
        )
        binding.tvDiagnosticResult.setText(R.string.diagnostic_result_empty)
    }

    private fun styleResultCard(bgRes: Int, accentRes: Int, textRes: Int) {
        binding.cardDiagnosticResult.setCardBackgroundColor(ContextCompat.getColor(this, bgRes))
        binding.cardDiagnosticResult.strokeColor = ContextCompat.getColor(this, accentRes)
        binding.tvDiagnosticResult.setTextColor(ContextCompat.getColor(this, textRes))
    }

    private fun sampleTextFor(app: SupportedApp): String = when (app) {
        SupportedApp.UBER -> "UberX\nR$ 24,50\n★ 4,88\n4 min (1,2 km) de distância\nRua Augusta, 1200 - Consolação\n16 min (8,5 km) de viagem\nAv. Paulista, 900 - Bela Vista"
        SupportedApp.APP_99 -> "99Pop\nR$ 19,80\n★ 4,62\nDinheiro • Com parada\n3 min (0,9 km)\nAv. Brasil, Centro\n14 min (6,8 km)"
        SupportedApp.INDRIVE -> "R$ 28,00\n4,91 ★\n2,1 km até você • 5 min\n9,4 km viagem • 18 min\nBairro Pinheiros"
    }

    private data class QuadResultStyle(
        val bgRes: Int,
        val accentRes: Int,
        val textRes: Int,
        val verdictLabel: String
    )
}
