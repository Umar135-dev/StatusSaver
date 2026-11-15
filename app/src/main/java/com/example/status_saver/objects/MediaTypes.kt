package com.example.status_saver

object MediaTypes {
    fun isVideo(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in setOf("mp4", "3gp", "mkv", "webm", "mov")

    fun mimeOf(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "mp4" -> "video/mp4"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
}