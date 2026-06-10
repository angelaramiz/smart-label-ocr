package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY timestamp DESC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE upc = :upc AND size = :size LIMIT 1")
    suspend fun findProduct(upc: String, size: String): ProductEntity?

    @Query("SELECT * FROM products WHERE upc = :upc AND size = :size AND model = :model LIMIT 1")
    suspend fun findProductExact(upc: String, size: String, model: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: Int)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()
}
