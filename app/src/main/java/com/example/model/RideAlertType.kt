package com.example.model

/**
 * Motivos e alertas operacionais identificados pelo motor [com.example.engine.RideAnalyzer].
 */
enum class RideAlertType {
    /** Destino ou região da corrida corresponde a uma área bloqueada pelo motorista. */
    BLOCKED_AREA,

    /** Valor bruto abaixo do mínimo por corrida configurado. */
    BELOW_MIN_FARE,

    /** Deslocamento até o embarque excede o limite configurado. */
    LONG_PICKUP,

    /** Nota do passageiro está abaixo da nota mínima desejada. */
    LOW_RATING,

    /** Corrida com pagamento em dinheiro. */
    CASH_PAYMENT,

    /** Corrida possui parada intermediária adicional. */
    EXTRA_STOP
}
