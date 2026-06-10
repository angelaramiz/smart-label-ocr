package com.example.data.network

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

object GitHubUpdateService {
    private const val TAG = "GitHubUpdateService"
    private const val RELEASE_URL = "https://api.github.com/repos/angelaramiz/smart-label-ocr/releases/latest"
    private val client = OkHttpClient()

    data class ReleaseInfo(val tagName: String, val downloadUrl: String)

    suspend fun checkForUpdates(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASE_URL)
                .header("User-Agent", "LabelScanAI-App") // GitHub API requires a User-Agent header
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "GitHub API request failed with code ${response.code}")
                    return@withContext null
                }
                val responseString = response.body?.string() ?: return@withContext null
                val json = JSONObject(responseString)
                val tagName = json.optString("tag_name", "").trim()
                val assets = json.optJSONArray("assets")
                if (tagName.isNotEmpty() && assets != null && assets.length() > 0) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            val downloadUrl = asset.optString("browser_download_url", "")
                            return@withContext ReleaseInfo(tagName, downloadUrl)
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates from GitHub", e)
            null
        }
    }
}

fun downloadAndInstallApk(context: Context, downloadUrl: String, fileName: String = "LabelScanAI-Update.apk") {
    // Android 8.0+ request install packages checks before downloading
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            Toast.makeText(context, "Por favor autorice la instalación de apps de esta fuente y vuelva a intentar", Toast.LENGTH_LONG).show()
            return
        }
    }

    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val uri = Uri.parse(downloadUrl)
    val request = DownloadManager.Request(uri).apply {
        setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        setTitle("Descargando actualización")
        setDescription("Obteniendo nueva versión de LabelScan AI desde GitHub...")
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
    }

    val onComplete = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            try {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                val query = DownloadManager.Query().setFilterById(id)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIdx != -1 && cursor.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL) {
                        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                        if (file.exists()) {
                            installApk(ctx, Uri.fromFile(file))
                        } else {
                            val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            if (localUriIdx != -1) {
                                val fileUriString = cursor.getString(localUriIdx)
                                val fileUri = Uri.parse(fileUriString)
                                installApk(ctx, fileUri)
                            }
                        }
                    }
                }
                cursor.close()
            } catch (e: Exception) {
                Log.e("GitHubUpdateService", "Error resolving downloaded file", e)
            } finally {
                ctx.unregisterReceiver(this)
            }
        }
    }

    // Register receiver with appropriate flags for modern Android compatibility
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Context.RECEIVER_EXPORTED
    } else {
        0
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), flags)
    } else {
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    try {
        downloadManager.enqueue(request)
        Toast.makeText(context, "Iniciando descarga de actualización...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Log.e("GitHubUpdateService", "Failed to enqueue download request", e)
        Toast.makeText(context, "Error al iniciar descarga: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun installApk(context: Context, fileUri: Uri) {
    try {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        var finalUri = fileUri
        if (fileUri.scheme == "file") {
            val filePath = fileUri.path
            if (filePath != null) {
                val file = File(filePath)
                finalUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
        }

        installIntent.setDataAndType(finalUri, "application/vnd.android.package-archive")
        context.startActivity(installIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Error al iniciar instalación: ${e.message}", Toast.LENGTH_LONG).show()
        Log.e("GitHubUpdateService", "Failed to launch install intent", e)
    }
}

fun isNewerVersion(latest: String, current: String): Boolean {
    val cleanLatest = latest.replace("v", "").replace("V", "").trim()
    val cleanCurrent = current.replace("v", "").replace("V", "").trim()

    val latestParts = cleanLatest.split(".")
    val currentParts = cleanCurrent.split(".")

    val length = maxOf(latestParts.size, currentParts.size)
    for (i in 0 until length) {
        val latestVal = latestParts.getOrNull(i)?.toIntOrNull() ?: 0
        val currentVal = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
        if (latestVal > currentVal) return true
        if (latestVal < currentVal) return false
    }
    return false
}
