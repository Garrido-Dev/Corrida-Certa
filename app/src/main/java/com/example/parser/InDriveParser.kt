package com.example.parser

import com.example.model.RideOffer

/**
 * Parser especializado nas ofertas do aplicativo inDrive.
 * Padrões comuns: "R$ 28,00", "~1,4 km (5 min)", "11,2 km (22 min)" ou distâncias com duração total.
 */
class InDriveParser : RideOfferParser {

    override val app: SupportedApp = SupportedApp.INDRIVE

    override fun parse(nodesText: List<String>): RideOffer? {
        if (nodesText.isEmpty() || ParserUtils.isLikelyNonOfferScreen(nodesText)) {
            return null
        }

        val fare = ParserUtils.extractFare(nodesText) ?: return null

        val legs = ParserUtils.extractRouteLegs(nodesText)
        if (legs.size >= 2) {
            return RideOffer(
                fare = fare,
                pickupKm = legs[0].distanceKm,
                pickupMin = legs[0].durationMin,
                tripKm = legs[1].distanceKm,
                tripMin = legs[1].durationMin
            )
        }

        // No inDrive é comum a busca mostrar apenas distância ("~900 m") e a viagem trazer distância + duração
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
