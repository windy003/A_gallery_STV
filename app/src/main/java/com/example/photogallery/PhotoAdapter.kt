package com.example.photogallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PhotoAdapter(
    private val photos: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photoPath = photos[position]
        
        Glide.with(holder.itemView.context)
            .load(photoPath)
            .centerCrop()
            .into(holder.imageView)
        
        holder.itemView.setOnClickListener {
            onItemClick(photoPath)
        }
    }

    override fun getItemCount(): Int = photos.size
}