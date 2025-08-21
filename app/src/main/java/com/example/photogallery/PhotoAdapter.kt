package com.example.photogallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PhotoAdapter(
    private val onItemClick: (MediaItem) -> Unit,
    private val onItemLongClick: (MediaItem) -> Unit
) : ListAdapter<MediaItem, PhotoAdapter.PhotoViewHolder>(MediaItemDiffCallback()) {

    private val selectedItems = mutableSetOf<MediaItem>()
    var selectionMode: Boolean = false
        set(value) {
            field = value
            if (!value) {
                selectedItems.clear()
            }
            notifyDataSetChanged() // Notify all items to update their selection state
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val mediaItem = getItem(position)
        holder.bind(mediaItem, onItemClick, onItemLongClick, selectedItems.contains(mediaItem), selectionMode)
    }

    fun selectItem(mediaItem: MediaItem) {
        selectedItems.add(mediaItem)
        notifyItemChanged(currentList.indexOf(mediaItem))
    }

    fun deselectItem(mediaItem: MediaItem) {
        selectedItems.remove(mediaItem)
        notifyItemChanged(currentList.indexOf(mediaItem))
    }

    fun clearSelection() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun getSelectedItems(): Set<MediaItem> = selectedItems

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val playIcon: ImageView = itemView.findViewById(R.id.playIcon)
        private val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)
        private val selectionOverlay: View = itemView.findViewById(R.id.selectionOverlay) // Assuming this is added to item_photo.xml

        fun bind(mediaItem: MediaItem, onItemClick: (MediaItem) -> Unit, onItemLongClick: (MediaItem) -> Unit, isSelected: Boolean, selectionMode: Boolean) {
            Glide.with(itemView.context)
                .load(mediaItem.path)
                .centerCrop()
                .into(imageView)

            playIcon.visibility = if (mediaItem.isVideo) View.VISIBLE else View.GONE

            if (mediaItem.isVideo && mediaItem.duration > 0) {
                durationTextView.visibility = View.VISIBLE
                durationTextView.text = formatDuration(mediaItem.duration)
            } else {
                durationTextView.visibility = View.GONE
            }

            itemView.setOnClickListener {
                if (selectionMode) {
                    // Toggle selection
                    if (isSelected) {
                        // This logic will be handled by MainActivity
                    } else {
                        // This logic will be handled by MainActivity
                    }
                } else {
                    onItemClick(mediaItem)
                }
            }

            itemView.setOnLongClickListener {
                onItemLongClick(mediaItem)
                true
            }

            selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
        }

        private fun formatDuration(milliseconds: Long): String {
            val seconds = (milliseconds / 1000) % 60
            val minutes = (milliseconds / (1000 * 60)) % 60
            val hours = milliseconds / (1000 * 60 * 60)
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}

class MediaItemDiffCallback : DiffUtil.ItemCallback<MediaItem>() {
    override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
        return oldItem.path == newItem.path
    }

    override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
        return oldItem == newItem
    }
}
