package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Banco de dados local Room do Rota Pro (sem sincronização ou envio externo).
 */
@Database(
    entities = [RotaProRide::class],
    version = 1,
    exportSchema = false
)
abstract class RotaProDatabase : RoomDatabase() {

    abstract fun rideDao(): RotaProRideDao

    companion object {
        @Volatile
        private var INSTANCE: RotaProDatabase? = null

        fun getInstance(context: Context): RotaProDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RotaProDatabase::class.java,
                    "rota_pro_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
