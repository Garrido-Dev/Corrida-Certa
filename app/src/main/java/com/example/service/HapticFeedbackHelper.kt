package com.example.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.model.RideVerdict

/**
 * Emite feedback tátil discreto exclusivamente para ofertas classificadas como ACEITAR (verde),
 * ajudando o motorista a reconhecer boas corridas sem tirar os olhos da via.
 */
object HapticFeedbackHelper {

    private const val GREEN_PULSE_DURATION_MS = 180L

    fun vibrateForVerdict(context: Context, verdict: RideVerdict) {
        if (verdict != RideVerdict.ACCEPT) return

        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                val effect = VibrationEffect.createOneShot(
                    GREEN_PULSE_DURATION_MS,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                vibrator.vibrate(effect)
            }
        }
    }
}
