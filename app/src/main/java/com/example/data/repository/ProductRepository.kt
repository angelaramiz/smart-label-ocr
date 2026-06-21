package com.example.data.repository

import com.example.data.database.ContainerEntity
import com.example.data.database.ProductDao
import com.example.data.database.ProductEntity
import kotlinx.coroutines.flow.Flow

class ProductRepository(private val productDao: ProductDao) {

    val allProducts: Flow<List<ProductEntity>> = productDao.getAllProducts()
    val allContainers: Flow<List<ContainerEntity>> = productDao.getAllContainers()

    suspend fun insertContainer(container: ContainerEntity) {
        productDao.insertContainer(container)
    }

    suspend fun deleteContainer(sku: String) {
        productDao.clearProductsFromContainer(sku)
        productDao.deleteContainer(sku)
    }

    fun getProductsInContainer(containerSku: String): Flow<List<ProductEntity>> {
        return productDao.getProductsInContainer(containerSku)
    }

    suspend fun associateProductWithContainer(productId: Int, containerSku: String?) {
        productDao.associateProductWithContainer(productId, containerSku)
    }

    suspend fun associateModelWithContainer(modelName: String, containerSku: String?) {
        productDao.associateModelWithContainer(modelName, containerSku)
    }

    suspend fun findProductByUpcPrefix(prefix: String): ProductEntity? {
        return productDao.findProductByUpcPrefix(prefix)
    }

    suspend fun getAllUniqueModels(): List<String> {
        return productDao.getAllUniqueModels()
    }

    suspend fun addOrIncrementProduct(upc: String, model: String, size: String, color: String, quantity: Int = 1): AddResult {
        var cleanedModel = model.trim().uppercase()
        var cleanedColor = color.trim().uppercase()
        val cleanedSize = size.trim().uppercase()
        val cleanedUpc = upc.trim()

        // Split by hyphen to extract color if present in the model string
        if (cleanedModel.contains("-")) {
            val parts = cleanedModel.split("-", limit = 2)
            cleanedModel = parts[0].trim()
            if (cleanedColor.isEmpty() || cleanedColor == "N/A") {
                cleanedColor = parts[1].trim()
            }
        }

        if (cleanedModel.isNotEmpty()) {
            val uniqueModels = productDao.getAllUniqueModels()
            for (existingModel in uniqueModels) {
                val existingUpper = existingModel.uppercase()
                if (getLevenshteinSimilarity(cleanedModel, existingUpper) >= 0.8) {
                    cleanedModel = existingUpper
                    break
                }
            }
        }

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

    private fun getLevenshteinSimilarity(s1: String, s2: String): Double {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        val longerLength = longer.length
        if (longerLength == 0) return 1.0
        return (longerLength - editDistance(longer, shorter)) / longerLength.toDouble()
    }

    private fun editDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        for (i in 0..s1.length) {
            var lastValue = i
            for (j in 0..s2.length) {
                if (i == 0) {
                    costs[j] = j
                } else {
                    if (j > 0) {
                        var newValue = costs[j - 1]
                        if (s1[i - 1] != s2[j - 1]) {
                            newValue = minOf(newValue, lastValue, costs[j]) + 1
                        }
                        costs[j - 1] = lastValue
                        lastValue = newValue
                    }
                }
            }
            if (i > 0) costs[s2.length] = lastValue
        }
        return costs[s2.length]
    }

    suspend fun findProductsByUpc(upc: String): List<ProductEntity> {
        return productDao.findProductsByUpc(upc)
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
