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
    @StringRes val fuelPriceError: Int? = null,
    @StringRes val consumptionError: Int? = null,
    @StringRes val maintenanceError: Int? = null,
    @StringRes val depreciationError: Int? = null,
    @StringRes val targetPerKmError: Int? = null,
    @StringRes val targetPerHourError: Int? = null
) {
    val hasVehicleError: Boolean
        get() = fuelPriceError != null || consumptionError != null ||
            maintenanceError != null || depreciationError != null

    val hasGoalsError: Boolean
        get() = targetPerKmError != null || targetPerHourError != null

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
