package com.example.photogallery

import android.content.Context
import com.example.photogallery.data.AppDatabase
import com.example.photogallery.data.ChangeLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChangeLogHelper(private val context: Context) {
    
    private val changeLogDao = AppDatabase.getDatabase(context).changeLogDao()

    fun logCollectionCreated(collectionName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            changeLogDao.insertChangeLog(
                ChangeLog(
                    timestamp = System.currentTimeMillis(),
                    action = "CREATE",
                    description = "Created collection: $collectionName",
                    collectionName = collectionName
                )
            )
        }
    }

    fun logCollectionDeleted(collectionName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            changeLogDao.insertChangeLog(
                ChangeLog(
                    timestamp = System.currentTimeMillis(),
                    action = "DELETE",
                    description = "Deleted collection: $collectionName",
                    collectionName = collectionName
                )
            )
        }
    }

    fun logItemAddedToCollection(collectionName: String, itemPath: String, itemCount: Int = 1) {
        CoroutineScope(Dispatchers.IO).launch {
            val description = if (itemCount == 1) {
                "Added item to collection: $collectionName"
            } else {
                "Added $itemCount items to collection: $collectionName"
            }
            changeLogDao.insertChangeLog(
                ChangeLog(
                    timestamp = System.currentTimeMillis(),
                    action = "ADD_ITEM",
                    description = description,
                    collectionName = collectionName,
                    itemPath = itemPath
                )
            )
        }
    }

    fun logItemRemovedFromCollection(collectionName: String, itemPath: String, itemCount: Int = 1) {
        CoroutineScope(Dispatchers.IO).launch {
            val description = if (itemCount == 1) {
                "Removed item from collection: $collectionName"
            } else {
                "Removed $itemCount items from collection: $collectionName"
            }
            changeLogDao.insertChangeLog(
                ChangeLog(
                    timestamp = System.currentTimeMillis(),
                    action = "REMOVE_ITEM",
                    description = description,
                    collectionName = collectionName,
                    itemPath = itemPath
                )
            )
        }
    }

    fun logMultipleItemsAddedToCollections(itemPaths: List<String>, collectionNames: List<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            val collectionsText = if (collectionNames.size <= 2) {
                collectionNames.joinToString(", ")
            } else {
                "${collectionNames.take(2).joinToString(", ")} and ${collectionNames.size - 2} more"
            }
            
            changeLogDao.insertChangeLog(
                ChangeLog(
                    timestamp = System.currentTimeMillis(),
                    action = "ADD_ITEM",
                    description = "Added ${itemPaths.size} items to collections: $collectionsText"
                )
            )
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ChangeLogHelper? = null

        fun getInstance(context: Context): ChangeLogHelper {
            return INSTANCE ?: synchronized(this) {
                val instance = ChangeLogHelper(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}