package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.HistoryPeriodFilter
import com.example.data.HistoryRepository
import com.example.data.SettingsRepository
import com.example.engine.DailyGoalCalculator
import com.example.engine.RideAnalyzer
import com.example.model.RideOffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel principal que observa as configurações persistidas no [SettingsRepository]
 * e executa o motor [RideAnalyzer] para ofertas manuais e acompanhamento da meta diária.
 */
class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository? = null,
    private val analyzer: RideAnalyzer = RideAnalyzer(),
    private val dailyGoalCalculator: DailyGoalCalculator = DailyGoalCalculator()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var lastOffer: RideOffer? = null

    init {
        val todayRidesFlow = historyRepository?.observeRides(HistoryPeriodFilter.TODAY)
            ?: flowOf(emptyList())

        // Atualiza automaticamente métricas e progresso da meta diária quando configurações ou histórico mudam
        viewModelScope.launch {
            combine(
                settingsRepository.settingsFlow,
                todayRidesFlow
            ) { updatedSettings, todayRides ->
                updatedSettings to todayRides
            }.collect { (updatedSettings, todayRides) ->
                val goalProgress = dailyGoalCalculator.calculate(todayRides, updatedSettings.dailyGoal)
                _uiState.update { current ->
                    val recalculated = lastOffer?.let { offer ->
                        analyzer.analyze(offer, updatedSettings)
                    }
                    current.copy(
                        settings = updatedSettings,
                        analysis = recalculated ?: current.analysis,
                        dailyGoalProgress = goalProgress
                    )
                }
            }
        }
    }

    /**
     * Valida os campos da oferta informada e calcula o veredito usando os parâmetros salvos.
     */
    fun analyzeManualInput(
        fareText: String,
        pickupKmText: String,
        pickupMinText: String,
        tripKmText: String,
        tripMinText: String,
        sourceAppName: String? = null
    ) {
        val fare = parseBrazilianDouble(fareText)
        val pickupKm = parseBrazilianDouble(pickupKmText)
        val pickupMin = parseBrazilianDouble(pickupMinText)
        val tripKm = parseBrazilianDouble(tripKmText)
        val tripMin = parseBrazilianDouble(tripMinText)

        val errors = FormFieldErrors(
            fareError = when {
                fare == null -> R.string.error_invalid_number
                fare <= 0.0 -> R.string.error_must_be_positive
                else -> null
            },
            pickupKmError = when {
                pickupKm == null -> R.string.error_invalid_number
                pickupKm < 0.0 -> R.string.error_cannot_be_negative
                else -> null
            },
            pickupMinError = when {
                pickupMin == null -> R.string.error_invalid_number
                pickupMin < 0.0 -> R.string.error_cannot_be_negative
                else -> null
            },
            tripKmError = when {
                tripKm == null -> R.string.error_invalid_number
                tripKm < 0.0 -> R.string.error_cannot_be_negative
                else -> null
            },
            tripMinError = when {
                tripMin == null -> R.string.error_invalid_number
                tripMin < 0.0 -> R.string.error_cannot_be_negative
                else -> null
            }
        )

        val hasBasicErrors = listOf(
            errors.fareError,
            errors.pickupKmError,
            errors.pickupMinError,
            errors.tripKmError,
            errors.tripMinError
        ).any { it != null }

        if (hasBasicErrors) {
            _uiState.update { it.copy(fieldErrors = errors) }
            return
        }

        val totalKm = pickupKm!! + tripKm!!
        val totalMin = pickupMin!! + tripMin!!
        if (totalKm <= 0.0) {
            _uiState.update {
                it.copy(fieldErrors = errors.copy(tripKmError = R.string.error_total_distance_zero))
            }
            return
        }
        if (totalMin <= 0.0) {
            _uiState.update {
                it.copy(fieldErrors = errors.copy(tripMinError = R.string.error_total_time_zero))
            }
            return
        }

        val offer = RideOffer(
            fare = fare!!,
            pickupKm = pickupKm,
            pickupMin = pickupMin,
            tripKm = tripKm,
            tripMin = tripMin
        )
        lastOffer = offer

        val currentSettings = _uiState.value.settings
        val result = analyzer.analyze(offer, currentSettings)
        _uiState.update {
            it.copy(
                analysis = result,
                fieldErrors = FormFieldErrors()
            )
        }

        if (sourceAppName != null && historyRepository != null) {
            viewModelScope.launch {
                historyRepository.recordRide(
                    appName = sourceAppName,
                    offer = offer,
                    analysis = result
                )
            }
        }
    }

    /**
     * Retorna uma oferta de exemplo para cada cenário de validação.
     */
    fun getPresetOffer(preset: RidePreset): RideOffer = when (preset) {
        RidePreset.ACCEPT -> RideOffer(
            fare = 32.00,
            pickupKm = 1.0,
            pickupMin = 3.0,
            tripKm = 9.0,
            tripMin = 22.0
        )
        RidePreset.CAUTION -> RideOffer(
            fare = 18.00,
            pickupKm = 1.5,
            pickupMin = 4.0,
            tripKm = 8.5,
            tripMin = 20.0
        )
        RidePreset.REJECT -> RideOffer(
            fare = 14.00,
            pickupKm = 2.5,
            pickupMin = 7.0,
            tripKm = 7.5,
            tripMin = 23.0
        )
        RidePreset.LOSS -> RideOffer(
            fare = 8.50,
            pickupKm = 3.0,
            pickupMin = 8.0,
            tripKm = 9.0,
            tripMin = 18.0
        )
    }

    fun clearAnalysis() {
        lastOffer = null
        _uiState.update {
            it.copy(
                analysis = null,
                fieldErrors = FormFieldErrors()
            )
        }
    }

    private fun parseBrazilianDouble(raw: String): Double? {
        val normalized = raw.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val historyRepository: HistoryRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                settingsRepository = settingsRepository,
                historyRepository = historyRepository
            ) as T
        }
    }
}
