package com.example.status_saver.adapters

import android.R
import android.content.Context
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.status_saver.StatusItem
import com.example.status_saver.StatusType

class StatusAdapter(
    private val onClick: (StatusItem) -> Unit,
    private val onLongClick: (StatusItem) -> Unit,
    private val onSave: (StatusItem) -> Unit,
) : ListAdapter<StatusItem, StatusAdapter.Holder>(Diff) {

    private var selection: Set<Uri> = emptySet()

    fun setSelection(newSelection: Set<Uri>) {
        if (newSelection == selection) return
        selection = newSelection
        notifyDataSetChanged()
    }

    private class Square(context: Context) : FrameLayout(context) {
        override fun onMeasure(widthSpec: Int, heightSpec: Int) = super.onMeasure(widthSpec, widthSpec)
    }

    class Holder(
        root: FrameLayout,
        val image: ImageView,
        val play: ImageView,
        val check: ImageView,
        val save: ImageView,
    ) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val c = parent.context
        val dp = c.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        fun badge(res: Int, size: Int, gravity: Int, margin: Int) = ImageView(c).apply {
            setImageResource(res)
            layoutParams = FrameLayout.LayoutParams(px(size), px(size), gravity).apply {
                setMargins(px(margin), px(margin), px(margin), px(margin))
            }
        }

        val root = Square(c).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(px(4), px(4), px(4), px(4)) }
        }
        val image = ImageView(c).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val play = badge(R.drawable.ic_media_play, 32, Gravity.CENTER, 0)
        val check = badge(R.drawable.checkbox_on_background, 28, Gravity.TOP or Gravity.END, 6)
        val save = badge(R.drawable.ic_menu_save, 32, Gravity.BOTTOM or Gravity.END, 6)

        root.addView(image); root.addView(play); root.addView(check); root.addView(save)
        return Holder(root, image, play, check, save)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val selected = item.uri in selection

        Glide.with(holder.image).load(item.uri).centerCrop().into(holder.image)
        holder.image.alpha = if (selected) 0.55f else 1f
        holder.play.visibility = if (item.type == StatusType.VIDEO) ImageView.VISIBLE else ImageView.GONE
        holder.check.visibility = if (selected) ImageView.VISIBLE else ImageView.GONE
        holder.save.visibility =
            if (!item.saved && !item.downloaded && selection.isEmpty()) ImageView.VISIBLE else ImageView.GONE

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item); true }
        holder.save.setOnClickListener { onSave(item) }
    }

    override fun onViewRecycled(holder: Holder) {
        Glide.with(holder.image).clear(holder.image)
        super.onViewRecycled(holder)
    }

    private object Diff : DiffUtil.ItemCallback<StatusItem>() {
        override fun areItemsTheSame(a: StatusItem, b: StatusItem) = a.uri == b.uri
        override fun areContentsTheSame(a: StatusItem, b: StatusItem) = a == b
    }
}