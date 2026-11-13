package com.example.ui

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.example.R
import com.example.data.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Teste de interface da [MainActivity] utilizando Espresso sobre Robolectric (JVM local)
 * para validar o carregamento do formulário manual e todos os campos de entrada da corrida.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class MainActivityTest {

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Garante que a MainActivity não redirecione para o Onboarding durante o teste
        SettingsRepository(context).setOnboardingCompleted(true)
    }

    @Test
    fun `formulario manual carrega corretamente com todos os campos de input da corrida`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Cartão do semáforo carregado no topo
            onView(withId(R.id.cardVerdict))
                .perform(nestedScrollTo())
                .check(matches(isDisplayed()))
            onView(withId(R.id.tvVerdictTitle))
                .perform(nestedScrollTo())
                .check(matches(isDisplayed()))

            // Todos os campos obrigatórios do formulário manual presentes e habilitados
            val inputFieldIds = listOf(
                R.id.tilFare to R.id.etFare,
                R.id.tilPickupKm to R.id.etPickupKm,
                R.id.tilPickupMin to R.id.etPickupMin,
                R.id.tilTripKm to R.id.etTripKm,
                R.id.tilTripMin to R.id.etTripMin
            )

            inputFieldIds.forEach { (layoutId, editTextId) ->
                onView(withId(layoutId))
                    .perform(nestedScrollTo())
                    .check(matches(isDisplayed()))

                onView(withId(editTextId))
                    .perform(nestedScrollTo())
                    .check(matches(isDisplayed()))
                    .check(matches(isEnabled()))
            }

            // Botões de ação do formulário
            onView(withId(R.id.btnClear))
                .perform(nestedScrollTo())
                .check(matches(isDisplayed()))
                .check(matches(isEnabled()))

            onView(withId(R.id.btnAnalyze))
                .perform(nestedScrollTo())
                .check(matches(isDisplayed()))
                .check(matches(isEnabled()))
        }
    }

    @Test
    fun `preencher formulario manual e clicar em analisar atualiza veredito na tela`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.etFare))
                .perform(nestedScrollTo(), replaceText("32.00"))
            onView(withId(R.id.etPickupKm))
                .perform(nestedScrollTo(), replaceText("1.0"))
            onView(withId(R.id.etPickupMin))
                .perform(nestedScrollTo(), replaceText("3"))
            onView(withId(R.id.etTripKm))
                .perform(nestedScrollTo(), replaceText("9.0"))
            onView(withId(R.id.etTripMin))
                .perform(nestedScrollTo(), replaceText("22"))

            onView(withId(R.id.btnAnalyze))
                .perform(nestedScrollTo(), click())

            var expectedAcceptTitle = ""
            scenario.onActivity { activity ->
                expectedAcceptTitle = activity.getString(R.string.verdict_accept_title)
            }

            onView(withId(R.id.tvVerdictTitle))
                .perform(nestedScrollTo())
                .check(matches(withText(expectedAcceptTitle)))
        }
    }

    /**
     * ViewAction auxiliar para rolar até qualquer descendente dentro de [NestedScrollView],
     * convertendo corretamente as coordenadas do elemento filho para o container rolável.
     */
    private fun nestedScrollTo(): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> {
            return isDescendantOfA(isAssignableFrom(NestedScrollView::class.java))
        }

        override fun getDescription(): String = "Rolar NestedScrollView até o elemento alvo"

        override fun perform(uiController: UiController, view: View) {
            var parent = view.parent
            while (parent != null && parent !is NestedScrollView) {
                parent = parent.parent
            }
            val scrollView = parent as? NestedScrollView ?: return
            val rect = Rect(0, 0, view.width, view.height)
            scrollView.offsetDescendantRectToMyCoords(view, rect)
            scrollView.scrollTo(0, rect.top)
            uiController.loopMainThreadUntilIdle()
        }
    }
}
