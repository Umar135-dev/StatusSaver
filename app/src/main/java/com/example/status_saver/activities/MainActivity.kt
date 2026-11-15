package com.example.status_saver.activities

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.status_saver.R
import com.example.status_saver.applyTopInsetPadding
import com.example.status_saver.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tabs: TabLayoutMediator

    internal val settingsOpen: Boolean
        get() = supportFragmentManager.backStackEntryCount > 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.appBar.applyTopInsetPadding()
        ViewCompat.setOnApplyWindowInsetsListener(binding.content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }

        binding.pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 3

            override fun createFragment(position: Int) = GalleryFragment.newInstance(position)
        }
        val labels = listOf(R.string.tab_images, R.string.tab_videos, R.string.nav_saved)
        tabs = TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.setText(labels[position])
        }
        tabs.attach()
        binding.toolbar.inflateMenu(R.menu.menu_bottom)
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId != R.id.nav_settings || settingsOpen) {
                false
            } else {
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    replace(R.id.container, SettingsFragment())
                    addToBackStack(null)
                }
                true
            }
        }
        binding.toolbar.setNavigationOnClickListener { supportFragmentManager.popBackStack() }
        supportFragmentManager.addOnBackStackChangedListener { renderScreen() }
        renderScreen()
    }

    private fun renderScreen() {
        val settings = settingsOpen
        binding.tabs.isVisible = !settings
        binding.pager.isVisible = !settings
        binding.container.isVisible = settings
        binding.toolbar.setTitle(if (settings) R.string.nav_settings else R.string.app_name)
        binding.toolbar.menu.findItem(R.id.nav_settings).isVisible = !settings
        binding.toolbar.menu.findItem(R.id.action_select_all).isVisible = !settings
        binding.toolbar.navigationIcon =
            if (settings) ContextCompat.getDrawable(this, R.drawable.ic_back) else null
        binding.toolbar.setNavigationContentDescription(android.R.string.cancel)
    }

    override fun onDestroy() {
        tabs.detach()
        super.onDestroy()
    }
}