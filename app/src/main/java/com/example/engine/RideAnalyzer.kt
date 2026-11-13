package com.example.engine

import com.example.model.DriverSettings
import com.example.model.RideAnalysis
import com.example.model.RideOffer
import com.example.model.RideVerdict

/**
 * Motor puro de cálculo de viabilidade de corridas.
 * Não possui dependências da API do Android, garantindo execução rápida e determinística em testes unitários.
 */
class RideAnalyzer {

    companion object {
        /** Tolerância de até 20% abaixo das metas para classificação como CAUTION (Avaliar). */
        private const val CAUTION_TOLERANCE_FACTOR = 0.80
        private const val MINUTES_PER_HOUR = 60.0
    }

    /**
     * Analisa uma oferta de corrida com base nos custos do veículo e metas do motorista.
     */
    fun analyze(offer: RideOffer, settings: DriverSettings): RideAnalysis {
        // Garante que distâncias e tempos negativos não distorçam a soma total
        val totalKm = offer.pickupKm.coerceAtLeast(0.0) + offer.tripKm.coerceAtLeast(0.0)
        val totalMin = offer.pickupMin.coerceAtLeast(0.0) + offer.tripMin.coerceAtLeast(0.0)
        val totalCost = totalKm * settings.costPerKm

        // Evita divisão por zero quando a oferta vier sem distância ou duração válidas
        if (totalKm <= 0.0 || totalMin <= 0.0 || offer.fare <= 0.0) {
            return RideAnalysis(
                grossPerKm = 0.0,
                grossPerHour = 0.0,
                netPerKm = 0.0,
                profit = offer.fare - totalCost,
                verdict = RideVerdict.REJECT,
                totalKm = totalKm,
                totalMin = totalMin,
                totalCost = totalCost
            )
        }

        // Cálculo das métricas brutas considerando TODO o trajeto (ida até o passageiro + corrida)
        val grossPerKm = offer.fare / totalKm
        val totalHours = totalMin / MINUTES_PER_HOUR
        val grossPerHour = offer.fare / totalHours

        // Cálculo do custo real e lucro líquido estimado
        val profit = offer.fare - totalCost
        val netPerKm = profit / totalKm

        // Regras do semáforo de decisão
        val verdict = resolveVerdict(
            grossPerKm = grossPerKm,
            grossPerHour = grossPerHour,
            profit = profit,
            settings = settings
        )

        return RideAnalysis(
            grossPerKm = grossPerKm,
            grossPerHour = grossPerHour,
            netPerKm = netPerKm,
            profit = profit,
            verdict = verdict,
            totalKm = totalKm,
            totalMin = totalMin,
            totalCost = totalCost
        )
    }

    /**
     * Aplica as regras do semáforo:
     * - Prejuízo (lucro <= 0) resulta sempre em REJECT.
     * - ACCEPT: R$/km >= meta E R$/h >= meta E lucro > 0.
     * - CAUTION: até 20% abaixo de qualquer meta (>= 80% de ambas as metas) com lucro > 0.
     * - REJECT: demais casos.
     */
    private fun resolveVerdict(
        grossPerKm: Double,
        grossPerHour: Double,
        profit: Double,
        settings: DriverSettings
    ): RideVerdict {
        if (profit <= 0.0) {
            return RideVerdict.REJECT
        }

        val meetsKmTarget = grossPerKm >= settings.targetPerKm
        val meetsHourTarget = grossPerHour >= settings.targetPerHour
        if (meetsKmTarget && meetsHourTarget) {
            return RideVerdict.ACCEPT
        }

        val minCautionPerKm = settings.targetPerKm * CAUTION_TOLERANCE_FACTOR
        val minCautionPerHour = settings.targetPerHour * CAUTION_TOLERANCE_FACTOR
        if (grossPerKm >= minCautionPerKm && grossPerHour >= minCautionPerHour) {
            return RideVerdict.CAUTION
        }

        return RideVerdict.REJECT
    }
}
