package com.example.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.DriverSettings
import com.example.model.RideAlertType
import com.example.model.RideAnalysis
import com.example.model.RideOffer
import com.example.model.RideVerdict
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Testes da lógica seletiva de síntese de voz (TTS) em [VoiceAlertHelper].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceAlertHelperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `fala resumo com valor por km e nota em corridas verdes`() {
        val settings = DriverSettings(voiceAlertEnabled = true)
        val offer = RideOffer(
            fare = 28.0,
            pickupKm = 1.0,
            pickupMin = 3.0,
            tripKm = 9.0,
            tripMin = 20.0,
            passengerRating = 4.92
        )
        val analysis = RideAnalysis(
            grossPerKm = 2.80,
            grossPerHour = 73.0,
            netPerKm = 1.85,
            profit = 18.5,
            verdict = RideVerdict.ACCEPT
        )

        val speech = VoiceAlertHelper.buildSpeechText(context, settings, analysis, offer)

        assertNotNull(speech)
        assertTrue(speech!!.contains("Corrida verde"))
        assertTrue(speech.contains("4,92") || speech.contains("4.92"))
    }

    @Test
    fun `prioriza aviso de area bloqueada mesmo com tarifa alta`() {
        val settings = DriverSettings(voiceAlertEnabled = true)
        val offer = RideOffer(25.0, 1.0, 3.0, 7.0, 15.0)
        val analysis = RideAnalysis(
            grossPerKm = 3.12,
            grossPerHour = 83.0,
            netPerKm = 2.1,
            profit = 17.0,
            verdict = RideVerdict.REJECT,
            alerts = listOf(RideAlertType.BLOCKED_AREA),
            blockedAreaMatch = "Centro"
        )

        val speech = VoiceAlertHelper.buildSpeechText(context, settings, analysis, offer)

        assertNotNull(speech)
        assertTrue(speech!!.contains("área bloqueada"))
        assertTrue(speech.contains("Centro"))
    }

    @Test
    fun `permanece em silencio para corridas amarelas, recusas comuns ou quando voz desativada`() {
        val enabled = DriverSettings(voiceAlertEnabled = true)
        val disabled = DriverSettings(voiceAlertEnabled = false)
        val offer = RideOffer(18.0, 1.0, 3.0, 8.0, 20.0)

        val cautionAnalysis = RideAnalysis(2.0, 46.0, 1.0, 9.0, RideVerdict.CAUTION)
        assertNull(VoiceAlertHelper.buildSpeechText(context, enabled, cautionAnalysis, offer))

        val normalReject = RideAnalysis(1.2, 30.0, 0.2, 2.0, RideVerdict.REJECT)
        assertNull(VoiceAlertHelper.buildSpeechText(context, enabled, normalReject, offer))

        val green = RideAnalysis(3.0, 75.0, 2.0, 20.0, RideVerdict.ACCEPT)
        assertNull(VoiceAlertHelper.buildSpeechText(context, disabled, green, offer))
    }
}
