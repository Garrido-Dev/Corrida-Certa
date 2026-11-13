package com.example.engine

import com.example.data.local.RotaProRide
import com.example.model.RideVerdict

/**
 * Resumo estatístico calculado sobre as corridas do período selecionado.
 */
data class RideStatistics(
    val totalCount: Int = 0,
    val acceptedCount: Int = 0,
    val cautionCount: Int = 0,
    val rejectedCount: Int = 0,
    val acceptanceRatePercent: Double = 0.0,
    val averageGrossPerKm: Double = 0.0,
    val totalProfit: Double = 0.0,
    val timeSavedMinutes: Double = 0.0
)

/**
 * Calculadora pura de estatísticas de histórico sem dependências do Android.
 */
class RideStatisticsCalculator {

    fun calculate(rides: List<RotaProRide>): RideStatistics {
        if (rides.isEmpty()) {
            return RideStatistics()
        }

        val accepted = rides.count { it.verdict == RideVerdict.ACCEPT.name }
        val caution = rides.count { it.verdict == RideVerdict.CAUTION.name }
        val rejected = rides.count { it.verdict == RideVerdict.REJECT.name }

        val acceptanceRatePercent = (accepted.toDouble() / rides.size.toDouble()) * 100.0

        // Média ponderada de R$/km pela quilometragem total analisada
        val sumFare = rides.sumOf { it.fare }
        val sumKm = rides.sumOf { it.totalKm }
        val averageGrossPerKm = if (sumKm > 0.0) {
            sumFare / sumKm
        } else {
            rides.map { it.grossPerKm }.average()
        }

        // Lucro potencial considera corridas viáveis (aceitas e avaliadas, excluindo recusadas)
        val totalProfit = rides
            .filter { it.verdict != RideVerdict.REJECT.name }
            .sumOf { it.profit }

        // Tempo economizado equivale aos minutos de corridas ruins recusadas
        val timeSavedMinutes = rides
            .filter { it.verdict == RideVerdict.REJECT.name }
            .sumOf { it.totalMin }

        return RideStatistics(
            totalCount = rides.size,
            acceptedCount = accepted,
            cautionCount = caution,
            rejectedCount = rejected,
            acceptanceRatePercent = acceptanceRatePercent,
            averageGrossPerKm = averageGrossPerKm,
            totalProfit = totalProfit,
            timeSavedMinutes = timeSavedMinutes
        )
    }
}
