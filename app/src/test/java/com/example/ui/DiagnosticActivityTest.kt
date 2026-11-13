package com.example.ui

import android.os.Looper
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import com.example.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Testes Robolectric da [DiagnosticActivity] validando simulação de leitura de ofertas
 * para Uber, 99 e inDrive e exibição de erro em textos inválidos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class DiagnosticActivityTest {

    @Test
    fun `carregar exemplo da Uber executa parser e exibe valores extraidos`() {
        ActivityScenario.launch(DiagnosticActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.btnDiagLoadSample).performClick()
                Shadows.shadowOf(Looper.getMainLooper()).idle()

                val result = activity.findViewById<TextView>(R.id.tvDiagnosticResult).text.toString()
                assertTrue("Resultado deveria identificar Uber: $result", result.contains("Uber"))
                assertTrue("Resultado deveria conter distância de viagem 8.5/8,5: $result", result.contains("8,5") || result.contains("8.5"))
            }
        }
    }

    @Test
    fun `texto sem oferta valida exibe orientacao de falha para o app selecionado`() {
        ActivityScenario.launch(DiagnosticActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.btnDiag99).performClick()
                activity.findViewById<EditText>(R.id.etDiagnosticRawText).setText("Tela inicial sem corrida")
                activity.findViewById<android.view.View>(R.id.btnDiagRunTest).performClick()
                Shadows.shadowOf(Looper.getMainLooper()).idle()

                val result = activity.findViewById<TextView>(R.id.tvDiagnosticResult).text.toString()
                assertTrue("Deveria informar falha de reconhecimento para 99: $result", result.contains("99"))
            }
        }
    }
}
