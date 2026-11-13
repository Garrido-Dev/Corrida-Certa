package com.example.ui

import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.ViewFlipper
import com.example.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Testes Robolectric validando o fluxo das 3 etapas do OnboardingActivity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingActivityTest {

    @Test
    fun `navega pelas 3 telas do onboarding exibindo controles esperados`() {
        val controller = Robolectric.buildActivity(OnboardingActivity::class.java).setup()
        val activity = controller.get()

        val flipper = activity.findViewById<ViewFlipper>(R.id.viewFlipperOnboarding)
        val stepIndicator = activity.findViewById<TextView>(R.id.tvOnboardingStepIndicator)
        val btnBack = activity.findViewById<Button>(R.id.btnOnboardingBack)
        val btnNext = activity.findViewById<Button>(R.id.btnOnboardingNext)

        // Etapa 1: O que é o Rota Pro
        assertEquals(0, flipper.displayedChild)
        assertEquals("Passo 1 de 3", stepIndicator.text.toString())
        assertEquals(View.INVISIBLE, btnBack.visibility)
        assertEquals(activity.getString(R.string.action_onboarding_next), btnNext.text.toString())

        // Avança para Etapa 2: Acessibilidade
        btnNext.performClick()
        assertEquals(1, flipper.displayedChild)
        assertEquals("Passo 2 de 3", stepIndicator.text.toString())
        assertEquals(View.VISIBLE, btnBack.visibility)

        // Avança para Etapa 3: Overlay
        btnNext.performClick()
        assertEquals(2, flipper.displayedChild)
        assertEquals("Passo 3 de 3", stepIndicator.text.toString())
        assertEquals(activity.getString(R.string.action_onboarding_finish), btnNext.text.toString())

        // Volta para Etapa 2
        btnBack.performClick()
        assertEquals(1, flipper.displayedChild)
        assertEquals("Passo 2 de 3", stepIndicator.text.toString())
    }
}
