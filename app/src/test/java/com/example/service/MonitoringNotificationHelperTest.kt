package com.example.service

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Testes Robolectric da notificação persistente de monitoramento ativo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MonitoringNotificationHelperTest {

    @Test
    fun `exibe e cancela notificacao persistente enquanto servico esta ativo`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowManager = Shadows.shadowOf(manager)

        MonitoringNotificationHelper.showMonitoringNotification(context)

        val channel = manager.getNotificationChannel(MonitoringNotificationHelper.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(1, shadowManager.allNotifications.size)

        MonitoringNotificationHelper.cancelMonitoringNotification(context)
        assertEquals(0, shadowManager.allNotifications.size)
    }
}
