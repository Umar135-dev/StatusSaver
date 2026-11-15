package com.example.status_saver

sealed class GalleryState {
    object Loading : GalleryState()
    object NeedsPermission : GalleryState()
    object Empty : GalleryState()
    object Error : GalleryState()
    data class Success(val items: List<StatusItem>) : GalleryState()
}