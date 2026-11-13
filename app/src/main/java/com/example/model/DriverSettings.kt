package com.example.model

/**
 * Configurações operacionais do veículo e metas financeiras do motorista.
 *
 * @property fuelPrice Preço do combustível por litro (R$/L).
 * @property consumption Consumo médio do veículo (km/L).
 * @property maintenance Custo médio de manutenção por km rodado (R$/km).
 * @property depreciation Custo médio de depreciação do veículo por km (R$/km).
 * @property targetPerKm Meta mínima de ganho bruto por km (R$/km).
 * @property targetPerHour Meta mínima de ganho bruto por hora (R$/h).
 */
data class DriverSettings(
    val fuelPrice: Double = 5.89,
    val consumption: Double = 10.0,
    val maintenance: Double = 0.15,
    val depreciation: Double = 0.20,
    val targetPerKm: Double = 2.00,
    val targetPerHour: Double = 50.00
) {
    /**
     * Custo operacional real por km rodado.
     * Fórmula: (preço do combustível / consumo) + manutenção + depreciação.
     */
    val costPerKm: Double
        get() {
            val fuelCostPerKm = if (consumption > 0.0) fuelPrice / consumption else 0.0
            return fuelCostPerKm + maintenance + depreciation
        }
}
