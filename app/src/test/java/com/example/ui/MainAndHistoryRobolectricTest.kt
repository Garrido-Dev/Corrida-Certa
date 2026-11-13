package com.example.ui

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.HistoryRepository
import com.google.android.material.chip.Chip
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Testes Robolectric de fluxo principal nas telas MainActivity, SettingsActivity e HistoryActivity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainAndHistoryRobolectricTest {

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        HistoryRepository(context).clearAll()
    }

    @Test
    fun `mainActivity simula cenarios do semaforo atualizando veredito e metricas`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val title = activity.findViewById<TextView>(R.id.tvVerdictTitle)
        val grossPerKm = activity.findViewById<TextView>(R.id.tvGrossPerKm)
        val chipAccept = activity.findViewById<Chip>(R.id.chipPresetAccept)
        val chipReject = activity.findViewById<Chip>(R.id.chipPresetReject)

        chipAccept.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(activity.getString(R.string.verdict_accept_title), title.text.toString())
        assertNotEquals(activity.getString(R.string.metric_placeholder), grossPerKm.text.toString())

        chipReject.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(activity.getString(R.string.verdict_reject_title), title.text.toString())
    }

    @Test
    fun `historyActivity exibe estado vazio e alterna para painel de estatisticas`() {
        val intent = HistoryActivity.createIntent(
            ApplicationProvider.getApplicationContext(),
            HistoryTab.LIST
        )
        val activity = Robolectric.buildActivity(HistoryActivity::class.java, intent).setup().get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val listContainer = activity.findViewById<View>(R.id.containerHistoryList)
        val statsContainer = activity.findViewById<View>(R.id.containerHistoryStats)
        val emptyLayout = activity.findViewById<View>(R.id.layoutHistoryEmpty)
        val statsTabButton = activity.findViewById<View>(R.id.btnTabHistoryStats)

        assertEquals(View.VISIBLE, listContainer.visibility)
        assertEquals(View.GONE, statsContainer.visibility)
        assertEquals(View.VISIBLE, emptyLayout.visibility)

        statsTabButton.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.GONE, listContainer.visibility)
        assertEquals(View.VISIBLE, statsContainer.visibility)
    }

    @Test
    fun `settingsActivity abre na secao de metas quando solicitado`() {
        val intent = SettingsActivity.createIntent(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            SettingsSection.GOALS
        )
        val activity = Robolectric.buildActivity(SettingsActivity::class.java, intent).setup().get()

        val vehicleCard = activity.findViewById<View>(R.id.cardVehicleCosts)
        val goalsContainer = activity.findViewById<View>(R.id.containerGoals)

        assertEquals(View.GONE, vehicleCard.visibility)
        assertEquals(View.VISIBLE, goalsContainer.visibility)
    }
}
