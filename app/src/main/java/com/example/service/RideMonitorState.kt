package com.example.service

import com.example.model.RideAnalysis
import com.example.model.RideOffer
import com.example.parser.SupportedApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Representa uma oferta detectada e analisada automaticamente em tempo real.
 */
data class DetectedRideAnalysis(
    val app: SupportedApp?,
    val offer: RideOffer,
    val analysis: RideAnalysis,
    val detectedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Barramento reativo em memória entre o [RideAccessibilityService], a UI e o futuro OverlayService.
 * Todos os dados permanecem estritamente em memória no dispositivo.
 */
object RideMonitorState {

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    private val _detectedRide = MutableStateFlow<DetectedRideAnalysis?>(null)
    val detectedRide: StateFlow<DetectedRideAnalysis?> = _detectedRide.asStateFlow()

    fun setServiceConnected(connected: Boolean) {
        _isServiceConnected.value = connected
        if (!connected) {
            _detectedRide.value = null
        }
    }

    fun updateDetectedRide(detected: DetectedRideAnalysis) {
        _detectedRide.value = detected
    }

    fun clearDetectedRide() {
        _detectedRide.value = null
    }
}
