package com.example.photogallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.photogallery.data.Collection

class CollectionAdapter(
    private val onItemClick: (Collection) -> Unit,
    private val onOptionsMenuClick: (View, Collection) -> Unit
) : ListAdapter<Collection, CollectionAdapter.CollectionViewHolder>(CollectionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_collection, parent, false)
        return CollectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: CollectionViewHolder, position: Int) {
        val collection = getItem(position)
        holder.bind(collection)
    }

    inner class CollectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.collectionNameTextView)
        private val optionsMenu: ImageView = itemView.findViewById(R.id.optionsMenu)

        fun bind(collection: Collection) {
            nameTextView.text = collection.name
            itemView.setOnClickListener { onItemClick(collection) }
            optionsMenu.setOnClickListener { onOptionsMenuClick(it, collection) }
        }
    }
}

class CollectionDiffCallback : DiffUtil.ItemCallback<Collection>() {
    override fun areItemsTheSame(oldItem: Collection, newItem: Collection): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Collection, newItem: Collection): Boolean {
        return oldItem == newItem
    }
}
