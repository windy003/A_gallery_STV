package com.example.photogallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PhotoAdapter(
    private val mediaItems: List<MediaItem>,
    private val onItemClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        val playIcon: ImageView = itemView.findViewById(R.id.playIcon)
        val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val mediaItem = mediaItems[position]

        Glide.with(holder.itemView.context)
            .load(mediaItem.path)
            .centerCrop()
            .into(holder.imageView)

        holder.playIcon.visibility = if (mediaItem.isVideo) View.VISIBLE else View.GONE

        if (mediaItem.isVideo && mediaItem.duration > 0) {
            holder.durationTextView.visibility = View.VISIBLE
            holder.durationTextView.text = formatDuration(mediaItem.duration)
        } else {
            holder.durationTextView.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClick(mediaItem)
        }
    }

    override fun getItemCount(): Int = mediaItems.size

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
