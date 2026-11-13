package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.SettingsRepository
import com.example.model.DriverSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Eventos pontuais emitidos pela [SettingsViewModel] após ações de persistência.
 */
sealed interface SettingsEvent {
    data object Saved : SettingsEvent
    data class ResetCompleted(val defaults: DriverSettings) : SettingsEvent
    data class Error(val messageRes: Int) : SettingsEvent
}

/**
 * ViewModel responsável por gerenciar a edição e persistência de custos do veículo e metas no DataStore.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    initialSection: SettingsSection = SettingsSection.VEHICLE_COSTS
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(activeSection = initialSection))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val saved = settingsRepository.settingsFlow.first()
            _uiState.update {
                it.copy(
                    previewSettings = saved,
                    isLoaded = true
                )
            }
        }
    }

    fun selectSection(section: SettingsSection) {
        _uiState.update { it.copy(activeSection = section) }
    }

    /**
     * Recalcula em tempo real o custo derivado por km enquanto o motorista digita.
     */
    fun onInputsChanged(
        fuelPriceText: String,
        consumptionText: String,
        maintenanceText: String,
        depreciationText: String,
        targetPerKmText: String,
        targetPerHourText: String,
        dailyGoalText: String? = null
    ) {
        val current = _uiState.value.previewSettings
        val updated = DriverSettings(
            fuelPrice = parseBrazilianDouble(fuelPriceText) ?: current.fuelPrice,
            consumption = parseBrazilianDouble(consumptionText) ?: current.consumption,
            maintenance = parseBrazilianDouble(maintenanceText) ?: current.maintenance,
            depreciation = parseBrazilianDouble(depreciationText) ?: current.depreciation,
            targetPerKm = parseBrazilianDouble(targetPerKmText) ?: current.targetPerKm,
            targetPerHour = parseBrazilianDouble(targetPerHourText) ?: current.targetPerHour,
            dailyGoal = dailyGoalText?.let(::parseBrazilianDouble) ?: current.dailyGoal
        )
        _uiState.update { it.copy(previewSettings = updated) }
    }

    /**
     * Valida os campos de custos e metas e persiste no DataStore.
     */
    fun saveSettings(
        fuelPriceText: String,
        consumptionText: String,
        maintenanceText: String,
        depreciationText: String,
        targetPerKmText: String,
        targetPerHourText: String,
        dailyGoalText: String? = null
    ) {
        val fuelPrice = parseBrazilianDouble(fuelPriceText)
        val consumption = parseBrazilianDouble(consumptionText)
        val maintenance = parseBrazilianDouble(maintenanceText)
        val depreciation = parseBrazilianDouble(depreciationText)
        val targetPerKm = parseBrazilianDouble(targetPerKmText)
        val targetPerHour = parseBrazilianDouble(targetPerHourText)
        val dailyGoal = if (dailyGoalText == null) {
            _uiState.value.previewSettings.dailyGoal
        } else {
            parseBrazilianDouble(dailyGoalText)
        }

        val errors = SettingsFieldErrors(
            fuelPriceError = when {
                fuelPrice == null -> R.string.error_invalid_number
                fuelPrice < 0.0 -> R.string.error_cannot_be_negative
                else -> null
            },
            consumptionError = when {
                consumption == null -> R.string.error_invalid_number
                consumption <= 0.0 -> R.string.error_must_be_positive
                else -> null
            },
            maintenanceError = when {
                maintenance == null -> R.string.error_invalid_number
                maintenance < 0.0 -> R.string.error_cannot_be_negative
                else -> null
            },
            depreciationError = when {
                depreciation == null -> R.string.error_invalid_number
                depreciation < 0.0 -> R.string.error_cannot_be_negative
                else -> null
            },
            targetPerKmError = when {
                targetPerKm == null -> R.string.error_invalid_number
                targetPerKm <= 0.0 -> R.string.error_must_be_positive
                else -> null
            },
            targetPerHourError = when {
                targetPerHour == null -> R.string.error_invalid_number
                targetPerHour <= 0.0 -> R.string.error_must_be_positive
                else -> null
            },
            dailyGoalError = when {
                dailyGoal == null -> R.string.error_invalid_number
                dailyGoal <= 0.0 -> R.string.error_must_be_positive
                else -> null
            }
        )

        if (errors.hasAnyError) {
            // Muda automaticamente para a aba com erro para evitar campo inválido oculto
            val targetSection = when {
                errors.hasVehicleError && _uiState.value.activeSection != SettingsSection.VEHICLE_COSTS ->
                    SettingsSection.VEHICLE_COSTS
                errors.hasGoalsError && !errors.hasVehicleError &&
                    _uiState.value.activeSection != SettingsSection.GOALS ->
                    SettingsSection.GOALS
                else -> _uiState.value.activeSection
            }
            _uiState.update {
                it.copy(
                    activeSection = targetSection,
                    fieldErrors = errors
                )
            }
            return
        }

        val validated = DriverSettings(
            fuelPrice = fuelPrice!!,
            consumption = consumption!!,
            maintenance = maintenance!!,
            depreciation = depreciation!!,
            targetPerKm = targetPerKm!!,
            targetPerHour = targetPerHour!!,
            dailyGoal = dailyGoal!!
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, fieldErrors = SettingsFieldErrors()) }
            try {
                settingsRepository.saveSettings(validated)
                _uiState.update {
                    it.copy(
                        previewSettings = validated,
                        isSaving = false
                    )
                }
                _events.emit(SettingsEvent.Saved)
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(SettingsEvent.Error(R.string.error_saving_settings))
            }
        }
    }

    /**
     * Restaura os valores padrão no DataStore e atualiza os campos da tela.
     */
    fun resetToDefaults() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, fieldErrors = SettingsFieldErrors()) }
            try {
                settingsRepository.resetToDefaults()
                val defaults = DriverSettings()
                _uiState.update {
                    it.copy(
                        previewSettings = defaults,
                        isSaving = false
                    )
                }
                _events.emit(SettingsEvent.ResetCompleted(defaults))
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(SettingsEvent.Error(R.string.error_saving_settings))
            }
        }
    }

    private fun parseBrazilianDouble(raw: String): Double? {
        val normalized = raw.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val initialSection: SettingsSection
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                settingsRepository = settingsRepository,
                initialSection = initialSection
            ) as T
        }
    }
}
