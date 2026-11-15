package com.example.status_saver.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.status_saver.Prefs
import com.example.status_saver.R
import com.example.status_saver.Source
import com.example.status_saver.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)
        prefs = Prefs(requireContext())

        binding.rowTheme.setOnClickListener { chooseTheme() }
        binding.rowWhatsapp.setOnClickListener { confirmRemove(Source.WHATSAPP) }
        binding.rowBusiness.setOnClickListener { confirmRemove(Source.BUSINESS) }

        val context = requireContext()
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
        binding.versionValue.text = getString(R.string.version_value, version)
    }

    override fun onResume() {
        super.onResume()
        refreshRows()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refreshRows() {
        val b = _binding ?: return
        b.themeValue.setText(
            when (prefs.themeMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> R.string.theme_light
                AppCompatDelegate.MODE_NIGHT_YES -> R.string.theme_dark
                else -> R.string.theme_system
            }
        )
        b.whatsappValue.setText(folderStatus(Source.WHATSAPP))
        b.businessValue.setText(folderStatus(Source.BUSINESS))
    }

    private fun folderStatus(source: Source) =
        if (prefs.getTreeUri(source) != null) R.string.folder_allowed else R.string.folder_not_set

    private fun chooseTheme() {
        val modes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        )
        val labels = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system),
        )
        val checked = modes.indexOf(prefs.themeMode).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.theme_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                prefs.themeMode = modes[which]
                AppCompatDelegate.setDefaultNightMode(modes[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun confirmRemove(source: Source) {
        val uri = prefs.getTreeUri(source)
        if (uri == null) {
            Toast.makeText(requireContext(), R.string.folder_nothing_to_remove, Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.folder_remove_title)
            .setMessage(getString(R.string.folder_remove_message, source.label))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.folder_remove) { _, _ ->
                val resolver = requireContext().applicationContext.contentResolver
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            resolver.releasePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                        prefs.clearTreeUri(source)
                    }
                    refreshRows()
                }
            }
            .show()
    }
}