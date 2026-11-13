package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.BuildConfig
import com.example.R
import com.example.databinding.ActivityAboutBinding

/**
 * Tela "Sobre o Rota Pro" exibindo versão dinâmica do build, política de privacidade local
 * e especificações técnicas da arquitetura.
 */
class AboutActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, AboutActivity::class.java)
        }
    }

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyEdgeToEdgeInsets()
        bindVersionInfo()
        setupListeners()
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.aboutContainer) { view, insets ->
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

    private fun bindVersionInfo() {
        binding.tvAboutVersion.text = getString(
            R.string.about_version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )
        binding.tvAboutTechDetails.text = getString(
            R.string.about_tech_desc,
            BuildConfig.APPLICATION_ID
        )
    }

    private fun setupListeners() {
        binding.toolbarAbout.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.btnAboutTutorial.setOnClickListener {
            startActivity(OnboardingActivity.createIntent(this, launchMainOnFinish = false))
        }
    }
}
