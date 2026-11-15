package com.example.status_saver

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract


object StatusPaths {
    private const val AUTHORITY = "com.android.externalstorage.documents"

    private fun legacyPath(source: Source): String = when (source) {
        Source.WHATSAPP -> "WhatsApp/Media/.Statuses"
        Source.BUSINESS -> "WhatsApp Business/Media/.Statuses"
    }

    private fun modernPath(source: Source): String {
        val packageName = when (source) {
            Source.WHATSAPP -> "com.whatsapp"
            Source.BUSINESS -> "com.whatsapp.w4b"
        }
        return "Android/media/$packageName/${legacyPath(source)}"
    }

    fun pickerIntent(source: Source): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            modernPath(source)
        } else {
            legacyPath(source)
        }
        putExtra(
            DocumentsContract.EXTRA_INITIAL_URI,
            DocumentsContract.buildDocumentUri(AUTHORITY, "primary:$path"),
        )
    }

    fun matches(uri: Uri, source: Source): Boolean {
        if (uri.authority != AUTHORITY || !DocumentsContract.isTreeUri(uri)) return false
        val id = DocumentsContract.getTreeDocumentId(uri)
        val path = id.substringAfter(':', "")
        return path == modernPath(source) || path == legacyPath(source)
    }
}
