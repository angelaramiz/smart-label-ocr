package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.database.ProductEntity
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.ScanState
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val factory = MainViewModelFactory(applicationContext)
                val viewModel: MainViewModel = viewModel(factory = factory)
                
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Extension to draw custom sheet cell borders for the Excel High Density theme
fun Modifier.cellBorder(
    right: Boolean = true,
    bottom: Boolean = false,
    color: Color = Color(0xFFE2E8F0)
): Modifier = this.drawBehind {
    if (right) {
        drawLine(
            color = color,
            start = Offset(size.width, 0f),
            end = Offset(size.width, size.height),
            strokeWidth = 3f
        )
    }
    if (bottom) {
        drawLine(
            color = color,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 3f
        )
    }
}


@Composable
fun MainScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // States from ViewModel
    val inventoryList by viewModel.inventoryList.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val verificationProduct by viewModel.verificationProduct.collectAsState()

    // Batch states from ViewModel
    val isBatchMode by viewModel.isBatchMode.collectAsState()
    val batchQueue by viewModel.batchQueue.collectAsState()
    val isBatchProcessing by viewModel.isBatchProcessing.collectAsState()
    val batchRpmLimit by viewModel.batchRpmLimit.collectAsState()
    val showBatchResults by viewModel.showBatchResults.collectAsState()
    val batchProgressText by viewModel.batchProgressText.collectAsState()
    
    // Viewport Toggle: "escáner" (Scanner) vs "inventario" (Grouped Inventory View)
    var currentTab by remember { mutableStateOf("escáner") }
    
    // Search query for repository data
    var searchQuery by remember { mutableStateOf("") }
    
    // Manual inputs form dialog trigger
    var showManualAddDialog by remember { mutableStateOf(false) }
    
    // Image selection state
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // File structure for Camera captures
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // GitHub Auto-update states
    var showUpdateDialog by remember { mutableStateOf(false) }
    var latestReleaseInfo by remember { mutableStateOf<com.example.data.network.GitHubUpdateService.ReleaseInfo?>(null) }

    LaunchedEffect(Unit) {
        val latest = com.example.data.network.GitHubUpdateService.checkForUpdates()
        if (latest != null) {
            val currentVersion = "v" + com.example.BuildConfig.VERSION_NAME
            if (com.example.data.network.isNewerVersion(latest.tagName, currentVersion)) {
                latestReleaseInfo = latest
                showUpdateDialog = true
            }
        }
    }

    // Launcher for Selecting Gallery Images
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bitmap = uriToBitmap(context, uri)
            if (bitmap != null) {
                if (isBatchMode) {
                    viewModel.addImageToBatch(bitmap, uri)
                } else {
                    selectedImageUri = uri
                    selectedBitmap = bitmap
                    viewModel.analyzeLabel(bitmap)
                }
            }
        }
    }

    // Launcher for Selecting Multiple Gallery Images at once
    val multipleGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        for (uri in uris) {
            val bitmap = uriToBitmap(context, uri)
            if (bitmap != null) {
                viewModel.addImageToBatch(bitmap, uri)
            }
        }
    }

    // Launcher for Capturing Camera Images
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraImageUri?.let { uri ->
                val bitmap = uriToBitmap(context, uri)
                if (bitmap != null) {
                    if (isBatchMode) {
                        viewModel.addImageToBatch(bitmap, uri)
                    } else {
                        selectedImageUri = uri
                        selectedBitmap = bitmap
                        viewModel.analyzeLabel(bitmap)
                    }
                }
            }
        }
    }

    // Auto-completion detection of a running active batch with real-time sound/vibration alerts
    var previouslyProcessing by remember { mutableStateOf(false) }
    LaunchedEffect(isBatchProcessing) {
        if (previouslyProcessing && !isBatchProcessing && batchQueue.isNotEmpty()) {
            sendCompletionNotification(context)
        }
        previouslyProcessing = isBatchProcessing
    }

    // Alert Handling (Toast proxy)
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearToast()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // App Header Row
        HeaderBar(
            onClearAll = { viewModel.clearAllInventory() },
            onCheckForUpdates = {
                scope.launch {
                    Toast.makeText(context, "Comprobando actualizaciones...", Toast.LENGTH_SHORT).show()
                    val latest = com.example.data.network.GitHubUpdateService.checkForUpdates()
                    if (latest != null) {
                        val currentVersion = "v" + com.example.BuildConfig.VERSION_NAME
                        if (com.example.data.network.isNewerVersion(latest.tagName, currentVersion)) {
                            latestReleaseInfo = latest
                            showUpdateDialog = true
                        } else {
                            Toast.makeText(context, "Ya tienes la versión más reciente ($currentVersion)", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "No se encontraron actualizaciones o hubo un error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Metrics Banner Dashboard
        MetricsDashboardBanner(
            totalProductsCount = inventoryList.size,
            uniqueModelsCount = inventoryList.map { it.model }.distinct().size,
            totalQuantitySum = inventoryList.sumOf { it.quantity }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Inline Navigation Segmented Toggles
        SegmentedTabBar(
            currentTab = currentTab,
            onTabSelected = { tab ->
                currentTab = tab
                if (tab == "escáner") {
                    viewModel.setScanStateIdle()
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Active layout viewport switcher with slide tab transitions
        AnimatedContent(
            targetState = currentTab,
            modifier = Modifier.fillMaxWidth().weight(1f),
            transitionSpec = {
                if (targetState == "inventario") {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width } + fadeOut()
                    )
                } else {
                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> width } + fadeOut()
                    )
                }
            },
            label = "tabTransition"
        ) { targetTab ->
            if (targetTab == "escáner") {
                // VIEWPORT 1: ESCÁNER INTELIGENTE (Smart Label OCR Scanner)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Dynamic Mode Switcher Card at the top of the tab
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isBatchMode) Icons.AutoMirrored.Filled.List else Icons.Default.Home,
                                            contentDescription = "Operating Mode",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (isBatchMode) "Modo Lote (Cola Activa)" else "Modo Escaneo Individual",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isBatchMode) "Buffer de fotos con límites RPM and revisión" else "Procesa una etiqueta a la vez",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = isBatchMode,
                                    onCheckedChange = { viewModel.setBatchMode(it) }
                                )
                            }
                        }
                    }

                    if (!isBatchMode) {
                        // --- SINGLE SCAN MODE LAYOUT ---
                        item {
                            ScannerOperationsPanel(
                                selectedImageUri = selectedImageUri,
                                selectedBitmap = selectedBitmap,
                                scanState = scanState,
                                onLaunchGallery = { galleryLauncher.launch("image/*") },
                                onLaunchCamera = {
                                    val tempFile = File(context.cacheDir, "camera_label_${System.currentTimeMillis()}.jpg")
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        tempFile
                                    )
                                    cameraImageUri = uri
                                    cameraLauncher.launch(uri)
                                },
                                onSimulateReferenceLabel = {
                                    // High Craftsmanship UI simulator of the exact footwear labels specified in the prompt
                                    viewModel.setScanStateIdle()
                                    // Create a simulated 1x1 test scan representing the user's box label request
                                    val simulatedUpc = "198969847733"
                                    val simulatedModel = "GWFASHIE"
                                    val simulatedSize = "6 M"
                                    val simulatedColor = "DARK RED 600"
                                    
                                    viewModel.commitProduct(simulatedUpc, simulatedModel, simulatedSize, simulatedColor, 1)
                                }
                            )
                        }

                        item {
                            // Gemini Analysis Loading Indicators
                            AnimatedVisibility(
                                visible = scanState is ScanState.Processing,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "Gemini 2.5 Flash analizando etiqueta...",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Verificando UPC, Modelo y Tallas mediante OCR multi-modal.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // If Error occurred, show detailed error prompt
                        if (scanState is ScanState.Error) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "Error",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Error de Procesamiento OCR",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = (scanState as ScanState.Error).message,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = { viewModel.setScanStateIdle() },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("Reintentar")
                                        }
                                    }
                                }
                            }
                        }

                        // Extracted values verification review sheet
                        if (verificationProduct != null && scanState is ScanState.Success) {
                            item {
                                VerificationProductForm(
                                    product = verificationProduct!!,
                                    onSave = { updatedModel, updatedUpc, updatedSize, updatedColor, qty ->
                                        viewModel.commitProduct(
                                            upc = updatedUpc,
                                            model = updatedModel,
                                            size = updatedSize,
                                            color = updatedColor,
                                            quantity = qty
                                        )
                                        // Clear image selection
                                        selectedImageUri = null
                                        selectedBitmap = null
                                    },
                                    onCancel = {
                                        viewModel.setScanStateIdle()
                                        selectedImageUri = null
                                        selectedBitmap = null
                                    }
                                )
                            }
                        }
                    } else {
                        // --- BATCH MODE LAYOUT ---
                        item {
                            ScannerOperationsPanel(
                                selectedImageUri = null,
                                selectedBitmap = if (batchQueue.isNotEmpty()) batchQueue.last().bitmap else null,
                                scanState = ScanState.Idle,
                                onLaunchGallery = { multipleGalleryLauncher.launch("image/*") },
                                onLaunchCamera = {
                                    val tempFile = File(context.cacheDir, "camera_batch_${System.currentTimeMillis()}.jpg")
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        tempFile
                                    )
                                    cameraImageUri = uri
                                    cameraLauncher.launch(uri)
                                },
                                onSimulateReferenceLabel = {
                                    injectMockBatchProducts(viewModel, context)
                                }
                            )
                        }

                        item {
                            BatchOperationsPanel(
                                viewModel = viewModel,
                                batchQueue = batchQueue,
                                isBatchProcessing = isBatchProcessing,
                                batchRpmLimit = batchRpmLimit,
                                batchProgressText = batchProgressText,
                                onLaunchGallery = { multipleGalleryLauncher.launch("image/*") },
                                onLaunchCamera = {
                                    val tempFile = File(context.cacheDir, "camera_batch_${System.currentTimeMillis()}.jpg")
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        tempFile
                                    )
                                    cameraImageUri = uri
                                    cameraLauncher.launch(uri)
                                }
                            )
                        }
                    }
                }
            } else {
                // VIEWPORT 2: TABLA INVENTARIO (Excel-styled spreadsheets grouped by model)
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Filter Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar por Modelo o UPC...") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Buscar") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Limpiar")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action row (Export / Manual Add)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val csv = viewModel.getInventoryCsvString()
                                if (csv.isEmpty()) {
                                    Toast.makeText(context, "No hay productos que exportar", Toast.LENGTH_SHORT).show()
                                } else {
                                    shareCsvAndSave(context, csv)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Exportar")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exportar Excel (CSV)")
                        }

                        Button(
                            onClick = { showManualAddDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Manual")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Agregar Manual")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Excel Spreadsheet groups matching requested criteria
                    val filteredInventory = inventoryList.filter {
                        it.model.contains(searchQuery, ignoreCase = true) || 
                        it.upc.contains(searchQuery, ignoreCase = true) ||
                        it.size.contains(searchQuery, ignoreCase = true)
                    }

                    val groupedByModel = filteredInventory.groupBy { it.model.uppercase().trim() }

                    if (groupedByModel.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "Sin coincidencias" else "Inventario vacío",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "Intente con otros términos de búsqueda." else "Escanee una etiqueta de calzado o prenda para registrar su stock.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            groupedByModel.forEach { (modelName, products) ->
                                item {
                                    ProductModelSpreadsheetCard(
                                        modelName = modelName,
                                        productsList = products,
                                        onIncrement = { prod -> viewModel.updateQuantity(prod, prod.quantity + 1) },
                                        onDecrement = { prod -> viewModel.updateQuantity(prod, prod.quantity - 1) },
                                        onDelete = { id -> viewModel.deleteProduct(id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Manual Entry Dialog pop-up
    if (showManualAddDialog) {
        ManualFormDialog(
            onDismiss = { showManualAddDialog = false },
            onConfirm = { m, u, s, c, q ->
                viewModel.commitProduct(u, m, s, c, q)
                showManualAddDialog = false
            }
        )
    }

    // Batch Results Review Table Dialog pop-up overlay
    if (showBatchResults) {
        BatchResultsReviewDialog(
            viewModel = viewModel,
            batchQueue = batchQueue,
            onClose = { viewModel.closeBatchResults() }
        )
    }

    // GitHub Auto-updater Dialog
    if (showUpdateDialog && latestReleaseInfo != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Actualización disponible") },
            text = { Text("Una nueva versión (${latestReleaseInfo!!.tagName}) está disponible en GitHub. ¿Deseas descargarla e instalarla ahora?") },
            confirmButton = {
                Button(
                    onClick = {
                        showUpdateDialog = false
                        com.example.data.network.downloadAndInstallApk(context, latestReleaseInfo!!.downloadUrl)
                    }
                ) {
                    Text("Actualizar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUpdateDialog = false }) {
                    Text("Omitir")
                }
            }
        )
    }
}

@Composable
fun HeaderBar(onClearAll: () -> Unit, onCheckForUpdates: () -> Unit) {
    var showConfirmClear by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Elegant scanner visual logo icon in primary teal
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "Scanner Logo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "LabelScan AI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 20.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "v${com.example.BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val activeProvider = com.example.data.network.GeminiService.getActiveProviderName()
                Text(
                    text = "OCR: $activeProvider",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opciones",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Comprobar actualización") },
                    onClick = {
                        menuExpanded = false
                        onCheckForUpdates()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualizar",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Restablecer inventario") },
                    onClick = {
                        menuExpanded = false
                        showConfirmClear = true
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Limpiar",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }

    if (showConfirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            title = { Text("¿Deseas restablecer el inventario?") },
            text = { Text("Esta acción eliminará de forma permanente todas las etiquetas cargadas y el inventario recopilado hasta el momento.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showConfirmClear = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Borrar Todo")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmClear = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun SegmentedTabBar(currentTab: String, onTabSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val scannerActive = currentTab == "escáner"
        val inventoryActive = currentTab == "inventario"

        val scannerBg by androidx.compose.animation.animateColorAsState(
            targetValue = if (scannerActive) MaterialTheme.colorScheme.primary else Color.Transparent,
            label = "scannerBg"
        )
        val scannerContent by androidx.compose.animation.animateColorAsState(
            targetValue = if (scannerActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "scannerContent"
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(scannerBg)
                .clickable { onTabSelected("escáner") }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Escanear",
                tint = scannerContent
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Escanear Etiquetas",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scannerContent
            )
        }

        val inventoryBg by androidx.compose.animation.animateColorAsState(
            targetValue = if (inventoryActive) MaterialTheme.colorScheme.primary else Color.Transparent,
            label = "inventoryBg"
        )
        val inventoryContent by androidx.compose.animation.animateColorAsState(
            targetValue = if (inventoryActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "inventoryContent"
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(inventoryBg)
                .clickable { onTabSelected("inventario") }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = "Inventario",
                tint = inventoryContent
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Tabla Excel (BD)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = inventoryContent
            )
        }
    }
}
@Composable
fun MetricsDashboardBanner(
    totalProductsCount: Int,
    uniqueModelsCount: Int,
    totalQuantitySum: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Tallas/Variantes",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = totalProductsCount.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(32.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Modelos Únicos",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = uniqueModelsCount.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(32.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Stock Total",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = totalQuantitySum.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ScannerOperationsPanel(
    selectedImageUri: Uri?,
    selectedBitmap: Bitmap?,
    scanState: ScanState,
    onLaunchGallery: () -> Unit,
    onLaunchCamera: () -> Unit,
    onSimulateReferenceLabel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val scanlineOffsetY by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanlineOffsetY"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // High Density Viewfinder card with animated borders
        val accentColor = MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                .drawBehind {
                    val strokeWidth = 3.dp.toPx()
                    val cornerLength = 20.dp.toPx()
                    val colorWithAlpha = accentColor.copy(alpha = pulseAlpha)

                    // Top Left Corner
                    drawLine(colorWithAlpha, Offset(0f, 0f), Offset(cornerLength, 0f), strokeWidth)
                    drawLine(colorWithAlpha, Offset(0f, 0f), Offset(0f, cornerLength), strokeWidth)

                    // Top Right Corner
                    drawLine(colorWithAlpha, Offset(size.width, 0f), Offset(size.width - cornerLength, 0f), strokeWidth)
                    drawLine(colorWithAlpha, Offset(size.width, 0f), Offset(size.width, cornerLength), strokeWidth)

                    // Bottom Left Corner
                    drawLine(colorWithAlpha, Offset(0f, size.height), Offset(cornerLength, size.height), strokeWidth)
                    drawLine(colorWithAlpha, Offset(0f, size.height), Offset(0f, size.height - cornerLength), strokeWidth)

                    // Bottom Right Corner
                    drawLine(colorWithAlpha, Offset(size.width, size.height), Offset(size.width - cornerLength, size.height), strokeWidth)
                    drawLine(colorWithAlpha, Offset(size.width, size.height), Offset(size.width, size.height - cornerLength), strokeWidth)

                    // Draw animated laser scanline
                    drawLine(
                        color = accentColor.copy(alpha = 0.8f),
                        start = Offset(0f, scanlineOffsetY * size.height),
                        end = Offset(size.width, scanlineOffsetY * size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
        ) {
            if (selectedBitmap != null) {
                // If a photo was selected, show it inside our majestic viewfinder!
                Image(
                    bitmap = selectedBitmap.asImageBitmap(),
                    contentDescription = "Preview de Etiqueta",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Add translucent scanning overlay color
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(accentColor.copy(alpha = 0.05f))
                )
            } else {
                // Futuristic tech grid background illustration
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Scan icon",
                        tint = accentColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Visor de Escaneo AI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Coloque la etiqueta dentro del recuadro",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Dynamic Status Tag at bottom-left
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (selectedBitmap != null) "ANALIZANDO: ETIQUETA_CARGADA.JPG" else "VISOR ACTIVO: LISTO PARA CAPTURAR",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedBitmap != null) Color(0xFF38BDF8) else Color(0xFF34D399)
                )
            }
        }

        // Selection Actions & Simulated Trigger Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onLaunchCamera,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = "Cámara", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cámara", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onLaunchGallery,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Icon(imageVector = Icons.Default.Face, contentDescription = "Galería", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Galería", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        // Reference simulation test widget - CRITICAL FOR PREVIEW/DEVELOPMENT DEMONSTRATIONS!
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSimulateReferenceLabel() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Simulacro",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Inyección Rápida de Etiqueta (GWFASHIE)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Registrar de inmediato datos preestablecidos de la etiqueta modelo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun VerificationProductForm(
    product: ProductEntity,
    onSave: (model: String, upc: String, size: String, color: String, qty: Int) -> Unit,
    onCancel: () -> Unit
) {
    var modelVal by remember { mutableStateOf(product.model) }
    var upcVal by remember { mutableStateOf(product.upc) }
    var sizeVal by remember { mutableStateOf(product.size) }
    var colorVal by remember { mutableStateOf(product.color) }
    var qtyVal by remember { mutableStateOf(product.quantity) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🔍 Verificación de Datos OCR",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Gemini extrajo estos campos de la etiqueta. Corrígelos si es necesario antes de registrar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Input fields
            OutlinedTextField(
                value = modelVal,
                onValueChange = { modelVal = it },
                label = { Text("Modelo (Model)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors,
                singleLine = true
            )

            OutlinedTextField(
                value = upcVal,
                onValueChange = { upcVal = it },
                label = { Text("Código de Barras (UPC)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            OutlinedTextField(
                value = sizeVal,
                onValueChange = { sizeVal = it },
                label = { Text("Talla (Size)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors,
                singleLine = true
            )

            // Quantity incrementer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cantidad a Ingresar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (qtyVal > 1) qtyVal-- },
                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    ) {
                        Text("-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = qtyVal.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(
                        onClick = { qtyVal++ },
                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    ) {
                        Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text("Descartar")
                }

                Button(
                    onClick = {
                        if (upcVal.isEmpty() || modelVal.isEmpty() || sizeVal.isEmpty()) {
                            // Validation checks
                        } else {
                            onSave(modelVal, upcVal, sizeVal, colorVal, qtyVal)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Confirmar y Guardar")
                }
            }
        }
    }
}

@Composable
fun ProductModelSpreadsheetCard(
    modelName: String,
    productsList: List<ProductEntity>,
    onIncrement: (ProductEntity) -> Unit,
    onDecrement: (ProductEntity) -> Unit,
    onDelete: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            // Card Header (Model details)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Modelo",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "MODELO: ${modelName.uppercase()}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${productsList.size} variante(s) de talla | Stock: ${productsList.sumOf { it.quantity }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Excel styled Grid Spreadsheet Header
                    ExcelSpreadsheetHeader()

                    // Rows
                    productsList.forEachIndexed { idx, product ->
                        ExcelSpreadsheetRow(
                            product = product,
                            isZebra = idx % 2 == 1,
                            index = idx + 1,
                            onIncrement = { onIncrement(product) },
                            onDecrement = { onDecrement(product) },
                            onDelete = { onDelete(product.id) }
                        )
                    }

                    // Model Spreadsheet foot totals
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL MODELO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "Variantes: ${productsList.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Stock: ${productsList.sumOf { it.quantity }}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExcelSpreadsheetHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index cell
        Box(
            modifier = Modifier
                .weight(0.6f)
                .height(30.dp)
                .cellBorder(color = MaterialTheme.colorScheme.outlineVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // UPC header cell
        Box(
            modifier = Modifier
                .weight(2.8f)
                .height(30.dp)
                .cellBorder(color = MaterialTheme.colorScheme.outlineVariant)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "UPC",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // size header cell
        Box(
            modifier = Modifier
                .weight(1.4f)
                .height(30.dp)
                .cellBorder(color = MaterialTheme.colorScheme.outlineVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "TALLA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // stock header cell
        Box(
            modifier = Modifier
                .weight(1.4f)
                .height(30.dp)
                .cellBorder(color = MaterialTheme.colorScheme.outlineVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "CANT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // action cell
        Box(
            modifier = Modifier
                .weight(2.4f)
                .height(30.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ACCIONES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ExcelSpreadsheetRow(
    product: ProductEntity,
    isZebra: Boolean,
    index: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(if (isZebra) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
            .border(width = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Row index
        Box(
            modifier = Modifier
                .weight(0.6f)
                .height(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .cellBorder(color = MaterialTheme.colorScheme.outlineVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // UPC with copy
        Box(
            modifier = Modifier
                .weight(2.8f)
                .height(40.dp)
                .cellBorder(color = MaterialTheme.colorScheme.outlineVariant)
                .clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("UPC-Code", product.upc)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copiado UPC: ${product.upc}", Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = product.upc,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Talla
        Box(
            modifier = Modifier
                .weight(1.4f)
                .height(40.dp)
                .cellBorder(color = MaterialTheme.colorScheme.outlineVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = product.size,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }

        // Cantidad highlighted cell
        val qtyColor = if (product.quantity > 5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        Box(
            modifier = Modifier
                .weight(1.4f)
                .height(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                .cellBorder(color = MaterialTheme.colorScheme.outlineVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "x ${String.format("%02d", product.quantity)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = qtyColor,
                textAlign = TextAlign.Center
            )
        }

        // Action columns
        Row(
            modifier = Modifier
                .weight(2.4f)
                .height(40.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decrement
            IconButton(
                onClick = onDecrement,
                modifier = Modifier.size(24.dp)
            ) {
                Text(
                    text = "-",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Increment
            IconButton(
                onClick = onIncrement,
                modifier = Modifier.size(24.dp)
            ) {
                Text(
                    text = "+",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Delete
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Borrar",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Extension to wrap Content alignment in custom boxes
fun Modifier.wrapContentSize(align: Alignment): Modifier = this

@Composable
fun ManualFormDialog(
    onDismiss: () -> Unit,
    onConfirm: (model: String, upc: String, size: String, color: String, qty: Int) -> Unit
) {
    var m by remember { mutableStateOf("") }
    var u by remember { mutableStateOf("") }
    var s by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var q by remember { mutableStateOf(1) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Agregar Producto Manual",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = m,
                    onValueChange = { m = it },
                    label = { Text("Modelo (Model)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors,
                    singleLine = true
                )

                OutlinedTextField(
                    value = u,
                    onValueChange = { u = it },
                    label = { Text("Código de Barras (UPC)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                OutlinedTextField(
                    value = s,
                    onValueChange = { s = it },
                    label = { Text("Talla") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors,
                    singleLine = true
                )

                // Quantity Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Cantidad Inicial:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (q > 1) q-- }) {
                            Text("-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = q.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { q++ }) {
                            Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (m.isNotEmpty() && u.isNotEmpty() && s.isNotEmpty()) {
                        onConfirm(m, u, s, c, q)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Registrar")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text("Cerrar")
            }
        }
    )
}

// Helper utility to resolve local Uri targets into Bitmap values
fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        Log.e("MainActivity", "Error decoding uri to bitmap", e)
        null
    }
}

// Shared Utility to Export CSV values both saving locally and matching standard Android Share Intent sheets
fun shareCsvAndSave(context: Context, csvContent: String) {
    try {
        val fileName = "inventario_calzado_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        val schemaOutput = FileOutputStream(file)
        schemaOutput.write(csvContent.toByteArray())
        schemaOutput.close()

        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_SUBJECT, "Inventario OCR CSV")
            putExtra(Intent.EXTRA_TEXT, "Exportación del inventario agrupado de calzado procesado mediante Inteligencia Artificial OCR.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
 
        context.startActivity(Intent.createChooser(intent, "Compartir Tabla de Inventario"))
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo exportar: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// Visual layout to trigger background alerts, sound tones, vibrations and push messages when the batch completes
fun sendCompletionNotification(context: Context) {
    try {
        // 1. Alert with standard notification sound
        val notificationSoundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        val r = android.media.RingtoneManager.getRingtone(context, notificationSoundUri)
        r.play()
    } catch (e: Exception) {
        e.printStackTrace()
    }
 
    try {
        // 2. Play 300ms vibration alert
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(300, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(300)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    try {
        // 3. Post system status bar notification
        val channelId = "label_scan_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "LabelScan Notifications",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones de completado para el escáner de etiquetas"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val notificationBuilder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Lote Completo - Smart OCR")
            .setContentText("Todas las etiquetas en cola han sido procesadas.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            
        notificationManager.notify(1001, notificationBuilder.build())
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// Interactive helper to populate the batch queue with mock data to make testing immediate and frictionless
fun injectMockBatchProducts(viewModel: MainViewModel, context: Context) {
    try {
        val itemsData = listOf(
            Triple("195237841104", "COURT-VISION-MID-BLACK", "10 M"),
            Triple("196155940026", "PEGASUS-TRAIL-WHITE", "8.5"),
            Triple("194498371192", "AIR-MAX-90-RED", "11 M")
        )

        val mockItems = mutableListOf<com.example.ui.BatchItem>()
        for (item in itemsData) {
            val (upc, model, size) = item
            val width = 300
            val height = 300
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint()
            paint.color = android.graphics.Color.parseColor("#1e293b")
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 18f
            canvas.drawText("LABEL SIMULATION", 30f, 60f, paint)
            paint.color = android.graphics.Color.parseColor("#38bdf8")
            canvas.drawText("UPC: $upc", 30f, 130f, paint)
            canvas.drawText("Style: $model", 30f, 180f, paint)
            canvas.drawText("Size: $size", 30f, 230f, paint)
            
            mockItems.add(
                com.example.ui.BatchItem(
                    bitmap = bitmap,
                    imageUri = null,
                    status = com.example.ui.BatchItemStatus.SUCCESS,
                    extractedUpc = upc,
                    extractedModel = model,
                    extractedSize = size
                )
            )
        }
        viewModel.injectMockBatchProducts(mockItems)
        Toast.makeText(context, "Se inyectaron 3 etiquetas simuladas para revisión inmediata", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        val width = 300
        val height = 300
        val fallbackBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        viewModel.addImageToBatch(fallbackBitmap, null)
        Toast.makeText(context, "Doble click para simular lote", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun BatchOperationsPanel(
    viewModel: MainViewModel,
    batchQueue: List<com.example.ui.BatchItem>,
    isBatchProcessing: Boolean,
    batchRpmLimit: Int,
    batchProgressText: String,
    onLaunchGallery: () -> Unit,
    onLaunchCamera: () -> Unit
) {
    val context = LocalContext.current
    val successColor = Color(0xFF059669) // emerald-600
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Configuración del Lote",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Live badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Colas Secuenciales",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = "Define la velocidad del planificador. Gemini 2.5 Flash tiene un límite gratuito de 5 RPM (peticiones por minuto) para evitar colisiones 429.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Limits sliders
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Límite: $batchRpmLimit RPM",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "Envío: 1 cada ${60 / batchRpmLimit} segundos",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Slider(
                value = batchRpmLimit.toFloat(),
                onValueChange = { viewModel.setBatchRpmLimit(it.toInt()) },
                valueRange = 1f..15f,
                steps = 13,
                enabled = !isBatchProcessing,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Current items in queue headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Queue",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Imágenes en Cola (${batchQueue.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                if (batchQueue.isNotEmpty() && !isBatchProcessing) {
                    Text(
                        text = "Limpiar Todo",
                        modifier = Modifier.clickable { viewModel.clearBatch() },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (batchQueue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable { onLaunchGallery() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Agrega fotos de etiquetas para procesar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Display thumbnails in a horizontal scrolling row
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(batchQueue) { item ->
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    2.dp,
                                    when (item.status) {
                                        com.example.ui.BatchItemStatus.PENDING -> MaterialTheme.colorScheme.outline
                                        com.example.ui.BatchItemStatus.PROCESSING -> MaterialTheme.colorScheme.primary
                                        com.example.ui.BatchItemStatus.SUCCESS -> successColor
                                        com.example.ui.BatchItemStatus.ERROR -> MaterialTheme.colorScheme.error
                                    },
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            Image(
                                bitmap = item.bitmap.asImageBitmap(),
                                contentDescription = "Etiqueta",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            // Delete button
                            if (!isBatchProcessing) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(bottomStart = 8.dp))
                                        .clickable { viewModel.removeImageFromBatch(item.id) }
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Descartar",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                            
                            // Overlay Badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(
                                        when (item.status) {
                                            com.example.ui.BatchItemStatus.PENDING -> MaterialTheme.colorScheme.outline.copy(alpha = 0.85f)
                                            com.example.ui.BatchItemStatus.PROCESSING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                            com.example.ui.BatchItemStatus.SUCCESS -> successColor.copy(alpha = 0.85f)
                                            com.example.ui.BatchItemStatus.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                        }
                                    )
                                    .padding(vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (item.status) {
                                        com.example.ui.BatchItemStatus.PENDING -> "COLA"
                                        com.example.ui.BatchItemStatus.PROCESSING -> "ANALIZANDO"
                                        com.example.ui.BatchItemStatus.SUCCESS -> "LISTO"
                                        com.example.ui.BatchItemStatus.ERROR -> "FALLO"
                                    },
                                    color = Color.White,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live progress state
            if (isBatchProcessing || batchProgressText.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isBatchProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "Notificación", tint = successColor, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = batchProgressText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Big action button to trigger the sequence pool
            Button(
                onClick = { viewModel.startBatchProcessing() },
                enabled = batchQueue.any { it.status != com.example.ui.BatchItemStatus.SUCCESS } && !isBatchProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isBatchProcessing) {
                    Text("Procesando lote en segundo plano...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    val count = batchQueue.count { it.status != com.example.ui.BatchItemStatus.SUCCESS }
                    Text("Procesar Lote ($count pendientes)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            
            if (batchQueue.any { it.status == com.example.ui.BatchItemStatus.SUCCESS || it.status == com.example.ui.BatchItemStatus.ERROR } && !isBatchProcessing) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.openBatchResults() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = "Review", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Revisar Resultados del Lote", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BatchResultsReviewDialog(
    viewModel: MainViewModel,
    batchQueue: List<com.example.ui.BatchItem>,
    onClose: () -> Unit
) {
    val successColor = Color(0xFF059669) // emerald-600
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Sheet controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Resultados del Lote",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Visualiza y edita los campos extraídos antes de sumarlos al inventario general.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Cerrar Panel")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Table spreadsheet
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(batchQueue) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.status == com.example.ui.BatchItemStatus.ERROR) 
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f) 
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when (item.status) {
                                    com.example.ui.BatchItemStatus.SUCCESS -> successColor.copy(alpha = 0.35f)
                                    com.example.ui.BatchItemStatus.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                }
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Picture thumbnail (rectangular aspect ratio perfect for shoe-box labels)
                                Image(
                                    bitmap = item.bitmap.asImageBitmap(),
                                    contentDescription = "Foto Etiqueta Lote",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(90.dp)
                                        .height(122.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .align(Alignment.CenterVertically)
                                )

                                val textFieldColors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Form text inputs
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (item.status == com.example.ui.BatchItemStatus.SUCCESS) {
                                        // Barcode
                                        OutlinedTextField(
                                            value = item.extractedUpc,
                                            onValueChange = {
                                                viewModel.updateBatchItemDetails(item.id, item.extractedModel, it, item.extractedSize)
                                            },
                                            label = { Text("Código UPC (Barras)", style = MaterialTheme.typography.labelSmall) },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(58.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = textFieldColors,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            // Model Style
                                            OutlinedTextField(
                                                value = item.extractedModel,
                                                onValueChange = {
                                                    viewModel.updateBatchItemDetails(item.id, it, item.extractedUpc, item.extractedSize)
                                                },
                                                label = { Text("Modelo", style = MaterialTheme.typography.labelSmall) },
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier
                                                    .weight(1.4f)
                                                    .height(56.dp),
                                                colors = textFieldColors,
                                                shape = RoundedCornerShape(12.dp)
                                            )

                                            // Size
                                            OutlinedTextField(
                                                value = item.extractedSize,
                                                onValueChange = {
                                                    viewModel.updateBatchItemDetails(item.id, item.extractedModel, item.extractedUpc, it)
                                                },
                                                label = { Text("Talla", style = MaterialTheme.typography.labelSmall) },
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(56.dp),
                                                colors = textFieldColors,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                    } else if (item.status == com.example.ui.BatchItemStatus.PROCESSING) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(120.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Gemini analizando...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    } else {
                                        // Error text descriptions
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Etiqueta no procesada",
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = item.errorMessage ?: "Conexión rechazada. Intenta de nuevo.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }

                                // Interactive lateral controls (Reprocess / Delete)
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                ) {
                                    IconButton(
                                        onClick = { viewModel.reprocessBatchItem(item.id) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Volver a procesar",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.removeImageFromBatch(item.id) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar de lote",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom toolbar summary & confirms
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text("Regresar")
                    }

                    val validCount = batchQueue.count { it.status == com.example.ui.BatchItemStatus.SUCCESS }
                    Button(
                        onClick = { viewModel.confirmAndSaveBatch() },
                        enabled = validCount > 0,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Guardar", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Confirmar y Guardar ($validCount)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

