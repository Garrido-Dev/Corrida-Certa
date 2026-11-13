package com.example.data

import android.content.Context
import com.example.data.local.RotaProDatabase
import com.example.data.local.RotaProRide
import com.example.data.local.RotaProRideDao
import com.example.model.RideAnalysis
import com.example.model.RideOffer
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * Filtros temporais suportados no histórico e nas estatísticas.
 */
enum class HistoryPeriodFilter {
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS
}

/**
 * Repositório local para gravação e consulta do histórico de corridas no Room.
 */
class HistoryRepository(
    private val rideDao: RotaProRideDao,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {

    constructor(context: Context) : this(
        rideDao = RotaProDatabase.getInstance(context).rideDao()
    )

    companion object {
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }

    fun observeRides(filter: HistoryPeriodFilter): Flow<List<RotaProRide>> {
        val sinceTimestamp = computeStartTimestamp(filter, clockMillis())
        return rideDao.observeRidesSince(sinceTimestamp)
    }

    suspend fun recordRide(
        appName: String,
        offer: RideOffer,
        analysis: RideAnalysis,
        timestamp: Long = clockMillis()
    ): Long {
        val entity = RotaProRide(
            appName = appName,
            fare = offer.fare,
            totalKm = analysis.totalKm,
            totalMin = analysis.totalMin,
            grossPerKm = analysis.grossPerKm,
            grossPerHour = analysis.grossPerHour,
            profit = analysis.profit,
            verdict = analysis.verdict.name,
            timestamp = timestamp
        )
        return rideDao.insertRide(entity)
    }

    suspend fun deleteRide(id: Long) {
        rideDao.deleteRideById(id)
    }

    suspend fun clearAll() {
        rideDao.clearAll()
    }

    internal fun computeStartTimestamp(filter: HistoryPeriodFilter, nowMillis: Long): Long {
        return when (filter) {
            HistoryPeriodFilter.TODAY -> {
                Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            HistoryPeriodFilter.LAST_7_DAYS -> nowMillis - (7L * MILLIS_PER_DAY)
            HistoryPeriodFilter.LAST_30_DAYS -> nowMillis - (30L * MILLIS_PER_DAY)
        }
    }
}
