package com.example.model

/**
 * Veredito do semáforo para decisão rápida do motorista.
 */
enum class RideVerdict {
    /** Corrida atinge todas as metas e gera lucro positivo (Verde). */
    ACCEPT,

    /** Corrida até 20% abaixo de qualquer meta, mantendo lucro positivo (Amarelo). */
    CAUTION,

    /** Corrida abaixo do limite mínimo ou com prejuízo operacional (Vermelho). */
    REJECT
}
