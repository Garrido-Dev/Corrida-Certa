package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.PaymentMethod

/**
 * Entidade Room que representa uma corrida analisada e armazenada localmente no dispositivo.
 */
@Entity(tableName = "rota_pro_rides")
data class RotaProRide(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val appName: String,
    val fare: Double,
    val totalKm: Double,
    val totalMin: Double,
    val grossPerKm: Double,
    val grossPerHour: Double,
    val profit: Double,
    val verdict: String,
    val timestamp: Long = System.currentTimeMillis(),
    val passengerRating: Double? = null,
    val routeSummary: String? = null,
    val paymentMethod: String = PaymentMethod.UNKNOWN.name,
    val hasExtraStop: Boolean = false,
    val alertSummary: String? = null
)
