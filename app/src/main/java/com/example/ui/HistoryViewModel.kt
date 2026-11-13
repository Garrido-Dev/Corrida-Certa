package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.HistoryPeriodFilter
import com.example.data.HistoryRepository
import com.example.engine.RideStatisticsCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel reativo para consulta do histórico por período (Hoje / 7 dias / 30 dias)
 * e cálculo de estatísticas consolidadas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val historyRepository: HistoryRepository,
    private val statisticsCalculator: RideStatisticsCalculator = RideStatisticsCalculator(),
    initialTab: HistoryTab = HistoryTab.LIST
) : ViewModel() {

    private val activeTabFlow = MutableStateFlow(initialTab)
    private val periodFilterFlow = MutableStateFlow(HistoryPeriodFilter.TODAY)

    private val ridesFlow = periodFilterFlow.flatMapLatest { filter ->
        historyRepository.observeRides(filter)
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        activeTabFlow,
        periodFilterFlow,
        ridesFlow
    ) { tab, filter, rides ->
        HistoryUiState(
            activeTab = tab,
            periodFilter = filter,
            rides = rides,
            statistics = statisticsCalculator.calculate(rides)
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
        private val initialTab: HistoryTab
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(
                historyRepository = historyRepository,
                initialTab = initialTab
            ) as T
        }
    }
}
