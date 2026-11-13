package com.example.parser

/**
 * Utilitários puros de regex e heurística para extração de valores monetários,
 * distâncias e durações em português brasileiro nos cards de oferta.
 */
internal object ParserUtils {

    data class RouteLeg(
        val distanceKm: Double,
        val durationMin: Double
    )

    // Ignora telas que claramente são de histórico, extrato, ganhos ou configurações
    private val NON_OFFER_KEYWORDS = listOf(
        "ganhos de hoje",
        "extrato",
        "histórico de viagens",
        "historico de viagens",
        "detalhes do pagamento",
        "recibo",
        "saldo disponível",
        "saldo disponivel",
        "solicitar saque",
        "configurações da conta",
        "configuracoes da conta"
    )

    // Valor monetário brasileiro (ex.: R$ 24,50 / R$24.50), ignorando taxas como R$ 2,10/km ou R$ 45/h
    private val FARE_REGEX = Regex(
        pattern = """(?<!\+\s{0,2})(?:R\$|BRL)\s*(\d{1,4}(?:[.,]\d{1,2})?)(?!\s*/\s*(?:km|h|hora|min))""",
        option = RegexOption.IGNORE_CASE
    )

    // Par duração + distância na mesma linha (ex.: "4 min (1,2 km)" ou "1 h 10 min • 32,5 km")
    private val TIME_THEN_DIST_REGEX = Regex(
        pattern = """(?:(\d{1,2})\s*h(?:oras?)?\s*)?(\d{1,3})\s*min(?:utos?|s)?\b[^\n\r]{0,20}?(\d{1,4}(?:[.,]\d{1,2})?)\s*(km|m(?:etros?)?)\b""",
        option = RegexOption.IGNORE_CASE
    )

    // Par distância + duração na mesma linha (ex.: "1,2 km · 4 min" ou "850 m (3 min)")
    private val DIST_THEN_TIME_REGEX = Regex(
        pattern = """(\d{1,4}(?:[.,]\d{1,2})?)\s*(km|m(?:etros?)?)\b[^\n\r]{0,20}?(?:(\d{1,2})\s*h(?:oras?)?\s*)?(\d{1,3})\s*min(?:utos?|s)?\b""",
        option = RegexOption.IGNORE_CASE
    )

    // Distância isolada (ex.: "8,5 km" ou "850 m")
    private val DISTANCE_ONLY_REGEX = Regex(
        pattern = """(\d{1,4}(?:[.,]\d{1,2})?)\s*(km|m(?:etros?)?)\b""",
        option = RegexOption.IGNORE_CASE
    )

    // Duração isolada (ex.: "18 min" ou "1 h 05 min")
    private val DURATION_ONLY_REGEX = Regex(
        pattern = """(?:(\d{1,2})\s*h(?:oras?)?\s*)?(\d{1,3})\s*min(?:utos?|s)?\b""",
        option = RegexOption.IGNORE_CASE
    )

    fun isLikelyNonOfferScreen(nodesText: List<String>): Boolean {
        val joined = nodesText.joinToString(" ").lowercase()
        return NON_OFFER_KEYWORDS.any { keyword -> joined.contains(keyword) }
    }

    /**
     * Extrai o valor principal da corrida em reais dentro de uma faixa realista.
     */
    fun extractFare(nodesText: List<String>): Double? {
        for (node in nodesText) {
            val trimmed = node.trim()
            if (trimmed.isEmpty()) continue
            val match = FARE_REGEX.find(trimmed) ?: continue
            val value = parsePtNumber(match.groupValues[1]) ?: continue
            if (value in 3.0..2500.0) {
                return value
            }
        }
        return null
    }

    /**
     * Extrai pares (distância, duração) quando aparecem combinados no mesmo nó ou linha.
     */
    fun extractRouteLegs(nodesText: List<String>): List<RouteLeg> {
        val legs = mutableListOf<RouteLeg>()
        for (raw in nodesText) {
            val text = raw.trim()
            if (text.isEmpty()) continue

            val timeFirstMatch = TIME_THEN_DIST_REGEX.find(text)
            if (timeFirstMatch != null) {
                val hours = timeFirstMatch.groupValues[1].toIntOrNull() ?: 0
                val mins = timeFirstMatch.groupValues[2].toIntOrNull() ?: 0
                val distValue = parsePtNumber(timeFirstMatch.groupValues[3])
                val unit = timeFirstMatch.groupValues[4]
                if (distValue != null) {
                    val distKm = toKilometers(distValue, unit)
                    val totalMin = (hours * 60 + mins).toDouble()
                    if (distKm > 0.0 && totalMin > 0.0) {
                        legs.add(RouteLeg(distanceKm = distKm, durationMin = totalMin))
                        continue
                    }
                }
            }

            val distFirstMatch = DIST_THEN_TIME_REGEX.find(text)
            if (distFirstMatch != null) {
                val distValue = parsePtNumber(distFirstMatch.groupValues[1])
                val unit = distFirstMatch.groupValues[2]
                val hours = distFirstMatch.groupValues[3].toIntOrNull() ?: 0
                val mins = distFirstMatch.groupValues[4].toIntOrNull() ?: 0
                if (distValue != null) {
                    val distKm = toKilometers(distValue, unit)
                    val totalMin = (hours * 60 + mins).toDouble()
                    if (distKm > 0.0 && totalMin > 0.0) {
                        legs.add(RouteLeg(distanceKm = distKm, durationMin = totalMin))
                    }
                }
            }
        }
        return legs
    }

    fun extractStandaloneDistancesKm(nodesText: List<String>): List<Double> {
        val results = mutableListOf<Double>()
        for (raw in nodesText) {
            val text = raw.trim()
            if (text.isEmpty()) continue
            DISTANCE_ONLY_REGEX.findAll(text).forEach { match ->
                val number = parsePtNumber(match.groupValues[1]) ?: return@forEach
                val km = toKilometers(number, match.groupValues[2])
                if (km > 0.0 && km <= 500.0) {
                    results.add(km)
                }
            }
        }
        return results
    }

    fun extractStandaloneDurationsMin(nodesText: List<String>): List<Double> {
        val results = mutableListOf<Double>()
        for (raw in nodesText) {
            val text = raw.trim()
            if (text.isEmpty()) continue
            DURATION_ONLY_REGEX.findAll(text).forEach { match ->
                val hours = match.groupValues[1].toIntOrNull() ?: 0
                val mins = match.groupValues[2].toIntOrNull() ?: 0
                val totalMin = (hours * 60 + mins).toDouble()
                if (totalMin in 1.0..600.0) {
                    results.add(totalMin)
                }
            }
        }
        return results
    }

    fun extractFirstDistanceKm(text: String): Double? {
        val match = DISTANCE_ONLY_REGEX.find(text) ?: return null
        val value = parsePtNumber(match.groupValues[1]) ?: return null
        return toKilometers(value, match.groupValues[2]).takeIf { it > 0.0 }
    }

    fun extractFirstDurationMin(text: String): Double? {
        val match = DURATION_ONLY_REGEX.find(text) ?: return null
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val mins = match.groupValues[2].toIntOrNull() ?: 0
        val totalMin = (hours * 60 + mins).toDouble()
        return totalMin.takeIf { it > 0.0 }
    }

    private fun parsePtNumber(raw: String): Double? {
        return raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun toKilometers(value: Double, unit: String): Double {
        return if (unit.lowercase().startsWith("km")) value else value / 1000.0
    }
}
