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

    // Nota do passageiro (ex.: "★ 4,88", "4.92 ★", "Nota 4.75", "4,85")
    private val RATING_WITH_STAR_OR_LABEL_REGEX = Regex(
        pattern = """(?:[★⭐]|nota[:\s]+)\s*([1-5][.,]\d{1,2})|([1-5][.,]\d{1,2})\s*[★⭐]""",
        option = RegexOption.IGNORE_CASE
    )
    private val RATING_STANDALONE_REGEX = Regex(
        pattern = """^\s*([1-5][.,]\d{1,2})\s*$"""
    )

    // Indicador de parada extra na oferta
    private val EXTRA_STOP_REGEX = Regex(
        pattern = """\b(?:\d+\s*paradas?|com\s+parada|parada\s+(?:extra|intermedi[aá]ria|adicional)|m[uú]ltiplos?\s+destinos?)\b""",
        option = RegexOption.IGNORE_CASE
    )

    private val UI_NOISE_ADDRESS_LABELS = setOf(
        "aceitar", "recusar", "rejeitar", "concordar", "oferecer", "solicitar",
        "uberx", "uber x", "comfort", "uber black", "black", "flash", "uber flash",
        "99pop", "99 pop", "99comfort", "99 moto", "99moto", "pop", "indrive",
        "dinheiro", "em espécie", "em especie", "pix", "cartão", "cartao",
        "pagamento em dinheiro", "pagamento no app", "com parada"
    )

    fun extractPassengerRating(nodesText: List<String>): Double? {
        for (raw in nodesText) {
            val text = raw.trim()
            if (text.isEmpty() || text.length > 48) continue
            val lower = text.lowercase()
            if (lower.contains("r$") || lower.contains("km") || lower.contains("min") || lower.contains("x")) {
                continue
            }

            val starMatch = RATING_WITH_STAR_OR_LABEL_REGEX.find(text)
            if (starMatch != null) {
                val numStr = starMatch.groupValues[1].ifEmpty { starMatch.groupValues[2] }
                val rating = parsePtNumber(numStr)
                if (rating != null && rating in 1.0..5.0) {
                    return rating
                }
            }

            val standaloneMatch = RATING_STANDALONE_REGEX.find(text)
            if (standaloneMatch != null) {
                val rating = parsePtNumber(standaloneMatch.groupValues[1])
                if (rating != null && rating in 1.0..5.0) {
                    return rating
                }
            }
        }
        return null
    }

    fun extractPaymentMethod(nodesText: List<String>): com.example.model.PaymentMethod {
        val joined = nodesText.joinToString(" ").lowercase()
        if (joined.contains("dinheiro") ||
            joined.contains("em espécie") ||
            joined.contains("em especie")
        ) {
            return com.example.model.PaymentMethod.CASH
        }
        if (joined.contains("cartão") ||
            joined.contains("cartao") ||
            joined.contains("pix") ||
            joined.contains("no app") ||
            joined.contains("carteira")
        ) {
            return com.example.model.PaymentMethod.DIGITAL
        }
        return com.example.model.PaymentMethod.UNKNOWN
    }

    fun extractHasExtraStop(nodesText: List<String>): Boolean {
        val joined = nodesText.joinToString(" ")
        return EXTRA_STOP_REGEX.containsMatchIn(joined)
    }

    fun extractRouteSummary(nodesText: List<String>): String? {
        val candidates = mutableListOf<String>()
        for (raw in nodesText) {
            val line = raw.trim()
            if (line.length < 4 || line.length > 90) continue
            val lower = line.lowercase()

            if (lower in UI_NOISE_ADDRESS_LABELS) continue
            if (FARE_REGEX.containsMatchIn(line)) continue
            if (TIME_THEN_DIST_REGEX.containsMatchIn(line) || DIST_THEN_TIME_REGEX.containsMatchIn(line)) continue
            if (RATING_STANDALONE_REGEX.matches(line) || RATING_WITH_STAR_OR_LABEL_REGEX.containsMatchIn(line)) continue
            if (EXTRA_STOP_REGEX.matches(line)) continue

            val onlyMetricOrDigits = Regex("""^[\d.,\s+\-*/()xkmhminr$:]+$""", RegexOption.IGNORE_CASE).matches(line)
            if (onlyMetricOrDigits) continue
            if (!line.any { it.isLetter() }) continue

            // Evita linhas que sejam apenas rótulo curto de busca/viagem sem endereço
            val looksLikePureMetric = (DISTANCE_ONLY_REGEX.containsMatchIn(line) || DURATION_ONLY_REGEX.containsMatchIn(line)) &&
                !line.contains(",") && !line.contains(" - ") && !line.contains("rua", ignoreCase = true) &&
                !line.contains("av", ignoreCase = true) && !line.contains("bairro", ignoreCase = true)
            if (looksLikePureMetric) continue

            if (!candidates.contains(line)) {
                candidates.add(line)
            }
            if (candidates.size >= 3) break
        }

        if (candidates.isEmpty()) return null
        val joined = candidates.joinToString(" → ")
        return if (joined.length > 140) joined.take(137) + "..." else joined
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
