package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.HistoryRepository
import com.example.data.SettingsRepository
import com.example.engine.RideAnalyzer
import com.example.model.DriverSettings
import com.example.model.RideAnalysis
import com.example.model.RideOffer
import com.example.parser.RideParserRegistry
import com.example.parser.SupportedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Serviço de Acessibilidade que monitora mudanças de tela exclusivamente nos apps Uber, 99 e inDrive,
 * extrai ofertas via regex/heurísticas e publica a análise sem armazenar ou enviar dados externamente.
 */
class RideAccessibilityService : AccessibilityService() {

    companion object {
        private const val DEBOUNCE_MS = 180L
        private const val DEDUP_WINDOW_MS = 15_000L
        private const val MAX_COLLECTED_NODES = 250
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val parserRegistry = RideParserRegistry()
    private val rideAnalyzer = RideAnalyzer()

    private var historyRepository: HistoryRepository? = null
    @Volatile
    private var currentSettings: DriverSettings = DriverSettings()
    private var parseJob: Job? = null
    private var lastRecordedKey: String? = null
    private var lastRecordedAtMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        RideMonitorState.setServiceConnected(true)
        MonitoringNotificationHelper.showMonitoringNotification(applicationContext)

        historyRepository = HistoryRepository(applicationContext)
        val settingsRepository = SettingsRepository(applicationContext)
        serviceScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                currentSettings = settings
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        // Ignora eventos internos do próprio Rota Pro para não limpar o último card lido
        if (packageName == applicationContext.packageName) {
            return
        }

        // Se o motorista saiu dos apps de corrida suportados, limpa eventual leitura antiga
        if (!parserRegistry.isSupportedPackage(packageName)) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                parseJob?.cancel()
                RideMonitorState.clearDetectedRide()
            }
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val rootNode = rootInActiveWindow ?: return
        val extractedTexts = collectNodeTexts(rootNode)

        parseJob?.cancel()
        parseJob = serviceScope.launch {
            delay(DEBOUNCE_MS)
            val app = SupportedApp.fromPackageName(packageName)
            val parsed = parserRegistry.parse(packageName, extractedTexts)
            if (parsed == null) {
                RideMonitorState.updateDiagnosticStatus(
                    DiagnosticCaptureStatus(
                        packageName = packageName,
                        app = app,
                        parsedSuccessfully = false
                    )
                )
                // Fallback seguro: se não conseguir parsear com confiança, não exibe nada
                RideMonitorState.clearDetectedRide()
            } else {
                val analysis = rideAnalyzer.analyze(parsed.offer, currentSettings)
                RideMonitorState.updateDiagnosticStatus(
                    DiagnosticCaptureStatus(
                        packageName = packageName,
                        app = parsed.app,
                        parsedSuccessfully = true,
                        summary = String.format(
                            java.util.Locale.forLanguageTag("pt-BR"),
                            "R$ %.2f • %.1f km • %.0f min",
                            parsed.offer.fare,
                            analysis.totalKm,
                            analysis.totalMin
                        )
                    )
                )
                RideMonitorState.updateDetectedRide(
                    DetectedRideAnalysis(
                        app = parsed.app,
                        offer = parsed.offer,
                        analysis = analysis
                    )
                )
                recordIfNotDuplicate(parsed.app, parsed.offer, analysis)
                OverlayService.startOrUpdate(this@RideAccessibilityService)
            }
        }
    }

    private suspend fun recordIfNotDuplicate(
        app: SupportedApp,
        offer: RideOffer,
        analysis: RideAnalysis
    ) {
        val now = System.currentTimeMillis()
        val key = "${app.name}|${offer.fare}|${analysis.totalKm}|${analysis.totalMin}"
        if (key == lastRecordedKey && (now - lastRecordedAtMs) < DEDUP_WINDOW_MS) {
            return
        }
        lastRecordedKey = key
        lastRecordedAtMs = now
        historyRepository?.recordRide(
            appName = app.displayName,
            offer = offer,
            analysis = analysis,
            timestamp = now
        )
    }

    override fun onInterrupt() {
        RideMonitorState.setServiceConnected(false)
        MonitoringNotificationHelper.cancelMonitoringNotification(applicationContext)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        RideMonitorState.setServiceConnected(false)
        MonitoringNotificationHelper.cancelMonitoringNotification(applicationContext)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        RideMonitorState.setServiceConnected(false)
        MonitoringNotificationHelper.cancelMonitoringNotification(applicationContext)
        serviceScope.cancel()
    }

    /**
     * Percorre a árvore de nós de acessibilidade em largura com limite de segurança.
     */
    private fun collectNodeTexts(root: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_COLLECTED_NODES) {
            val node = queue.removeFirst()
            visited++

            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                texts.add(text)
            }

            val description = node.contentDescription?.toString()?.trim()
            if (!description.isNullOrEmpty() && description != text) {
                texts.add(description)
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                queue.add(child)
            }
        }
        return texts
    }
}
