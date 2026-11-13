package com.example.engine

import com.example.data.local.RotaProRide
import com.example.model.RideVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes unitários do [RideStatisticsCalculator] cobrindo lista vazia e métricas consolidadas.
 */
class RideStatisticsCalculatorTest {

    private val calculator = RideStatisticsCalculator()

    @Test
    fun `lista vazia retorna estatisticas zeradas`() {
        val stats = calculator.calculate(emptyList())

        assertEquals(0, stats.totalCount)
        assertEquals(0.0, stats.acceptanceRatePercent, 0.001)
        assertEquals(0.0, stats.averageGrossPerKm, 0.001)
        assertEquals(0.0, stats.totalProfit, 0.001)
        assertEquals(0.0, stats.timeSavedMinutes, 0.001)
    }

    @Test
    fun `calcula taxa de aceite, R$ por km medio, lucro total e tempo economizado`() {
        val rides = listOf(
            // Corrida 1: Aceita -> R$ 30 em 10 km, 25 min, lucro R$ 20,60
            RotaProRide(
                id = 1,
                appName = "Uber",
                fare = 30.0,
                totalKm = 10.0,
                totalMin = 25.0,
                grossPerKm = 3.0,
                grossPerHour = 72.0,
                profit = 20.6,
                verdict = RideVerdict.ACCEPT.name,
                timestamp = 1000L
            ),
            // Corrida 2: Avaliar -> R$ 18 em 10 km, 20 min, lucro R$ 8,60
            RotaProRide(
                id = 2,
                appName = "99",
                fare = 18.0,
                totalKm = 10.0,
                totalMin = 20.0,
                grossPerKm = 1.8,
                grossPerHour = 54.0,
                profit = 8.6,
                verdict = RideVerdict.CAUTION.name,
                timestamp = 2000L
            ),
            // Corrida 3: Recusada -> R$ 12 em 10 km, 30 min, lucro R$ 2,60 (economizou 30 min)
            RotaProRide(
                id = 3,
                appName = "inDrive",
                fare = 12.0,
                totalKm = 10.0,
                totalMin = 30.0,
                grossPerKm = 1.2,
                grossPerHour = 24.0,
                profit = 2.6,
                verdict = RideVerdict.REJECT.name,
                timestamp = 3000L
            ),
            // Corrida 4: Aceita -> R$ 24 em 10 km, 20 min, lucro R$ 14,60
            RotaProRide(
                id = 4,
                appName = "Uber",
                fare = 24.0,
                totalKm = 10.0,
                totalMin = 20.0,
                grossPerKm = 2.4,
                grossPerHour = 72.0,
                profit = 14.6,
                verdict = RideVerdict.ACCEPT.name,
                timestamp = 4000L
            )
        )

        val stats = calculator.calculate(rides)

        assertEquals(4, stats.totalCount)
        assertEquals(2, stats.acceptedCount)
        assertEquals(1, stats.cautionCount)
        assertEquals(1, stats.rejectedCount)
        // 2 aceitas em 4 = 50%
        assertEquals(50.0, stats.acceptanceRatePercent, 0.001)
        // Total fare = 84.0 / Total km = 40.0 => R$ 2,10/km
        assertEquals(2.10, stats.averageGrossPerKm, 0.001)
        // Lucro total (não recusadas) = 20.6 + 8.6 + 14.6 = 43.8
        assertEquals(43.8, stats.totalProfit, 0.001)
        // Tempo economizado (recusadas) = 30.0 min
        assertEquals(30.0, stats.timeSavedMinutes, 0.001)
    }
}
