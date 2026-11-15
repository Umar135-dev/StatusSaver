package com.example.status_saver.adapters

import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.status_saver.StatusItem
import com.example.status_saver.StatusType

class PreviewAdapter(
    private val items: List<StatusItem>,
    private val onVideoAttached: (Int, PlayerView) -> Unit,
    private val onVideoDetached: (PlayerView) -> Unit
) : RecyclerView.Adapter<PreviewAdapter.PreviewHolder>() {

    companion object {
        private const val TYPE_IMAGE = 1
        private const val TYPE_VIDEO = 2
    }

    // Holds the image or video view for each page.
    class PreviewHolder(
        val container: FrameLayout,
        val imageView: ImageView? = null,
        val playerView: PlayerView? = null
    ) : RecyclerView.ViewHolder(container)

    override fun getItemViewType(position: Int): Int =
        if (items[position].type == StatusType.VIDEO) TYPE_VIDEO else TYPE_IMAGE

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewHolder {
        val context = parent.context

        val container = FrameLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        return when (viewType) {
            TYPE_IMAGE -> {
                val imageView = ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                    setBackgroundColor(Color.BLACK)
                }
                container.addView(imageView)
                PreviewHolder(container = container, imageView = imageView)
            }

            TYPE_VIDEO -> {
                val playerView = PlayerView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
                container.addView(playerView)
                PreviewHolder(container = container, playerView = playerView)
            }

            else -> error("Unsupported preview type")
        }
    }

    override fun onBindViewHolder(holder: PreviewHolder, position: Int) {
        val item = items[position]

        holder.imageView?.let { image ->
            Glide.with(image.context)
                .load(item.uri)
                .fitCenter()
                .into(image)
        }

        // ExoPlayer is controlled by PreviewActivity.
        holder.playerView?.player = null
    }

    override fun onViewAttachedToWindow(holder: PreviewHolder) {
        super.onViewAttachedToWindow(holder)
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
            holder.playerView?.let { view -> onVideoAttached(position, view) }
        }
    }

    override fun onViewDetachedFromWindow(holder: PreviewHolder) {
        holder.playerView?.let { view ->
            onVideoDetached(view)
            view.player = null
        }
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewRecycled(holder: PreviewHolder) {
        holder.imageView?.let { image ->
            Glide.with(image.context).clear(image)
        }
        holder.playerView?.let { view ->
            onVideoDetached(view)
            view.player = null
        }
        super.onViewRecycled(holder)
    }
}