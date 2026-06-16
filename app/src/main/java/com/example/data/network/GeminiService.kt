package com.example.data.network

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    // OkHttp Client with 60-second timeouts as mandated by the skill instructions
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        // Resize slightly if the image is huge to save tokens and speed up upload
        val maxDimension = 2048
        val scaledBitmap = if (width > maxDimension || height > maxDimension) {
            val aspectRatio = width.toFloat() / height.toFloat()
            val (newWidth, newHeight) = if (width > height) {
                maxDimension to (maxDimension / aspectRatio).toInt()
            } else {
                (maxDimension * aspectRatio).toInt() to maxDimension
            }
            Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
        } else {
            this
        }
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun analyzeLabelImage(
        bitmap: Bitmap,
        customGeminiKey: String? = null,
        customGroqKey: String? = null,
        customHfKey: String? = null,
        selectedProvider: String = "auto"
    ): OcrResult = withContext(Dispatchers.IO) {
        val finalGroqKey = if (!customGroqKey.isNullOrEmpty()) customGroqKey else BuildConfig.GROQ_API_KEY
        val finalHfKey = if (!customHfKey.isNullOrEmpty()) customHfKey else BuildConfig.HF_API_KEY
        val finalGeminiKey = if (!customGeminiKey.isNullOrEmpty()) customGeminiKey else BuildConfig.GEMINI_API_KEY

        val base64Image = bitmap.toBase64()

        when (selectedProvider.lowercase()) {
            "groq" -> {
                if (finalGroqKey.isEmpty() || finalGroqKey == "MY_GROQ_API_KEY") {
                    return@withContext OcrResult.Error("La clave de API de Groq no está configurada.")
                }
                try {
                    return@withContext runGroq(base64Image, finalGroqKey)
                } catch (e: Exception) {
                    return@withContext OcrResult.Error("Groq Error: ${e.message}")
                }
            }
            "hf" -> {
                if (finalHfKey.isEmpty() || finalHfKey == "MY_HF_API_KEY") {
                    return@withContext OcrResult.Error("La clave de API de Hugging Face no está configurada.")
                }
                try {
                    return@withContext runHuggingFace(base64Image, finalHfKey)
                } catch (e: Exception) {
                    return@withContext OcrResult.Error("Hugging Face Error: ${e.message}")
                }
            }
            "gemini" -> {
                if (finalGeminiKey.isEmpty() || finalGeminiKey == "MY_GEMINI_API_KEY") {
                    return@withContext OcrResult.Error("La clave de API de Gemini no está configurada.")
                }
                return@withContext runGemini(base64Image, finalGeminiKey)
            }
            else -> {
                // Auto mode: sequentially try Groq, HF, Gemini
                if (finalGroqKey.isNotEmpty() && finalGroqKey != "MY_GROQ_API_KEY") {
                    try {
                        return@withContext runGroq(base64Image, finalGroqKey)
                    } catch (e: Exception) {
                        Log.e(TAG, "Groq fallback failed, trying next", e)
                    }
                }
                if (finalHfKey.isNotEmpty() && finalHfKey != "MY_HF_API_KEY") {
                    try {
                        return@withContext runHuggingFace(base64Image, finalHfKey)
                    } catch (e: Exception) {
                        Log.e(TAG, "HF fallback failed, trying next", e)
                    }
                }
                if (finalGeminiKey.isNotEmpty() && finalGeminiKey != "MY_GEMINI_API_KEY") {
                    try {
                        return@withContext runGemini(base64Image, finalGeminiKey)
                    } catch (e: Exception) {
                        Log.e(TAG, "Gemini fallback failed", e)
                    }
                }
                return@withContext OcrResult.Error("No se encontraron claves de API válidas configuradas o todos los proveedores fallaron.")
            }
        }
    }

    private suspend fun runGroq(base64Image: String, apiKey: String): OcrResult {
        Log.d(TAG, "Routing to Groq API using Llama 4 Scout")
        return callOpenAiCompatibleApi(
            url = "https://api.groq.com/openai/v1/chat/completions",
            apiKey = apiKey,
            model = "meta-llama/llama-4-scout-17b-16e-instruct",
            base64Image = base64Image
        )
    }

    private suspend fun runHuggingFace(base64Image: String, apiKey: String): OcrResult {
        Log.d(TAG, "Routing to Hugging Face API using Qwen2-VL")
        return callOpenAiCompatibleApi(
            url = "https://api-inference.huggingface.co/models/Qwen/Qwen2-VL-7B-Instruct/v1/chat/completions",
            apiKey = apiKey,
            model = "Qwen/Qwen2-VL-7B-Instruct",
            base64Image = base64Image
        )
    }

    private suspend fun runGemini(base64Image: String, apiKey: String): OcrResult = withContext(Dispatchers.IO) {
        try {
            val requestJson = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()

            val textPart = JSONObject().apply {
                put("text", "Perform OCR on this clothing/footwear label image. Extract the barcode UPC (merge any isolated first/last numbers if they are part of the UPC string), the product / style model identifier combined with its color/style code suffix (e.g., if model is 'M6PG34K3200' and color code is 'FBDB', you must return 'M6PG34K3200-FBDB' as the 'model'), and the size ('Tallas' e.g. '6 M', '9', 'L'). Return ONLY a JSON object matching the required schema.")
            }
            partsArray.put(textPart)

            val inlineDataObj = JSONObject().apply {
                put("mimeType", "image/jpeg")
                put("data", base64Image)
            }
            val imagePart = JSONObject().apply {
                put("inlineData", inlineDataObj)
            }
            partsArray.put(imagePart)

            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            requestJson.put("contents", contentsArray)

            val systemInstructionObj = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "You are an expert product label OCR extractor. Your only task is reading clothing and footwear tags, shoe boxes labels, and extracting structured data. You MUST return a JSON object with properties 'upc', 'model', and 'size'. 'model' must combine the general product style code with its specific color or style code suffix using a hyphen (e.g. 'M6PG34K3200-FBDB', 'GWFASHIE-DARKRED'). OCR must be extremely precise, pay careful attention to characters (e.g. read 'M6PG' or 'M6GP' correctly). 'upc' contains ONLY numeric digits of the barcode. 'size' is the product/shoe size (e.g. '6 M', '9', 'M').")
                    })
                })
            }
            requestJson.put("systemInstruction", systemInstructionObj)

            val generationConfigJson = JSONObject().apply {
                put("temperature", 0.15)
                put("responseMimeType", "application/json")
                
                val responseSchemaObj = JSONObject().apply {
                    put("type", "OBJECT")
                    val propertiesObj = JSONObject().apply {
                        put("upc", JSONObject().apply { 
                            put("type", "STRING") 
                            put("description", "UPC digits code of the product barcode, usually 12 digits, including prefix/checksum digit if shown.")
                        })
                        put("model", JSONObject().apply { 
                            put("type", "STRING") 
                            put("description", "Product design or style code name, combined with style suffix, e.g. 'M6PG34K3200-FBDB'.")
                        })
                        put("size", JSONObject().apply { 
                            put("type", "STRING") 
                            put("description", "The physical shoe/clothes size, e.g. '6 M'.")
                        })
                    }
                    put("properties", propertiesObj)
                    put("required", JSONArray().apply {
                        put("upc")
                        put("model")
                        put("size")
                    })
                }
                put("responseSchema", responseSchemaObj)
            }
            requestJson.put("generationConfig", generationConfigJson)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val urlWithKey = "$BASE_URL?key=$apiKey"
            val request = Request.Builder()
                .url(urlWithKey)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseString = response.body?.string()
                Log.d(TAG, "Response: $responseString")

                if (!response.isSuccessful) {
                    return@withContext OcrResult.Error("API request failed with code ${response.code}: $responseString", response.code)
                }

                if (responseString == null) {
                    return@withContext OcrResult.Error("Empty response from Gemini API.")
                }

                val rootJson = JSONObject(responseString)
                val usageMetadata = rootJson.optJSONObject("usageMetadata")
                val tokensUsed = usageMetadata?.optInt("totalTokenCount", 0) ?: 0

                val candidates = rootJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext OcrResult.Error("No candidates returned from Gemini.", tokensUsed = tokensUsed)
                }

                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts == null || parts.length() == 0) {
                    return@withContext OcrResult.Error("Incomplete response content.", tokensUsed = tokensUsed)
                }

                val text = parts.getJSONObject(0).optString("text")
                if (text.isNullOrEmpty()) {
                    return@withContext OcrResult.Error("Could not extract text from label.", tokensUsed = tokensUsed)
                }

                Log.d(TAG, "Extracted parsed text: $text")

                val parsedObj = JSONObject(text.trim())
                val upc = parsedObj.optString("upc", "").trim()
                val model = parsedObj.optString("model", "").trim()
                val size = parsedObj.optString("size", "").trim()
                val color = parsedObj.optString("color", "").trim()

                if (upc.isEmpty() || model.isEmpty() || size.isEmpty()) {
                    return@withContext OcrResult.Error("Could not read essential info. Found UPC: $upc, Model: $model, Size: $size. Please ensure photo is well lit and cropped.", tokensUsed = tokensUsed)
                }

                OcrResult.Success(upc, model, size, color, tokensUsed = tokensUsed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during analysis", e)
            OcrResult.Error("Network error: ${e.message}")
        }
    }

    private suspend fun callOpenAiCompatibleApi(
        url: String,
        apiKey: String,
        model: String,
        base64Image: String
    ): OcrResult = withContext(Dispatchers.IO) {
        try {
            val requestJson = JSONObject()
            requestJson.put("model", model)

            val messagesArray = JSONArray()

            val systemMessage = JSONObject().apply {
                put("role", "system")
                put("content", "You are an expert product label OCR extractor. Your only task is reading clothing/footwear tags and shoe box labels, and extracting structured data. You MUST return a JSON object with properties: 'upc', 'model', and 'size'.\n" +
                               "Rules:\n" +
                               "1. 'upc': Must contain ONLY the numeric digits of the barcode. Barcodes on shoe boxes often have isolated digits printed slightly to the left and right of the main group (e.g., a leading '7' or trailing '3'). You MUST merge these isolated numbers to form the full UPC (usually 12 digits total). Do not include letters, spaces, or hyphens.\n" +
                               "2. 'model': Look for the model/style code. Combine the general style code with its specific color or style code suffix using a hyphen (e.g. 'M6PG34K3200-FBDB', 'GWFASHIE-DARKRED').\n" +
                               "3. 'size': Extract the physical shoe or clothing size (e.g. '6 M', '9', 'L', 'M').\n" +
                               "4. OCR must be extremely precise, pay careful attention to characters (e.g. do not mix up '8' and 'B', '0' and 'O', 'M6PG' or 'M6GP').")
            }
            messagesArray.put(systemMessage)

            val userMessage = JSONObject()
            userMessage.put("role", "user")

            val contentArray = JSONArray()

            val textContent = JSONObject()
            textContent.put("type", "text")
            textContent.put("text", "Perform OCR on this clothing/footwear label image. Scan the barcode numbers carefully. Merge any isolated numbers on the sides of the barcode into the 'upc'. Return ONLY a JSON object matching the required schema.")
            contentArray.put(textContent)

            val imageContent = JSONObject()
            imageContent.put("type", "image_url")
            val imageUrlObj = JSONObject()
            imageUrlObj.put("url", "data:image/jpeg;base64,$base64Image")
            imageContent.put("image_url", imageUrlObj)
            contentArray.put(imageContent)

            userMessage.put("content", contentArray)
            messagesArray.put(userMessage)
            requestJson.put("messages", messagesArray)

            val responseFormat = JSONObject()
            responseFormat.put("type", "json_object")
            requestJson.put("response_format", responseFormat)

            requestJson.put("temperature", 0.15)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseString = response.body?.string()
                Log.d(TAG, "OpenAI-compatible Response: $responseString")

                if (!response.isSuccessful) {
                    return@withContext OcrResult.Error("API request failed with code ${response.code}: $responseString", response.code)
                }

                if (responseString == null) {
                    return@withContext OcrResult.Error("Empty response from API.")
                }

                val rootJson = JSONObject(responseString)
                val usageObj = rootJson.optJSONObject("usage")
                val tokensUsed = usageObj?.optInt("total_tokens", 0) ?: 0

                val choices = rootJson.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return@withContext OcrResult.Error("No choices returned from API.", tokensUsed = tokensUsed)
                }

                val message = choices.getJSONObject(0).optJSONObject("message")
                val text = message?.optString("content", "")
                if (text.isNullOrEmpty()) {
                    return@withContext OcrResult.Error("Could not extract text from API response.", tokensUsed = tokensUsed)
                }

                Log.d(TAG, "Extracted parsed content: $text")

                val parsedObj = JSONObject(text.trim())
                val upc = parsedObj.optString("upc", "").trim()
                val model = parsedObj.optString("model", "").trim()
                val size = parsedObj.optString("size", "").trim()
                val color = parsedObj.optString("color", "").trim()

                if (upc.isEmpty() || model.isEmpty() || size.isEmpty()) {
                    return@withContext OcrResult.Error("Could not read essential info. Found UPC: $upc, Model: $model, Size: $size. Please ensure photo is well lit and cropped.", tokensUsed = tokensUsed)
                }

                OcrResult.Success(upc, model, size, color, tokensUsed = tokensUsed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI-compatible API Exception", e)
            OcrResult.Error("API error: ${e.message}")
        }
    }

    fun getActiveProviderName(
        customGeminiKey: String? = null,
        customGroqKey: String? = null,
        customHfKey: String? = null,
        selectedProvider: String = "auto"
    ): String {
        val finalGroqKey = if (!customGroqKey.isNullOrEmpty()) customGroqKey else BuildConfig.GROQ_API_KEY
        val finalHfKey = if (!customHfKey.isNullOrEmpty()) customHfKey else BuildConfig.HF_API_KEY
        val finalGeminiKey = if (!customGeminiKey.isNullOrEmpty()) customGeminiKey else BuildConfig.GEMINI_API_KEY

        return when (selectedProvider.lowercase()) {
            "groq" -> "Groq (Llama 4 Scout)"
            "hf" -> "Hugging Face (Qwen2-VL)"
            "gemini" -> "Gemini 2.5 Flash"
            else -> {
                if (finalGroqKey.isNotEmpty() && finalGroqKey != "MY_GROQ_API_KEY") {
                    "Auto: Groq (Llama 4 Scout)"
                } else if (finalHfKey.isNotEmpty() && finalHfKey != "MY_HF_API_KEY") {
                    "Auto: HF (Qwen2-VL)"
                } else if (finalGeminiKey.isNotEmpty() && finalGeminiKey != "MY_GEMINI_API_KEY") {
                    "Auto: Gemini 2.5 Flash"
                } else {
                    "Ninguno (Sin Configurar)"
                }
            }
        }
    }
}

sealed class OcrResult {
    data class Success(val upc: String, val model: String, val size: String, val color: String, val tokensUsed: Int = 0) : OcrResult()
    data class Error(val message: String, val errorCode: Int = 0, val tokensUsed: Int = 0) : OcrResult()
}
