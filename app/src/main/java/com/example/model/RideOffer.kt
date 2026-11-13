package com.example.model

/**
 * Representa uma oferta de corrida exibida pelo aplicativo de transporte.
 *
 * @property fare Valor bruto pago ao motorista pela corrida (R$).
 * @property pickupKm Distância até o ponto de embarque do passageiro (km).
 * @property pickupMin Tempo estimado até o embarque (minutos).
 * @property tripKm Distância da viagem com o passageiro (km).
 * @property tripMin Duração estimada da viagem com o passageiro (minutos).
 */
data class RideOffer(
    val fare: Double,
    val pickupKm: Double,
    val pickupMin: Double,
    val tripKm: Double,
    val tripMin: Double
) {
    /** Distância total percorrida considerando deslocamento até o passageiro e viagem. */
    val totalKm: Double
        get() = pickupKm + tripKm

    /** Tempo total empenhado na corrida em minutos (busca + viagem). */
    val totalMin: Double
        get() = pickupMin + tripMin
}
