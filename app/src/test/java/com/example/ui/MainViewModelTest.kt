package com.example.ui

import com.example.data.SettingsRepository
import com.example.engine.RideAnalyzer
import com.example.model.DriverSettings
import com.example.model.RideAnalysis
import com.example.model.RideVerdict
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Testes unitários do [MainViewModel] usando MockK para validar integração com [SettingsRepository]
 * e [RideAnalyzer].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

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
    fun `analyzeManualInput aceita separador decimal com virgula e usa configuracoes salvas`() = runTest {
        val savedSettings = DriverSettings(
            fuelPrice = 6.00,
            consumption = 12.0,
            maintenance = 0.10,
            depreciation = 0.15,
            targetPerKm = 2.00,
            targetPerHour = 50.00
        )
        val mockRepository = mockk<SettingsRepository>()
        every { mockRepository.settingsFlow } returns flowOf(savedSettings)

        val mockAnalyzer = mockk<RideAnalyzer>()
        val expectedAnalysis = RideAnalysis(
            grossPerKm = 3.0,
            grossPerHour = 72.0,
            netPerKm = 2.25,
            profit = 22.50,
            verdict = RideVerdict.ACCEPT,
            totalKm = 10.0,
            totalMin = 25.0,
            totalCost = 7.50
        )
        every { mockAnalyzer.analyze(any(), savedSettings) } returns expectedAnalysis

        val viewModel = MainViewModel(settingsRepository = mockRepository, analyzer = mockAnalyzer)
        viewModel.analyzeManualInput(
            fareText = "30,00",
            pickupKmText = "1,0",
            pickupMinText = "3",
            tripKmText = "9,0",
            tripMinText = "22"
        )

        val state = viewModel.uiState.value
        assertEquals(savedSettings, state.settings)
        assertEquals(expectedAnalysis, state.analysis)
        assertNull(state.fieldErrors.fareError)
        verify(exactly = 1) { mockAnalyzer.analyze(any(), savedSettings) }
    }

    @Test
    fun `quando settingsFlow emite novas metas, a ultima corrida analisada e recalculada`() = runTest {
        val initialSettings = DriverSettings(targetPerKm = 2.00, targetPerHour = 50.00)
        val stricterSettings = DriverSettings(targetPerKm = 4.00, targetPerHour = 90.00)
        val settingsFlow = MutableStateFlow(initialSettings)

        val mockRepository = mockk<SettingsRepository>()
        every { mockRepository.settingsFlow } returns settingsFlow

        val viewModel = MainViewModel(settingsRepository = mockRepository, analyzer = RideAnalyzer())
        viewModel.analyzeManualInput(
            fareText = "30.00",
            pickupKmText = "1.0",
            pickupMinText = "3",
            tripKmText = "9.0",
            tripMinText = "22"
        )
        assertEquals(RideVerdict.ACCEPT, viewModel.uiState.value.analysis?.verdict)

        settingsFlow.value = stricterSettings
        assertEquals(RideVerdict.REJECT, viewModel.uiState.value.analysis?.verdict)
    }

    @Test
    fun `analyzeManualInput sinaliza erro quando valor da corrida e invalido`() = runTest {
        val mockRepository = mockk<SettingsRepository>()
        every { mockRepository.settingsFlow } returns flowOf(DriverSettings())
        val mockAnalyzer = mockk<RideAnalyzer>()

        val viewModel = MainViewModel(settingsRepository = mockRepository, analyzer = mockAnalyzer)
        viewModel.analyzeManualInput(
            fareText = "",
            pickupKmText = "1.0",
            pickupMinText = "3",
            tripKmText = "9.0",
            tripMinText = "20"
        )

        val state = viewModel.uiState.value
        assertNotNull(state.fieldErrors.fareError)
        assertNull(state.analysis)
        verify(exactly = 0) { mockAnalyzer.analyze(any(), any()) }
    }
}
