package com.example.ui

import androidx.annotation.StringRes
import com.example.engine.DailyGoalProgress
import com.example.model.DriverSettings
import com.example.model.RideAnalysis

/**
 * Cenários pré-definidos para validação rápida do motor.
 */
enum class RidePreset {
    ACCEPT,
    CAUTION,
    REJECT,
    LOSS
}

/**
 * Erros de validação por campo do formulário da oferta de corrida.
 */
data class FormFieldErrors(
    @param:StringRes val fareError: Int? = null,
    @param:StringRes val pickupKmError: Int? = null,
    @param:StringRes val pickupMinError: Int? = null,
    @param:StringRes val tripKmError: Int? = null,
    @param:StringRes val tripMinError: Int? = null
)

/**
 * Estado imutável da tela principal com configurações persistidas pelo DataStore e meta diária.
 */
data class MainUiState(
    val settings: DriverSettings = DriverSettings(),
    val analysis: RideAnalysis? = null,
    val fieldErrors: FormFieldErrors = FormFieldErrors(),
    val dailyGoalProgress: DailyGoalProgress = DailyGoalProgress()
)
