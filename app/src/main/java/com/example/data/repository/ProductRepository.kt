package com.example.data.repository

import com.example.data.database.ProductDao
import com.example.data.database.ProductEntity
import kotlinx.coroutines.flow.Flow

class ProductRepository(private val productDao: ProductDao) {

    val allProducts: Flow<List<ProductEntity>> = productDao.getAllProducts()

    suspend fun addOrIncrementProduct(upc: String, model: String, size: String, color: String, quantity: Int = 1): AddResult {
        val cleanedModel = model.trim().uppercase()
        val cleanedSize = size.trim().uppercase()
        val cleanedColor = color.trim().uppercase()
        val cleanedUpc = upc.trim()

        // Check if matching exact UPC, model and size already exists.
        val existing = productDao.findProductExact(cleanedUpc, cleanedSize, cleanedModel)
        return if (existing != null) {
            val updated = existing.copy(
                quantity = existing.quantity + quantity,
                color = if (cleanedColor.isNotEmpty()) cleanedColor else existing.color,
                timestamp = System.currentTimeMillis() // Move to top of the scan list
            )
            productDao.updateProduct(updated)
            AddResult.Incremented(updated)
        } else {
            // Check if UPC + size matches under any other model, if so we assume the model is corrected or we still insert as new.
            // Let's just matching exact.
            val newProduct = ProductEntity(
                upc = cleanedUpc,
                model = if (cleanedModel.isNotEmpty()) cleanedModel else "DESCONOCIDO",
                size = if (cleanedSize.isNotEmpty()) cleanedSize else "U",
                color = if (cleanedColor.isNotEmpty()) cleanedColor else "N/A",
                quantity = quantity
            )
            productDao.insertProduct(newProduct)
            AddResult.NewAdded(newProduct)
        }
    }

    suspend fun updateProduct(product: ProductEntity) {
        productDao.updateProduct(product)
    }

    suspend fun updateQuantity(product: ProductEntity, newQuantity: Int) {
        if (newQuantity <= 0) {
            productDao.deleteProduct(product.id)
        } else {
            productDao.updateProduct(product.copy(quantity = newQuantity))
        }
    }

    suspend fun deleteProduct(id: Int) {
        productDao.deleteProduct(id)
    }

    suspend fun deleteAll() {
        productDao.deleteAllProducts()
    }
}

sealed class AddResult {
    data class NewAdded(val product: ProductEntity) : AddResult()
    data class Incremented(val product: ProductEntity) : AddResult()
}
