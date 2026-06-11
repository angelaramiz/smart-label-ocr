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
        val maxDimension = 1024
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

    suspend fun analyzeLabelImage(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val groqKey = BuildConfig.GROQ_API_KEY
        if (groqKey.isNotEmpty() && groqKey != "MY_GROQ_API_KEY") {
            try {
                Log.d(TAG, "Routing to Groq API using Llama 4 Scout")
                val base64Image = bitmap.toBase64()
                return@withContext callOpenAiCompatibleApi(
                    url = "https://api.groq.com/openai/v1/chat/completions",
                    apiKey = groqKey,
                    model = "meta-llama/llama-4-scout-17b-16e-instruct",
                    base64Image = base64Image
                )
            } catch (e: Exception) {
                Log.e(TAG, "Groq API analysis failed, falling back to other providers", e)
            }
        }

        val hfKey = BuildConfig.HF_API_KEY
        if (hfKey.isNotEmpty() && hfKey != "MY_HF_API_KEY") {
            try {
                Log.d(TAG, "Routing to Hugging Face API using Qwen2-VL")
                val base64Image = bitmap.toBase64()
                return@withContext callOpenAiCompatibleApi(
                    url = "https://api-inference.huggingface.co/models/Qwen/Qwen2-VL-7B-Instruct/v1/chat/completions",
                    apiKey = hfKey,
                    model = "Qwen/Qwen2-VL-7B-Instruct",
                    base64Image = base64Image
                )
            } catch (e: Exception) {
                Log.e(TAG, "Hugging Face API analysis failed, falling back to other providers", e)
            }
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext OcrResult.Error("API Key is missing or invalid. Please configure GEMINI_API_KEY in the Secrets Panel.")
        }

        try {
            // Convert bitmap to base64
            val base64Image = bitmap.toBase64()

            // Construct JSON request body using standard org.json classes
            val requestJson = JSONObject()
            
            // Contents
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()

            // Prompt text part
            val textPart = JSONObject().apply {
                put("text", "Perform OCR on this clothing/footwear label image. Extract the barcode UPC (merge any isolated first/last numbers if they are part of the UPC string), the product / style model identifier combined with its color/style code suffix (e.g., if model is 'M6PG34K3200' and color code is 'FBDB', you must return 'M6PG34K3200-FBDB' as the 'model'), and the size ('Tallas' e.g. '6 M', '9', 'L'). Return ONLY a JSON object matching the required schema.")
            }
            partsArray.put(textPart)

            // Image part
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

            // System Instruction
            val systemInstructionObj = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "You are an expert product label OCR extractor. Your only task is reading clothing and footwear tags, shoe boxes labels, and extracting structured data. You MUST return a JSON object with properties 'upc', 'model', and 'size'. 'model' must combine the general product style code with its specific color or style code suffix using a hyphen (e.g. 'M6PG34K3200-FBDB', 'GWFASHIE-DARKRED'). OCR must be extremely precise, pay careful attention to characters (e.g. read 'M6PG' or 'M6GP' correctly). 'upc' contains ONLY numeric digits of the barcode. 'size' is the product/shoe size (e.g. '6 M', '9', 'M').")
                    })
                })
            }
            requestJson.put("systemInstruction", systemInstructionObj)

            // Generation Config
            val generationConfigJson = JSONObject().apply {
                put("temperature", 0.15) // Low temperature for high precision/OCR tasks
                put("responseMimeType", "application/json") // Response MimeType directly in generationConfig
                
                // Response Schema specification for structured JSON output directly under generationConfig
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
                put("responseSchema", responseSchemaObj) // responseSchema directly in generationConfig
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

                // Parse the response
                val rootJson = JSONObject(responseString)
                val candidates = rootJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext OcrResult.Error("No candidates returned from Gemini.")
                }

                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts == null || parts.length() == 0) {
                    return@withContext OcrResult.Error("Incomplete response content.")
                }

                val text = parts.getJSONObject(0).optString("text")
                if (text.isNullOrEmpty()) {
                    return@withContext OcrResult.Error("Could not extract text from label.")
                }

                Log.d(TAG, "Extracted parsed text: $text")

                // Parse the inner JSON returned from the schema Format
                val parsedObj = JSONObject(text.trim())
                val upc = parsedObj.optString("upc", "").trim()
                val model = parsedObj.optString("model", "").trim()
                val size = parsedObj.optString("size", "").trim()
                val color = parsedObj.optString("color", "").trim()

                if (upc.isEmpty() || model.isEmpty() || size.isEmpty()) {
                    return@withContext OcrResult.Error("Could not read essential info. Found UPC: $upc, Model: $model, Size: $size. Please ensure photo is well lit and cropped.")
                }

                OcrResult.Success(upc, model, size, color)
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
            val userMessage = JSONObject()
            userMessage.put("role", "user")

            val contentArray = JSONArray()

            val textContent = JSONObject()
            textContent.put("type", "text")
            textContent.put("text", "Perform OCR on this clothing/footwear label image. Extract the barcode UPC (numeric digits of the barcode only), the product/style model identifier combined with its color/style code suffix using a hyphen (e.g. 'M6PG34K3200-FBDB'), and the size (e.g. '6 M', '9', 'L'). Return ONLY a JSON object with properties 'upc', 'model', and 'size'.")
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
                val choices = rootJson.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return@withContext OcrResult.Error("No choices returned from API.")
                }

                val message = choices.getJSONObject(0).optJSONObject("message")
                val text = message?.optString("content", "")
                if (text.isNullOrEmpty()) {
                    return@withContext OcrResult.Error("Could not extract text from API response.")
                }

                Log.d(TAG, "Extracted parsed content: $text")

                val parsedObj = JSONObject(text.trim())
                val upc = parsedObj.optString("upc", "").trim()
                val model = parsedObj.optString("model", "").trim()
                val size = parsedObj.optString("size", "").trim()
                val color = parsedObj.optString("color", "").trim()

                if (upc.isEmpty() || model.isEmpty() || size.isEmpty()) {
                    return@withContext OcrResult.Error("Could not read essential info. Found UPC: $upc, Model: $model, Size: $size. Please ensure photo is well lit and cropped.")
                }

                OcrResult.Success(upc, model, size, color)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI-compatible API Exception", e)
            OcrResult.Error("API error: ${e.message}")
        }
    }

    fun getActiveProviderName(): String {
        val groqKey = BuildConfig.GROQ_API_KEY
        if (groqKey.isNotEmpty() && groqKey != "MY_GROQ_API_KEY") {
            return "Groq (Llama 4 Scout)"
        }
        val hfKey = BuildConfig.HF_API_KEY
        if (hfKey.isNotEmpty() && hfKey != "MY_HF_API_KEY") {
            return "Hugging Face (Qwen2-VL)"
        }
        return "Gemini 2.5 Flash"
    }
}

sealed class OcrResult {
    data class Success(val upc: String, val model: String, val size: String, val color: String) : OcrResult()
    data class Error(val message: String, val errorCode: Int = 0) : OcrResult()
}
