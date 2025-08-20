package com.example.photogallery.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: Collection): Long

    @Update
    suspend fun updateCollection(collection: Collection)

    @Delete
    suspend fun deleteCollection(collection: Collection)

    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun getAllCollections(): Flow<List<Collection>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemIntoCollection(collectionItem: CollectionItem)

    @Query("DELETE FROM collection_items WHERE collectionId = :collectionId AND mediaPath = :mediaPath")
    suspend fun removeItemFromCollection(collectionId: Long, mediaPath: String)

    @Query("SELECT * FROM collection_items WHERE collectionId = :collectionId")
    fun getItemsForCollection(collectionId: Long): Flow<List<CollectionItem>>

    @Query("SELECT * FROM collection_items WHERE mediaPath = :mediaPath")
    fun getCollectionsForItem(mediaPath: String): Flow<List<CollectionItem>>

    // Sync methods - non-Flow versions for background operations
    @Query("SELECT * FROM collections ORDER BY name ASC")
    suspend fun getAllCollectionsSync(): List<Collection>

    @Query("SELECT * FROM collection_items WHERE collectionId = :collectionId")
    suspend fun getItemsForCollectionSync(collectionId: Long): List<CollectionItem>

    @Query("DELETE FROM collections")
    suspend fun deleteAllCollections()

    @Query("DELETE FROM collection_items")
    suspend fun deleteAllCollectionItems()

    @Query("UPDATE collection_items SET mediaPath = :newPath WHERE mediaPath = :oldPath")
    suspend fun updateItemPath(oldPath: String, newPath: String)
}
