package com.example.status_saver

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class StatusSaverApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(Prefs(this).themeMode)
    }
}