package com.example.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.model.RideAlertType
import com.example.model.RideAnalysis
import com.example.model.RideVerdict

/**
 * Emite feedback tátil discreto para ofertas classificadas como ACEITAR (verde)
 * e um pulso duplo de atenção quando uma área bloqueada é detectada.
 */
object HapticFeedbackHelper {

    private const val GREEN_PULSE_DURATION_MS = 180L
    private val BLOCKED_ALERT_PATTERN = longArrayOf(0L, 120L, 80L, 180L)

    fun vibrateForVerdict(context: Context, verdict: RideVerdict) {
        if (verdict != RideVerdict.ACCEPT) return
        vibrateEffect(
            context,
            VibrationEffect.createOneShot(GREEN_PULSE_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    fun vibrateForAnalysis(context: Context, analysis: RideAnalysis) {
        when {
            RideAlertType.BLOCKED_AREA in analysis.alerts -> {
                vibrateEffect(context, VibrationEffect.createWaveform(BLOCKED_ALERT_PATTERN, -1))
            }
            analysis.verdict == RideVerdict.ACCEPT -> {
                vibrateEffect(
                    context,
                    VibrationEffect.createOneShot(
                        GREEN_PULSE_DURATION_MS,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            }
        }
    }

    private fun vibrateEffect(context: Context, effect: VibrationEffect) {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(effect)
            }
        }
    }
}
