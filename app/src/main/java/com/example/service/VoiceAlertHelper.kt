package com.example.service

import android.content.Context
import android.speech.tts.TextToSpeech
import com.example.R
import com.example.model.DriverSettings
import com.example.model.RideAlertType
import com.example.model.RideAnalysis
import com.example.model.RideOffer
import com.example.model.RideVerdict
import java.text.NumberFormat
import java.util.Locale

/**
 * Gerenciador local de leitura por voz (Text-to-Speech) em português brasileiro.
 * Anuncia apenas corridas verdes (ACEITAR) ou alertas de área bloqueada para manter o trânsito silencioso.
 */
class VoiceAlertHelper(
    context: Context,
    private val onUnavailable: (() -> Unit)? = null
) {

    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var isReady = false
    private var pendingSpeech: String? = null

    init {
        textToSpeech = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val ptBr = Locale.forLanguageTag("pt-BR")
                val langResult = textToSpeech?.setLanguage(ptBr)
                if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                    langResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    isReady = false
                    onUnavailable?.invoke()
                } else {
                    isReady = true
                    textToSpeech?.setSpeechRate(1.12f)
                    pendingSpeech?.let { queued ->
                        pendingSpeech = null
                        speakNow(queued)
                    }
                }
            } else {
                isReady = false
                onUnavailable?.invoke()
            }
        }
    }

    fun speakForDetection(
        settings: DriverSettings,
        analysis: RideAnalysis,
        offer: RideOffer
    ) {
        val message = buildSpeechText(appContext, settings, analysis, offer) ?: return
        speakText(message)
    }

    fun speakText(text: String) {
        if (text.isBlank()) return
        if (!isReady) {
            pendingSpeech = text
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        runCatching {
            textToSpeech?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "corrida_certa_tts_${System.currentTimeMillis()}"
            )
        }
    }

    fun shutdown() {
        pendingSpeech = null
        isReady = false
        runCatching {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
        textToSpeech = null
    }

    companion object {
        /**
         * Constrói a frase de voz apenas quando configurada:
         * 1) Área bloqueada tem prioridade máxima (independente de ganhos).
         * 2) Corrida verde (ACCEPT) anuncia R$/km e nota do passageiro (se disponível).
         * Demais ofertas permanecem silenciosas.
         */
        fun buildSpeechText(
            context: Context,
            settings: DriverSettings,
            analysis: RideAnalysis,
            offer: RideOffer
        ): String? {
            if (!settings.voiceAlertEnabled) return null

            if (RideAlertType.BLOCKED_AREA in analysis.alerts) {
                val match = analysis.blockedAreaMatch
                return if (!match.isNullOrBlank()) {
                    context.getString(R.string.tts_blocked_area_named, match)
                } else {
                    context.getString(R.string.tts_blocked_area)
                }
            }

            if (analysis.verdict == RideVerdict.ACCEPT) {
                val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
                val perKmFormatted = currencyFormat.format(analysis.grossPerKm)
                val rating = offer.passengerRating
                return if (rating != null) {
                    val ratingFormatted = String.format(Locale.forLanguageTag("pt-BR"), "%.2f", rating)
                    context.getString(R.string.tts_green_ride_with_rating, perKmFormatted, ratingFormatted)
                } else {
                    context.getString(R.string.tts_green_ride, perKmFormatted)
                }
            }

            return null
        }
    }
}
