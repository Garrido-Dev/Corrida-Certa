package com.example.engine

import com.example.model.DriverSettings
import com.example.model.PaymentMethod
import com.example.model.RideAlertType
import com.example.model.RideAnalysis
import com.example.model.RideOffer
import com.example.model.RideVerdict
import java.text.Normalizer

/**
 * Motor puro de cálculo de viabilidade de corridas.
 * Não possui dependências da API do Android, garantindo execução rápida e determinística em testes unitários.
 */
class RideAnalyzer {

    companion object {
        /** Tolerância de até 20% abaixo das metas para classificação como CAUTION (Avaliar). */
        private const val CAUTION_TOLERANCE_FACTOR = 0.80
        private const val MINUTES_PER_HOUR = 60.0
        private val COMBINING_MARKS_REGEX = Regex("""\p{Mn}+""")
    }

    /**
     * Analisa uma oferta de corrida com base nos custos do veículo, metas e filtros do motorista.
     */
    fun analyze(offer: RideOffer, settings: DriverSettings): RideAnalysis {
        // Garante que distâncias e tempos negativos não distorçam a soma total
        val totalKm = offer.pickupKm.coerceAtLeast(0.0) + offer.tripKm.coerceAtLeast(0.0)
        val totalMin = offer.pickupMin.coerceAtLeast(0.0) + offer.tripMin.coerceAtLeast(0.0)
        val totalCost = totalKm * settings.costPerKm

        val (alerts, blockedMatch) = collectAlerts(offer, settings)

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
                totalCost = totalCost,
                alerts = alerts,
                blockedAreaMatch = blockedMatch
            )
        }

        // Cálculo das métricas brutas considerando TODO o trajeto (ida até o passageiro + corrida)
        val grossPerKm = offer.fare / totalKm
        val totalHours = totalMin / MINUTES_PER_HOUR
        val grossPerHour = offer.fare / totalHours

        // Cálculo do custo real e lucro líquido estimado
        val profit = offer.fare - totalCost
        val netPerKm = profit / totalKm

        // Regras do semáforo de decisão combinando finanças + filtros de segurança/operação
        val baseVerdict = resolveFinancialVerdict(
            grossPerKm = grossPerKm,
            grossPerHour = grossPerHour,
            profit = profit,
            settings = settings
        )
        val finalVerdict = applyAlertOverrides(baseVerdict, alerts)

        return RideAnalysis(
            grossPerKm = grossPerKm,
            grossPerHour = grossPerHour,
            netPerKm = netPerKm,
            profit = profit,
            verdict = finalVerdict,
            totalKm = totalKm,
            totalMin = totalMin,
            totalCost = totalCost,
            alerts = alerts,
            blockedAreaMatch = blockedMatch
        )
    }

    private fun collectAlerts(
        offer: RideOffer,
        settings: DriverSettings
    ): Pair<List<RideAlertType>, String?> {
        val alerts = mutableListOf<RideAlertType>()
        var blockedMatch: String? = null

        val routeText = offer.routeSummary
        if (!routeText.isNullOrBlank()) {
            val normalizedRoute = normalizeText(routeText)
            for (token in settings.blockedAreaTokens()) {
                val normalizedToken = normalizeText(token)
                if (normalizedToken.isNotEmpty() && normalizedRoute.contains(normalizedToken)) {
                    alerts.add(RideAlertType.BLOCKED_AREA)
                    blockedMatch = token
                    break
                }
            }
        }

        if (settings.minFare > 0.0 && offer.fare < settings.minFare) {
            alerts.add(RideAlertType.BELOW_MIN_FARE)
        }

        if (settings.maxPickupKm > 0.0 && offer.pickupKm > settings.maxPickupKm) {
            alerts.add(RideAlertType.LONG_PICKUP)
        }

        if (settings.minPassengerRating > 0.0 &&
            offer.passengerRating != null &&
            offer.passengerRating < settings.minPassengerRating
        ) {
            alerts.add(RideAlertType.LOW_RATING)
        }

        if (settings.alertCashRide && offer.paymentMethod == PaymentMethod.CASH) {
            alerts.add(RideAlertType.CASH_PAYMENT)
        }

        if (settings.alertExtraStop && offer.hasExtraStop) {
            alerts.add(RideAlertType.EXTRA_STOP)
        }

        return alerts to blockedMatch
    }

    /**
     * Aplica as regras do semáforo financeiro:
     * - Prejuízo (lucro <= 0) resulta sempre em REJECT.
     * - ACCEPT: R$/km >= meta E R$/h >= meta E lucro > 0.
     * - CAUTION: até 20% abaixo de qualquer meta (>= 80% de ambas as metas) com lucro > 0.
     * - REJECT: demais casos.
     */
    private fun resolveFinancialVerdict(
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

    private fun applyAlertOverrides(
        baseVerdict: RideVerdict,
        alerts: List<RideAlertType>
    ): RideVerdict {
        val hasBlockingFilter = alerts.any {
            it == RideAlertType.BLOCKED_AREA ||
                it == RideAlertType.BELOW_MIN_FARE ||
                it == RideAlertType.LONG_PICKUP ||
                it == RideAlertType.LOW_RATING
        }
        if (hasBlockingFilter) {
            return RideVerdict.REJECT
        }

        val hasOperationalWarning = alerts.any {
            it == RideAlertType.CASH_PAYMENT || it == RideAlertType.EXTRA_STOP
        }
        if (hasOperationalWarning && baseVerdict == RideVerdict.ACCEPT) {
            return RideVerdict.CAUTION
        }

        return baseVerdict
    }

    private fun normalizeText(raw: String): String {
        val decomposed = Normalizer.normalize(raw.lowercase().trim(), Normalizer.Form.NFD)
        return COMBINING_MARKS_REGEX.replace(decomposed, "")
    }
}
