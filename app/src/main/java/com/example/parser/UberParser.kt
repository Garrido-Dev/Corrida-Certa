package com.example.parser

import com.example.model.RideOffer

/**
 * Parser especializado nos cards de oferta do aplicativo Uber Driver.
 * Padrões comuns: "R$ 24,50", "4 min (1,2 km) de distância" e "16 min (8,5 km) de viagem".
 */
class UberParser : RideOfferParser {

    override val app: SupportedApp = SupportedApp.UBER

    override fun parse(nodesText: List<String>): RideOffer? {
        if (nodesText.isEmpty() || ParserUtils.isLikelyNonOfferScreen(nodesText)) {
            return null
        }

        val fare = ParserUtils.extractFare(nodesText) ?: return null

        // Prioriza pares completos (tempo + distância) na ordem típica da Uber: busca depois viagem
        val legs = ParserUtils.extractRouteLegs(nodesText)
        if (legs.size >= 2) {
            val pickup = legs[0]
            val trip = legs[1]
            return RideOffer(
                fare = fare,
                pickupKm = pickup.distanceKm,
                pickupMin = pickup.durationMin,
                tripKm = trip.distanceKm,
                tripMin = trip.durationMin
            )
        }

        // Caso alternativo: busca exibe apenas tempo (ex.: "3 min") e viagem exibe par completo "15 min (8,0 km)"
        if (legs.size == 1) {
            val trip = legs[0]
            val allDurations = ParserUtils.extractStandaloneDurationsMin(nodesText)
            val pickupMin = allDurations.firstOrNull { it != trip.durationMin } ?: 0.0
            return RideOffer(
                fare = fare,
                pickupKm = 0.0,
                pickupMin = pickupMin,
                tripKm = trip.distanceKm,
                tripMin = trip.durationMin
            )
        }

        // Fallback seguro com distâncias e durações separadas em nós distintos
        val distances = ParserUtils.extractStandaloneDistancesKm(nodesText)
        val durations = ParserUtils.extractStandaloneDurationsMin(nodesText)
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
            tripMin = tripMin
        )
        return offer.takeIf { it.totalKm > 0.0 && it.totalMin > 0.0 }
    }
}
