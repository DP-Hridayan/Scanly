package com.skeler.scanely.core.ocr

/**
 * Sealed class representing OCR recognition results.
 */
sealed class OcrResult {
    /**
     * Successful text recognition.
     * @param text Full extracted text
     * @param blocks List of text blocks with position data
     * @param confidence Average recognition confidence (0.0-1.0)
     */
    data class Success(
        val text: String,
        val blocks: List<TextBlockData> = emptyList(),
        val confidence: Float = 1.0f
    ) : OcrResult()

    /**
     * Recognition failed.
     */
    data class Error(val message: String) : OcrResult()

    /**
     * No text detected in image.
     */
    data object Empty : OcrResult()
}

/**
 * Simplified text block data for UI rendering.
 */
data class TextBlockData(
    val text: String,
    val boundingBoxLeft: Int,
    val boundingBoxTop: Int,
    val boundingBoxRight: Int,
    val boundingBoxBottom: Int
)
