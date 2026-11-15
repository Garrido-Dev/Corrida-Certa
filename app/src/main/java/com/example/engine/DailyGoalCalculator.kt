package com.example.engine

import com.example.data.local.RotaProRide
import com.example.model.RideVerdict
import kotlin.math.roundToInt

/**
 * Progresso consolidado do motorista em relação à meta diária de lucro líquido.
 */
data class DailyGoalProgress(
    val todayProfit: Double = 0.0,
    val dailyGoal: Double = 250.0,
    val dailyFixedCost: Double = 0.0,
    val fixedCostRemaining: Double = 0.0,
    val isFixedCostCovered: Boolean = true,
    val netProfitAfterFixedCost: Double = 0.0,
    val progressPercent: Int = 0,
    val remainingAmount: Double = 250.0,
    val isGoalReached: Boolean = false
)

/**
 * Calculadora pura de progresso da meta diária sem dependências do Android.
 */
class DailyGoalCalculator {

    fun calculate(
        todayRides: List<RotaProRide>,
        dailyGoal: Double,
        dailyFixedCost: Double = 0.0
    ): DailyGoalProgress {
        val safeGoal = dailyGoal.coerceAtLeast(0.0)
        val safeFixed = dailyFixedCost.coerceAtLeast(0.0)
        val todayProfit = todayRides
            .filter { it.verdict != RideVerdict.REJECT.name }
            .sumOf { it.profit }

        val fixedRemaining = (safeFixed - todayProfit).coerceAtLeast(0.0)
        val isFixedCovered = safeFixed <= 0.0 || todayProfit >= safeFixed
        val netAfterFixed = (todayProfit - safeFixed).coerceAtLeast(0.0)

        if (safeGoal <= 0.0) {
            return DailyGoalProgress(
                todayProfit = todayProfit,
                dailyGoal = 0.0,
                dailyFixedCost = safeFixed,
                fixedCostRemaining = fixedRemaining,
                isFixedCostCovered = isFixedCovered,
                netProfitAfterFixedCost = netAfterFixed,
                progressPercent = 0,
                remainingAmount = 0.0,
                isGoalReached = false
            )
        }

        val rawPercent = ((netAfterFixed / safeGoal) * 100.0).roundToInt()
        val progressPercent = rawPercent.coerceIn(0, 100)
        val remaining = (safeGoal - netAfterFixed).coerceAtLeast(0.0)
        val reached = netAfterFixed >= safeGoal

        return DailyGoalProgress(
            todayProfit = todayProfit,
            dailyGoal = safeGoal,
            dailyFixedCost = safeFixed,
            fixedCostRemaining = fixedRemaining,
            isFixedCostCovered = isFixedCovered,
            netProfitAfterFixedCost = netAfterFixed,
            progressPercent = progressPercent,
            remainingAmount = remaining,
            isGoalReached = reached
        )
    }
}
