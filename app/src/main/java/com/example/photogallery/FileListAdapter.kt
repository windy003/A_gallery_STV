package com.example.photogallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

data class FileListItem(
    val fileName: String,
    val filePath: String,
    val isVideo: Boolean
)

interface FileListItemClickListener {
    fun onItemClick(fileItem: FileListItem)
    fun onItemLongClick(fileItem: FileListItem)
}

class FileListAdapter(
    private val fileList: List<FileListItem>,
    private val clickListener: FileListItemClickListener? = null
) : RecyclerView.Adapter<FileListAdapter.FileListViewHolder>() {

    class FileListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileIcon: ImageView = itemView.findViewById(R.id.ivFileIcon)
        val fileName: TextView = itemView.findViewById(R.id.tvFileName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileListViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file_list, parent, false)
        return FileListViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileListViewHolder, position: Int) {
        val fileItem = fileList[position]
        
        holder.fileName.text = fileItem.fileName
        
        // 设置图标
        val iconRes = if (fileItem.isVideo) {
            android.R.drawable.ic_media_play // 视频图标
        } else {
            android.R.drawable.ic_menu_gallery // 图片图标
        }
        holder.fileIcon.setImageResource(iconRes)
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            clickListener?.onItemClick(fileItem)
        }
        
        // 设置长按事件
        holder.itemView.setOnLongClickListener {
            clickListener?.onItemLongClick(fileItem)
            true
        }
    }

    override fun getItemCount(): Int = fileList.size
}