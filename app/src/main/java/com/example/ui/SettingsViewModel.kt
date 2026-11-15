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
        dailyGoalText: String? = null,
        dailyFixedCostText: String? = null,
        minFareText: String? = null,
        maxPickupKmText: String? = null,
        minPassengerRatingText: String? = null,
        blockedAreasText: String? = null,
        voiceAlertEnabled: Boolean? = null,
        alertCashRide: Boolean? = null,
        alertExtraStop: Boolean? = null
    ) {
        val current = _uiState.value.previewSettings
        val updated = current.copy(
            fuelPrice = parseBrazilianDouble(fuelPriceText) ?: current.fuelPrice,
            consumption = parseBrazilianDouble(consumptionText) ?: current.consumption,
            maintenance = parseBrazilianDouble(maintenanceText) ?: current.maintenance,
            depreciation = parseBrazilianDouble(depreciationText) ?: current.depreciation,
            targetPerKm = parseBrazilianDouble(targetPerKmText) ?: current.targetPerKm,
            targetPerHour = parseBrazilianDouble(targetPerHourText) ?: current.targetPerHour,
            dailyGoal = dailyGoalText?.let(::parseBrazilianDouble) ?: current.dailyGoal,
            dailyFixedCost = dailyFixedCostText?.let(::parseBrazilianDouble) ?: current.dailyFixedCost,
            minFare = minFareText?.let(::parseBrazilianDouble) ?: current.minFare,
            maxPickupKm = maxPickupKmText?.let(::parseBrazilianDouble) ?: current.maxPickupKm,
            minPassengerRating = minPassengerRatingText?.let(::parseBrazilianDouble)
                ?: current.minPassengerRating,
            blockedAreas = blockedAreasText ?: current.blockedAreas,
            voiceAlertEnabled = voiceAlertEnabled ?: current.voiceAlertEnabled,
            alertCashRide = alertCashRide ?: current.alertCashRide,
            alertExtraStop = alertExtraStop ?: current.alertExtraStop
        )
        _uiState.update { it.copy(previewSettings = updated) }
    }

    /**
     * Valida os campos de custos, metas e filtros e persiste no DataStore.
     */
    fun saveSettings(
        fuelPriceText: String,
        consumptionText: String,
        maintenanceText: String,
        depreciationText: String,
        targetPerKmText: String,
        targetPerHourText: String,
        dailyGoalText: String? = null,
        dailyFixedCostText: String? = null,
        minFareText: String? = null,
        maxPickupKmText: String? = null,
        minPassengerRatingText: String? = null,
        blockedAreasText: String? = null,
        voiceAlertEnabled: Boolean? = null,
        alertCashRide: Boolean? = null,
        alertExtraStop: Boolean? = null
    ) {
        val current = _uiState.value.previewSettings
        val fuelPrice = parseBrazilianDouble(fuelPriceText)
        val consumption = parseBrazilianDouble(consumptionText)
        val maintenance = parseBrazilianDouble(maintenanceText)
        val depreciation = parseBrazilianDouble(depreciationText)
        val targetPerKm = parseBrazilianDouble(targetPerKmText)
        val targetPerHour = parseBrazilianDouble(targetPerHourText)
        val dailyGoal = if (dailyGoalText == null) {
            current.dailyGoal
        } else {
            parseBrazilianDouble(dailyGoalText)
        }
        val dailyFixedCost = if (dailyFixedCostText == null) {
            current.dailyFixedCost
        } else {
            parseOptionalZeroOrDouble(dailyFixedCostText)
        }
        val minFare = if (minFareText == null) {
            current.minFare
        } else {
            parseOptionalZeroOrDouble(minFareText)
        }
        val maxPickupKm = if (maxPickupKmText == null) {
            current.maxPickupKm
        } else {
            parseOptionalZeroOrDouble(maxPickupKmText)
        }
        val minPassengerRating = if (minPassengerRatingText == null) {
            current.minPassengerRating
        } else {
            parseOptionalZeroOrDouble(minPassengerRatingText)
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
            dailyFixedCostError = when {
                dailyFixedCost == null -> R.string.error_invalid_number
                dailyFixedCost < 0.0 -> R.string.error_cannot_be_negative
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
            },
            minFareError = when {
                minFare == null -> R.string.error_invalid_number
                minFare < 0.0 -> R.string.error_cannot_be_negative
                else -> null
            },
            maxPickupKmError = when {
                maxPickupKm == null -> R.string.error_invalid_number
                maxPickupKm < 0.0 -> R.string.error_cannot_be_negative
                else -> null
            },
            minPassengerRatingError = when {
                minPassengerRating == null -> R.string.error_invalid_number
                minPassengerRating < 0.0 || minPassengerRating > 5.0 -> R.string.error_rating_range
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
            dailyGoal = dailyGoal!!,
            dailyFixedCost = dailyFixedCost!!,
            minFare = minFare!!,
            maxPickupKm = maxPickupKm!!,
            minPassengerRating = minPassengerRating!!,
            blockedAreas = (blockedAreasText ?: current.blockedAreas).trim(),
            voiceAlertEnabled = voiceAlertEnabled ?: current.voiceAlertEnabled,
            alertCashRide = alertCashRide ?: current.alertCashRide,
            alertExtraStop = alertExtraStop ?: current.alertExtraStop
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

    private fun parseOptionalZeroOrDouble(raw: String): Double? {
        if (raw.isBlank()) return 0.0
        return parseBrazilianDouble(raw)
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
