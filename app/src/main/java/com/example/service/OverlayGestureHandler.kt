package com.example.service

import kotlin.math.abs

/**
 * Ações resultantes do processamento de gestos no overlay flutuante.
 */
sealed interface OverlayGestureAction {
    data object None : OverlayGestureAction
    data object Tap : OverlayGestureAction
    data class Move(val newX: Int, val newY: Int) : OverlayGestureAction
    data class SwipeDismiss(val directionSign: Int) : OverlayGestureAction
}

/**
 * Máquina de estados pura para distinguir toque (abrir detalhes),
 * arraste livre e swipe horizontal para fechamento do card flutuante.
 */
class OverlayGestureHandler(
    private val touchSlopPx: Float,
    private val swipeDismissDistancePx: Float,
    private val swipeQuickDistancePx: Float,
    private val quickSwipeMaxDurationMs: Long = 280L
) {
    private var startWindowX = 0
    private var startWindowY = 0
    private var startRawX = 0f
    private var startRawY = 0f
    private var downTimeMs = 0L
    private var isDragging = false

    fun onDown(windowX: Int, windowY: Int, rawX: Float, rawY: Float, eventTimeMs: Long) {
        startWindowX = windowX
        startWindowY = windowY
        startRawX = rawX
        startRawY = rawY
        downTimeMs = eventTimeMs
        isDragging = false
    }

    fun onMove(rawX: Float, rawY: Float): OverlayGestureAction {
        val dx = rawX - startRawX
        val dy = rawY - startRawY

        if (!isDragging && (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx)) {
            isDragging = true
        }

        return if (isDragging) {
            OverlayGestureAction.Move(
                newX = (startWindowX + dx).toInt(),
                newY = (startWindowY + dy).toInt()
            )
        } else {
            OverlayGestureAction.None
        }
    }

    fun onUp(rawX: Float, rawY: Float, eventTimeMs: Long): OverlayGestureAction {
        val dx = rawX - startRawX
        val dy = rawY - startRawY
        val elapsed = (eventTimeMs - downTimeMs).coerceAtLeast(0L)

        if (!isDragging && abs(dx) <= touchSlopPx && abs(dy) <= touchSlopPx) {
            return OverlayGestureAction.Tap
        }

        // Swipe horizontal longo ou arremesso rápido predominantemente horizontal fecha o overlay
        val isHorizontalDominant = abs(dx) > abs(dy) * 1.3f
        val isLongSwipe = abs(dx) >= swipeDismissDistancePx
        val isQuickFlick = isHorizontalDominant &&
            abs(dx) >= swipeQuickDistancePx &&
            elapsed <= quickSwipeMaxDurationMs

        if (isLongSwipe || isQuickFlick) {
            val direction = if (dx >= 0f) 1 else -1
            return OverlayGestureAction.SwipeDismiss(direction)
        }

        return OverlayGestureAction.None
    }
}
