package com.example.ui

import com.example.data.SettingsRepository
import com.example.model.DriverSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Testes unitários do [SettingsViewModel] validando cálculo derivado de costPerKm,
 * troca automática de aba em erro e persistência no [SettingsRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

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
    fun `onInputsChanged calcula costPerKm pela formula combustivel sobre consumo mais manutencao e depreciacao`() = runTest {
        val mockRepo = mockk<SettingsRepository>()
        every { mockRepo.settingsFlow } returns flowOf(DriverSettings())

        val viewModel = SettingsViewModel(mockRepo)
        viewModel.onInputsChanged(
            fuelPriceText = "6,00",
            consumptionText = "10,0",
            maintenanceText = "0,15",
            depreciationText = "0,25",
            targetPerKmText = "2,20",
            targetPerHourText = "55,00"
        )

        // (6.00 / 10.0) + 0.15 + 0.25 = 1.00
        assertEquals(1.00, viewModel.uiState.value.previewSettings.costPerKm, 0.0001)
    }

    @Test
    fun `saveSettings persiste no repositorio quando todos os campos sao validos`() = runTest {
        val mockRepo = mockk<SettingsRepository>()
        every { mockRepo.settingsFlow } returns flowOf(DriverSettings())
        coEvery { mockRepo.saveSettings(any()) } just runs

        val viewModel = SettingsViewModel(mockRepo)
        viewModel.saveSettings(
            fuelPriceText = "6,00",
            consumptionText = "12,0",
            maintenanceText = "0,18",
            depreciationText = "0,22",
            targetPerKmText = "2,40",
            targetPerHourText = "60,00"
        )

        coVerify(exactly = 1) {
            mockRepo.saveSettings(
                DriverSettings(
                    fuelPrice = 6.00,
                    consumption = 12.0,
                    maintenance = 0.18,
                    depreciation = 0.22,
                    targetPerKm = 2.40,
                    targetPerHour = 60.00
                )
            )
        }
    }

    @Test
    fun `saveSettings muda para aba de metas se houver erro em meta enquanto aba de custos esta ativa`() = runTest {
        val mockRepo = mockk<SettingsRepository>()
        every { mockRepo.settingsFlow } returns flowOf(DriverSettings())

        val viewModel = SettingsViewModel(mockRepo, initialSection = SettingsSection.VEHICLE_COSTS)
        viewModel.saveSettings(
            fuelPriceText = "5,89",
            consumptionText = "10,0",
            maintenanceText = "0,15",
            depreciationText = "0,20",
            targetPerKmText = "0",
            targetPerHourText = "50,00"
        )

        assertEquals(SettingsSection.GOALS, viewModel.uiState.value.activeSection)
        assertNotNull(viewModel.uiState.value.fieldErrors.targetPerKmError)
        coVerify(exactly = 0) { mockRepo.saveSettings(any()) }
    }

    @Test
    fun `saveSettings valida e persiste meta diaria positiva`() = runTest {
        val mockRepo = mockk<SettingsRepository>()
        every { mockRepo.settingsFlow } returns flowOf(DriverSettings())
        coEvery { mockRepo.saveSettings(any()) } just runs

        val viewModel = SettingsViewModel(mockRepo)
        viewModel.saveSettings(
            fuelPriceText = "5,89",
            consumptionText = "10,0",
            maintenanceText = "0,15",
            depreciationText = "0,20",
            targetPerKmText = "2,00",
            targetPerHourText = "50,00",
            dailyGoalText = "320,00"
        )

        coVerify(exactly = 1) {
            mockRepo.saveSettings(match { it.dailyGoal == 320.0 })
        }
    }

    @Test
    fun `saveSettings valida faixa de nota do passageiro e persiste aluguel e areas bloqueadas`() = runTest {
        val mockRepo = mockk<SettingsRepository>()
        every { mockRepo.settingsFlow } returns flowOf(DriverSettings())
        coEvery { mockRepo.saveSettings(any()) } just runs

        val viewModel = SettingsViewModel(mockRepo)
        viewModel.saveSettings(
            fuelPriceText = "5,89",
            consumptionText = "10,0",
            maintenanceText = "0,15",
            depreciationText = "0,20",
            targetPerKmText = "2,00",
            targetPerHourText = "50,00",
            dailyGoalText = "250,00",
            minPassengerRatingText = "5,5"
        )

        assertEquals(SettingsSection.GOALS, viewModel.uiState.value.activeSection)
        assertNotNull(viewModel.uiState.value.fieldErrors.minPassengerRatingError)
        coVerify(exactly = 0) { mockRepo.saveSettings(any()) }

        viewModel.saveSettings(
            fuelPriceText = "5,89",
            consumptionText = "10,0",
            maintenanceText = "0,15",
            depreciationText = "0,20",
            targetPerKmText = "2,00",
            targetPerHourText = "50,00",
            dailyGoalText = "250,00",
            dailyFixedCostText = "120,00",
            minFareText = "10,00",
            maxPickupKmText = "3,0",
            minPassengerRatingText = "4,70",
            blockedAreasText = "Centro, Zona Leste",
            voiceAlertEnabled = true,
            alertCashRide = true,
            alertExtraStop = false
        )

        coVerify(exactly = 1) {
            mockRepo.saveSettings(
                match {
                    it.dailyFixedCost == 120.0 &&
                        it.minFare == 10.0 &&
                        it.maxPickupKm == 3.0 &&
                        it.minPassengerRating == 4.70 &&
                        it.blockedAreas == "Centro, Zona Leste" &&
                        !it.alertExtraStop
                }
            )
        }
    }
}
