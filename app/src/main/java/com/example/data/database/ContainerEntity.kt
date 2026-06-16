package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "containers")
data class ContainerEntity(
    @PrimaryKey val sku: String, // Code 128 format identifier
    val name: String,
    val timestamp: Long = System.currentTimeMillis()
)
