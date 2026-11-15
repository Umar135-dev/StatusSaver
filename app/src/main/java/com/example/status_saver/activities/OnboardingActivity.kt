package com.example.status_saver.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.status_saver.Prefs
import com.example.status_saver.R
import com.example.status_saver.Source
import com.example.status_saver.StatusRepository
import com.example.status_saver.databinding.ActivityOnboardingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var repo: StatusRepository
    private lateinit var prefs: Prefs

    private val pickWhatsApp =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) connect(uri, Source.WHATSAPP)
        }

    private val pickBusiness =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) connect(uri, Source.BUSINESS)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = StatusRepository(this)
        prefs = Prefs(this)

        // Already connected on an earlier launch: skip this screen.
        if (prefs.folderUri(Source.WHATSAPP) != null ||
            prefs.folderUri(Source.BUSINESS) != null
        ) {
            goToMain()
            return
        }

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnWhatsApp.setOnClickListener {
            pickWhatsApp.launch(repo.pickerHint(Source.WHATSAPP))
        }
        binding.btnBusiness.setOnClickListener {
            pickBusiness.launch(repo.pickerHint(Source.BUSINESS))
        }
    }

    private fun connect(uri: android.net.Uri, source: Source) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.connectFolder(uri, source) }
            if (ok) {
                prefs.lastSource = source
                goToMain()
            } else {
                val appName = getString(
                    if (source == Source.WHATSAPP) R.string.whatsapp else R.string.whatsapp_business
                )
                Toast.makeText(
                    this@OnboardingActivity,
                    getString(R.string.wrong_folder, appName),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    private fun goToMain() {
        // Change MainActivity to the screen that shows your dashboard.
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}