package com.example.ui

import com.example.data.HistoryPeriodFilter
import com.example.data.HistoryRepository
import com.example.data.local.RotaProRide
import com.example.model.RideVerdict
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Testes unitários do [HistoryViewModel] validando alternância de filtros e atualização das estatísticas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectPeriod alterna entre Hoje, 7 dias e 30 dias recalculando estatisticas`() = runTest {
        val todayRide = RotaProRide(
            id = 1,
            appName = "Uber",
            fare = 30.0,
            totalKm = 10.0,
            totalMin = 20.0,
            grossPerKm = 3.0,
            grossPerHour = 90.0,
            profit = 20.0,
            verdict = RideVerdict.ACCEPT.name,
            timestamp = 1000L
        )
        val weekOldRejectedRide = RotaProRide(
            id = 2,
            appName = "99",
            fare = 10.0,
            totalKm = 10.0,
            totalMin = 25.0,
            grossPerKm = 1.0,
            grossPerHour = 24.0,
            profit = 1.0,
            verdict = RideVerdict.REJECT.name,
            timestamp = 500L
        )

        val mockRepo = mockk<HistoryRepository>()
        every { mockRepo.observeRides(HistoryPeriodFilter.TODAY) } returns flowOf(listOf(todayRide))
        every { mockRepo.observeRides(HistoryPeriodFilter.LAST_7_DAYS) } returns flowOf(
            listOf(todayRide, weekOldRejectedRide)
        )
        every { mockRepo.observeRides(HistoryPeriodFilter.LAST_30_DAYS) } returns flowOf(
            listOf(todayRide, weekOldRejectedRide)
        )

        val viewModel = HistoryViewModel(historyRepository = mockRepo, initialTab = HistoryTab.STATS)
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        // Filtro inicial: Hoje (1 corrida aceita -> 100% aceite)
        assertEquals(HistoryTab.STATS, viewModel.uiState.value.activeTab)
        assertEquals(1, viewModel.uiState.value.rides.size)
        assertEquals(100.0, viewModel.uiState.value.statistics.acceptanceRatePercent, 0.001)

        // Muda para 7 dias (2 corridas -> 50% aceite e 25 min economizados)
        viewModel.selectPeriod(HistoryPeriodFilter.LAST_7_DAYS)
        assertEquals(HistoryPeriodFilter.LAST_7_DAYS, viewModel.uiState.value.periodFilter)
        assertEquals(2, viewModel.uiState.value.rides.size)
        assertEquals(50.0, viewModel.uiState.value.statistics.acceptanceRatePercent, 0.001)
        assertEquals(25.0, viewModel.uiState.value.statistics.timeSavedMinutes, 0.001)

        collectJob.cancel()
    }
}
