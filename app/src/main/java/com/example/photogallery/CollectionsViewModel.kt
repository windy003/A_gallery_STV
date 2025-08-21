package com.example.photogallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.photogallery.data.Collection
import com.example.photogallery.data.CollectionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class CollectionsViewModel(private val dao: CollectionDao) : ViewModel() {

    val allCollections: Flow<List<Collection>> = dao.getAllCollections()

    fun insertCollection(name: String) = viewModelScope.launch {
        dao.insertCollection(Collection(name = name))
    }

    fun updateCollection(collection: Collection) = viewModelScope.launch {
        dao.updateCollection(collection)
    }

    fun deleteCollection(collection: Collection) = viewModelScope.launch {
        dao.deleteCollection(collection)
    }
}

class CollectionsViewModelFactory(private val dao: CollectionDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CollectionsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CollectionsViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
