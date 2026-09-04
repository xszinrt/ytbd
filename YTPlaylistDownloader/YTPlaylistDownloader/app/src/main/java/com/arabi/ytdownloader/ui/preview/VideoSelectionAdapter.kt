package com.arabi.ytdownloader.ui.preview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arabi.ytdownloader.databinding.ItemVideoBinding

data class PreviewItem(
    val id: String,
    val title: String,
    val url: String,
    val alreadyQueued: Boolean,
    var checked: Boolean
)

class VideoSelectionAdapter(
    private val items: MutableList<PreviewItem>,
    private val onCheckedChanged: () -> Unit
) : RecyclerView.Adapter<VideoSelectionAdapter.VideoViewHolder>() {

    inner class VideoViewHolder(val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val item = items[position]
        holder.binding.titleText.text = item.title
        holder.binding.checkbox.setOnCheckedChangeListener(null)
        holder.binding.checkbox.isChecked = item.checked
        holder.binding.checkbox.isEnabled = !item.alreadyQueued
        holder.binding.queuedLabel.visibility =
            if (item.alreadyQueued) android.view.View.VISIBLE else android.view.View.GONE

        holder.binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
            item.checked = isChecked
            onCheckedChanged()
        }
    }

    override fun getItemCount(): Int = items.size

    fun setAllChecked(checked: Boolean) {
        items.forEach { if (!it.alreadyQueued) it.checked = checked }
        notifyDataSetChanged()
        onCheckedChanged()
    }

    fun getSelectedItems(): List<PreviewItem> = items.filter { it.checked && !it.alreadyQueued }
}
