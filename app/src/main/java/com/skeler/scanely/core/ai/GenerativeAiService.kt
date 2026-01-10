package com.skeler.scanely.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.data.datastore.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI processing mode for image analysis.
 */
enum class AiMode {
    EXTRACT_TEXT,
    DESCRIBE_IMAGE
}

/**
 * Sealed class representing AI operation results.
 */
sealed class AiResult {
    data class Success(val text: String) : AiResult()
    data class RateLimited(val remainingMs: Long) : AiResult()
    data class Error(val message: String) : AiResult()
}

/**
 * Service handling Google Generative AI (Gemini) operations.
 *
 * Features:
 * - Rate limiting (1 request per minute) via DataStore
 * - Image-to-text extraction
 * - Image description generation
 * - Error handling with sealed class results
 */
@Singleton
class GenerativeAiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val generativeModel: GenerativeModel
) {
    companion object {
        private const val RATE_LIMIT_MS = 60_000L
        private const val PROMPT_EXTRACT = "Extract all visible text from this image. Return only the extracted text, nothing else."
        private const val PROMPT_DESCRIBE = "Describe what is happening in this image in detail."
    }

    /**
     * Process an image with the specified AI mode.
     * 
     * Note: Rate limiting is handled at UI level (ScanViewModel) - 2 requests allowed per minute.
     *
     * @param imageUri URI of the image to process
     * @param mode AiMode.EXTRACT_TEXT or AiMode.DESCRIBE_IMAGE
     * @return AiResult indicating success or error
     */
    suspend fun processImage(imageUri: Uri, mode: AiMode): AiResult = withContext(Dispatchers.IO) {
        try {
            // Load bitmap from URI
            val bitmap = loadBitmapFromUri(imageUri)
                ?: return@withContext AiResult.Error("Failed to load image")

            // Select prompt based on mode
            val prompt = when (mode) {
                AiMode.EXTRACT_TEXT -> PROMPT_EXTRACT
                AiMode.DESCRIBE_IMAGE -> PROMPT_DESCRIBE
            }

            // Generate content with Gemini
            val response = generativeModel.generateContent(
                content {
                    image(bitmap)
                    text(prompt)
                }
            )

            val resultText = response.text ?: "No response generated"
            AiResult.Success(resultText)

        } catch (e: Exception) {
            AiResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    /**
     * Extract text from an image.
     */
    suspend fun extractText(imageUri: Uri): AiResult = processImage(imageUri, AiMode.EXTRACT_TEXT)

    /**
     * Generate a description of an image.
     */
    suspend fun describeImage(imageUri: Uri): AiResult = processImage(imageUri, AiMode.DESCRIBE_IMAGE)

    /**
     * Translate text to a target language.
     * 
     * Note: Rate limiting is handled at UI level (ScanViewModel) - 2 requests allowed per minute.
     *
     * @param text The text to translate
     * @param targetLanguage The target language (e.g., "Spanish", "French", "German")
     * @return AiResult with translated text
     */
    suspend fun translateText(text: String, targetLanguage: String): AiResult = withContext(Dispatchers.IO) {
        try {
            val prompt = "Translate the following text to $targetLanguage. Return only the translated text, nothing else:\n\n$text"

            val response = generativeModel.generateContent(prompt)

            val resultText = response.text ?: "Translation failed"
            AiResult.Success(resultText)

        } catch (e: Exception) {
            AiResult.Error(e.message ?: "Translation error occurred")
        }
    }

    /**
     * Load a Bitmap from a content URI.
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }
}
