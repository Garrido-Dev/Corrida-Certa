package com.example.model

/**
 * Representa uma oferta de corrida exibida pelo aplicativo de transporte.
 *
 * @property fare Valor bruto pago ao motorista pela corrida (R$).
 * @property pickupKm Distância até o ponto de embarque do passageiro (km).
 * @property pickupMin Tempo estimado até o embarque (minutos).
 * @property tripKm Distância da viagem com o passageiro (km).
 * @property tripMin Duração estimada da viagem com o passageiro (minutos).
 * @property passengerRating Nota média do passageiro exibida pelo app (1.0 a 5.0), quando disponível.
 * @property routeSummary Resumo textual de origem/destino ou bairros extraídos do card da oferta.
 * @property paymentMethod Forma de pagamento identificada na oferta (digital, dinheiro ou desconhecida).
 * @property hasExtraStop Indica se a corrida possui parada intermediária adicional.
 */
data class RideOffer(
    val fare: Double,
    val pickupKm: Double,
    val pickupMin: Double,
    val tripKm: Double,
    val tripMin: Double,
    val passengerRating: Double? = null,
    val routeSummary: String? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.UNKNOWN,
    val hasExtraStop: Boolean = false
) {
    /** Distância total percorrida considerando deslocamento até o passageiro e viagem. */
    val totalKm: Double
        get() = pickupKm + tripKm

    /** Tempo total empenhado na corrida em minutos (busca + viagem). */
    val totalMin: Double
        get() = pickupMin + tripMin
}
