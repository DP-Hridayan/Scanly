package com.skeler.scanely.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.core.ocr.MlKitOcrService
import com.skeler.scanely.core.ocr.OcrResult
import com.skeler.scanely.history.data.HistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for OCR operations.
 */
data class OcrUiState(
    val isProcessing: Boolean = false,
    val result: OcrResult? = null,
    val isPdf: Boolean = false,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val sourceUri: Uri? = null
)

/**
 * ViewModel for ML Kit On-Device OCR operations.
 * Saves successful results to history.
 */
@HiltViewModel
class OcrViewModel @Inject constructor(
    private val ocrService: MlKitOcrService,
    private val historyManager: HistoryManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    /**
     * Process an image for text extraction.
     */
    fun processImage(imageUri: Uri) {
        viewModelScope.launch {
            _uiState.value = OcrUiState(isProcessing = true, isPdf = false, sourceUri = imageUri)

            val result = ocrService.recognizeFromUri(imageUri)

            _uiState.value = OcrUiState(
                isProcessing = false,
                result = result,
                isPdf = false,
                sourceUri = imageUri
            )

            // Save to history on success
            if (result is OcrResult.Success && result.text.isNotBlank()) {
                saveToHistory(result.text, imageUri)
            }
        }
    }

    /**
     * Process a PDF document for text extraction.
     */
    fun processPdf(pdfUri: Uri, pageIndex: Int? = null) {
        viewModelScope.launch {
            _uiState.value = OcrUiState(isProcessing = true, isPdf = true, sourceUri = pdfUri)

            val result = ocrService.recognizeFromPdf(pdfUri, pageIndex)

            _uiState.value = OcrUiState(
                isProcessing = false,
                result = result,
                isPdf = true,
                sourceUri = pdfUri
            )

            // Save to history on success
            if (result is OcrResult.Success && result.text.isNotBlank()) {
                saveToHistory(result.text, pdfUri)
            }
        }
    }

    /**
     * Save extraction result to history.
     */
    private fun saveToHistory(text: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                historyManager.saveItem(text, uri.toString())
            } catch (e: Exception) {
                // Silent fail - don't interrupt user flow
            }
        }
    }

    /**
     * Clear the current OCR result.
     */
    fun clearResult() {
        _uiState.value = OcrUiState()
    }
}
