package com.example.status_saver

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StatusRepository(private val context: Context) {

    private val prefs = Prefs(context)

    // ------------------------------------------------------------------ loading

    /**
     * source == null  -> the Saved screen (files in Pictures/StatusSaver and Movies/StatusSaver)
     * source != null  -> live statuses from the folder the user granted for that app
     * Returns null when the user has not granted (or has lost) access to the folder.
     */
    suspend fun loadGallery(source: Source?): List<StatusItem>? = withContext(Dispatchers.IO) {
        val saved = querySaved()
        if (source == null) return@withContext saved

        val stored = prefs.folderUri(source) ?: return@withContext null
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(stored))
        if (tree == null || !tree.exists() || !tree.canRead()) return@withContext null

        val savedNames = saved.map { it.name }.toSet()
        tree.listFiles()
            .filter { file ->
                val name = file.name
                file.isFile && name != null && isMedia(name)
            }
            .map { file ->
                val name = file.name!!
                StatusItem(
                    uri = file.uri,
                    name = name,
                    type = if (MediaTypes.isVideo(name)) StatusType.VIDEO else StatusType.IMAGE,
                    saved = false,
                    downloaded = name in savedNames,
                    lastModified = file.lastModified(),
                )
            }
            .sortedByDescending { it.lastModified }
    }

    /** Validates and remembers the .Statuses folder the user picked. Returns false if it is the wrong folder. */
    fun connectFolder(uri: Uri, source: Source): Boolean {
        val doc = DocumentFile.fromTreeUri(context, uri) ?: return false
        if (doc.name != ".Statuses") return false

        // Stop WhatsApp and WhatsApp Business folders being mixed up.
        val text = uri.toString()
        val looksBusiness = text.contains("w4b", ignoreCase = true) ||
                text.contains("Business", ignoreCase = true)
        if (looksBusiness != (source == Source.BUSINESS)) return false

        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            return false
        }

        val old = prefs.folderUri(source)
        if (old != null && old != text) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
        }
        prefs.setFolderUri(source, text)
        return true
    }

    /** Optional: a starting location for the folder picker (OpenDocumentTree). It is only a hint. */
    fun pickerHint(source: Source): Uri {
        val path = when (source) {
            Source.WHATSAPP -> "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
            Source.BUSINESS -> "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"
        }
        return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$path")
    }

    // ------------------------------------------------------------------ saved files

    private fun querySaved(): List<StatusItem> =
        (queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, StatusType.IMAGE) +
                queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, StatusType.VIDEO))
            .sortedByDescending { it.lastModified }

    @Suppress("DEPRECATION")
    private fun queryCollection(collection: Uri, type: StatusType): List<StatusItem> {
        val out = mutableListOf<StatusItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        else
            "${MediaStore.MediaColumns.DATA} LIKE ?"

        try {
            context.contentResolver.query(
                collection, projection, selection, arrayOf("%/StatusSaver/%"), null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)

                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                while (c.moveToNext()) {
                    out += StatusItem(
                        uri = ContentUris.withAppendedId(collection, c.getLong(idCol)),
                        name = c.getString(nameCol) ?: continue,
                        type = type,
                        saved = true,
                        lastModified = c.getLong(dateCol) * 1000L,
                    )
                }
            }
        } catch (_: Exception) { }
        return out
    }

    // ------------------------------------------------------------------ actions

    /** Copies a status into Pictures/StatusSaver or Movies/StatusSaver. Returns the new Uri, or null on failure. */
    @Suppress("DEPRECATION")
    fun saveToGallery(item: StatusItem): Uri? {
        val isVideo = item.type == StatusType.VIDEO
        val folder = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, MediaTypes.mimeOf(item.name))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/StatusSaver")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                // Android 9 and below: needs WRITE_EXTERNAL_STORAGE (maxSdkVersion 28)
                val dir = File(Environment.getExternalStoragePublicDirectory(folder), "StatusSaver")
                dir.mkdirs()
                put(MediaStore.MediaColumns.DATA, File(dir, item.name).absolutePath)
            }
        }
        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val resolver = context.contentResolver
        val dest = resolver.insert(collection, values) ?: return null
        return try {
            val input = resolver.openInputStream(item.uri) ?: error("Cannot read source")
            val output = resolver.openOutputStream(dest) ?: error("Cannot write destination")
            input.use { i -> output.use { o -> i.copyTo(o) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(dest, values, null, null)
            }
            dest
        } catch (e: Exception) {
            resolver.delete(dest, null, null)
            null
        }
    }

    /** Copies the file into the app cache and returns a shareable FileProvider Uri. */
    suspend fun prepareShare(item: StatusItem): Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "preview-share").apply { mkdirs() }
            val file = File(dir, item.name)
            val input = context.contentResolver.openInputStream(item.uri)
                ?: return@withContext null
            input.use { i -> file.outputStream().use { o -> i.copyTo(o) } }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }

    /** Deletes a saved file. */
    fun delete(uri: Uri): Boolean = try {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } else {
            context.contentResolver.delete(uri, null, null) > 0
        }
    } catch (e: Exception) {
        false
    }

    private fun isMedia(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in MEDIA_EXTENSIONS

    private companion object {
        val MEDIA_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "gif",
            "mp4", "3gp", "mkv", "webm", "mov",
        )
    }
}