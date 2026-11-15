package com.example.status_saver

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("status_saver_prefs", Context.MODE_PRIVATE)

    var lastSource: Source
        get() = runCatching { Source.valueOf(sp.getString(KEY_SOURCE, "") ?: "") }
            .getOrDefault(Source.WHATSAPP)
        set(value) = sp.edit().putString(KEY_SOURCE, value.name).apply()

    var themeMode: Int
        get() = sp.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_NO) // light by default
        set(value) = sp.edit().putInt(KEY_THEME, value).apply()

    fun folderUri(source: Source): String? = sp.getString(KEY_FOLDER + source.name, null)

    fun setFolderUri(source: Source, uri: String?) {
        sp.edit().putString(KEY_FOLDER + source.name, uri).apply()
    }

    fun getTreeUri(source: Source): Uri? = folderUri(source)?.let { Uri.parse(it) }

    fun clearTreeUri(source: Source) {
        sp.edit().remove(KEY_FOLDER + source.name).apply()
    }

    private companion object {
        const val KEY_SOURCE = "last_source"
        const val KEY_THEME = "theme_mode"
        const val KEY_FOLDER = "folder_uri_"
    }
}