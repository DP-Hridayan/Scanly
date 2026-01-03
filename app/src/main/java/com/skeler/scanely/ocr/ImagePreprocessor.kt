package com.skeler.scanely.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val TAG = "ImagePreprocessor"

/**
 * Image quality levels for adaptive preprocessing.
 */
enum class ImageQuality {
    HIGH,      // Clean scanned document
    MEDIUM,    // Photo with good lighting
    LOW        // Photo with poor lighting/noise
}

/**
 * Enhanced image preprocessing pipeline for OCR optimization.
 * 
 * Pipeline stages:
 * 1. Resize - Optimal dimensions for OCR (800-2048px)
 * 2. Grayscale - Remove color information
 * 3. Quality Detection - Analyze contrast, noise, and brightness
 * 4. Contrast Enhancement - Linear or CLAHE-style based on quality
 * 5. Adaptive Threshold - Local or global based on quality
 * 6. Morphological Operations - Text repair for LOW quality
 * 7. Denoise - Remove small artifacts
 * 
 * Each stage is applied based on detected image quality.
 */
object ImagePreprocessor {
    
    // Maximum dimension for OCR processing (balances quality vs performance)
    private const val MAX_DIMENSION = 2048
    private const val MIN_DIMENSION = 800
    
    // Thresholds for quality detection
    private const val HIGH_CONTRAST_THRESHOLD = 0.7f
    private const val MEDIUM_CONTRAST_THRESHOLD = 0.4f
    private const val LOW_NOISE_THRESHOLD = 0.1f
    private const val HIGH_NOISE_THRESHOLD = 0.25f
    
    // CLAHE parameters
    private const val CLAHE_CLIP_LIMIT = 2.0f
    private const val CLAHE_TILE_SIZE = 8
    
    // Sauvola threshold parameters
    private const val SAUVOLA_WINDOW_SIZE = 15
    private const val SAUVOLA_K = 0.2f
    private const val SAUVOLA_R = 128f
    
    /**
     * Full preprocessing pipeline for OCR.
     * 
     * @param context Application context
     * @param imageUri Source image URI
     * @return Preprocessed bitmap ready for OCR, or null on failure
     */
    suspend fun preprocess(context: Context, imageUri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (original == null) {
                Log.e(TAG, "Failed to decode image from URI: $imageUri")
                return null
            }
            
            preprocessBitmap(original, recycleInput = true)
        } catch (e: Exception) {
            Log.e(TAG, "Preprocessing failed", e)
            null
        }
    }
    
    /**
     * Preprocess from existing bitmap.
     * 
     * @param bitmap Input bitmap
     * @param recycleInput Whether to recycle the input bitmap after processing
     * @return Preprocessed bitmap
     */
    fun preprocess(bitmap: Bitmap, recycleInput: Boolean = false): Bitmap {
        return preprocessBitmap(bitmap, recycleInput)
    }
    
    /**
     * Core preprocessing pipeline with quality-aware processing.
     */
    private fun preprocessBitmap(bitmap: Bitmap, recycleInput: Boolean): Bitmap {
        val startTime = System.currentTimeMillis()
        
        // Step 1: Resize to optimal dimensions
        val resized = resizeForOcr(bitmap)
        if (recycleInput && resized !== bitmap) bitmap.recycle()
        
        // Step 2: Convert to grayscale
        val grayscale = toGrayscale(resized)
        if (grayscale !== resized) resized.recycle()
        
        // Step 3: Detect quality to determine pipeline intensity
        val quality = detectQuality(grayscale)
        Log.d(TAG, "Detected image quality: $quality")
        
        // Step 4: Enhanced contrast based on quality
        val contrasted = when (quality) {
            ImageQuality.HIGH -> enhanceContrastLinear(grayscale, 1.1f)
            ImageQuality.MEDIUM -> enhanceContrastLinear(grayscale, 1.3f)
            ImageQuality.LOW -> applyLocalHistogramEqualization(grayscale)
        }
        if (contrasted !== grayscale) grayscale.recycle()
        
        // Step 5: Adaptive thresholding based on quality
        val thresholded = when (quality) {
            ImageQuality.HIGH -> contrasted // Skip for high quality
            ImageQuality.MEDIUM -> applyOtsuThreshold(contrasted)
            ImageQuality.LOW -> applyLocalAdaptiveThreshold(contrasted)
        }
        if (thresholded !== contrasted) contrasted.recycle()
        
        // Step 6: Morphological operations for LOW quality only
        val morphed = if (quality == ImageQuality.LOW) {
            applyMorphologicalOperations(thresholded)
        } else {
            thresholded
        }
        if (morphed !== thresholded) thresholded.recycle()
        
        // Step 7: Denoise for LOW and MEDIUM quality
        val denoised = if (quality != ImageQuality.HIGH) {
            denoise(morphed)
        } else {
            morphed
        }
        if (denoised !== morphed) morphed.recycle()
        
        val processingTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "Preprocessing completed in ${processingTime}ms (quality: $quality)")
        
        return denoised
    }
    
    /**
     * Enhanced quality detection based on contrast, noise, edge density, and brightness.
     */
    fun detectQuality(bitmap: Bitmap): ImageQuality {
        val sampleSize = min(100, min(bitmap.width, bitmap.height) / 2)
        val startX = (bitmap.width - sampleSize) / 2
        val startY = (bitmap.height - sampleSize) / 2
        
        if (startX < 0 || startY < 0 || sampleSize < 10) {
            return ImageQuality.MEDIUM
        }
        
        var minBrightness = 255
        var maxBrightness = 0
        var brightnessSum = 0L
        var brightnessSqSum = 0L
        var brightnessDiffs = 0
        var lastBrightness = -1
        var edgeCount = 0
        
        val pixelCount = sampleSize * sampleSize
        
        for (y in startY until startY + sampleSize) {
            for (x in startX until startX + sampleSize) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = Color.red(pixel)
                
                minBrightness = minOf(minBrightness, brightness)
                maxBrightness = maxOf(maxBrightness, brightness)
                brightnessSum += brightness
                brightnessSqSum += brightness.toLong() * brightness
                
                if (lastBrightness >= 0) {
                    val diff = abs(brightness - lastBrightness)
                    brightnessDiffs += diff
                    // Count significant edges
                    if (diff > 30) edgeCount++
                }
                lastBrightness = brightness
            }
        }
        
        // Calculate metrics
        val contrastRatio = (maxBrightness - minBrightness) / 255f
        val noiseLevel = brightnessDiffs / pixelCount.toFloat() / 255f
        val meanBrightness = brightnessSum / pixelCount.toFloat()
        val variance = (brightnessSqSum / pixelCount.toFloat()) - (meanBrightness * meanBrightness)
        val stdDev = sqrt(max(0f, variance))
        val edgeDensity = edgeCount / pixelCount.toFloat()
        
        Log.d(TAG, "Quality metrics - contrast: $contrastRatio, noise: $noiseLevel, " +
                "brightness: $meanBrightness, stdDev: $stdDev, edgeDensity: $edgeDensity")
        
        return when {
            // High quality: good contrast, low noise, balanced brightness
            contrastRatio >= HIGH_CONTRAST_THRESHOLD && 
            noiseLevel <= LOW_NOISE_THRESHOLD &&
            meanBrightness in 50f..220f -> ImageQuality.HIGH
            
            // Medium quality: acceptable contrast
            contrastRatio >= MEDIUM_CONTRAST_THRESHOLD &&
            noiseLevel <= HIGH_NOISE_THRESHOLD -> ImageQuality.MEDIUM
            
            // Low quality: poor contrast or high noise
            else -> ImageQuality.LOW
        }
    }
    
    /**
     * Resize image to optimal dimensions for OCR.
     */
    private fun resizeForOcr(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION &&
            width >= MIN_DIMENSION && height >= MIN_DIMENSION) {
            return bitmap
        }
        
        val scaleFactor = when {
            max(width, height) > MAX_DIMENSION -> {
                MAX_DIMENSION.toFloat() / max(width, height)
            }
            min(width, height) < MIN_DIMENSION -> {
                MIN_DIMENSION.toFloat() / min(width, height)
            }
            else -> 1f
        }
        
        val newWidth = (width * scaleFactor).toInt()
        val newHeight = (height * scaleFactor).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Convert image to grayscale.
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val grayscale = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )
        
        val canvas = Canvas(grayscale)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f)
            })
        }
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayscale
    }
    
    /**
     * Linear contrast enhancement.
     */
    private fun enhanceContrastLinear(bitmap: Bitmap, contrast: Float): Bitmap {
        val enhanced = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )
        
        val canvas = Canvas(enhanced)
        val translate = (-.5f * contrast + .5f) * 255f
        
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return enhanced
    }
    
    /**
     * CLAHE-style local histogram equalization.
     * Improves contrast locally, handling uneven lighting.
     */
    private fun applyLocalHistogramEqualization(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val tileWidth = max(1, width / CLAHE_TILE_SIZE)
        val tileHeight = max(1, height / CLAHE_TILE_SIZE)
        
        // Process each tile
        for (ty in 0 until CLAHE_TILE_SIZE) {
            for (tx in 0 until CLAHE_TILE_SIZE) {
                val x1 = tx * tileWidth
                val y1 = ty * tileHeight
                val x2 = min(x1 + tileWidth, width)
                val y2 = min(y1 + tileHeight, height)
                
                if (x2 <= x1 || y2 <= y1) continue
                
                // Build histogram for this tile
                val histogram = IntArray(256)
                for (y in y1 until y2) {
                    for (x in x1 until x2) {
                        val pixel = bitmap.getPixel(x, y)
                        val gray = Color.red(pixel)
                        histogram[gray]++
                    }
                }
                
                val pixelCount = (x2 - x1) * (y2 - y1)
                val clipLimit = (CLAHE_CLIP_LIMIT * pixelCount / 256).toInt()
                
                // Clip histogram
                var excess = 0
                for (i in 0 until 256) {
                    if (histogram[i] > clipLimit) {
                        excess += histogram[i] - clipLimit
                        histogram[i] = clipLimit
                    }
                }
                
                // Redistribute excess
                val increment = excess / 256
                for (i in 0 until 256) {
                    histogram[i] += increment
                }
                
                // Build CDF
                val cdf = IntArray(256)
                cdf[0] = histogram[0]
                for (i in 1 until 256) {
                    cdf[i] = cdf[i - 1] + histogram[i]
                }
                
                val cdfMin = cdf.first { it > 0 }
                
                // Apply equalization
                for (y in y1 until y2) {
                    for (x in x1 until x2) {
                        val pixel = bitmap.getPixel(x, y)
                        val gray = Color.red(pixel)
                        val newGray = ((cdf[gray] - cdfMin) * 255f / (pixelCount - cdfMin)).toInt()
                            .coerceIn(0, 255)
                        result.setPixel(x, y, Color.rgb(newGray, newGray, newGray))
                    }
                }
            }
        }
        
        Log.d(TAG, "Applied CLAHE-style local histogram equalization")
        return result
    }
    
    /**
     * Otsu's global thresholding method.
     */
    private fun applyOtsuThreshold(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Build histogram
        val histogram = IntArray(256)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val gray = Color.red(pixel)
                histogram[gray]++
            }
        }
        
        val totalPixels = width * height
        var sum = 0f
        for (i in 0 until 256) {
            sum += i * histogram[i]
        }
        
        var sumB = 0f
        var wB = 0
        var maxVariance = 0f
        var threshold = 128
        
        for (t in 0 until 256) {
            wB += histogram[t]
            if (wB == 0) continue
            
            val wF = totalPixels - wB
            if (wF == 0) break
            
            sumB += t * histogram[t]
            
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            
            val variance = wB.toFloat() * wF.toFloat() * (mB - mF) * (mB - mF)
            
            if (variance > maxVariance) {
                maxVariance = variance
                threshold = t
            }
        }
        
        // Apply threshold
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val gray = Color.red(pixel)
                val newColor = if (gray > threshold) Color.WHITE else Color.BLACK
                result.setPixel(x, y, newColor)
            }
        }
        
        Log.d(TAG, "Applied Otsu threshold: $threshold")
        return result
    }
    
    /**
     * Sauvola-inspired local adaptive thresholding.
     * Better handles shadows and uneven illumination.
     */
    private fun applyLocalAdaptiveThreshold(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val halfWindow = SAUVOLA_WINDOW_SIZE / 2
        
        // Pre-compute integral image and integral of squared values for fast mean/stddev
        val integral = Array(height + 1) { LongArray(width + 1) }
        val integralSq = Array(height + 1) { LongArray(width + 1) }
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val gray = Color.red(pixel).toLong()
                
                integral[y + 1][x + 1] = gray + integral[y][x + 1] + integral[y + 1][x] - integral[y][x]
                integralSq[y + 1][x + 1] = gray * gray + integralSq[y][x + 1] + integralSq[y + 1][x] - integralSq[y][x]
            }
        }
        
        // Apply Sauvola threshold
        for (y in 0 until height) {
            for (x in 0 until width) {
                val x1 = max(0, x - halfWindow)
                val y1 = max(0, y - halfWindow)
                val x2 = min(width - 1, x + halfWindow)
                val y2 = min(height - 1, y + halfWindow)
                
                val area = (x2 - x1 + 1) * (y2 - y1 + 1)
                
                val sum = integral[y2 + 1][x2 + 1] - integral[y1][x2 + 1] - integral[y2 + 1][x1] + integral[y1][x1]
                val sumSq = integralSq[y2 + 1][x2 + 1] - integralSq[y1][x2 + 1] - integralSq[y2 + 1][x1] + integralSq[y1][x1]
                
                val mean = sum.toFloat() / area
                val variance = (sumSq.toFloat() / area) - (mean * mean)
                val stdDev = sqrt(max(0f, variance))
                
                // Sauvola formula: T = mean * (1 + k * (stdDev / R - 1))
                val threshold = mean * (1 + SAUVOLA_K * (stdDev / SAUVOLA_R - 1))
                
                val pixel = bitmap.getPixel(x, y)
                val gray = Color.red(pixel)
                val newColor = if (gray > threshold) Color.WHITE else Color.BLACK
                result.setPixel(x, y, newColor)
            }
        }
        
        Log.d(TAG, "Applied Sauvola local adaptive threshold")
        return result
    }
    
    /**
     * Morphological operations to repair broken characters.
     * Applies closing (dilation then erosion) to connect broken strokes.
     */
    private fun applyMorphologicalOperations(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // Dilation
        val dilated = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var isDark = false
                
                // 3x3 structuring element
                outer@ for (dy in -1..1) {
                    for (dx in -1..1) {
                        val pixel = bitmap.getPixel(x + dx, y + dy)
                        if (Color.red(pixel) < 128) {
                            isDark = true
                            break@outer
                        }
                    }
                }
                
                dilated.setPixel(x, y, if (isDark) Color.BLACK else Color.WHITE)
            }
        }
        
        // Copy edges
        for (x in 0 until width) {
            dilated.setPixel(x, 0, bitmap.getPixel(x, 0))
            dilated.setPixel(x, height - 1, bitmap.getPixel(x, height - 1))
        }
        for (y in 0 until height) {
            dilated.setPixel(0, y, bitmap.getPixel(0, y))
            dilated.setPixel(width - 1, y, bitmap.getPixel(width - 1, y))
        }
        
        // Erosion
        val eroded = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var allDark = true
                
                // 3x3 structuring element
                outer@ for (dy in -1..1) {
                    for (dx in -1..1) {
                        val pixel = dilated.getPixel(x + dx, y + dy)
                        if (Color.red(pixel) >= 128) {
                            allDark = false
                            break@outer
                        }
                    }
                }
                
                eroded.setPixel(x, y, if (allDark) Color.BLACK else Color.WHITE)
            }
        }
        
        // Copy edges
        for (x in 0 until width) {
            eroded.setPixel(x, 0, dilated.getPixel(x, 0))
            eroded.setPixel(x, height - 1, dilated.getPixel(x, height - 1))
        }
        for (y in 0 until height) {
            eroded.setPixel(0, y, dilated.getPixel(0, y))
            eroded.setPixel(width - 1, y, dilated.getPixel(width - 1, y))
        }
        
        dilated.recycle()
        Log.d(TAG, "Applied morphological operations (closing)")
        return eroded
    }
    
    /**
     * Median filter denoising to remove small artifacts.
     */
    private fun denoise(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val neighbors = mutableListOf<Int>()
                
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val pixel = bitmap.getPixel(x + dx, y + dy)
                        neighbors.add(Color.red(pixel))
                    }
                }
                
                neighbors.sort()
                val median = neighbors[4]
                result.setPixel(x, y, Color.rgb(median, median, median))
            }
        }
        
        // Copy edges
        for (x in 0 until width) {
            result.setPixel(x, 0, bitmap.getPixel(x, 0))
            result.setPixel(x, height - 1, bitmap.getPixel(x, height - 1))
        }
        for (y in 0 until height) {
            result.setPixel(0, y, bitmap.getPixel(0, y))
            result.setPixel(width - 1, y, bitmap.getPixel(width - 1, y))
        }
        
        return result
    }
}

