package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Banco de dados local Room do Corrida Certa (sem sincronização ou envio externo).
 */
@Database(
    entities = [RotaProRide::class],
    version = 2,
    exportSchema = false
)
abstract class RotaProDatabase : RoomDatabase() {

    abstract fun rideDao(): RotaProRideDao

    companion object {
        @Volatile
        private var INSTANCE: RotaProDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rota_pro_rides ADD COLUMN passengerRating REAL")
                db.execSQL("ALTER TABLE rota_pro_rides ADD COLUMN routeSummary TEXT")
                db.execSQL("ALTER TABLE rota_pro_rides ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE rota_pro_rides ADD COLUMN hasExtraStop INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rota_pro_rides ADD COLUMN alertSummary TEXT")
            }
        }

        fun getInstance(context: Context): RotaProDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RotaProDatabase::class.java,
                    "rota_pro_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
