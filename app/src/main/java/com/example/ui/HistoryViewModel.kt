package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.HistoryPeriodFilter
import com.example.data.HistoryRepository
import com.example.data.SettingsRepository
import com.example.engine.DailyGoalCalculator
import com.example.engine.RideStatisticsCalculator
import com.example.model.DriverSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel reativo para consulta do histórico por período (Hoje / 7 dias / 30 dias),
 * filtro por aplicativo, cálculo de estatísticas consolidadas e meta diária.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val historyRepository: HistoryRepository,
    private val statisticsCalculator: RideStatisticsCalculator = RideStatisticsCalculator(),
    private val settingsRepository: SettingsRepository? = null,
    private val dailyGoalCalculator: DailyGoalCalculator = DailyGoalCalculator(),
    initialTab: HistoryTab = HistoryTab.LIST
) : ViewModel() {

    private val activeTabFlow = MutableStateFlow(initialTab)
    private val periodFilterFlow = MutableStateFlow(HistoryPeriodFilter.TODAY)
    private val appFilterFlow = MutableStateFlow(HistoryAppFilter.ALL)

    private val ridesFlow = periodFilterFlow.flatMapLatest { filter ->
        historyRepository.observeRides(filter)
    }

    private val todayRidesFlow = historyRepository.observeRides(HistoryPeriodFilter.TODAY)
    private val settingsFlow = settingsRepository?.settingsFlow ?: flowOf(DriverSettings())

    val uiState: StateFlow<HistoryUiState> = combine(
        activeTabFlow,
        periodFilterFlow,
        appFilterFlow,
        ridesFlow,
        todayRidesFlow,
        settingsFlow
    ) { values ->
        val tab = values[0] as HistoryTab
        val periodFilter = values[1] as HistoryPeriodFilter
        val appFilter = values[2] as HistoryAppFilter
        @Suppress("UNCHECKED_CAST")
        val periodRides = values[3] as List<com.example.data.local.RotaProRide>
        @Suppress("UNCHECKED_CAST")
        val todayRides = values[4] as List<com.example.data.local.RotaProRide>
        val settings = values[5] as DriverSettings

        val filteredRides = appFilter.appNameMatch?.let { target ->
            periodRides.filter { it.appName.equals(target, ignoreCase = true) }
        } ?: periodRides

        HistoryUiState(
            activeTab = tab,
            periodFilter = periodFilter,
            appFilter = appFilter,
            rides = filteredRides,
            statistics = statisticsCalculator.calculate(filteredRides),
            dailyGoalProgress = dailyGoalCalculator.calculate(todayRides, settings.dailyGoal)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState(activeTab = initialTab)
    )

    fun selectTab(tab: HistoryTab) {
        activeTabFlow.value = tab
    }

    fun selectPeriod(filter: HistoryPeriodFilter) {
        periodFilterFlow.value = filter
    }

    fun selectAppFilter(filter: HistoryAppFilter) {
        appFilterFlow.value = filter
    }

    fun deleteRide(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteRide(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearAll()
        }
    }

    class Factory(
        private val historyRepository: HistoryRepository,
        private val settingsRepository: SettingsRepository? = null,
        private val initialTab: HistoryTab
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(
                historyRepository = historyRepository,
                settingsRepository = settingsRepository,
                initialTab = initialTab
            ) as T
        }
    }
}
