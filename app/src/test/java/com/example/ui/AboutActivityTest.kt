package com.example.ui

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import com.example.BuildConfig
import com.example.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Testes Robolectric da [AboutActivity] validando exibição dinâmica da versão,
 * identificador do aplicativo e atalho para reabrir o tutorial.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class AboutActivityTest {

    @Test
    fun `exibe versionName, versionCode e applicationId vindos do BuildConfig`() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val versionText = activity.findViewById<TextView>(R.id.tvAboutVersion).text.toString()
                val techDetailsText = activity.findViewById<TextView>(R.id.tvAboutTechDetails).text.toString()

                assertTrue(
                    "Versão exibida deveria conter versionName (${BuildConfig.VERSION_NAME}), mas foi: $versionText",
                    versionText.contains(BuildConfig.VERSION_NAME)
                )
                assertTrue(
                    "Versão exibida deveria conter versionCode (${BuildConfig.VERSION_CODE}), mas foi: $versionText",
                    versionText.contains(BuildConfig.VERSION_CODE.toString())
                )
                assertTrue(
                    "Detalhes técnicos deveriam conter applicationId (${BuildConfig.APPLICATION_ID}), mas foi: $techDetailsText",
                    techDetailsText.contains(BuildConfig.APPLICATION_ID)
                )
            }
        }
    }

    @Test
    fun `clicar em rever tutorial abre a OnboardingActivity`() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.btnAboutTutorial).performClick()
                val nextIntent = Shadows.shadowOf(activity).nextStartedActivity
                assertTrue(
                    "Deveria abrir OnboardingActivity ao clicar no botão de tutorial",
                    nextIntent?.component?.className == OnboardingActivity::class.java.name
                )
            }
        }
    }
}
