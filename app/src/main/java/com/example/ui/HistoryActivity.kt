package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.HistoryPeriodFilter
import com.example.data.HistoryRepository
import com.example.data.SettingsRepository
import com.example.databinding.ActivityHistoryBinding
import com.example.engine.DailyGoalProgress
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Tela dedicada ao histórico local de corridas analisadas (Hoje / 7 dias / 30 dias)
 * e ao painel de estatísticas de desempenho do motorista.
 */
class HistoryActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_INITIAL_TAB = "extra_initial_tab"

        fun createIntent(
            context: Context,
            initialTab: HistoryTab = HistoryTab.LIST
        ): Intent {
            return Intent(context, HistoryActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_TAB, initialTab.name)
            }
        }
    }

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryRideAdapter

    private val viewModel: HistoryViewModel by viewModels {
        val tabName = intent.getStringExtra(EXTRA_INITIAL_TAB)
        val initialTab = tabName?.let {
            runCatching { HistoryTab.valueOf(it) }.getOrNull()
        } ?: HistoryTab.LIST

        HistoryViewModel.Factory(
            historyRepository = HistoryRepository(applicationContext),
            settingsRepository = SettingsRepository(applicationContext),
            initialTab = initialTab
        )
    }

    private val currencyFormat: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyEdgeToEdgeInsets()
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeUiState()
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.historyContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }
    }

    private fun setupToolbar() {
        binding.toolbarHistory.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.btnClearHistory.setOnClickListener {
            confirmClearHistory()
        }
    }

    private fun setupRecyclerView() {
        adapter = HistoryRideAdapter { ride ->
            viewModel.deleteRide(ride.id)
            Toast.makeText(this, R.string.msg_ride_deleted, Toast.LENGTH_SHORT).show()
        }
        binding.rvHistoryRides.layoutManager = LinearLayoutManager(this)
        binding.rvHistoryRides.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnEmptyGoSimulate.setOnClickListener {
            finish()
        }

        binding.toggleHistoryTab.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnTabHistoryList -> viewModel.selectTab(HistoryTab.LIST)
                R.id.btnTabHistoryStats -> viewModel.selectTab(HistoryTab.STATS)
            }
        }

        binding.chipGroupPeriod.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipFilterToday -> viewModel.selectPeriod(HistoryPeriodFilter.TODAY)
                R.id.chipFilter7Days -> viewModel.selectPeriod(HistoryPeriodFilter.LAST_7_DAYS)
                R.id.chipFilter30Days -> viewModel.selectPeriod(HistoryPeriodFilter.LAST_30_DAYS)
            }
        }

        binding.chipGroupAppFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipFilterAppAll -> viewModel.selectAppFilter(HistoryAppFilter.ALL)
                R.id.chipFilterAppUber -> viewModel.selectAppFilter(HistoryAppFilter.UBER)
                R.id.chipFilterApp99 -> viewModel.selectAppFilter(HistoryAppFilter.APP_99)
                R.id.chipFilterAppInDrive -> viewModel.selectAppFilter(HistoryAppFilter.INDRIVE)
                R.id.chipFilterAppSimulator -> viewModel.selectAppFilter(HistoryAppFilter.SIMULATOR)
            }
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderActiveTab(state.activeTab)
                    renderPeriodChip(state.periodFilter)
                    renderAppChip(state.appFilter)
                    renderDailyGoal(state.dailyGoalProgress)

                    adapter.submitList(state.rides)
                    val isEmpty = state.rides.isEmpty()
                    binding.rvHistoryRides.visibility = if (isEmpty) View.GONE else View.VISIBLE
                    binding.layoutHistoryEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    binding.btnClearHistory.visibility = if (isEmpty) View.GONE else View.VISIBLE

                    val stats = state.statistics
                    binding.tvStatAcceptanceRate.text = getString(
                        R.string.stats_percent_format,
                        stats.acceptanceRatePercent
                    )
                    binding.tvStatAvgPerKm.text = currencyFormat.format(stats.averageGrossPerKm)
                    binding.tvStatTotalProfit.text = currencyFormat.format(stats.totalProfit)
                    binding.tvStatTimeSaved.text = getString(
                        R.string.stats_time_saved_format,
                        stats.timeSavedMinutes
                    )
                    binding.tvStatBreakdown.text = getString(
                        R.string.stats_breakdown_format,
                        stats.totalCount,
                        stats.acceptedCount,
                        stats.cautionCount,
                        stats.rejectedCount
                    )
                }
            }
        }
    }

    private fun renderActiveTab(tab: HistoryTab) {
        when (tab) {
            HistoryTab.LIST -> {
                if (binding.toggleHistoryTab.checkedButtonId != R.id.btnTabHistoryList) {
                    binding.toggleHistoryTab.check(R.id.btnTabHistoryList)
                }
                binding.containerHistoryList.visibility = View.VISIBLE
                binding.containerHistoryStats.visibility = View.GONE
            }
            HistoryTab.STATS -> {
                if (binding.toggleHistoryTab.checkedButtonId != R.id.btnTabHistoryStats) {
                    binding.toggleHistoryTab.check(R.id.btnTabHistoryStats)
                }
                binding.containerHistoryList.visibility = View.GONE
                binding.containerHistoryStats.visibility = View.VISIBLE
            }
        }
    }

    private fun renderPeriodChip(filter: HistoryPeriodFilter) {
        val targetId = when (filter) {
            HistoryPeriodFilter.TODAY -> R.id.chipFilterToday
            HistoryPeriodFilter.LAST_7_DAYS -> R.id.chipFilter7Days
            HistoryPeriodFilter.LAST_30_DAYS -> R.id.chipFilter30Days
        }
        if (binding.chipGroupPeriod.checkedChipId != targetId) {
            binding.chipGroupPeriod.check(targetId)
        }
    }

    private fun renderAppChip(filter: HistoryAppFilter) {
        val targetId = when (filter) {
            HistoryAppFilter.ALL -> R.id.chipFilterAppAll
            HistoryAppFilter.UBER -> R.id.chipFilterAppUber
            HistoryAppFilter.APP_99 -> R.id.chipFilterApp99
            HistoryAppFilter.INDRIVE -> R.id.chipFilterAppInDrive
            HistoryAppFilter.SIMULATOR -> R.id.chipFilterAppSimulator
        }
        if (binding.chipGroupAppFilter.checkedChipId != targetId) {
            binding.chipGroupAppFilter.check(targetId)
        }
    }

    private fun renderDailyGoal(progress: DailyGoalProgress) {
        binding.tvHistoryDailyGoalProgress.text = getString(
            R.string.daily_goal_progress_format,
            currencyFormat.format(progress.todayProfit),
            currencyFormat.format(progress.dailyGoal),
            progress.progressPercent
        )
        binding.progressHistoryDailyGoal.progress = progress.progressPercent
        val indicatorColor = ContextCompat.getColor(
            this,
            if (progress.isGoalReached) R.color.verdict_accept_accent else R.color.md_theme_secondary
        )
        binding.progressHistoryDailyGoal.setIndicatorColor(indicatorColor)
        binding.tvHistoryDailyGoalProgress.setTextColor(indicatorColor)

        binding.tvHistoryDailyGoalSubtitle.text = if (progress.isGoalReached) {
            getString(R.string.daily_goal_reached)
        } else {
            getString(
                R.string.daily_goal_remaining_format,
                currencyFormat.format(progress.remainingAmount)
            )
        }
    }

    private fun confirmClearHistory() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_clear_history_title)
            .setMessage(R.string.dialog_clear_history_message)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_confirm_clear) { _, _ ->
                viewModel.clearAllHistory()
                Toast.makeText(this, R.string.msg_history_cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
