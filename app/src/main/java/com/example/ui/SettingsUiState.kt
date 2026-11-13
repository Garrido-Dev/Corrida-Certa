package com.example.ui

import androidx.annotation.StringRes
import com.example.model.DriverSettings

/**
 * Seções disponíveis na tela de configurações.
 */
enum class SettingsSection {
    VEHICLE_COSTS,
    GOALS
}

/**
 * Erros de validação específicos dos campos de configuração.
 */
data class SettingsFieldErrors(
    @param:StringRes val fuelPriceError: Int? = null,
    @param:StringRes val consumptionError: Int? = null,
    @param:StringRes val maintenanceError: Int? = null,
    @param:StringRes val depreciationError: Int? = null,
    @param:StringRes val targetPerKmError: Int? = null,
    @param:StringRes val targetPerHourError: Int? = null,
    @param:StringRes val dailyGoalError: Int? = null
) {
    val hasVehicleError: Boolean
        get() = fuelPriceError != null || consumptionError != null ||
            maintenanceError != null || depreciationError != null

    val hasGoalsError: Boolean
        get() = targetPerKmError != null || targetPerHourError != null || dailyGoalError != null

    val hasAnyError: Boolean
        get() = hasVehicleError || hasGoalsError
}

/**
 * Estado imutável da [SettingsActivity].
 */
data class SettingsUiState(
    val activeSection: SettingsSection = SettingsSection.VEHICLE_COSTS,
    val previewSettings: DriverSettings = DriverSettings(),
    val fieldErrors: SettingsFieldErrors = SettingsFieldErrors(),
    val isSaving: Boolean = false,
    val isLoaded: Boolean = false
)
