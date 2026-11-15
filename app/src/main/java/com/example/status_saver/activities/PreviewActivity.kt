package com.example.status_saver.activities

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.viewpager2.widget.ViewPager2
import com.example.status_saver.MediaTypes
import com.example.status_saver.R
import com.example.status_saver.Source
import com.example.status_saver.StatusItem
import com.example.status_saver.StatusRepository
import com.example.status_saver.StatusType
import com.example.status_saver.adapters.PreviewAdapter
import com.example.status_saver.applySystemBarsPadding
import com.example.status_saver.databinding.ActivityPreviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen viewer. Swipe left and right to move between statuses. */
class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private lateinit var repo: StatusRepository
    private var items: List<StatusItem> = emptyList()
    private var source: Source = Source.WHATSAPP

    /** Names saved during this visit, so the Save button hides straight away. */
    private val savedNames = mutableSetOf<String>()

    private var busy = false

    private var player: ExoPlayer? = null
    private val attachedViews = mutableMapOf<PlayerView, Int>()
    private var playingUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        repo = StatusRepository(this)
        items = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_ITEMS, StatusItem::class.java)
            .orEmpty()
        if (items.isEmpty()) {
            finish()
            return
        }
        source = runCatching { Source.valueOf(intent.getStringExtra(EXTRA_SOURCE) ?: "") }
            .getOrDefault(Source.WHATSAPP)
        savedNames.addAll(savedInstanceState?.getStringArrayList(STATE_SAVED_NAMES).orEmpty())
        val start = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(items.indices)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.pager.adapter = PreviewAdapter(
            items = items,
            onVideoAttached = { position, view ->
                attachedViews[view] = position
                updatePlayback()
            },
            onVideoDetached = { view ->
                attachedViews.remove(view)
                updatePlayback()
            },
        )
        binding.pager.setCurrentItem(start, false)
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateChrome()
                updatePlayback()
            }
        })

        binding.btnSave.setOnClickListener { save() }
        binding.btnShare.setOnClickListener { share(repostToApp = false) }
        binding.btnRepost.setOnClickListener { share(repostToApp = true) }
        binding.btnDelete.setOnClickListener { delete() }
        updateChrome()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(STATE_SAVED_NAMES, ArrayList(savedNames))
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        if (items.isEmpty()) return
        if (items.none { it.type == StatusType.VIDEO }) return
        player = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
        updatePlayback()
    }

    override fun onStop() {
        super.onStop()
        attachedViews.keys.forEach { it.player = null }
        player?.release()
        player = null
        playingUri = null
    }

    // ------------------------------------------------------------------ UI

    private fun current(): StatusItem = items[binding.pager.currentItem]

    private fun updateChrome() {
        val item = current()
        binding.toolbar.title = item.name
        binding.toolbar.subtitle = getString(
            R.string.page_position,
            binding.pager.currentItem + 1,
            items.size,
        )
        binding.btnSave.isVisible = !item.saved && !item.downloaded && item.name !in savedNames
        binding.btnDelete.isVisible = item.saved
        binding.operationProgress.isVisible = busy
        binding.btnSave.isEnabled = !busy
        binding.btnShare.isEnabled = !busy
        binding.btnRepost.isEnabled = !busy
        binding.btnDelete.isEnabled = !busy
        binding.pager.isUserInputEnabled = !busy
    }

    /** Gives the single ExoPlayer to the video page that is on screen. */
    private fun updatePlayback() {
        val exo = player ?: return
        val position = binding.pager.currentItem
        val item = items[position]
        val view = attachedViews.entries.firstOrNull { it.value == position }?.key
        attachedViews.keys.forEach { if (it !== view) it.player = null }
        if (item.type != StatusType.VIDEO || view == null) {
            exo.pause()
            playingUri = null
            return
        }
        if (view.player !== exo) view.player = exo
        if (playingUri != item.uri) {
            exo.setMediaItem(MediaItem.fromUri(item.uri))
            exo.prepare()
            playingUri = item.uri
            exo.playWhenReady = true
        }
    }

    // ------------------------------------------------------------------ actions

    private fun save() {
        val item = current()
        runAction {
            val uri = withContext(Dispatchers.IO) { repo.saveToGallery(item) }
            if (uri != null) savedNames += item.name
            toast(if (uri != null) R.string.saved_ok else R.string.saved_failed)
        }
    }

    private fun share(repostToApp: Boolean) {
        val item = current()
        runAction {
            val uri = repo.prepareShare(item)
            if (uri == null) {
                toast(R.string.share_failed)
                return@runAction
            }

            val send = Intent(Intent.ACTION_SEND).apply {
                type = MediaTypes.mimeOf(item.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(item.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (!repostToApp) {
                try {
                    startActivity(Intent.createChooser(send, getString(R.string.share)))
                } catch (_: ActivityNotFoundException) {
                    toast(R.string.share_failed)
                }
                return@runAction
            }
            // Try the app the status came from first, then the other one.
            val packages = listOf(source.packageName) +
                    Source.values().map { it.packageName }.filter { it != source.packageName }
            for (pkg in packages) {
                try {
                    startActivity(Intent(send).setPackage(pkg))
                    return@runAction
                } catch (e: ActivityNotFoundException) {
                    // try the next package
                }
            }
            toast(R.string.whatsapp_missing)
        }
    }

    private fun delete() {
        val item = current()
        runAction {
            val ok = withContext(Dispatchers.IO) { repo.delete(item.uri) }
            if (ok) {
                toast(R.string.deleted_ok)
                finish()
            } else {
                toast(R.string.delete_failed)
            }
        }
    }

    private fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        updateChrome()
        lifecycleScope.launch {
            try {
                action()
            } finally {
                busy = false
                updateChrome()
            }
        }
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val STATE_SAVED_NAMES = "preview_saved_names"
        private const val EXTRA_ITEMS = "items"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_SOURCE = "source"

        fun intent(context: Context, items: ArrayList<StatusItem>, index: Int, source: Source) =
            Intent(context, PreviewActivity::class.java)
                .putParcelableArrayListExtra(EXTRA_ITEMS, items)
                .putExtra(EXTRA_INDEX, index)
                .putExtra(EXTRA_SOURCE, source.name)
    }
}