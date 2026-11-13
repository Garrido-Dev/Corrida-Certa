package com.example.data

import com.example.data.local.RotaProRide
import com.example.data.local.RotaProRideDao
import com.example.model.RideAnalysis
import com.example.model.RideOffer
import com.example.model.RideVerdict
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes unitários do [HistoryRepository] com MockK validando gravação Room e filtros temporais.
 */
class HistoryRepositoryTest {

    @Test
    fun `recordRide converte oferta e analise para entidade RotaProRide corretamente`() = runTest {
        val mockDao = mockk<RotaProRideDao>()
        val rideSlot = slot<RotaProRide>()
        coEvery { mockDao.insertRide(capture(rideSlot)) } returns 42L

        val repository = HistoryRepository(rideDao = mockDao, clockMillis = { 1_700_000_000_000L })
        val offer = RideOffer(fare = 30.0, pickupKm = 1.0, pickupMin = 3.0, tripKm = 9.0, tripMin = 22.0)
        val analysis = RideAnalysis(
            grossPerKm = 3.0,
            grossPerHour = 72.0,
            netPerKm = 2.06,
            profit = 20.6,
            verdict = RideVerdict.ACCEPT,
            totalKm = 10.0,
            totalMin = 25.0,
            totalCost = 9.4
        )

        val insertedId = repository.recordRide("Uber", offer, analysis)

        assertEquals(42L, insertedId)
        assertEquals("Uber", rideSlot.captured.appName)
        assertEquals(30.0, rideSlot.captured.fare, 0.001)
        assertEquals(10.0, rideSlot.captured.totalKm, 0.001)
        assertEquals(25.0, rideSlot.captured.totalMin, 0.001)
        assertEquals("ACCEPT", rideSlot.captured.verdict)
        assertEquals(1_700_000_000_000L, rideSlot.captured.timestamp)
    }

    @Test
    fun `computeStartTimestamp respeita janelas de Hoje, 7 dias e 30 dias`() {
        val mockDao = mockk<RotaProRideDao>()
        every { mockDao.observeRidesSince(any()) } returns flowOf(emptyList())
        val now = 1_700_000_000_000L
        val repository = HistoryRepository(rideDao = mockDao, clockMillis = { now })

        val startToday = repository.computeStartTimestamp(HistoryPeriodFilter.TODAY, now)
        val start7Days = repository.computeStartTimestamp(HistoryPeriodFilter.LAST_7_DAYS, now)
        val start30Days = repository.computeStartTimestamp(HistoryPeriodFilter.LAST_30_DAYS, now)

        assertTrue(startToday <= now)
        assertEquals(7L * 24 * 60 * 60 * 1000, now - start7Days)
        assertEquals(30L * 24 * 60 * 60 * 1000, now - start30Days)

        repository.observeRides(HistoryPeriodFilter.LAST_7_DAYS)
        io.mockk.verify { mockDao.observeRidesSince(start7Days) }
    }

    @Test
    fun `deleteRide e clearAll delegam corretamente ao DAO`() = runTest {
        val mockDao = mockk<RotaProRideDao>(relaxed = true)
        val repository = HistoryRepository(rideDao = mockDao)

        repository.deleteRide(7L)
        repository.clearAll()

        coVerify(exactly = 1) { mockDao.deleteRideById(7L) }
        coVerify(exactly = 1) { mockDao.clearAll() }
    }
}
