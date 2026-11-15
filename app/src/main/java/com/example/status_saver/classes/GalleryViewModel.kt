package com.example.status_saver.classes

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.status_saver.GalleryState
import com.example.status_saver.Prefs
import com.example.status_saver.Source
import com.example.status_saver.StatusItem
import com.example.status_saver.StatusRepository
import com.example.status_saver.StatusType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = StatusRepository(app)
    private val prefs = Prefs(app)

    private val _state = MutableStateFlow<GalleryState>(GalleryState.Loading)
    val state: StateFlow<GalleryState> = _state.asStateFlow()

    private val _selection = MutableStateFlow<Set<Uri>>(emptySet())
    val selection: StateFlow<Set<Uri>> = _selection.asStateFlow()

    private val _bulkRunning = MutableStateFlow(false)
    val bulkRunning: StateFlow<Boolean> = _bulkRunning.asStateFlow()

    var isSavedScreen = false
        private set
    var source: Source = Source.WHATSAPP
        private set

    /** 0 = images, 1 = videos. */
    var tab = 0

    private var started = false
    private var loadJob: Job? = null

    fun start(savedScreen: Boolean) {
        if (started) return
        started = true
        isSavedScreen = savedScreen
        source = prefs.lastSource
    }

    fun syncSource() {
        if (!isSavedScreen) selectSource(prefs.lastSource)
    }

    fun selectSource(newSource: Source) {
        if (newSource == source) return
        source = newSource
        prefs.lastSource = newSource
        clearSelection()
        refresh()
    }

    /** Cheap to call, so the screen does it every time it comes back into view. */
    fun refresh() {
        loadJob?.cancel()
        _state.value = GalleryState.Loading
        val requestedSource = if (isSavedScreen) null else source
        val requestedTab = tab
        loadJob = viewModelScope.launch {
            val next = try {
                val items = repo.loadGallery(requestedSource)
                if (items == null) {
                    GalleryState.NeedsPermission
                } else {
                    val visible = when (requestedTab) {
                        0 -> items.filter { it.type == StatusType.IMAGE }
                        1 -> items.filter { it.type == StatusType.VIDEO }
                        else -> items
                    }
                    if (visible.isEmpty()) GalleryState.Empty else GalleryState.Success(visible)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                GalleryState.Error
            }

            val existing = (next as? GalleryState.Success)?.items.orEmpty().map { it.uri }.toSet()
            _selection.update { it intersect existing }
            _state.value = next
        }
    }

    suspend fun onFolderPicked(uri: Uri, pickedSource: Source): Boolean {
        val accepted = withContext(Dispatchers.IO) { repo.connectFolder(uri, pickedSource) }
        if (accepted && source == pickedSource) refresh()
        return accepted
    }

    // ------------------------------------------------------------------ selection

    fun toggle(item: StatusItem) {
        if (_bulkRunning.value) return
        _selection.update { if (item.uri in it) it - item.uri else it + item.uri }
    }

    fun selectAll() {
        if (_bulkRunning.value) return
        _selection.value = currentItems().map { it.uri }.toSet()
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    // ------------------------------------------------------------------ actions

    fun saveOne(item: StatusItem, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) { repo.saveToGallery(item) }
            if (uri != null) refresh()
            onDone(uri != null)
        }
    }

    fun saveSelected(onDone: (saved: Int, failed: Int) -> Unit) =
        runBulk({ repo.saveToGallery(it) != null }, onDone)

    fun deleteSelected(onDone: (deleted: Int, failed: Int) -> Unit) =
        runBulk({ repo.delete(it.uri) }, onDone)

    private fun runBulk(action: (StatusItem) -> Boolean, onDone: (Int, Int) -> Unit) {
        if (_bulkRunning.value) return
        val chosen = currentItems().filter { it.uri in _selection.value }
        if (chosen.isEmpty()) return
        _bulkRunning.value = true
        viewModelScope.launch {
            try {
                val succeeded = withContext(Dispatchers.IO) { chosen.count(action) }
                clearSelection()
                refresh()
                onDone(succeeded, chosen.size - succeeded)
            } finally {
                _bulkRunning.value = false
            }
        }
    }

    fun currentItems(): List<StatusItem> =
        (state.value as? GalleryState.Success)?.items.orEmpty()
}