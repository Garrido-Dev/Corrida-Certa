package com.example.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.databinding.ActivityAccessibilityGuideBinding
import com.example.service.AccessibilityStatusHelper
import com.example.service.RideMonitorState
import kotlinx.coroutines.launch

/**
 * Tela de instruções para ativação segura do [com.example.service.RideAccessibilityService].
 */
class AccessibilityGuideActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, AccessibilityGuideActivity::class.java)
        }
    }

    private lateinit var binding: ActivityAccessibilityGuideBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAccessibilityGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyEdgeToEdgeInsets()
        binding.toolbarGuide.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.btnOpenAndroidAccessibility.setOnClickListener {
            openSystemAccessibilitySettings()
        }
        binding.btnOpenDiagnostic.setOnClickListener {
            startActivity(DiagnosticActivity.createIntent(this))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                RideMonitorState.isServiceConnected.collect {
                    refreshStatusCard()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatusCard()
    }

    private fun applyEdgeToEdgeInsets() {
        val basePadding = resources.getDimensionPixelSize(R.dimen.spacing_medium_fallback)
        ViewCompat.setOnApplyWindowInsetsListener(binding.guideContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }
        binding.guideScrollContent.setPadding(basePadding, basePadding, basePadding, basePadding)
    }

    private fun refreshStatusCard() {
        val isActive = AccessibilityStatusHelper.isServiceEnabled(this)
        val bgRes = if (isActive) R.color.verdict_accept_bg else R.color.verdict_caution_bg
        val accentRes = if (isActive) R.color.verdict_accept_accent else R.color.verdict_caution_accent
        val textRes = if (isActive) R.color.verdict_accept_text else R.color.verdict_caution_text

        val bgColor = ContextCompat.getColor(this, bgRes)
        val accentColor = ContextCompat.getColor(this, accentRes)
        val textColor = ContextCompat.getColor(this, textRes)

        binding.cardAccessibilityStatus.setCardBackgroundColor(bgColor)
        binding.cardAccessibilityStatus.strokeColor = accentColor
        binding.iconGuideStatusContainer.setBackgroundColor(accentColor)
        binding.tvGuideStatusTitle.setTextColor(textColor)
        binding.tvGuideStatusDesc.setTextColor(textColor)

        if (isActive) {
            binding.tvGuideStatusTitle.setText(R.string.accessibility_status_active)
            binding.tvGuideStatusDesc.setText(R.string.accessibility_status_active_desc)
        } else {
            binding.tvGuideStatusTitle.setText(R.string.accessibility_status_inactive)
            binding.tvGuideStatusDesc.setText(R.string.accessibility_status_inactive_desc)
        }
    }

    private fun openSystemAccessibilitySettings() {
        try {
            startActivity(AccessibilityStatusHelper.createAccessibilitySettingsIntent())
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                R.string.error_open_accessibility_settings,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
