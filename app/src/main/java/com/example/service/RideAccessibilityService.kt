package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.HistoryRepository
import com.example.data.SettingsRepository
import com.example.engine.RideAnalyzer
import com.example.model.DriverSettings
import com.example.model.RideAnalysis
import com.example.model.RideOffer
import com.example.parser.ParsedRideOffer
import com.example.parser.RideParserRegistry
import com.example.parser.SupportedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Serviço de Acessibilidade que monitora mudanças de tela exclusivamente nos apps Uber, 99 e inDrive,
 * extrai ofertas via regex/heurísticas e publica a análise sem armazenar ou enviar dados externamente.
 */
class RideAccessibilityService : AccessibilityService() {

    companion object {
        private const val THROTTLE_WINDOW_MS = 220L
        private const val DEDUP_WINDOW_MS = 15_000L
        private const val MAX_COLLECTED_NODES = 1500
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val parserRegistry = RideParserRegistry()
    private val rideAnalyzer = RideAnalyzer()

    private var historyRepository: HistoryRepository? = null
    @Volatile
    private var currentSettings: DriverSettings = DriverSettings()
    private var pendingInspectionJob: Job? = null
    private var lastInspectionAtMs: Long = 0L
    private var lastRecordedKey: String? = null
    private var lastRecordedAtMs: Long = 0L
    private var lastShownKey: String? = null
    private var lastShownAtMs: Long = 0L

    private data class WindowTextSnapshot(
        val packageName: String,
        val texts: List<String>
    )

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
        if (event == null) return
        val eventPackage = event.packageName?.toString()

        // Ignora eventos internos do próprio Corrida Certa para não interferir no card ativo
        if (eventPackage == applicationContext.packageName) {
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        val eventSupported = eventPackage != null && parserRegistry.isSupportedPackage(eventPackage)
        if (!eventSupported && !hasVisibleSupportedWindow()) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastInspectionAtMs
        if (elapsed >= THROTTLE_WINDOW_MS) {
            pendingInspectionJob?.cancel()
            pendingInspectionJob = null
            lastInspectionAtMs = now
            inspectCurrentWindows(event)
        } else if (pendingInspectionJob?.isActive != true) {
            val remaining = (THROTTLE_WINDOW_MS - elapsed).coerceAtLeast(50L)
            pendingInspectionJob = serviceScope.launch {
                delay(remaining)
                lastInspectionAtMs = SystemClock.elapsedRealtime()
                inspectCurrentWindows(event = null)
            }
        }
    }

    private fun hasVisibleSupportedWindow(): Boolean {
        val activePackage = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (parserRegistry.isSupportedPackage(activePackage)) {
            return true
        }
        return runCatching { windows }.getOrNull().orEmpty().any { window ->
            val pkg = window.root?.packageName?.toString()
            parserRegistry.isSupportedPackage(pkg)
        }
    }

    private fun inspectCurrentWindows(event: AccessibilityEvent?) {
        val snapshots = collectSupportedWindowSnapshots(event)
        if (snapshots.isEmpty()) return

        serviceScope.launch {
            val result = withContext(Dispatchers.Default) {
                evaluateSnapshots(snapshots)
            }
            val parsed = result.parsed
            if (parsed == null) {
                RideMonitorState.updateDiagnosticStatus(
                    DiagnosticCaptureStatus(
                        packageName = result.primaryPackage,
                        app = SupportedApp.fromPackageName(result.primaryPackage),
                        parsedSuccessfully = false,
                        nodeCount = result.totalTexts
                    )
                )
                return@launch
            }

            val analysis = withContext(Dispatchers.Default) {
                rideAnalyzer.analyze(parsed.offer, currentSettings)
            }

            RideMonitorState.updateDiagnosticStatus(
                DiagnosticCaptureStatus(
                    packageName = result.primaryPackage,
                    app = parsed.app,
                    parsedSuccessfully = true,
                    summary = String.format(
                        java.util.Locale.forLanguageTag("pt-BR"),
                        "R$ %.2f • %.1f km • %.0f min",
                        parsed.offer.fare,
                        analysis.totalKm,
                        analysis.totalMin
                    ),
                    nodeCount = result.totalTexts
                )
            )

            val now = System.currentTimeMillis()
            val offerKey = "${parsed.app.name}|${parsed.offer.fare}|${analysis.totalKm}|${analysis.totalMin}"
            val shouldNotifyOverlay = offerKey != lastShownKey || (now - lastShownAtMs) >= DEDUP_WINDOW_MS

            if (shouldNotifyOverlay) {
                lastShownKey = offerKey
                lastShownAtMs = now
                RideMonitorState.updateDetectedRide(
                    DetectedRideAnalysis(
                        app = parsed.app,
                        offer = parsed.offer,
                        analysis = analysis,
                        detectedAtMillis = now
                    )
                )
                OverlayService.startOrUpdate(this@RideAccessibilityService)
            }

            withContext(Dispatchers.IO) {
                recordIfNotDuplicate(parsed.app, parsed.offer, analysis)
            }
        }
    }

    private data class EvaluationOutcome(
        val primaryPackage: String,
        val parsed: ParsedRideOffer?,
        val totalTexts: Int
    )

    private fun evaluateSnapshots(snapshots: List<WindowTextSnapshot>): EvaluationOutcome {
        val totalTexts = snapshots.sumOf { it.texts.size }
        val primaryPackage = snapshots.first().packageName

        // Tenta primeiro cada janela isoladamente (prioriza popups/cards flutuantes no topo)
        for (snapshot in snapshots) {
            val parsed = parserRegistry.parse(snapshot.packageName, snapshot.texts)
            if (parsed != null) {
                return EvaluationOutcome(snapshot.packageName, parsed, totalTexts)
            }
        }

        // Fallback combinando textos do mesmo aplicativo quando o card está dividido em camadas
        snapshots.groupBy { it.packageName }.forEach { (pkg, pkgSnapshots) ->
            if (pkgSnapshots.size > 1) {
                val merged = pkgSnapshots.flatMap { it.texts }
                val parsed = parserRegistry.parse(pkg, merged)
                if (parsed != null) {
                    return EvaluationOutcome(pkg, parsed, totalTexts)
                }
            }
        }

        return EvaluationOutcome(primaryPackage, null, totalTexts)
    }

    private fun collectSupportedWindowSnapshots(event: AccessibilityEvent?): List<WindowTextSnapshot> {
        val snapshots = mutableListOf<WindowTextSnapshot>()
        val seenRoots = mutableListOf<AccessibilityNodeInfo>()

        fun addRootIfSupported(root: AccessibilityNodeInfo?) {
            if (root == null) return
            val pkg = root.packageName?.toString() ?: return
            if (!parserRegistry.isSupportedPackage(pkg)) return
            if (seenRoots.any { it == root }) return
            seenRoots.add(root)
            val texts = collectNodeTexts(root)
            if (texts.isNotEmpty()) {
                snapshots.add(WindowTextSnapshot(packageName = pkg, texts = texts))
            }
        }

        // 1. Janelas interativas ordenadas da camada mais alta (popup/overlay) para o fundo
        val interactiveWindows = runCatching { windows }.getOrNull().orEmpty()
            .sortedByDescending { it.layer }
        for (window in interactiveWindows) {
            addRootIfSupported(runCatching { window.root }.getOrNull())
        }

        // 2. Origem direta do evento (caso venha de janela flutuante não listada em rootInActiveWindow)
        val eventSource = runCatching { event?.source }.getOrNull()
        if (eventSource != null) {
            val sourceWindowRoot = runCatching { eventSource.window?.root }.getOrNull()
            addRootIfSupported(sourceWindowRoot ?: eventSource)
        }

        // 3. Janela ativa padrão
        addRootIfSupported(runCatching { rootInActiveWindow }.getOrNull())

        return snapshots
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
     * Percorre a árvore de nós de acessibilidade em largura com limite amplo para apps com mapa/Compose.
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
