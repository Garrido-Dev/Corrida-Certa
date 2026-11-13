package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val timestamp: Long = System.currentTimeMillis()
)
