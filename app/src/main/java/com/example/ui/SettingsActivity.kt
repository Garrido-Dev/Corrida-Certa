package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.data.SettingsRepository
import com.example.databinding.ActivitySettingsBinding
import com.example.model.DriverSettings
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Tela de configuração persistente de custos do veículo e metas financeiras (DataStore).
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_INITIAL_SECTION = "extra_initial_section"

        fun createIntent(
            context: Context,
            initialSection: SettingsSection = SettingsSection.VEHICLE_COSTS
        ): Intent {
            return Intent(context, SettingsActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_SECTION, initialSection.name)
            }
        }
    }

    private lateinit var binding: ActivitySettingsBinding

    private val viewModel: SettingsViewModel by viewModels {
        val sectionName = intent.getStringExtra(EXTRA_INITIAL_SECTION)
        val initialSection = sectionName?.let {
            runCatching { SettingsSection.valueOf(it) }.getOrNull()
        } ?: SettingsSection.VEHICLE_COSTS

        SettingsViewModel.Factory(
            settingsRepository = SettingsRepository(applicationContext),
            initialSection = initialSection
        )
    }

    private val currencyFormat: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    }

    private var hasPopulatedInitialInputs = false
    private var isUpdatingFieldsProgrammatically = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyEdgeToEdgeInsets()
        setupToolbar()
        setupListeners()
        observeViewModel()
    }

    private fun applyEdgeToEdgeInsets() {
        val basePadding = resources.getDimensionPixelSize(R.dimen.spacing_medium_fallback)
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }
        binding.settingsScrollContent.setPadding(
            basePadding,
            basePadding,
            basePadding,
            basePadding
        )
    }

    private fun setupToolbar() {
        binding.toolbarSettings.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupListeners() {
        binding.toggleSettingsSection.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnTabVehicle -> viewModel.selectSection(SettingsSection.VEHICLE_COSTS)
                R.id.btnTabGoals -> viewModel.selectSection(SettingsSection.GOALS)
            }
        }

        val inputWatcher = {
            if (!isUpdatingFieldsProgrammatically) {
                viewModel.onInputsChanged(
                    fuelPriceText = binding.etFuelPrice.text?.toString().orEmpty(),
                    consumptionText = binding.etConsumption.text?.toString().orEmpty(),
                    maintenanceText = binding.etMaintenance.text?.toString().orEmpty(),
                    depreciationText = binding.etDepreciation.text?.toString().orEmpty(),
                    targetPerKmText = binding.etTargetPerKm.text?.toString().orEmpty(),
                    targetPerHourText = binding.etTargetPerHour.text?.toString().orEmpty()
                )
            }
        }

        binding.etFuelPrice.doAfterTextChanged { inputWatcher() }
        binding.etConsumption.doAfterTextChanged { inputWatcher() }
        binding.etMaintenance.doAfterTextChanged { inputWatcher() }
        binding.etDepreciation.doAfterTextChanged { inputWatcher() }
        binding.etTargetPerKm.doAfterTextChanged { inputWatcher() }
        binding.etTargetPerHour.doAfterTextChanged { inputWatcher() }

        binding.btnSaveSettings.setOnClickListener {
            viewModel.saveSettings(
                fuelPriceText = binding.etFuelPrice.text?.toString().orEmpty(),
                consumptionText = binding.etConsumption.text?.toString().orEmpty(),
                maintenanceText = binding.etMaintenance.text?.toString().orEmpty(),
                depreciationText = binding.etDepreciation.text?.toString().orEmpty(),
                targetPerKmText = binding.etTargetPerKm.text?.toString().orEmpty(),
                targetPerHourText = binding.etTargetPerHour.text?.toString().orEmpty()
            )
        }

        binding.btnResetDefaults.setOnClickListener {
            viewModel.resetToDefaults()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (state.isLoaded && !hasPopulatedInitialInputs) {
                            populateFields(state.previewSettings)
                            hasPopulatedInitialInputs = true
                        }
                        renderSection(state.activeSection)
                        renderPreview(state.previewSettings)
                        renderErrors(state.fieldErrors)
                        binding.btnSaveSettings.isEnabled = !state.isSaving
                        binding.btnResetDefaults.isEnabled = !state.isSaving
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            SettingsEvent.Saved -> {
                                Toast.makeText(
                                    this@SettingsActivity,
                                    R.string.msg_settings_saved,
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            }
                            is SettingsEvent.ResetCompleted -> {
                                populateFields(event.defaults)
                                Toast.makeText(
                                    this@SettingsActivity,
                                    R.string.msg_settings_reset,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is SettingsEvent.Error -> {
                                Toast.makeText(
                                    this@SettingsActivity,
                                    event.messageRes,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun populateFields(settings: DriverSettings) {
        isUpdatingFieldsProgrammatically = true
        binding.etFuelPrice.setText(String.format(Locale.US, "%.2f", settings.fuelPrice))
        binding.etConsumption.setText(String.format(Locale.US, "%.1f", settings.consumption))
        binding.etMaintenance.setText(String.format(Locale.US, "%.2f", settings.maintenance))
        binding.etDepreciation.setText(String.format(Locale.US, "%.2f", settings.depreciation))
        binding.etTargetPerKm.setText(String.format(Locale.US, "%.2f", settings.targetPerKm))
        binding.etTargetPerHour.setText(String.format(Locale.US, "%.2f", settings.targetPerHour))
        isUpdatingFieldsProgrammatically = false
    }

    private fun renderSection(section: SettingsSection) {
        when (section) {
            SettingsSection.VEHICLE_COSTS -> {
                if (binding.toggleSettingsSection.checkedButtonId != R.id.btnTabVehicle) {
                    binding.toggleSettingsSection.check(R.id.btnTabVehicle)
                }
                binding.cardVehicleCosts.visibility = View.VISIBLE
                binding.containerGoals.visibility = View.GONE
            }
            SettingsSection.GOALS -> {
                if (binding.toggleSettingsSection.checkedButtonId != R.id.btnTabGoals) {
                    binding.toggleSettingsSection.check(R.id.btnTabGoals)
                }
                binding.cardVehicleCosts.visibility = View.GONE
                binding.containerGoals.visibility = View.VISIBLE
            }
        }
    }

    private fun renderPreview(settings: DriverSettings) {
        val fuelCostPerKm = if (settings.consumption > 0.0) {
            settings.fuelPrice / settings.consumption
        } else {
            0.0
        }
        binding.tvPreviewCostPerKm.text = getString(
            R.string.settings_cost_per_km_format,
            currencyFormat.format(settings.costPerKm)
        )
        binding.tvPreviewFormula.text = getString(
            R.string.settings_preview_formula_format,
            currencyFormat.format(fuelCostPerKm),
            currencyFormat.format(settings.maintenance),
            currencyFormat.format(settings.depreciation)
        )
        binding.tvPreviewTargets.text = getString(
            R.string.settings_preview_targets_format,
            currencyFormat.format(settings.targetPerKm),
            currencyFormat.format(settings.targetPerHour)
        )
    }

    private fun renderErrors(errors: SettingsFieldErrors) {
        binding.tilFuelPrice.error = errors.fuelPriceError?.let(::getString)
        binding.tilConsumption.error = errors.consumptionError?.let(::getString)
        binding.tilMaintenance.error = errors.maintenanceError?.let(::getString)
        binding.tilDepreciation.error = errors.depreciationError?.let(::getString)
        binding.tilTargetPerKm.error = errors.targetPerKmError?.let(::getString)
        binding.tilTargetPerHour.error = errors.targetPerHourError?.let(::getString)
    }
}
