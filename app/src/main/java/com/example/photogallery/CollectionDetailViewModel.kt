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

class CollectionDetailViewModel(application: Application, private val dao: CollectionDao, private val collectionId: Long) : AndroidViewModel(application) {

    val mediaItems: Flow<List<MediaItem>> = dao.getItemsForCollection(collectionId)
        .map { collectionItems ->
            collectionItems.mapNotNull { item ->
                // We need to query MediaStore again to get all details for the MediaItem
                // This is not the most efficient way, but it's simple for now.
                val path = item.mediaPath
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
                        MediaItem(path, mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO, dateAdded, duration)
                    } else {
                        null
                    }
                }
            }
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
