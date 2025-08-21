package com.example.photogallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.photogallery.data.Collection
import com.example.photogallery.data.CollectionDao
import com.example.photogallery.data.CollectionItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ImageDetailViewModel(private val dao: CollectionDao) : ViewModel() {

    fun getAllCollections(): Flow<List<Collection>> = dao.getAllCollections()

    fun getCollectionsForImage(imagePath: String): Flow<List<CollectionItem>> {
        return dao.getCollectionsForItem(imagePath)
    }

    fun updateImageInCollections(imagePath: String, selectedCollectionIds: List<Long>) = viewModelScope.launch {
        val currentCollections = getCollectionsForImage(imagePath).first()
        val currentCollectionIds = currentCollections.map { it.collectionId }

        // Add to new collections
        selectedCollectionIds.forEach { collectionId ->
            if (!currentCollectionIds.contains(collectionId)) {
                dao.insertItemIntoCollection(CollectionItem(collectionId = collectionId, mediaPath = imagePath))
            }
        }

        // Remove from deselected collections
        currentCollectionIds.forEach { collectionId ->
            if (!selectedCollectionIds.contains(collectionId)) {
                dao.removeItemFromCollection(collectionId, imagePath)
            }
        }
    }
}

class ImageDetailViewModelFactory(private val dao: CollectionDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ImageDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ImageDetailViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
