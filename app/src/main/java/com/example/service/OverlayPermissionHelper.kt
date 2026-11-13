package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Utilitário para verificar e solicitar a permissão SYSTEM_ALERT_WINDOW (sobrepor a outros apps).
 */
object OverlayPermissionHelper {

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun createOverlayPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
