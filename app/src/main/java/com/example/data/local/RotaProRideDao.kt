package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Objeto de acesso a dados (DAO) para histórico local de corridas.
 */
@Dao
interface RotaProRideDao {

    @Query("SELECT * FROM rota_pro_rides WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun observeRidesSince(sinceTimestamp: Long): Flow<List<RotaProRide>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRide(ride: RotaProRide): Long

    @Query("DELETE FROM rota_pro_rides WHERE id = :id")
    suspend fun deleteRideById(id: Long)

    @Query("DELETE FROM rota_pro_rides")
    suspend fun clearAll()
}
