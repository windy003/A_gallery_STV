package com.example.photogallery.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "collection_items",
    foreignKeys = [
        ForeignKey(
            entity = Collection::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["collectionId"])]
)
data class CollectionItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val collectionId: Long,
    val mediaPath: String
)
