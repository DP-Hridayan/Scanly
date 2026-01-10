package com.skeler.scanely.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PdfRendererHelper"

/**
 * Helper class for extracting Bitmap pages from PDF documents.
 *
 * Uses Android's native PdfRenderer for efficient PDF parsing.
 * Scales pages to 2x resolution for optimal ML Kit OCR accuracy.
 */
@Singleton
class PdfRendererHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Scale factor for PDF rendering.
     * 2.0x provides optimal resolution for ML Kit text recognition.
     * Higher values increase accuracy but use more memory.
     */
    private val scaleFactor = 2.0f

    /**
     * Render a specific page from a PDF to a Bitmap.
     *
     * @param pdfUri URI of the PDF document
     * @param pageIndex 0-based page index
     * @return Bitmap of the rendered page, or null if failed
     */
    suspend fun renderPage(pdfUri: Uri, pageIndex: Int = 0): Bitmap? = withContext(Dispatchers.Default) {
        var parcelFileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null

        try {
            parcelFileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: return@withContext null

            pdfRenderer = PdfRenderer(parcelFileDescriptor)

            if (pageIndex < 0 || pageIndex >= pdfRenderer.pageCount) {
                Log.e(TAG, "Page index $pageIndex out of bounds (0-${pdfRenderer.pageCount - 1})")
                return@withContext null
            }

            val page = pdfRenderer.openPage(pageIndex)

            // Scale dimensions for higher resolution
            val scaledWidth = (page.width * scaleFactor).toInt()
            val scaledHeight = (page.height * scaleFactor).toInt()

            val bitmap = Bitmap.createBitmap(
                scaledWidth,
                scaledHeight,
                Bitmap.Config.ARGB_8888
            )
            
            // Fill with white background (PDF pages may be transparent)
            bitmap.eraseColor(android.graphics.Color.WHITE)

            // Render page to bitmap
            page.render(
                bitmap,
                null, // Use full page bounds
                null, // No transform matrix (scaling handled by bitmap size)
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )

            page.close()
            bitmap

        } catch (e: Exception) {
            Log.e(TAG, "Failed to render PDF page", e)
            null
        } finally {
            pdfRenderer?.close()
            parcelFileDescriptor?.close()
        }
    }

    /**
     * Render all pages from a PDF to Bitmaps.
     * 
     * Note: PDF pages are rendered with white background for optimal ML Kit OCR.
     *
     * @param pdfUri URI of the PDF document
     * @return List of Bitmaps for each page
     */
    suspend fun renderAllPages(pdfUri: Uri): List<Bitmap> = withContext(Dispatchers.Default) {
        val bitmaps = mutableListOf<Bitmap>()
        var parcelFileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null

        try {
            parcelFileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
            if (parcelFileDescriptor == null) {
                Log.e(TAG, "Failed to open file descriptor for PDF: $pdfUri")
                return@withContext emptyList()
            }

            pdfRenderer = PdfRenderer(parcelFileDescriptor)
            Log.d(TAG, "PDF opened: ${pdfRenderer.pageCount} pages")

            for (i in 0 until pdfRenderer.pageCount) {
                try {
                    val page = pdfRenderer.openPage(i)

                    val scaledWidth = (page.width * scaleFactor).toInt()
                    val scaledHeight = (page.height * scaleFactor).toInt()

                    val bitmap = Bitmap.createBitmap(
                        scaledWidth,
                        scaledHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    
                    // Fill with white background (PDF pages may be transparent)
                    bitmap.eraseColor(android.graphics.Color.WHITE)

                    page.render(
                        bitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )

                    page.close()
                    bitmaps.add(bitmap)
                    Log.d(TAG, "Rendered page ${i + 1}/${pdfRenderer.pageCount}")
                } catch (pageError: Exception) {
                    Log.e(TAG, "Failed to render page $i", pageError)
                }
            }

            Log.d(TAG, "Total bitmaps rendered: ${bitmaps.size}")
            bitmaps

        } catch (e: Exception) {
            Log.e(TAG, "Failed to render PDF pages", e)
            // Clean up any bitmaps already created
            bitmaps.forEach { it.recycle() }
            emptyList()
        } finally {
            pdfRenderer?.close()
            parcelFileDescriptor?.close()
        }
    }

    /**
     * Get the page count of a PDF document.
     */
    suspend fun getPageCount(pdfUri: Uri): Int = withContext(Dispatchers.Default) {
        var parcelFileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null

        try {
            parcelFileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: return@withContext 0

            pdfRenderer = PdfRenderer(parcelFileDescriptor)
            pdfRenderer.pageCount

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get PDF page count", e)
            0
        } finally {
            pdfRenderer?.close()
            parcelFileDescriptor?.close()
        }
    }
}
