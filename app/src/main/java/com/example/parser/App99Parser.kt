package com.example.parser

import com.example.model.RideOffer

/**
 * Parser especializado nos cards de oferta do aplicativo 99 Motorista.
 * Padrões comuns: "R$ 19,80", "Embarque: 1,2 km · 4 min", "Viagem: 7,5 km · 16 min".
 */
class App99Parser : RideOfferParser {

    override val app: SupportedApp = SupportedApp.APP_99

    override fun parse(nodesText: List<String>): RideOffer? {
        val normalized = ParserUtils.normalizeNodesText(nodesText)
        if (normalized.isEmpty() || ParserUtils.isLikelyNonOfferScreen(normalized)) {
            return null
        }

        val fare = ParserUtils.extractFare(normalized) ?: return null
        val rating = ParserUtils.extractPassengerRating(normalized)
        val routeSummary = ParserUtils.extractRouteSummary(normalized)
        val paymentMethod = ParserUtils.extractPaymentMethod(normalized)
        val hasExtraStop = ParserUtils.extractHasExtraStop(normalized)

        // Tenta identificar nós explicitamente rotulados primeiro
        var pickupKm: Double? = null
        var pickupMin: Double? = null
        var tripKm: Double? = null
        var tripMin: Double? = null

        for (raw in normalized) {
            val lower = raw.lowercase()
            when {
                lower.contains("embarque") || lower.contains("busca") || lower.contains("até o passageiro") -> {
                    pickupKm = pickupKm ?: ParserUtils.extractFirstDistanceKm(raw)
                    pickupMin = pickupMin ?: ParserUtils.extractFirstDurationMin(raw)
                }
                lower.contains("viagem") || lower.contains("destino") || lower.contains("corrida") -> {
                    tripKm = tripKm ?: ParserUtils.extractFirstDistanceKm(raw)
                    tripMin = tripMin ?: ParserUtils.extractFirstDurationMin(raw)
                }
            }
        }

        if (tripKm != null && tripMin != null) {
            return RideOffer(
                fare = fare,
                pickupKm = pickupKm ?: 0.0,
                pickupMin = pickupMin ?: 0.0,
                tripKm = tripKm,
                tripMin = tripMin,
                passengerRating = rating,
                routeSummary = routeSummary,
                paymentMethod = paymentMethod,
                hasExtraStop = hasExtraStop
            )
        }

        // Caso sem rótulo explícito: extrai pares combinados (busca primeiro, viagem depois)
        val legs = ParserUtils.extractRouteLegs(nodesText)
        if (legs.size >= 2) {
            return RideOffer(
                fare = fare,
                pickupKm = legs[0].distanceKm,
                pickupMin = legs[0].durationMin,
                tripKm = legs[1].distanceKm,
                tripMin = legs[1].durationMin,
                passengerRating = rating,
                routeSummary = routeSummary,
                paymentMethod = paymentMethod,
                hasExtraStop = hasExtraStop
            )
        }
        if (legs.size == 1) {
            return RideOffer(
                fare = fare,
                pickupKm = 0.0,
                pickupMin = 0.0,
                tripKm = legs[0].distanceKm,
                tripMin = legs[0].durationMin,
                passengerRating = rating,
                routeSummary = routeSummary,
                paymentMethod = paymentMethod,
                hasExtraStop = hasExtraStop
            )
        }

        // Fallback em nós isolados exigindo distância e tempo válidos
        val distances = ParserUtils.extractStandaloneDistancesKm(nodesText)
        val durations = ParserUtils.extractStandaloneDurationsMin(nodesText)
        if (distances.isEmpty() || durations.isEmpty()) {
            return null
        }

        val (pKm, tKm) = if (distances.size >= 2) distances[0] to distances[1] else 0.0 to distances[0]
        val (pMin, tMin) = if (durations.size >= 2) durations[0] to durations[1] else 0.0 to durations[0]

        val offer = RideOffer(
            fare = fare,
            pickupKm = pKm,
            pickupMin = pMin,
            tripKm = tKm,
            tripMin = tMin,
            passengerRating = rating,
            routeSummary = routeSummary,
            paymentMethod = paymentMethod,
            hasExtraStop = hasExtraStop
        )
        return offer.takeIf { it.totalKm > 0.0 && it.totalMin > 0.0 }
    }
}
