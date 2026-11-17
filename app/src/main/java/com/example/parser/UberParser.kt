package com.example.parser

import com.example.model.RideOffer

/**
 * Parser especializado nos cards de oferta do aplicativo Uber Driver.
 * Padrões comuns: "R$ 24,50", "4 min (1,2 km) de distância" e "16 min (8,5 km) de viagem".
 */
class UberParser : RideOfferParser {

    override val app: SupportedApp = SupportedApp.UBER

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

        // Prioriza pares completos (tempo + distância) na ordem típica da Uber: busca depois viagem
        val legs = ParserUtils.extractRouteLegs(normalized)
        if (legs.size >= 2) {
            val pickup = legs[0]
            val trip = legs[1]
            return RideOffer(
                fare = fare,
                pickupKm = pickup.distanceKm,
                pickupMin = pickup.durationMin,
                tripKm = trip.distanceKm,
                tripMin = trip.durationMin,
                passengerRating = rating,
                routeSummary = routeSummary,
                paymentMethod = paymentMethod,
                hasExtraStop = hasExtraStop
            )
        }

        // Caso alternativo: busca exibe apenas tempo/distância e viagem exibe par completo "15 min (8,0 km)"
        if (legs.size == 1) {
            val trip = legs[0]
            val remainingDurations = ParserUtils.extractStandaloneDurationsMin(normalized).toMutableList()
            remainingDurations.remove(trip.durationMin)
            val remainingDistances = ParserUtils.extractStandaloneDistancesKm(normalized).toMutableList()
            remainingDistances.remove(trip.distanceKm)
            val pickupMin = remainingDurations.firstOrNull() ?: 0.0
            val pickupKm = remainingDistances.firstOrNull() ?: 0.0
            return RideOffer(
                fare = fare,
                pickupKm = pickupKm,
                pickupMin = pickupMin,
                tripKm = trip.distanceKm,
                tripMin = trip.durationMin,
                passengerRating = rating,
                routeSummary = routeSummary,
                paymentMethod = paymentMethod,
                hasExtraStop = hasExtraStop
            )
        }

        // Fallback seguro com distâncias e durações separadas em nós distintos
        val distances = ParserUtils.extractStandaloneDistancesKm(normalized)
        val durations = ParserUtils.extractStandaloneDurationsMin(normalized)
        if (distances.isEmpty() || durations.isEmpty()) {
            return null
        }

        val (pickupKm, tripKm) = if (distances.size >= 2) {
            distances[0] to distances[1]
        } else {
            0.0 to distances[0]
        }

        val (pickupMin, tripMin) = if (durations.size >= 2) {
            durations[0] to durations[1]
        } else {
            0.0 to durations[0]
        }

        val offer = RideOffer(
            fare = fare,
            pickupKm = pickupKm,
            pickupMin = pickupMin,
            tripKm = tripKm,
            tripMin = tripMin,
            passengerRating = rating,
            routeSummary = routeSummary,
            paymentMethod = paymentMethod,
            hasExtraStop = hasExtraStop
        )
        return offer.takeIf { it.totalKm > 0.0 && it.totalMin > 0.0 }
    }
}
