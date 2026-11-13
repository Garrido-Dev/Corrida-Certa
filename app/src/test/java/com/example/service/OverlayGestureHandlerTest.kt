package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes unitários dos gestos do card flutuante: toque (detalhes), arraste e swipe para fechar.
 */
class OverlayGestureHandlerTest {

    private val handler = OverlayGestureHandler(
        touchSlopPx = 12f,
        swipeDismissDistancePx = 180f,
        swipeQuickDistancePx = 70f,
        quickSwipeMaxDurationMs = 280L
    )

    @Test
    fun `toque curto dentro do touchSlop dispara Tap para expandir detalhes`() {
        handler.onDown(windowX = 32, windowY = 180, rawX = 100f, rawY = 200f, eventTimeMs = 1000L)
        val move = handler.onMove(rawX = 104f, rawY = 203f)
        val up = handler.onUp(rawX = 105f, rawY = 204f, eventTimeMs = 1090L)

        assertEquals(OverlayGestureAction.None, move)
        assertEquals(OverlayGestureAction.Tap, up)
    }

    @Test
    fun `arraste lento reposiciona a janela sem fechar por swipe`() {
        handler.onDown(windowX = 32, windowY = 180, rawX = 100f, rawY = 200f, eventTimeMs = 1000L)
        val move = handler.onMove(rawX = 180f, rawY = 340f)
        val up = handler.onUp(rawX = 180f, rawY = 340f, eventTimeMs = 1650L)

        assertEquals(OverlayGestureAction.Move(newX = 112, newY = 320), move)
        assertEquals(OverlayGestureAction.None, up)
    }

    @Test
    fun `swipe horizontal longo ou rapido dispara SwipeDismiss`() {
        // Swipe longo para a direita
        handler.onDown(windowX = 32, windowY = 180, rawX = 100f, rawY = 200f, eventTimeMs = 1000L)
        handler.onMove(rawX = 320f, rawY = 210f)
        val longSwipe = handler.onUp(rawX = 320f, rawY = 210f, eventTimeMs = 1500L)
        assertEquals(OverlayGestureAction.SwipeDismiss(directionSign = 1), longSwipe)

        // Flick rápido para a esquerda
        handler.onDown(windowX = 200, windowY = 180, rawX = 300f, rawY = 200f, eventTimeMs = 2000L)
        handler.onMove(rawX = 210f, rawY = 205f)
        val quickSwipe = handler.onUp(rawX = 210f, rawY = 205f, eventTimeMs = 2140L)
        assertTrue(quickSwipe is OverlayGestureAction.SwipeDismiss)
        assertEquals(-1, (quickSwipe as OverlayGestureAction.SwipeDismiss).directionSign)
    }
}
