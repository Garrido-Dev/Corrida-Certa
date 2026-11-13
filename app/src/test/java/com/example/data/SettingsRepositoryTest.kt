package com.example.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.model.DriverSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Testes de persistência do [SettingsRepository] usando DataStore em diretório temporário.
 */
class SettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `retorna configuracoes padrao quando DataStore esta vazio e persiste alteracoes`() = runTest {
        val testFile = tempFolder.newFile("test_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { testFile }
        )
        val repository = SettingsRepository(dataStore)

        // Valores padrão iniciais e fórmula costPerKm = (5.89 / 10.0) + 0.15 + 0.20 = 0.939
        val initial = repository.settingsFlow.first()
        assertEquals(DriverSettings(), initial)
        assertEquals(0.939, initial.costPerKm, 0.0001)

        // Salva nova configuração personalizada
        val custom = DriverSettings(
            fuelPrice = 6.20,
            consumption = 8.0,
            maintenance = 0.20,
            depreciation = 0.25,
            targetPerKm = 2.50,
            targetPerHour = 65.00
        )
        repository.saveSettings(custom)

        val loaded = repository.settingsFlow.first()
        assertEquals(custom, loaded)
        // costPerKm = (6.20 / 8.0) + 0.20 + 0.25 = 0.775 + 0.45 = 1.225
        assertEquals(1.225, loaded.costPerKm, 0.0001)

        // Restaura padrão
        repository.resetToDefaults()
        assertEquals(DriverSettings(), repository.settingsFlow.first())
    }

    @Test
    fun `onboardingCompletedFlow inicia falso e persiste conclusao`() = runTest {
        val testFile = tempFolder.newFile("test_onboarding.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { testFile }
        )
        val repository = SettingsRepository(dataStore)

        assertEquals(false, repository.onboardingCompletedFlow.first())

        repository.setOnboardingCompleted(true)
        assertEquals(true, repository.onboardingCompletedFlow.first())
    }
}
