package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.databinding.OverlayRideCardBinding
import com.example.model.RideVerdict
import com.example.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Serviço responsável por exibir o card flutuante de semáforo sobre os apps de transporte
 * utilizando WindowManager com TYPE_APPLICATION_OVERLAY.
 */
class OverlayService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "rota_pro_overlay_channel"
        private const val NOTIFICATION_ID = 4001
        const val ACTION_START_OR_UPDATE = "com.example.action.START_OR_UPDATE_OVERLAY"
        const val ACTION_HIDE = "com.example.action.HIDE_OVERLAY"

        fun startOrUpdate(context: Context) {
            if (!OverlayPermissionHelper.hasOverlayPermission(context)) return
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_START_OR_UPDATE
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private lateinit var themedContext: Context
    private var binding: OverlayRideCardBinding? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isViewAttached = false
    private var isDetailsExpanded = false

    private val currencyFormat: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        themedContext = ContextThemeWrapper(this, R.style.Theme_RotaPro)
        createNotificationChannel()
        startAsForeground()
        observeDetectedRide()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            removeOverlayView()
            RideMonitorState.clearDetectedRide()
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        val current = RideMonitorState.detectedRide.value
        if (current != null && OverlayPermissionHelper.hasOverlayPermission(this)) {
            showOrUpdateOverlay(current)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeOverlayView()
        serviceScope.cancel()
    }

    private fun startAsForeground() {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.overlay_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.overlay_notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_layers)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun observeDetectedRide() {
        serviceScope.launch {
            RideMonitorState.detectedRide.collect { detected ->
                if (detected == null || !OverlayPermissionHelper.hasOverlayPermission(this@OverlayService)) {
                    removeOverlayView()
                } else {
                    showOrUpdateOverlay(detected)
                }
            }
        }
    }

    private fun showOrUpdateOverlay(detected: DetectedRideAnalysis) {
        if (!OverlayPermissionHelper.hasOverlayPermission(this)) return

        val viewBinding = ensureOverlayView()
        bindRideData(viewBinding, detected)

        if (!isViewAttached) {
            val params = layoutParams ?: return
            try {
                windowManager.addView(viewBinding.root, params)
                isViewAttached = true
            } catch (_: WindowManager.BadTokenException) {
                isViewAttached = false
            } catch (_: IllegalStateException) {
                isViewAttached = false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureOverlayView(): OverlayRideCardBinding {
        binding?.let { return it }

        val inflater = LayoutInflater.from(themedContext)
        val created = OverlayRideCardBinding.inflate(inflater)

        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * density).toInt()
            y = (120 * density).toInt()
        }

        val touchSlop = ViewConfiguration.get(themedContext).scaledTouchSlop.toFloat()
        val gestureHandler = OverlayGestureHandler(
            touchSlopPx = touchSlop,
            swipeDismissDistancePx = 140f * density,
            swipeQuickDistancePx = 64f * density
        )

        created.cardOverlayRoot.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureHandler.onDown(
                        windowX = params.x,
                        windowY = params.y,
                        rawX = event.rawX,
                        rawY = event.rawY,
                        eventTimeMs = event.eventTime
                    )
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    when (val action = gestureHandler.onMove(event.rawX, event.rawY)) {
                        is OverlayGestureAction.Move -> {
                            params.x = action.newX
                            params.y = action.newY
                            if (isViewAttached) {
                                windowManager.updateViewLayout(created.root, params)
                            }
                        }
                        else -> Unit
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    when (gestureHandler.onUp(event.rawX, event.rawY, event.eventTime)) {
                        OverlayGestureAction.Tap -> {
                            toggleDetails(created)
                        }
                        is OverlayGestureAction.SwipeDismiss -> {
                            dismissFromUserGesture()
                        }
                        else -> Unit
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        created.btnOverlayClose.setOnClickListener {
            dismissFromUserGesture()
        }

        created.btnOverlayOpenApp.setOnClickListener {
            val openDetailsIntent = MainActivity.createFromOverlayIntent(this)
            startActivity(openDetailsIntent)
        }

        binding = created
        layoutParams = params
        return created
    }

    private fun toggleDetails(viewBinding: OverlayRideCardBinding) {
        isDetailsExpanded = !isDetailsExpanded
        viewBinding.layoutOverlayDetails.visibility = if (isDetailsExpanded) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun dismissFromUserGesture() {
        isDetailsExpanded = false
        removeOverlayView()
        RideMonitorState.clearDetectedRide()
    }

    private fun removeOverlayView() {
        val view = binding?.root
        if (view != null && isViewAttached) {
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: IllegalArgumentException) {
                // View já desanexada
            }
        }
        isViewAttached = false
        isDetailsExpanded = false
        binding?.layoutOverlayDetails?.visibility = View.GONE
    }

    private fun bindRideData(viewBinding: OverlayRideCardBinding, detected: DetectedRideAnalysis) {
        val analysis = detected.analysis
        val offer = detected.offer

        val (bgRes, accentRes, textRes, iconRes, verdictLabelRes) = when (analysis.verdict) {
            RideVerdict.ACCEPT -> OverlayStyle(
                R.color.verdict_accept_bg,
                R.color.verdict_accept_accent,
                R.color.verdict_accept_text,
                R.drawable.ic_traffic_accept,
                R.string.overlay_verdict_accept
            )
            RideVerdict.CAUTION -> OverlayStyle(
                R.color.verdict_caution_bg,
                R.color.verdict_caution_accent,
                R.color.verdict_caution_text,
                R.drawable.ic_traffic_caution,
                R.string.overlay_verdict_caution
            )
            RideVerdict.REJECT -> OverlayStyle(
                R.color.verdict_reject_bg,
                R.color.verdict_reject_accent,
                R.color.verdict_reject_text,
                R.drawable.ic_traffic_reject,
                R.string.overlay_verdict_reject
            )
        }

        val bgColor = ContextCompat.getColor(themedContext, bgRes)
        val accentColor = ContextCompat.getColor(themedContext, accentRes)
        val textColor = ContextCompat.getColor(themedContext, textRes)

        viewBinding.cardOverlayRoot.setCardBackgroundColor(bgColor)
        viewBinding.cardOverlayRoot.strokeColor = accentColor
        viewBinding.iconOverlayContainer.setBackgroundColor(accentColor)
        viewBinding.ivOverlayVerdict.setImageResource(iconRes)
        viewBinding.btnOverlayClose.setColorFilter(textColor)

        viewBinding.tvOverlayAppName.text = detected.app?.displayName
            ?: getString(R.string.overlay_app_simulation)
        viewBinding.tvOverlayVerdict.setText(verdictLabelRes)

        viewBinding.tvOverlayPerKm.text = currencyFormat.format(analysis.grossPerKm)
        viewBinding.tvOverlayPerHour.text = currencyFormat.format(analysis.grossPerHour)
        viewBinding.tvOverlayProfit.text = currencyFormat.format(analysis.profit)

        viewBinding.tvOverlayDetailsText.text = getString(
            R.string.overlay_details_format,
            currencyFormat.format(offer.fare),
            analysis.totalKm,
            analysis.totalMin,
            currencyFormat.format(analysis.totalCost),
            currencyFormat.format(analysis.netPerKm)
        )

        val coloredTextViews = listOf(
            viewBinding.tvOverlayAppName,
            viewBinding.tvOverlayVerdict,
            viewBinding.tvOverlayLabelPerKm,
            viewBinding.tvOverlayPerKm,
            viewBinding.tvOverlayLabelPerHour,
            viewBinding.tvOverlayPerHour,
            viewBinding.tvOverlayLabelProfit,
            viewBinding.tvOverlayProfit,
            viewBinding.tvOverlayHint,
            viewBinding.tvOverlayDetailsText
        )
        coloredTextViews.forEach { it.setTextColor(textColor) }
    }

    private data class OverlayStyle(
        val bgRes: Int,
        val accentRes: Int,
        val textRes: Int,
        val iconRes: Int,
        val verdictLabelRes: Int
    )
}
