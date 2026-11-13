package com.example.ui

import com.example.data.HistoryPeriodFilter
import com.example.data.local.RotaProRide
import com.example.engine.DailyGoalProgress
import com.example.engine.RideStatistics

/**
 * Abas exibidas na tela de Histórico e Estatísticas.
 */
enum class HistoryTab {
    LIST,
    STATS
}

/**
 * Filtro por aplicativo/origem no histórico e estatísticas.
 */
enum class HistoryAppFilter(val appNameMatch: String?) {
    ALL(null),
    UBER("Uber"),
    APP_99("99"),
    INDRIVE("inDrive"),
    SIMULATOR("Simulador")
}

/**
 * Estado imutável da [HistoryActivity].
 */
data class HistoryUiState(
    val activeTab: HistoryTab = HistoryTab.LIST,
    val periodFilter: HistoryPeriodFilter = HistoryPeriodFilter.TODAY,
    val appFilter: HistoryAppFilter = HistoryAppFilter.ALL,
    val rides: List<RotaProRide> = emptyList(),
    val statistics: RideStatistics = RideStatistics(),
    val dailyGoalProgress: DailyGoalProgress = DailyGoalProgress()
)
