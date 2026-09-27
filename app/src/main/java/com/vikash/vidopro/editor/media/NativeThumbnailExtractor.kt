package com.vikash.vidopro.editor.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.collection.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-performance thumbnail extractor utilizing FFmpeg JNI with MediaMetadataRetriever fallback.
 * Strictly operates on Dispatchers.IO and employs an in-memory LruCache to prevent redundant extractions.
 * Guarantees graceful fallback to a default gray placeholder image so the timeline never shows black strips.
 */
class NativeThumbnailExtractor {

    companion object {
        private const val TAG = "NativeThumbnailExtractor"
        private var isNativeLibLoaded = false

        // In-memory LRU Cache (max 150 frames cached across timeline sessions)
        private val thumbnailLruCache = object : LruCache<String, Bitmap>(150) {
            override fun sizeOf(key: String, value: Bitmap): Int = 1
        }

        init {
            try {
                System.loadLibrary("native-lib")
                isNativeLibLoaded = true
                Log.d(TAG, "Successfully loaded native-lib.so")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "native-lib.so not found, falling back to MediaMetadataRetriever: ${e.message}")
                isNativeLibLoaded = false
            } catch (t: Throwable) {
                Log.e(TAG, "Error loading native library", t)
                isNativeLibLoaded = false
            }
        }

        /**
         * Generates a clean default gray placeholder image with subtle frame border.
         */
        fun createGrayPlaceholder(width: Int, height: Int): Bitmap {
            val w = width.coerceAtLeast(10)
            val h = height.coerceAtLeast(10)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint().apply {
                color = Color.parseColor("#2E2E30")
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

            paint.color = Color.parseColor("#444446")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRect(1f, 1f, w - 1f, h - 1f, paint)

            return bmp
        }
    }

    private external fun extractFramesNative(
        videoPath: String,
        timestampsMs: LongArray,
        targetWidth: Int,
        targetHeight: Int
    ): Array<Bitmap>?

    /**
     * Extracts thumbnails for the requested timestamps strictly on Dispatchers.IO.
     * Uses LruCache to return cached frames instantly.
     * Replaces any missing or failed frames with a default gray placeholder image.
     */
    suspend fun extractThumbnails(
        videoPath: String,
        timestampsMs: List<Long>,
        targetWidth: Int = 120,
        targetHeight: Int = 160
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        if (timestampsMs.isEmpty()) return@withContext emptyList()

        val results = MutableList<Bitmap?>(timestampsMs.size) { null }
        val missingIndices = mutableListOf<Int>()
        val missingTimestamps = mutableListOf<Long>()

        // 1. Check Memory Cache
        for (i in timestampsMs.indices) {
            val timeMs = timestampsMs[i]
            val key = "$videoPath-$timeMs-$targetWidth-$targetHeight"
            val cached = thumbnailLruCache.get(key)
            if (cached != null && !cached.isRecycled) {
                results[i] = cached
            } else {
                missingIndices.add(i)
                missingTimestamps.add(timeMs)
            }
        }

        // If all found in cache, return immediately
        if (missingTimestamps.isEmpty()) {
            return@withContext results.filterNotNull()
        }

        // 2. Extract missing frames via Native JNI if available
        var nativeBitmaps: Array<Bitmap>? = null
        if (isNativeLibLoaded) {
            try {
                nativeBitmaps = extractFramesNative(
                    videoPath = videoPath,
                    timestampsMs = missingTimestamps.toLongArray(),
                    targetWidth = targetWidth,
                    targetHeight = targetHeight
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Native extraction failed, using MediaMetadataRetriever", t)
            }
        }

        val nativeList = nativeBitmaps?.toList() ?: emptyList()

        // 3. Fallback extraction via MediaMetadataRetriever for any still-missing frames
        var retriever: MediaMetadataRetriever? = null
        try {
            for (m in missingIndices.indices) {
                val originalIndex = missingIndices[m]
                val timeMs = missingTimestamps[m]
                var bitmap: Bitmap? = if (m < nativeList.size) nativeList[m] else null

                if (bitmap == null || bitmap.isRecycled) {
                    if (retriever == null) {
                        retriever = MediaMetadataRetriever().apply {
                            setDataSource(videoPath)
                        }
                    }
                    val timeMicros = timeMs * 1000L
                    bitmap = try {
                        retriever.getScaledFrameAtTime(
                            timeMicros,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            targetWidth,
                            targetHeight
                        ) ?: retriever.getFrameAtTime(timeMicros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } catch (e: Exception) {
                        Log.w(TAG, "Frame extraction failed for $timeMs ms: ${e.message}")
                        null
                    }
                }

                // If extraction still failed, use default gray placeholder instead of showing black strips
                val finalBitmap = bitmap ?: createGrayPlaceholder(targetWidth, targetHeight)

                val key = "$videoPath-$timeMs-$targetWidth-$targetHeight"
                thumbnailLruCache.put(key, finalBitmap)
                results[originalIndex] = finalBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback extraction encountered error: ${e.message}", e)
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {}
        }

        // Final guarantee: replace any remaining null with placeholder
        return@withContext results.map { it ?: createGrayPlaceholder(targetWidth, targetHeight) }
    }
}
