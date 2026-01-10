package com.skeler.scanely.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.core.ai.AiMode
import com.skeler.scanely.core.ai.AiResult
import com.skeler.scanely.core.ai.GenerativeAiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for AI scanning operations.
 */
data class AiScanState(
    val isProcessing: Boolean = false,
    val result: AiResult? = null,
    val mode: AiMode? = null,
    val originalText: String? = null,
    val translatedText: String? = null,
    val isTranslating: Boolean = false
)

/**
 * ViewModel for AI-powered image analysis.
 */
@HiltViewModel
class AiScanViewModel @Inject constructor(
    private val aiService: GenerativeAiService
) : ViewModel() {

    private val _aiState = MutableStateFlow(AiScanState())
    val aiState: StateFlow<AiScanState> = _aiState.asStateFlow()

    /**
     * Process an image with the specified AI mode.
     */
    fun processImage(imageUri: Uri, mode: AiMode) {
        viewModelScope.launch {
            _aiState.value = AiScanState(
                isProcessing = true,
                mode = mode
            )

            val result = aiService.processImage(imageUri, mode)

            val originalText = if (result is AiResult.Success) result.text else null

            _aiState.value = AiScanState(
                isProcessing = false,
                result = result,
                mode = mode,
                originalText = originalText
            )
        }
    }

    /**
     * Translate the current result text to a target language.
     */
    fun translateResult(targetLanguage: String) {
        val currentText = _aiState.value.originalText ?: return

        viewModelScope.launch {
            _aiState.value = _aiState.value.copy(isTranslating = true)

            val translationResult = aiService.translateText(currentText, targetLanguage)

            _aiState.value = _aiState.value.copy(
                isTranslating = false,
                translatedText = when (translationResult) {
                    is AiResult.Success -> translationResult.text
                    is AiResult.RateLimited -> "Rate limited: wait ${translationResult.remainingMs / 1000}s"
                    is AiResult.Error -> "Translation error: ${translationResult.message}"
                }
            )
        }
    }

    /**
     * Check rate limit before showing mode selection.
     * Returns AiResult.RateLimited if exceeded, null if allowed.
     */
    suspend fun checkRateLimit(): AiResult? {
        return try {
            val result = aiService.extractText(Uri.EMPTY)
            if (result is AiResult.RateLimited) result else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clear translation to show original text again.
     */
    fun clearTranslation() {
        _aiState.value = _aiState.value.copy(translatedText = null)
    }

    /**
     * Clear the current AI result.
     */
    fun clearResult() {
        _aiState.value = AiScanState()
    }
}
