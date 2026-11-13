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
    val progressPercent: Int = 0,
    val remainingAmount: Double = 250.0,
    val isGoalReached: Boolean = false
)

/**
 * Calculadora pura de progresso da meta diária sem dependências do Android.
 */
class DailyGoalCalculator {

    fun calculate(todayRides: List<RotaProRide>, dailyGoal: Double): DailyGoalProgress {
        val safeGoal = dailyGoal.coerceAtLeast(0.0)
        val todayProfit = todayRides
            .filter { it.verdict != RideVerdict.REJECT.name }
            .sumOf { it.profit }

        if (safeGoal <= 0.0) {
            return DailyGoalProgress(
                todayProfit = todayProfit,
                dailyGoal = 0.0,
                progressPercent = 0,
                remainingAmount = 0.0,
                isGoalReached = false
            )
        }

        val rawPercent = ((todayProfit / safeGoal) * 100.0).roundToInt()
        val progressPercent = rawPercent.coerceIn(0, 100)
        val remaining = (safeGoal - todayProfit).coerceAtLeast(0.0)
        val reached = todayProfit >= safeGoal

        return DailyGoalProgress(
            todayProfit = todayProfit,
            dailyGoal = safeGoal,
            progressPercent = progressPercent,
            remainingAmount = remaining,
            isGoalReached = reached
        )
    }
}
