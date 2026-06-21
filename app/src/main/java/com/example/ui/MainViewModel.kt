package com.example.ui

import android.util.Log
import android.graphics.Bitmap
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.ProductEntity
import com.example.data.database.ContainerEntity
import com.example.data.network.GeminiService
import com.example.data.network.OcrResult
import com.example.data.repository.AddResult
import com.example.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.BarcodeScanning
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed class ScanState {
    object Idle : ScanState()
    object Processing : ScanState()
    data class Success(val upc: String, val model: String, val size: String, val color: String) : ScanState()
    data class Error(val message: String) : ScanState()
}

enum class BatchItemStatus {
    PENDING,     // En cola
    PROCESSING,  // Procesando
    SUCCESS,     // Completado
    ERROR        // Error
}

data class BatchItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    val imageUri: android.net.Uri?,
    val status: BatchItemStatus,
    val extractedUpc: String = "",
    val extractedModel: String = "",
    val extractedSize: String = "",
    val errorMessage: String? = null
)

class MainViewModel(private val repository: ProductRepository, private val context: Context) : ViewModel() {

    private val prefs = context.getSharedPreferences("label_scan_prefs", Context.MODE_PRIVATE)

    // Token Tracker States
    private val _totalTokens = MutableStateFlow(prefs.getInt("total_tokens", 0))
    val totalTokens: StateFlow<Int> = _totalTokens.asStateFlow()

    private val _lastScanTokens = MutableStateFlow(0)
    val lastScanTokens: StateFlow<Int> = _lastScanTokens.asStateFlow()

    fun resetTotalTokens() {
        prefs.edit().putInt("total_tokens", 0).apply()
        _totalTokens.value = 0
        _lastScanTokens.value = 0
        _toastMessage.value = "Contador de tokens reiniciado"
    }

    // Containers list StateFlow
    val containersList: StateFlow<List<ContainerEntity>> = repository.allContainers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createContainer(name: String) {
        viewModelScope.launch {
            try {
                val sku = "CONT-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())}"
                repository.insertContainer(ContainerEntity(sku = sku, name = name))
                _toastMessage.value = "Contenedor '$name' creado con éxito."
            } catch (e: Exception) {
                _toastMessage.value = "Error al crear contenedor: ${e.message}"
            }
        }
    }

    fun deleteContainer(sku: String) {
        viewModelScope.launch {
            try {
                repository.deleteContainer(sku)
                _toastMessage.value = "Contenedor eliminado y productos desvinculados."
            } catch (e: Exception) {
                _toastMessage.value = "Error al eliminar contenedor: ${e.message}"
            }
        }
    }

    fun associateProductWithContainer(productId: Int, containerSku: String?) {
        viewModelScope.launch {
            try {
                repository.associateProductWithContainer(productId, containerSku)
                if (containerSku != null) {
                    _toastMessage.value = "Producto asignado al contenedor."
                } else {
                    _toastMessage.value = "Producto removido del contenedor."
                }
            } catch (e: Exception) {
                _toastMessage.value = "Error al asociar producto: ${e.message}"
            }
        }
    }

    fun associateModelWithContainer(modelName: String, containerSku: String?) {
        viewModelScope.launch {
            try {
                repository.associateModelWithContainer(modelName, containerSku)
                if (containerSku != null) {
                    _toastMessage.value = "Todos los productos del modelo '$modelName' fueron asignados al contenedor."
                } else {
                    _toastMessage.value = "Modelo desasociado del contenedor."
                }
            } catch (e: Exception) {
                _toastMessage.value = "Error al asociar modelo: ${e.message}"
            }
        }
    }

    fun getProductsInContainer(containerSku: String): Flow<List<ProductEntity>> {
        return repository.getProductsInContainer(containerSku)
    }

    private val _customGeminiKey = MutableStateFlow(prefs.getString("gemini_key", "") ?: "")
    val customGeminiKey = _customGeminiKey.asStateFlow()

    private val _customGroqKey = MutableStateFlow(prefs.getString("groq_key", "") ?: "")
    val customGroqKey = _customGroqKey.asStateFlow()

    private val _customHfKey = MutableStateFlow(prefs.getString("hf_key", "") ?: "")
    val customHfKey = _customHfKey.asStateFlow()

    private val _selectedProvider = MutableStateFlow(prefs.getString("selected_provider", "auto") ?: "auto")
    val selectedProvider = _selectedProvider.asStateFlow()

    fun saveApiKeys(gemini: String, groq: String, hf: String) {
        prefs.edit().apply {
            putString("gemini_key", gemini)
            putString("groq_key", groq)
            putString("hf_key", hf)
            apply()
        }
        _customGeminiKey.value = gemini
        _customGroqKey.value = groq
        _customHfKey.value = hf
    }

    fun setSelectedProvider(provider: String) {
        prefs.edit().putString("selected_provider", provider).apply()
        _selectedProvider.value = provider
    }

    // CSV Import states
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importProgressText = MutableStateFlow("")
    val importProgressText: StateFlow<String> = _importProgressText.asStateFlow()

    private val _importErrorDetails = MutableStateFlow<List<String>?>(null)
    val importErrorDetails: StateFlow<List<String>?> = _importErrorDetails.asStateFlow()

    private val _showImportSummary = MutableStateFlow<String?>(null)
    val showImportSummary: StateFlow<String?> = _showImportSummary.asStateFlow()

    fun clearImportSummary() {
        _showImportSummary.value = null
        _importErrorDetails.value = null
    }

    fun importCsv(uri: android.net.Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importProgressText.value = "Abriendo archivo..."
            _importErrorDetails.value = null
            _showImportSummary.value = null
            
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _toastMessage.value = "No se pudo abrir el archivo CSV."
                    _isImporting.value = false
                    return@launch
                }
                
                _importProgressText.value = "Procesando archivo CSV..."
                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                val lines = reader.readLines()
                if (lines.isEmpty()) {
                    _toastMessage.value = "El archivo CSV está vacío."
                    _isImporting.value = false
                    return@launch
                }

                // Header validation
                val firstLine = lines.first()
                val separators = listOf(",", ";")
                var selectedSeparator = ","
                var headerTokens = firstLine.split(",")
                
                // Determine separator
                if (firstLine.contains(";") && !firstLine.contains(",")) {
                    selectedSeparator = ";"
                    headerTokens = firstLine.split(";")
                }
                
                val headers = headerTokens.map { it.trim().uppercase().removeSurrounding("\"") }
                
                val upcIdx = headers.indexOfFirst { it == "UPC" || it == "CODIGO" || it == "CÓDIGO" || it == "BARCODE" }
                val modelIdx = headers.indexOfFirst { it == "MODELO" || it == "MODEL" || it == "ESTILO" }
                val sizeIdx = headers.indexOfFirst { it == "TALLA" || it == "SIZE" }
                val qtyIdx = headers.indexOfFirst { it == "CANTIDAD" || it == "CANT" || it == "QUANTITY" || it == "QTY" }

                if (upcIdx == -1 || modelIdx == -1 || sizeIdx == -1 || qtyIdx == -1) {
                    _toastMessage.value = "Formato de CSV inválido. Columnas requeridas: UPC, MODELO, TALLA, CANTIDAD."
                    _isImporting.value = false
                    return@launch
                }

                var successCount = 0
                var errorCount = 0
                val errorList = mutableListOf<String>()

                for (i in 1 until lines.size) {
                    val line = lines[i].trim()
                    if (line.isEmpty()) continue

                    val tokens = line.split(selectedSeparator).map { it.trim().removeSurrounding("\"") }
                    val maxIndexNeeded = maxOf(upcIdx, modelIdx, sizeIdx, qtyIdx)
                    
                    if (tokens.size <= maxIndexNeeded) {
                        errorCount++
                        errorList.add("Fila ${i + 1}: Columnas incompletas (esperadas al menos ${maxIndexNeeded + 1}, encontradas ${tokens.size}).")
                        continue
                    }

                    val upc = tokens[upcIdx]
                    val model = tokens[modelIdx]
                    val size = tokens[sizeIdx]
                    val qtyStr = tokens[qtyIdx]

                    if (upc.isEmpty() || model.isEmpty() || size.isEmpty()) {
                        errorCount++
                        errorList.add("Fila ${i + 1}: Datos vacíos (UPC, modelo o talla faltantes).")
                        continue
                    }

                    val qty = qtyStr.toIntOrNull()
                    if (qty == null || qty <= 0) {
                        errorCount++
                        errorList.add("Fila ${i + 1}: Cantidad inválida ($qtyStr). Debe ser un número mayor a 0.")
                        continue
                    }

                    // Insert or increment in DB (color is empty, it will be split from the model string if it contains a hyphen)
                    repository.addOrIncrementProduct(upc, model, size, "", qty)
                    successCount++
                }

                if (errorList.isNotEmpty()) {
                    _importErrorDetails.value = errorList
                }
                
                _showImportSummary.value = "Importación completada:\n- $successCount registros cargados con éxito.\n- $errorCount filas ignoradas con errores."
                _toastMessage.value = "Carga CSV finalizada con ${if (errorCount > 0) "algunos errores" else "éxito"}"
            } catch (e: Exception) {
                _toastMessage.value = "Error al leer CSV: ${e.message}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun incrementStockByUpc(upc: String, onMatched: (ProductEntity) -> Unit, onNotFound: () -> Unit) {
        viewModelScope.launch {
            try {
                val matches = repository.findProductsByUpc(upc)
                if (matches.isNotEmpty()) {
                    val firstMatch = matches.first()
                    val newQty = firstMatch.quantity + 1
                    repository.updateQuantity(firstMatch, newQty)
                    onMatched(firstMatch.copy(quantity = newQty))
                } else {
                    onNotFound()
                }
            } catch (e: Exception) {
                _toastMessage.value = "Error al buscar UPC: ${e.message}"
            }
        }
    }

    // Reactive inventory list from database
    val inventoryList: StateFlow<List<ProductEntity>> = repository.allProducts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // Temporary values for user verification / edit before saving
    private val _verificationProduct = MutableStateFlow<ProductEntity?>(null)
    val verificationProduct: StateFlow<ProductEntity?> = _verificationProduct.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun clearToast() {
        _toastMessage.value = null
    }

    fun setScanStateIdle() {
        _scanState.value = ScanState.Idle
        _verificationProduct.value = null
    }

    // Batch Scanning State Properties
    private val _isBatchMode = MutableStateFlow(false)
    val isBatchMode: StateFlow<Boolean> = _isBatchMode.asStateFlow()

    private val _batchQueue = MutableStateFlow<List<BatchItem>>(emptyList())
    val batchQueue: StateFlow<List<BatchItem>> = _batchQueue.asStateFlow()

    private val _isBatchProcessing = MutableStateFlow(false)
    val isBatchProcessing: StateFlow<Boolean> = _isBatchProcessing.asStateFlow()

    private val _batchRpmLimit = MutableStateFlow(5) // default 5 RPM as gemini 2.5 f is 5 / min
    val batchRpmLimit: StateFlow<Int> = _batchRpmLimit.asStateFlow()

    private val _showBatchResults = MutableStateFlow(false)
    val showBatchResults: StateFlow<Boolean> = _showBatchResults.asStateFlow()

    private val _batchProgressText = MutableStateFlow("")
    val batchProgressText: StateFlow<String> = _batchProgressText.asStateFlow()

    fun setBatchMode(enabled: Boolean) {
        _isBatchMode.value = enabled
        if (!enabled) {
            _batchQueue.value = emptyList()
            _showBatchResults.value = false
            _isBatchProcessing.value = false
            _batchProgressText.value = ""
        }
    }

    fun setBatchRpmLimit(limit: Int) {
        _batchRpmLimit.value = limit
    }

    fun addImageToBatch(bitmap: Bitmap, uri: android.net.Uri?) {
        val currentList = _batchQueue.value.toMutableList()
        currentList.add(
            BatchItem(
                bitmap = bitmap,
                imageUri = uri,
                status = BatchItemStatus.PENDING
            )
        )
        _batchQueue.value = currentList
    }

    fun removeImageFromBatch(id: String) {
        _batchQueue.value = _batchQueue.value.filter { it.id != id }
    }

    fun updateBatchItemDetails(id: String, model: String, upc: String, size: String) {
        _batchQueue.value = _batchQueue.value.map { item ->
            if (item.id == id) {
                item.copy(
                    extractedModel = model,
                    extractedUpc = upc,
                    extractedSize = size
                )
            } else {
                item
            }
        }
    }

    fun clearBatch() {
        _batchQueue.value = emptyList()
        _showBatchResults.value = false
        _isBatchProcessing.value = false
        _batchProgressText.value = ""
    }

    fun closeBatchResults() {
        _showBatchResults.value = false
    }

    fun openBatchResults() {
        _showBatchResults.value = true
    }

    fun reprocessBatchItem(id: String) {
        val item = _batchQueue.value.find { it.id == id } ?: return
        
        _batchQueue.value = _batchQueue.value.map {
            if (it.id == id) it.copy(status = BatchItemStatus.PROCESSING, errorMessage = null) else it
        }
        
        viewModelScope.launch {
            when (val result = GeminiService.analyzeLabelImage(
                bitmap = item.bitmap,
                customGeminiKey = _customGeminiKey.value,
                customGroqKey = _customGroqKey.value,
                customHfKey = _customHfKey.value,
                selectedProvider = _selectedProvider.value
            )) {
                is OcrResult.Success -> {
                    val tokens = result.tokensUsed
                    if (tokens > 0) {
                        val newTotal = _totalTokens.value + tokens
                        prefs.edit().putInt("total_tokens", newTotal).apply()
                        _totalTokens.value = newTotal
                        _lastScanTokens.value = tokens
                    }
                    _batchQueue.value = _batchQueue.value.map {
                        if (it.id == id) {
                            it.copy(
                                status = BatchItemStatus.SUCCESS,
                                extractedUpc = result.upc,
                                extractedModel = result.model,
                                extractedSize = result.size,
                                errorMessage = null
                            )
                        } else {
                            it
                        }
                    }
                }
                is OcrResult.Error -> {
                    val tokens = result.tokensUsed
                    if (tokens > 0) {
                        val newTotal = _totalTokens.value + tokens
                        prefs.edit().putInt("total_tokens", newTotal).apply()
                        _totalTokens.value = newTotal
                        _lastScanTokens.value = tokens
                    }
                    _batchQueue.value = _batchQueue.value.map {
                        if (it.id == id) {
                            it.copy(
                                status = BatchItemStatus.ERROR,
                                errorMessage = result.message
                            )
                        } else {
                            it
                        }
                    }
                }
            }
        }
    }

    fun startBatchProcessing(onFinished: () -> Unit = {}) {
        if (_batchQueue.value.isEmpty() || _isBatchProcessing.value) return
        _isBatchProcessing.value = true
        _batchProgressText.value = "Iniciando procesamiento..."
        
        viewModelScope.launch {
            val list = _batchQueue.value
            val rpm = _batchRpmLimit.value
            val delayMs = 60_000L / rpm
            
            for (i in list.indices) {
                val item = list[i]
                if (item.status == BatchItemStatus.SUCCESS) {
                    continue
                }
                
                _isBatchProcessing.value = true // Ensure still active
                
                // Pre-scan local barcode first!
                _batchProgressText.value = "Pre-escaneando barras en ${i + 1} de ${list.size}..."
                val localBarcode = scanBarcodeFromBitmap(item.bitmap)
                if (localBarcode != null && localBarcode.isNotEmpty()) {
                    // 1. Check if the UPC already exists in DB
                    val dbMatches = repository.findProductsByUpc(localBarcode)
                    if (dbMatches.isNotEmpty()) {
                        val matchedProduct = dbMatches.first()
                        _batchQueue.value = _batchQueue.value.map {
                            if (it.id == item.id) {
                                it.copy(
                                    status = BatchItemStatus.SUCCESS,
                                    extractedUpc = localBarcode,
                                    extractedModel = matchedProduct.model,
                                    extractedSize = matchedProduct.size,
                                    errorMessage = null
                                )
                            } else {
                                it
                            }
                        }
                        continue // Skip Vision AI completely!
                    }
                    
                    // 2. Check if another item in the current batch queue has already successfully resolved this UPC
                    val dupResolved = _batchQueue.value.firstOrNull {
                        it.id != item.id && it.extractedUpc == localBarcode && it.status == BatchItemStatus.SUCCESS
                    }
                    if (dupResolved != null) {
                        _batchQueue.value = _batchQueue.value.map {
                            if (it.id == item.id) {
                                it.copy(
                                    status = BatchItemStatus.SUCCESS,
                                    extractedUpc = localBarcode,
                                    extractedModel = dupResolved.extractedModel,
                                    extractedSize = dupResolved.extractedSize,
                                    errorMessage = null
                                )
                            } else {
                                it
                            }
                        }
                        continue // Skip Vision AI completely!
                    }
                }
                
                var attempts = 0
                var success = false
                var finalResult: OcrResult? = null
                val batchItemStartTime = System.currentTimeMillis()
                
                while (attempts < 3 && !success && _isBatchProcessing.value) {
                    attempts++
                    if (attempts == 1) {
                        _batchProgressText.value = "Procesando imagen ${i + 1} de ${list.size}..."
                    } else {
                        _batchProgressText.value = "Procesando imagen ${i + 1} de ${list.size} (Intento $attempts)..."
                    }
                    
                    _batchQueue.value = _batchQueue.value.map {
                        if (it.id == item.id) it.copy(status = BatchItemStatus.PROCESSING, errorMessage = null) else it
                    }
                    
                    val result = GeminiService.analyzeLabelImage(
                        bitmap = item.bitmap,
                        customGeminiKey = _customGeminiKey.value,
                        customGroqKey = _customGroqKey.value,
                        customHfKey = _customHfKey.value,
                        selectedProvider = _selectedProvider.value
                    )
                    
                    // Apply family auto-completion / prefix-matching based on 8-digit prefix
                    val processedResult = if (result is OcrResult.Success) {
                        val upcToUse = if (localBarcode != null && localBarcode.isNotEmpty()) localBarcode else result.upc
                        var modelToUse = result.model
                        
                        if (upcToUse.length >= 8) {
                            val prefix = upcToUse.substring(0, 8)
                            val prefixMatch = repository.findProductByUpcPrefix(prefix)
                            if (prefixMatch != null) {
                                modelToUse = prefixMatch.model
                            }
                        }
                        result.copy(upc = upcToUse, model = modelToUse)
                    } else {
                        result
                    }
                    
                    finalResult = processedResult
                    
                    val tokens = when (processedResult) {
                        is OcrResult.Success -> processedResult.tokensUsed
                        is OcrResult.Error -> processedResult.tokensUsed
                    }
                    if (tokens > 0) {
                        val newTotal = _totalTokens.value + tokens
                        prefs.edit().putInt("total_tokens", newTotal).apply()
                        _totalTokens.value = newTotal
                        _lastScanTokens.value = tokens
                    }
                    
                    if (processedResult is OcrResult.Success) {
                        success = true
                    } else if (processedResult is OcrResult.Error && processedResult.errorCode == 429) {
                        if (attempts < 3) {
                            val backoffTimeMs = 15_000L * attempts
                            var ticks = backoffTimeMs / 1000L
                            while (ticks > 0 && _isBatchProcessing.value) {
                                _batchProgressText.value = "Límite superado (429). Reintentando en ${ticks}s..."
                                kotlinx.coroutines.delay(1000)
                                ticks--
                            }
                        }
                    } else {
                        break
                    }
                }
                
                val resultToSave = finalResult ?: OcrResult.Error("Procesamiento cancelado.")
                
                _batchQueue.value = _batchQueue.value.map {
                    if (it.id == item.id) {
                        when (resultToSave) {
                            is OcrResult.Success -> {
                                it.copy(
                                    status = BatchItemStatus.SUCCESS,
                                    extractedUpc = resultToSave.upc,
                                    extractedModel = resultToSave.model,
                                    extractedSize = resultToSave.size,
                                    errorMessage = null
                                )
                            }
                            is OcrResult.Error -> {
                                it.copy(
                                    status = BatchItemStatus.ERROR,
                                    errorMessage = resultToSave.message
                                )
                            }
                        }
                    } else {
                        it
                    }
                }
                
                if (i < list.size - 1 && _isBatchProcessing.value && resultToSave is OcrResult.Success) {
                    val elapsedTime = System.currentTimeMillis() - batchItemStartTime
                    val remainingDelay = delayMs - elapsedTime
                    if (remainingDelay > 0) {
                        var ticks = remainingDelay / 1000L
                        while (ticks > 0 && _isBatchProcessing.value) {
                            _batchProgressText.value = "Límite RPM: Foto ${i + 1}/${list.size} procesada. " +
                                    "Siguiente en ${ticks}s..."
                            kotlinx.coroutines.delay(1000)
                            ticks--
                        }
                    }
                }
            }
            
            _isBatchProcessing.value = false
            _batchProgressText.value = "¡Lote completo!"
            _showBatchResults.value = true
            onFinished()
        }
    }

    fun confirmAndSaveBatch() {
        viewModelScope.launch {
            val list = _batchQueue.value.filter { it.status == BatchItemStatus.SUCCESS }
            if (list.isEmpty()) {
                _toastMessage.value = "No hay productos listos para guardar en el lote"
                return@launch
            }
            
            var addedCount = 0
            var updatedCount = 0
            
            for (item in list) {
                try {
                    val res = repository.addOrIncrementProduct(
                        upc = item.extractedUpc,
                        model = item.extractedModel,
                        size = item.extractedSize,
                        color = "",
                        quantity = 1
                    )
                    when (res) {
                        is AddResult.NewAdded -> addedCount++
                        is AddResult.Incremented -> updatedCount++
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Error saving batch item", e)
                }
            }
            
            _toastMessage.value = "¡Lote guardado! $addedCount nuevos, $updatedCount actualizados."
            _batchQueue.value = emptyList()
            _showBatchResults.value = false
        }
    }

    fun injectMockBatchProducts(mockItems: List<BatchItem>) {
        val currentList = _batchQueue.value.toMutableList()
        currentList.addAll(mockItems)
        _batchQueue.value = currentList
        _showBatchResults.value = true
    }

    // Run OCR analysis on label
    fun analyzeLabel(bitmap: Bitmap) {
        viewModelScope.launch {
            _scanState.value = ScanState.Processing
            _verificationProduct.value = null

            // Pre-scan local barcode first!
            val localBarcode = scanBarcodeFromBitmap(bitmap)
            if (localBarcode != null && localBarcode.isNotEmpty()) {
                val dbMatches = repository.findProductsByUpc(localBarcode)
                if (dbMatches.isNotEmpty()) {
                    val dbProduct = dbMatches.first()
                    _scanState.value = ScanState.Success(
                        upc = localBarcode,
                        model = dbProduct.model,
                        size = dbProduct.size,
                        color = dbProduct.color
                    )
                    _verificationProduct.value = ProductEntity(
                        upc = localBarcode,
                        model = dbProduct.model,
                        size = dbProduct.size,
                        color = dbProduct.color,
                        quantity = 1
                    )
                    _toastMessage.value = "Producto pre-escaneado localmente de la base de datos (0 tokens)"
                    return@launch
                }
            }

            when (val result = GeminiService.analyzeLabelImage(
                bitmap = bitmap,
                customGeminiKey = _customGeminiKey.value,
                customGroqKey = _customGroqKey.value,
                customHfKey = _customHfKey.value,
                selectedProvider = _selectedProvider.value
            )) {
                is OcrResult.Success -> {
                    val upcToUse = if (localBarcode != null && localBarcode.isNotEmpty()) localBarcode else result.upc
                    var modelToUse = result.model
                    
                    if (upcToUse.length >= 8) {
                        val prefix = upcToUse.substring(0, 8)
                        val prefixMatch = repository.findProductByUpcPrefix(prefix)
                        if (prefixMatch != null) {
                            modelToUse = prefixMatch.model
                        }
                    }

                    val tokens = result.tokensUsed
                    if (tokens > 0) {
                        val newTotal = _totalTokens.value + tokens
                        prefs.edit().putInt("total_tokens", newTotal).apply()
                        _totalTokens.value = newTotal
                        _lastScanTokens.value = tokens
                        _toastMessage.value = "Tokens usados en escaneo: $tokens"
                    }
                    _scanState.value = ScanState.Success(
                        upc = upcToUse,
                        model = modelToUse,
                        size = result.size,
                        color = result.color
                    )
                    // Populate verification model to let user double-check or adjust before saving
                    _verificationProduct.value = ProductEntity(
                        upc = upcToUse,
                        model = modelToUse,
                        size = result.size,
                        color = result.color,
                        quantity = 1
                    )
                }
                is OcrResult.Error -> {
                    val tokens = result.tokensUsed
                    if (tokens > 0) {
                        val newTotal = _totalTokens.value + tokens
                        prefs.edit().putInt("total_tokens", newTotal).apply()
                        _totalTokens.value = newTotal
                        _lastScanTokens.value = tokens
                        _toastMessage.value = "Error (Tokens: $tokens): ${result.message}"
                    } else {
                        _toastMessage.value = result.message
                    }
                    _scanState.value = ScanState.Error(result.message)
                }
            }
        }
    }

    // Save/Commit product to database (handles checking existences and incrementing)
    fun commitProduct(upc: String, model: String, size: String, color: String, quantity: Int) {
        viewModelScope.launch {
            try {
                val res = repository.addOrIncrementProduct(upc, model, size, color, quantity)
                when (res) {
                    is AddResult.NewAdded -> {
                        _toastMessage.value = "Producto nuevo agregado: ${res.product.model} (${res.product.size})"
                    }
                    is AddResult.Incremented -> {
                        _toastMessage.value = "¡Coincidencia sumada! Cantidad para ${res.product.model} (${res.product.size}): ${res.product.quantity}"
                    }
                }
                setScanStateIdle()
            } catch (e: Exception) {
                _toastMessage.value = "Error al guardar: ${e.message}"
            }
        }
    }

    fun updateProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.updateProduct(product)
        }
    }

    fun updateQuantity(product: ProductEntity, newQuantity: Int) {
        viewModelScope.launch {
            repository.updateQuantity(product, newQuantity)
        }
    }

    fun deleteProduct(id: Int) {
        viewModelScope.launch {
            repository.deleteProduct(id)
        }
    }

    fun clearAllInventory() {
        viewModelScope.launch {
            repository.deleteAll()
            _toastMessage.value = "Inventario completamente restablecido"
        }
    }

    // Helper to generate CSV
    fun getInventoryCsvString(): String {
        val list = inventoryList.value
        if (list.isEmpty()) return ""

        val sb = StringBuilder()
        // Header
        sb.append("UPC,MODELO,TALLA,CANTIDAD\n")
        
        // Grouped by model as requested
        val grouped = list.groupBy { it.model }
        for ((model, items) in grouped) {
            for (item in items) {
                // Reconstruct composite model name if color is present
                val compositeModel = if (item.color.isNotEmpty() && item.color != "N/A") {
                    "${item.model}-${item.color}"
                } else {
                    item.model
                }
                // Clean fields from commas to prevent corrupt CSV formats
                val cleanModel = compositeModel.replace(",", ";").replace("\"", "'")
                val cleanUpc = item.upc.replace(",", ";").replace("\"", "'")
                val cleanSize = item.size.replace(",", ";").replace("\"", "'")
                sb.append("$cleanUpc,$cleanModel,$cleanSize,${item.quantity}\n")
            }
        }
        return sb.toString()
    }

    fun generateContainersPdfReport(context: Context, onFinished: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val containers = containersList.value
                val pdfDocument = android.graphics.pdf.PdfDocument()
                
                // Tabloid size in points: 11" x 17" -> 792 x 1224
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(792, 1224, 1).create()
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas
                
                val paint = android.graphics.Paint()
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 12f
                    isAntiAlias = true
                }
                val titlePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 20f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                val headerPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(0, 128, 128) // Teal
                    textSize = 14f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                
                var currentY = 50f
                val marginX = 40f
                val contentWidth = 792f - (marginX * 2) // 712f
                
                // Draw title
                canvas.drawText("REPORTE DE CONTENEDORES Y PRODUCTOS", marginX, currentY, titlePaint)
                currentY += 15f
                
                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                textPaint.textSize = 10f
                canvas.drawText("Generado el: $dateStr", marginX, currentY, textPaint)
                currentY += 35f
                
                if (containers.isEmpty()) {
                    textPaint.textSize = 12f
                    canvas.drawText("No hay contenedores registrados.", marginX, currentY, textPaint)
                } else {
                    for (container in containers) {
                        // Check if we need to start a new page
                        if (currentY > 1050f) {
                            pdfDocument.finishPage(page)
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            currentY = 50f
                        }
                        
                        // Draw Container Header Box
                        paint.color = android.graphics.Color.rgb(230, 242, 242) // Very light teal
                        paint.style = android.graphics.Paint.Style.FILL
                        canvas.drawRect(marginX, currentY, marginX + contentWidth, currentY + 60f, paint)
                        
                        paint.color = android.graphics.Color.rgb(0, 128, 128) // Teal border
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.strokeWidth = 1.5f
                        canvas.drawRect(marginX, currentY, marginX + contentWidth, currentY + 60f, paint)
                        
                        // Text inside header
                        canvas.drawText("Contenedor: ${container.name}", marginX + 15f, currentY + 25f, headerPaint)
                        
                        // Draw SKU Text
                        textPaint.textSize = 10f
                        textPaint.isFakeBoldText = true
                        canvas.drawText("SKU: ${container.sku}", marginX + 15f, currentY + 45f, textPaint)
                        
                        // Render Code 128 Barcode inside the Header Box
                        val barcodeX = marginX + contentWidth - 250f
                        val barcodeY = currentY + 10f
                        val barcodeHeight = 35f
                        drawCode128Barcode(canvas, container.sku, barcodeX, barcodeY, barcodeHeight)
                        
                        currentY += 75f
                        
                        // Fetch products for this container from DB
                        val products = repository.getProductsInContainer(container.sku).first()
                        
                        if (products.isEmpty()) {
                            textPaint.textSize = 11f
                            textPaint.isFakeBoldText = false
                            canvas.drawText("  (No hay productos asociados en este contenedor)", marginX + 15f, currentY, textPaint)
                            currentY += 25f
                        } else {
                            // Draw table headers
                            paint.color = android.graphics.Color.LTGRAY
                            paint.style = android.graphics.Paint.Style.FILL
                            canvas.drawRect(marginX, currentY, marginX + contentWidth, currentY + 20f, paint)
                            
                            textPaint.textSize = 10f
                            textPaint.isFakeBoldText = true
                            canvas.drawText("UPC (EAN-13 Barcode)", marginX + 10f, currentY + 14f, textPaint)
                            canvas.drawText("Modelo / Estilo", marginX + 260f, currentY + 14f, textPaint)
                            canvas.drawText("Talla", marginX + 440f, currentY + 14f, textPaint)
                            canvas.drawText("Color", marginX + 520f, currentY + 14f, textPaint)
                            canvas.drawText("Cant", marginX + 660f, currentY + 14f, textPaint)
                            
                            currentY += 20f
                            
                            textPaint.isFakeBoldText = false
                            for (product in products) {
                                if (currentY > 1130f) {
                                    pdfDocument.finishPage(page)
                                    page = pdfDocument.startPage(pageInfo)
                                    canvas = page.canvas
                                    currentY = 50f
                                }
                                
                                // Draw row divider
                                paint.color = android.graphics.Color.rgb(220, 220, 220)
                                paint.style = android.graphics.Paint.Style.STROKE
                                paint.strokeWidth = 0.5f
                                canvas.drawLine(marginX, currentY, marginX + contentWidth, currentY, paint)
                                
                                // Row background
                                paint.color = android.graphics.Color.WHITE
                                paint.style = android.graphics.Paint.Style.FILL
                                canvas.drawRect(marginX, currentY, marginX + contentWidth, currentY + 45f, paint)
                                
                                // Draw EAN-13 Barcode
                                drawEan13Barcode(canvas, product.upc, marginX + 10f, currentY + 5f, 25f)
                                
                                // Draw product UPC text under barcode
                                textPaint.textSize = 8f
                                canvas.drawText(product.upc, marginX + 10f, currentY + 40f, textPaint)
                                
                                textPaint.textSize = 10f
                                canvas.drawText(product.model, marginX + 260f, currentY + 25f, textPaint)
                                canvas.drawText(product.size, marginX + 440f, currentY + 25f, textPaint)
                                canvas.drawText(product.color, marginX + 520f, currentY + 25f, textPaint)
                                canvas.drawText(product.quantity.toString(), marginX + 660f, currentY + 25f, textPaint)
                                
                                currentY += 45f
                            }
                            
                            // Bottom border of table
                            paint.color = android.graphics.Color.rgb(180, 180, 180)
                            paint.style = android.graphics.Paint.Style.STROKE
                            paint.strokeWidth = 1f
                            canvas.drawLine(marginX, currentY, marginX + contentWidth, currentY, paint)
                            
                            currentY += 20f
                        }
                        
                        currentY += 15f
                    }
                }
                
                pdfDocument.finishPage(page)
                
                val file = File(context.cacheDir, "reporte_contenedores_${System.currentTimeMillis()}.pdf")
                file.outputStream().use { out ->
                    pdfDocument.writeTo(out)
                }
                pdfDocument.close()
                
                withContext(Dispatchers.Main) {
                    onFinished(file)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error generating PDF", e)
                withContext(Dispatchers.Main) {
                    _toastMessage.value = "Error al generar PDF: ${e.message}"
                    onFinished(null)
                }
            }
        }
    }

    private fun drawCode128Barcode(canvas: android.graphics.Canvas, text: String, x: Float, y: Float, height: Float) {
        try {
            val segments = BarcodeEncoder.encodeCode128(text)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                style = android.graphics.Paint.Style.FILL
            }
            val moduleWidth = 1.3f
            var currentX = x
            
            for (seg in segments) {
                val w = seg.first * moduleWidth
                if (seg.second) { // isBar
                    canvas.drawRect(currentX, y, currentX + w, y + height, paint)
                }
                currentX += w
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error drawing Code 128 barcode: ${e.message}")
        }
    }

    private fun drawEan13Barcode(canvas: android.graphics.Canvas, upc: String, x: Float, y: Float, height: Float) {
        try {
            val binary = BarcodeEncoder.encodeEan13(upc)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                style = android.graphics.Paint.Style.FILL
            }
            val moduleWidth = 1.8f
            var currentX = x
            
            for (char in binary) {
                if (char == '1') {
                    canvas.drawRect(currentX, y, currentX + moduleWidth, y + height, paint)
                }
                currentX += moduleWidth
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error drawing EAN-13 barcode: ${e.message}")
        }
    }

    private suspend fun scanBarcodeFromBitmap(bitmap: Bitmap): String? = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val scanner = BarcodeScanning.getClient()
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (continuation.isActive) {
                        val raw = barcodes.firstOrNull()?.rawValue
                        continuation.resume(raw)
                    }
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }
}
