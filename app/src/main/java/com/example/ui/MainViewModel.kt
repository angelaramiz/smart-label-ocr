package com.example.ui

import android.graphics.Bitmap
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.ProductEntity
import com.example.data.network.GeminiService
import com.example.data.network.OcrResult
import com.example.data.repository.AddResult
import com.example.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

                    // Insert or increment in DB
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
                    finalResult = result
                    
                    if (result is OcrResult.Success) {
                        success = true
                    } else if (result is OcrResult.Error && result.errorCode == 429) {
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

            when (val result = GeminiService.analyzeLabelImage(
                bitmap = bitmap,
                customGeminiKey = _customGeminiKey.value,
                customGroqKey = _customGroqKey.value,
                customHfKey = _customHfKey.value,
                selectedProvider = _selectedProvider.value
            )) {
                is OcrResult.Success -> {
                    _scanState.value = ScanState.Success(
                        upc = result.upc,
                        model = result.model,
                        size = result.size,
                        color = result.color
                    )
                    // Populate verification model to let user double-check or adjust before saving
                    _verificationProduct.value = ProductEntity(
                        upc = result.upc,
                        model = result.model,
                        size = result.size,
                        color = result.color,
                        quantity = 1
                    )
                }
                is OcrResult.Error -> {
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
                // Clean fields from commas to prevent corrupt CSV formats
                val cleanModel = item.model.replace(",", ";").replace("\"", "'")
                val cleanUpc = item.upc.replace(",", ";").replace("\"", "'")
                val cleanSize = item.size.replace(",", ";").replace("\"", "'")
                sb.append("$cleanUpc,$cleanModel,$cleanSize,${item.quantity}\n")
            }
        }
        return sb.toString()
    }
}
