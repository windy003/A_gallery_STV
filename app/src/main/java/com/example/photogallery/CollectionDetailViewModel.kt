package com.example.photogallery

import android.app.Application
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.photogallery.data.CollectionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class CollectionDetailViewModel(application: Application, private val dao: CollectionDao, private val collectionId: Long) : AndroidViewModel(application) {

    val mediaItems: Flow<List<MediaItem>> = dao.getItemsForCollection(collectionId)
        .map { collectionItems ->
            val validItems = collectionItems.mapNotNull { item ->
                val path = item.mediaPath
                val file = File(path)
                
                // 首先检查文件是否存在
                if (!file.exists()) {
                    // 文件不存在，从数据库中删除记录
                    viewModelScope.launch {
                        dao.removeItemFromCollection(collectionId, path)
                    }
                    return@mapNotNull null
                }
                
                // 尝试从MediaStore获取信息
                val uri = MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE,
                    MediaStore.Files.FileColumns.DATE_ADDED,
                    MediaStore.Video.Media.DURATION
                )
                val selection = "${MediaStore.Files.FileColumns.DATA}=?"
                val selectionArgs = arrayOf(path)

                val cursor = getApplication<Application>().contentResolver.query(uri, projection, selection, selectionArgs, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val mediaType = it.getInt(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE))
                        val dateAdded = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED))
                        val duration = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                            it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
                        } else {
                            0L
                        }
                        return@mapNotNull MediaItem(path, mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO, dateAdded, duration)
                    }
                }
                
                // 如果MediaStore中没有找到，直接根据文件扩展名判断类型
                val fileName = file.name.lowercase()
                val isVideo = fileName.endsWith(".mp4") || fileName.endsWith(".avi") || 
                             fileName.endsWith(".mkv") || fileName.endsWith(".mov") || 
                             fileName.endsWith(".wmv") || fileName.endsWith(".flv") || 
                             fileName.endsWith(".webm") || fileName.endsWith(".m4v")
                
                val dateAdded = file.lastModified() / 1000 // 转换为秒
                MediaItem(path, isVideo, dateAdded, 0L)
            }
            
            // 返回过滤后的有效项目
            validItems
        }

    fun removeItemsFromCollection(mediaPaths: List<String>) = viewModelScope.launch {
        mediaPaths.forEach { mediaPath ->
            dao.removeItemFromCollection(collectionId, mediaPath)
        }
    }

    fun updateImagePath(oldPath: String, newPath: String) = viewModelScope.launch {
        dao.updateItemPath(oldPath, newPath)
    }
}

class CollectionDetailViewModelFactory(private val application: Application, private val dao: CollectionDao, private val collectionId: Long) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CollectionDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CollectionDetailViewModel(application, dao, collectionId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
