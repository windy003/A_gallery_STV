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
    private val onItemClick: (MediaItem) -> Unit
) : ListAdapter<MediaItem, PhotoAdapter.PhotoViewHolder>(MediaItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val mediaItem = getItem(position)
        holder.bind(mediaItem, onItemClick)
    }

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val playIcon: ImageView = itemView.findViewById(R.id.playIcon)
        private val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)

        fun bind(mediaItem: MediaItem, onItemClick: (MediaItem) -> Unit) {
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
                onItemClick(mediaItem)
            }
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
