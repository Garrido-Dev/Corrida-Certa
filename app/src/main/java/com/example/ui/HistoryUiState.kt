package com.example.ui

import com.example.data.HistoryPeriodFilter
import com.example.data.local.RotaProRide
import com.example.engine.RideStatistics

/**
 * Abas exibidas na tela de Histórico e Estatísticas.
 */
enum class HistoryTab {
    LIST,
    STATS
}

/**
 * Estado imutável da [HistoryActivity].
 */
data class HistoryUiState(
    val activeTab: HistoryTab = HistoryTab.LIST,
    val periodFilter: HistoryPeriodFilter = HistoryPeriodFilter.TODAY,
    val rides: List<RotaProRide> = emptyList(),
    val statistics: RideStatistics = RideStatistics()
)
