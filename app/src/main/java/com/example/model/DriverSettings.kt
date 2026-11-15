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
 * @property dailyGoal Meta diária de lucro líquido (R$/dia).
 * @property dailyFixedCost Custo fixo diário do veículo (aluguel ou parcela/dia, R$).
 * @property minFare Valor mínimo bruto aceito por corrida (R$; 0 desativa).
 * @property maxPickupKm Distância máxima de busca até o passageiro (km; 0 desativa).
 * @property minPassengerRating Nota mínima aceita do passageiro (ex.: 4.65; 0 desativa).
 * @property blockedAreas Lista separada por vírgula ou quebra de linha de bairros/termos bloqueados.
 * @property voiceAlertEnabled Ativa leitura por voz (TTS) para corridas verdes ou área bloqueada.
 * @property alertCashRide Destaca e sinaliza ofertas com pagamento em dinheiro.
 * @property alertExtraStop Destaca e sinaliza ofertas com parada extra.
 */
data class DriverSettings(
    val fuelPrice: Double = 5.89,
    val consumption: Double = 10.0,
    val maintenance: Double = 0.15,
    val depreciation: Double = 0.20,
    val targetPerKm: Double = 2.00,
    val targetPerHour: Double = 50.00,
    val dailyGoal: Double = 250.00,
    val dailyFixedCost: Double = 0.0,
    val minFare: Double = 0.0,
    val maxPickupKm: Double = 0.0,
    val minPassengerRating: Double = 0.0,
    val blockedAreas: String = "",
    val voiceAlertEnabled: Boolean = true,
    val alertCashRide: Boolean = true,
    val alertExtraStop: Boolean = true
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

    /**
     * Lista limpa de bairros ou termos bloqueados configurados pelo motorista.
     */
    fun blockedAreaTokens(): List<String> {
        return blockedAreas
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.length >= 2 }
    }
}
