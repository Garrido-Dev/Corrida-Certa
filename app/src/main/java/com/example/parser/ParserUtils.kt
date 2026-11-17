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

    // Rótulos monetários que pertencem a ganhos acumulados/saldo, não ao valor da oferta
    private val NON_FARE_MONEY_HINTS = listOf(
        "ganhos",
        "hoje",
        "saldo",
        "bônus",
        "bonus",
        "carteira",
        "meta",
        "desconto",
        "taxa"
    )

    // Valor monetário brasileiro (ex.: R$ 24,50 / R$24.50 / R$ 1.250,00), ignorando taxas como R$ 2,10/km ou R$ 45/h
    private val FARE_REGEX = Regex(
        pattern = """(?<!\+\s{0,2})(?:R\$|BRL)\s*(\d{1,3}(?:\.\d{3})*,\d{1,2}|\d{1,4}(?:[.,]\d{1,2})?)(?!\s*/\s*(?:km|h|hora|min))""",
        option = RegexOption.IGNORE_CASE
    )

    private val CURRENCY_ONLY_REGEX = Regex("""^(?:R\$|BRL)\s*$""", RegexOption.IGNORE_CASE)
    private val STANDALONE_AMOUNT_REGEX = Regex("""^(\d{1,3}(?:\.\d{3})*,\d{1,2}|\d{1,4}(?:[.,]\d{1,2})?)$""")

    // Par duração + distância na mesma linha (ex.: "4 min (1,2 km)", "1 h 10 min • 32,5 km", "1 h (60 km)")
    private val TIME_THEN_DIST_REGEX = Regex(
        pattern = """(?:(\d{1,2})\s*h(?:oras?)?(?:\s*(\d{1,3})\s*min(?:utos?|s)?)?|(\d{1,3})\s*min(?:utos?|s)?)\b[^\n\r]{0,36}?(\d{1,4}(?:[.,]\d{1,2})?)\s*(km|m(?:etros?)?)\b""",
        option = RegexOption.IGNORE_CASE
    )

    // Par distância + duração na mesma linha (ex.: "1,2 km · 4 min" ou "850 m (3 min)")
    private val DIST_THEN_TIME_REGEX = Regex(
        pattern = """(\d{1,4}(?:[.,]\d{1,2})?)\s*(km|m(?:etros?)?)\b[^\n\r]{0,36}?(?:(\d{1,2})\s*h(?:oras?)?(?:\s*(\d{1,3})\s*min(?:utos?|s)?)?|(\d{1,3})\s*min(?:utos?|s)?)\b""",
        option = RegexOption.IGNORE_CASE
    )

    // Distância isolada (ex.: "8,5 km" ou "850 m")
    private val DISTANCE_ONLY_REGEX = Regex(
        pattern = """(\d{1,4}(?:[.,]\d{1,2})?)\s*(km|m(?:etros?)?)\b""",
        option = RegexOption.IGNORE_CASE
    )

    // Duração isolada (ex.: "18 min", "1 h 05 min", "2 h")
    private val DURATION_ONLY_REGEX = Regex(
        pattern = """(?:(\d{1,2})\s*h(?:oras?)?(?:\s*(\d{1,3})\s*min(?:utos?|s)?)?|(\d{1,3})\s*min(?:utos?|s)?)\b""",
        option = RegexOption.IGNORE_CASE
    )

    private val PURE_DURATION_LINE_REGEX = Regex(
        pattern = """^\s*~?(?:(\d{1,2})\s*h(?:oras?)?(?:\s*(\d{1,3})\s*min(?:utos?|s)?)?|(\d{1,3})\s*min(?:utos?|s)?)\s*(?:de\s+dist[aâ]ncia|at[eé]\s+voc[eê]|de\s+viagem)?\s*$""",
        option = RegexOption.IGNORE_CASE
    )

    private val PURE_DISTANCE_LINE_REGEX = Regex(
        pattern = """^\s*\(?\s*~?(\d{1,4}(?:[.,]\d{1,2})?)\s*(km|m(?:etros?)?)\s*\)?\s*(?:de\s+dist[aâ]ncia|de\s+viagem)?\s*$""",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Divide nós multilinha (comuns em cards Compose / semantics mescladas) em linhas limpas.
     */
    fun normalizeNodesText(nodesText: List<String>): List<String> {
        val normalized = mutableListOf<String>()
        for (raw in nodesText) {
            raw.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { line -> normalized.add(line) }
        }
        return normalized
    }

    fun isLikelyNonOfferScreen(nodesText: List<String>): Boolean {
        val lines = normalizeNodesText(nodesText)
        val joined = lines.joinToString(" ").lowercase()
        val hasNonOfferKeyword = NON_OFFER_KEYWORDS.any { keyword -> joined.contains(keyword) }
        if (!hasNonOfferKeyword) return false

        // Se houver sinais fortes de oferta ativa sobreposta (ex.: aba oculta ao fundo), não bloqueia
        val hasActiveOfferSignals = lines.any { line ->
            val lower = line.lowercase()
            lower == "aceitar" ||
                lower == "aceitar corrida" ||
                lower.contains("corresponder") ||
                lower.contains("de distância") ||
                lower.contains("de distancia") ||
                lower.contains("de viagem") ||
                lower.contains("até o passageiro") ||
                lower.contains("ate o passageiro")
        } || extractRouteLegs(lines).isNotEmpty()

        return !hasActiveOfferSignals
    }

    /**
     * Extrai o valor principal da corrida em reais dentro de uma faixa realista,
     * aceitando inclusive nós separados ("R$" seguido de "24,50") e evitando pílulas de ganhos.
     */
    fun extractFare(nodesText: List<String>): Double? {
        val lines = normalizeNodesText(nodesText)
        val candidates = mutableListOf<Pair<Int, Double>>()

        for (i in lines.indices) {
            val trimmed = lines[i]
            val lower = trimmed.lowercase()
            val prevLower = lines.getOrNull(i - 1)?.lowercase().orEmpty()
            val isNonOfferMoney = NON_FARE_MONEY_HINTS.any { hint ->
                lower.contains(hint) || prevLower.contains(hint)
            }
            if (isNonOfferMoney) continue

            val match = FARE_REGEX.find(trimmed)
            if (match != null) {
                val value = parsePtNumber(match.groupValues[1])
                if (value != null && value in 3.0..2500.0) {
                    candidates.add(i to value)
                    continue
                }
            }

            if (CURRENCY_ONLY_REGEX.matches(trimmed) && i + 1 < lines.size) {
                val nextLine = lines[i + 1]
                val nextMatch = STANDALONE_AMOUNT_REGEX.find(nextLine)
                if (nextMatch != null) {
                    val value = parsePtNumber(nextMatch.groupValues[1])
                    if (value != null && value in 3.0..2500.0) {
                        candidates.add(i to value)
                    }
                }
            }
        }

        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first().second

        // Se houver mais de um valor na tela (ex.: ganhos do dia no topo + tarifa da oferta),
        // prioriza o candidato mais próximo de sinais típicos do card da oferta.
        val best = candidates.maxByOrNull { (index, _) ->
            val windowStart = (index - 4).coerceAtLeast(0)
            val windowEnd = (index + 5).coerceAtMost(lines.lastIndex)
            var score = 0
            for (j in windowStart..windowEnd) {
                val l = lines[j].lowercase()
                if (l.contains("aceitar") || l.contains("corresponder")) score += 4
                if (l.contains("uber") || l.contains("comfort") || l.contains("black") ||
                    l.contains("flash") || l.contains("99") || l.contains("indrive")
                ) score += 2
                if (l.contains("km") || l.contains("min") || l.contains("★") || l.contains("⭐")) score += 2
                if (l.contains("dinheiro") || l.contains("parada")) score += 1
            }
            score
        }
        return best?.second ?: candidates.first().second
    }

    /**
     * Extrai pares (distância, duração) quando aparecem combinados no mesmo nó ou em par imediato.
     */
    fun extractRouteLegs(nodesText: List<String>): List<RouteLeg> {
        val lines = normalizeNodesText(nodesText)
        val legs = mutableListOf<RouteLeg>()
        val consumedAdjacentIndices = mutableSetOf<Int>()

        for (i in lines.indices) {
            if (i in consumedAdjacentIndices) continue
            val text = lines[i]
            var matchedAnyInLine = false

            TIME_THEN_DIST_REGEX.findAll(text).forEach { match ->
                val totalMin = parseDurationMinutes(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                val distValue = parsePtNumber(match.groupValues[4])
                val unit = match.groupValues[5]
                if (distValue != null && totalMin > 0.0) {
                    val distKm = toKilometers(distValue, unit)
                    if (distKm > 0.0) {
                        legs.add(RouteLeg(distanceKm = distKm, durationMin = totalMin))
                        matchedAnyInLine = true
                    }
                }
            }

            if (!matchedAnyInLine) {
                DIST_THEN_TIME_REGEX.findAll(text).forEach { match ->
                    val distValue = parsePtNumber(match.groupValues[1])
                    val unit = match.groupValues[2]
                    val totalMin = parseDurationMinutes(match.groupValues[3], match.groupValues[4], match.groupValues[5])
                    if (distValue != null && totalMin > 0.0) {
                        val distKm = toKilometers(distValue, unit)
                        if (distKm > 0.0) {
                            legs.add(RouteLeg(distanceKm = distKm, durationMin = totalMin))
                            matchedAnyInLine = true
                        }
                    }
                }
            }

            // Trata nós adjacentes separados apenas em tempo seguido imediatamente de distância (ex.: "4 min" + "(1,2 km)")
            if (!matchedAnyInLine && i + 1 < lines.size) {
                val durMatch = PURE_DURATION_LINE_REGEX.matchEntire(text)
                val distMatch = PURE_DISTANCE_LINE_REGEX.matchEntire(lines[i + 1])
                if (durMatch != null && distMatch != null) {
                    val totalMin = parseDurationMinutes(durMatch.groupValues[1], durMatch.groupValues[2], durMatch.groupValues[3])
                    val distVal = parsePtNumber(distMatch.groupValues[1])
                    val unit = distMatch.groupValues[2]
                    if (distVal != null && totalMin > 0.0) {
                        val distKm = toKilometers(distVal, unit)
                        if (distKm > 0.0) {
                            legs.add(RouteLeg(distanceKm = distKm, durationMin = totalMin))
                            consumedAdjacentIndices.add(i + 1)
                        }
                    }
                }
            }
        }
        return legs
    }

    fun extractStandaloneDistancesKm(nodesText: List<String>): List<Double> {
        val lines = normalizeNodesText(nodesText)
        val results = mutableListOf<Double>()
        for (text in lines) {
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
        val lines = normalizeNodesText(nodesText)
        val results = mutableListOf<Double>()
        for (text in lines) {
            DURATION_ONLY_REGEX.findAll(text).forEach { match ->
                val totalMin = parseDurationMinutes(match.groupValues[1], match.groupValues[2], match.groupValues[3])
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
        "pagamento em dinheiro", "pagamento no app", "com parada", "exclusivo"
    )

    fun extractPassengerRating(nodesText: List<String>): Double? {
        val lines = normalizeNodesText(nodesText)
        for (text in lines) {
            if (text.length > 48) continue
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
        val joined = normalizeNodesText(nodesText).joinToString(" ").lowercase()
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
        val joined = normalizeNodesText(nodesText).joinToString(" ")
        return EXTRA_STOP_REGEX.containsMatchIn(joined)
    }

    fun extractRouteSummary(nodesText: List<String>): String? {
        val lines = normalizeNodesText(nodesText)
        val candidates = mutableListOf<String>()
        for (line in lines) {
            if (line.length < 4 || line.length > 90) continue
            val lower = line.lowercase()

            if (lower in UI_NOISE_ADDRESS_LABELS) continue
            if (FARE_REGEX.containsMatchIn(line) || CURRENCY_ONLY_REGEX.matches(line)) continue
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
        val totalMin = parseDurationMinutes(match.groupValues[1], match.groupValues[2], match.groupValues[3])
        return totalMin.takeIf { it > 0.0 }
    }

    private fun parseDurationMinutes(hoursStr: String, minsAfterHoursStr: String, minsOnlyStr: String): Double {
        val hours = hoursStr.toIntOrNull() ?: 0
        val mins = (minsAfterHoursStr.ifEmpty { minsOnlyStr }).toIntOrNull() ?: 0
        return (hours * 60 + mins).toDouble()
    }

    private fun parsePtNumber(raw: String): Double? {
        val trimmed = raw.trim()
        val normalized = if (trimmed.contains(',') && trimmed.contains('.')) {
            trimmed.replace(".", "").replace(',', '.')
        } else {
            trimmed.replace(',', '.')
        }
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun toKilometers(value: Double, unit: String): Double {
        return if (unit.lowercase().startsWith("km")) value else value / 1000.0
    }
}
