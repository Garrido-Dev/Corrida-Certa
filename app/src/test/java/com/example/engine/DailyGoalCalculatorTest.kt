package com.example.engine

import com.example.data.local.RotaProRide
import com.example.model.RideVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes unitários da [DailyGoalCalculator] validando cálculo de progresso, restante e meta atingida.
 */
class DailyGoalCalculatorTest {

    private val calculator = DailyGoalCalculator()

    @Test
    fun `calcula progresso ignorando corridas recusadas e calcula valor restante`() {
        val rides = listOf(
            ride(profit = 50.0, verdict = RideVerdict.ACCEPT),
            ride(profit = 75.0, verdict = RideVerdict.CAUTION),
            ride(profit = 30.0, verdict = RideVerdict.REJECT)
        )

        val progress = calculator.calculate(todayRides = rides, dailyGoal = 250.0)

        assertEquals(125.0, progress.todayProfit, 0.001)
        assertEquals(250.0, progress.dailyGoal, 0.001)
        assertEquals(50, progress.progressPercent)
        assertEquals(125.0, progress.remainingAmount, 0.001)
        assertFalse(progress.isGoalReached)
    }

    @Test
    fun `marca meta como atingida e trava percentual em 100 quando ultrapassa valor diario`() {
        val rides = listOf(
            ride(profit = 180.0, verdict = RideVerdict.ACCEPT),
            ride(profit = 120.0, verdict = RideVerdict.ACCEPT)
        )

        val progress = calculator.calculate(todayRides = rides, dailyGoal = 250.0)

        assertEquals(300.0, progress.todayProfit, 0.001)
        assertEquals(100, progress.progressPercent)
        assertEquals(0.0, progress.remainingAmount, 0.001)
        assertTrue(progress.isGoalReached)
    }

    @Test
    fun `abate custo fixo diario antes de contar lucro livre para meta`() {
        val rides = listOf(
            ride(profit = 160.0, verdict = RideVerdict.ACCEPT)
        )

        val progress = calculator.calculate(
            todayRides = rides,
            dailyGoal = 200.0,
            dailyFixedCost = 100.0
        )

        assertEquals(160.0, progress.todayProfit, 0.001)
        assertEquals(100.0, progress.dailyFixedCost, 0.001)
        assertTrue(progress.isFixedCostCovered)
        assertEquals(0.0, progress.fixedCostRemaining, 0.001)
        assertEquals(60.0, progress.netProfitAfterFixedCost, 0.001)
        assertEquals(30, progress.progressPercent)
        assertEquals(140.0, progress.remainingAmount, 0.001)
    }

    @Test
    fun `indica saldo restante do custo fixo quando lucro do dia ainda nao pagou o aluguel`() {
        val rides = listOf(ride(profit = 70.0, verdict = RideVerdict.ACCEPT))

        val progress = calculator.calculate(
            todayRides = rides,
            dailyGoal = 200.0,
            dailyFixedCost = 110.0
        )

        assertFalse(progress.isFixedCostCovered)
        assertEquals(40.0, progress.fixedCostRemaining, 0.001)
        assertEquals(0.0, progress.netProfitAfterFixedCost, 0.001)
        assertEquals(0, progress.progressPercent)
    }

    private fun ride(profit: Double, verdict: RideVerdict): RotaProRide = RotaProRide(
        appName = "Uber",
        fare = 40.0,
        totalKm = 10.0,
        totalMin = 20.0,
        grossPerKm = 4.0,
        grossPerHour = 120.0,
        profit = profit,
        verdict = verdict.name
    )
}
