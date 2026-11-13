package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.RotaProDatabase
import com.example.data.local.RotaProRide
import com.example.data.local.RotaProRideDao
import com.example.model.RideVerdict
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Teste de integração local do Room ([RotaProDatabase] / [RotaProRideDao]) em memória via Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RotaProDatabaseTest {

    private lateinit var database: RotaProDatabase
    private lateinit var dao: RotaProRideDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RotaProDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.rideDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insere consulta por periodo exclui individual e limpa historico no Room`() = runTest {
        val oldRide = RotaProRide(
            appName = "Uber",
            fare = 15.0,
            totalKm = 10.0,
            totalMin = 20.0,
            grossPerKm = 1.5,
            grossPerHour = 45.0,
            profit = 5.5,
            verdict = RideVerdict.CAUTION.name,
            timestamp = 1_000L
        )
        val recentRide = RotaProRide(
            appName = "99",
            fare = 32.0,
            totalKm = 10.0,
            totalMin = 25.0,
            grossPerKm = 3.2,
            grossPerHour = 76.8,
            profit = 22.5,
            verdict = RideVerdict.ACCEPT.name,
            timestamp = 50_000L
        )

        val oldId = dao.insertRide(oldRide)
        val recentId = dao.insertRide(recentRide)

        // Filtra apenas recentes
        val recentOnly = dao.observeRidesSince(10_000L).first()
        assertEquals(1, recentOnly.size)
        assertEquals("99", recentOnly.first().appName)

        // Consulta tudo ordenado por timestamp DESC
        val allRides = dao.observeRidesSince(0L).first()
        assertEquals(2, allRides.size)
        assertEquals(recentId, allRides[0].id)
        assertEquals(oldId, allRides[1].id)

        // Exclui uma corrida
        dao.deleteRideById(oldId)
        val afterDelete = dao.observeRidesSince(0L).first()
        assertEquals(1, afterDelete.size)
        assertEquals(recentId, afterDelete.first().id)

        // Limpa todo o banco
        dao.clearAll()
        assertEquals(0, dao.observeRidesSince(0L).first().size)
    }
}
