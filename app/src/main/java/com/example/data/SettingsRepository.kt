package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.DriverSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.rotaProSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "rota_pro_settings"
)

/**
 * Repositório local baseado em DataStore Preferences para persistir custos do veículo e metas.
 * Todos os dados permanecem exclusivamente no dispositivo do motorista.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>
) {

    constructor(context: Context) : this(context.applicationContext.rotaProSettingsDataStore)

    private object Keys {
        val FUEL_PRICE = doublePreferencesKey("fuel_price")
        val CONSUMPTION = doublePreferencesKey("consumption")
        val MAINTENANCE = doublePreferencesKey("maintenance")
        val DEPRECIATION = doublePreferencesKey("depreciation")
        val TARGET_PER_KM = doublePreferencesKey("target_per_km")
        val TARGET_PER_HOUR = doublePreferencesKey("target_per_hour")
        val DAILY_GOAL = doublePreferencesKey("daily_goal")
        val DAILY_FIXED_COST = doublePreferencesKey("daily_fixed_cost")
        val MIN_FARE = doublePreferencesKey("min_fare")
        val MAX_PICKUP_KM = doublePreferencesKey("max_pickup_km")
        val MIN_PASSENGER_RATING = doublePreferencesKey("min_passenger_rating")
        val BLOCKED_AREAS = stringPreferencesKey("blocked_areas")
        val VOICE_ALERT_ENABLED = booleanPreferencesKey("voice_alert_enabled")
        val ALERT_CASH_RIDE = booleanPreferencesKey("alert_cash_ride")
        val ALERT_EXTRA_STOP = booleanPreferencesKey("alert_extra_stop")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    /**
     * Fluxo reativo com as configurações atuais do motorista, usando padrões seguros na primeira execução.
     */
    val settingsFlow: Flow<DriverSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val defaults = DriverSettings()
            DriverSettings(
                fuelPrice = preferences[Keys.FUEL_PRICE] ?: defaults.fuelPrice,
                consumption = preferences[Keys.CONSUMPTION] ?: defaults.consumption,
                maintenance = preferences[Keys.MAINTENANCE] ?: defaults.maintenance,
                depreciation = preferences[Keys.DEPRECIATION] ?: defaults.depreciation,
                targetPerKm = preferences[Keys.TARGET_PER_KM] ?: defaults.targetPerKm,
                targetPerHour = preferences[Keys.TARGET_PER_HOUR] ?: defaults.targetPerHour,
                dailyGoal = preferences[Keys.DAILY_GOAL] ?: defaults.dailyGoal,
                dailyFixedCost = preferences[Keys.DAILY_FIXED_COST] ?: defaults.dailyFixedCost,
                minFare = preferences[Keys.MIN_FARE] ?: defaults.minFare,
                maxPickupKm = preferences[Keys.MAX_PICKUP_KM] ?: defaults.maxPickupKm,
                minPassengerRating = preferences[Keys.MIN_PASSENGER_RATING] ?: defaults.minPassengerRating,
                blockedAreas = preferences[Keys.BLOCKED_AREAS] ?: defaults.blockedAreas,
                voiceAlertEnabled = preferences[Keys.VOICE_ALERT_ENABLED] ?: defaults.voiceAlertEnabled,
                alertCashRide = preferences[Keys.ALERT_CASH_RIDE] ?: defaults.alertCashRide,
                alertExtraStop = preferences[Keys.ALERT_EXTRA_STOP] ?: defaults.alertExtraStop
            )
        }

    /**
     * Persiste no DataStore todas as configurações validadas de veículo, metas e filtros.
     */
    suspend fun saveSettings(settings: DriverSettings) {
        dataStore.edit { preferences ->
            preferences[Keys.FUEL_PRICE] = settings.fuelPrice
            preferences[Keys.CONSUMPTION] = settings.consumption
            preferences[Keys.MAINTENANCE] = settings.maintenance
            preferences[Keys.DEPRECIATION] = settings.depreciation
            preferences[Keys.TARGET_PER_KM] = settings.targetPerKm
            preferences[Keys.TARGET_PER_HOUR] = settings.targetPerHour
            preferences[Keys.DAILY_GOAL] = settings.dailyGoal
            preferences[Keys.DAILY_FIXED_COST] = settings.dailyFixedCost
            preferences[Keys.MIN_FARE] = settings.minFare
            preferences[Keys.MAX_PICKUP_KM] = settings.maxPickupKm
            preferences[Keys.MIN_PASSENGER_RATING] = settings.minPassengerRating
            preferences[Keys.BLOCKED_AREAS] = settings.blockedAreas
            preferences[Keys.VOICE_ALERT_ENABLED] = settings.voiceAlertEnabled
            preferences[Keys.ALERT_CASH_RIDE] = settings.alertCashRide
            preferences[Keys.ALERT_EXTRA_STOP] = settings.alertExtraStop
        }
    }

    /**
     * Indica se o motorista já concluiu ou pulou o onboarding inicial em 3 telas.
     */
    val onboardingCompletedFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[Keys.ONBOARDING_COMPLETED] ?: false
        }

    /**
     * Marca o onboarding inicial como concluído no DataStore.
     */
    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.ONBOARDING_COMPLETED] = completed
        }
    }

    /**
     * Restaura os valores padrão removendo customizações salvas.
     */
    suspend fun resetToDefaults() {
        val defaults = DriverSettings()
        saveSettings(defaults)
    }
}
