package com.example.model

/**
 * Resultado completo da análise de viabilidade de uma corrida.
 *
 * @property grossPerKm Ganho bruto por quilômetro total (R$/km).
 * @property grossPerHour Ganho bruto projetado por hora trabalhada (R$/h).
 * @property netPerKm Lucro líquido por quilômetro rodado após custos (R$/km).
 * @property profit Lucro líquido estimado na corrida (R$).
 * @property verdict Decisão recomendada pelo semáforo.
 * @property totalKm Distância total considerada na análise (busca + viagem).
 * @property totalMin Tempo total considerado na análise (busca + viagem).
 * @property totalCost Custo operacional estimado da corrida (R$).
 */
data class RideAnalysis(
    val grossPerKm: Double,
    val grossPerHour: Double,
    val netPerKm: Double,
    val profit: Double,
    val verdict: RideVerdict,
    val totalKm: Double = 0.0,
    val totalMin: Double = 0.0,
    val totalCost: Double = 0.0
)
