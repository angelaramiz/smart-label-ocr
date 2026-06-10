package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val upc: String,
    val model: String,
    val size: String,
    val color: String,
    val quantity: Int,
    val timestamp: Long = System.currentTimeMillis()
)
