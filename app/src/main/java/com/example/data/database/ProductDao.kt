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

    @Query("SELECT * FROM products WHERE upc = :upc")
    suspend fun findProductsByUpc(upc: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE SUBSTR(upc, 1, 8) = :prefix LIMIT 1")
    suspend fun findProductByUpcPrefix(prefix: String): ProductEntity?

    @Query("SELECT DISTINCT model FROM products")
    suspend fun getAllUniqueModels(): List<String>

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: Int)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContainer(container: ContainerEntity)

    @Query("SELECT * FROM containers ORDER BY timestamp DESC")
    fun getAllContainers(): Flow<List<ContainerEntity>>

    @Query("SELECT * FROM containers WHERE sku = :sku LIMIT 1")
    suspend fun getContainerBySku(sku: String): ContainerEntity?

    @Query("DELETE FROM containers WHERE sku = :sku")
    suspend fun deleteContainer(sku: String)

    @Query("SELECT * FROM products WHERE containerSku = :containerSku ORDER BY timestamp DESC")
    fun getProductsInContainer(containerSku: String): Flow<List<ProductEntity>>

    @Query("UPDATE products SET containerSku = :containerSku WHERE id = :productId")
    suspend fun associateProductWithContainer(productId: Int, containerSku: String?)

    @Query("UPDATE products SET containerSku = :containerSku WHERE UPPER(TRIM(model)) = UPPER(TRIM(:modelName))")
    suspend fun associateModelWithContainer(modelName: String, containerSku: String?)

    @Query("UPDATE products SET containerSku = null WHERE containerSku = :containerSku")
    suspend fun clearProductsFromContainer(containerSku: String)
}
