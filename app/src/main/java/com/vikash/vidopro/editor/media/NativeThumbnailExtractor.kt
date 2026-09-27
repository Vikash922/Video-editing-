package com.vikash.vidopro.editor.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NativeThumbnailExtractor {

    companion object {
        private const val TAG = "NativeThumbnailExtractor"
        private var isNativeLibLoaded = false

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
    }

    private external fun extractFramesNative(
        videoPath: String,
        timestampsMs: LongArray,
        targetWidth: Int,
        targetHeight: Int
    ): Array<Bitmap>?

    suspend fun extractThumbnails(
        videoPath: String,
        timestampsMs: List<Long>,
        targetWidth: Int = 120,
        targetHeight: Int = 160
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        if (timestampsMs.isEmpty()) return@withContext emptyList()

        if (isNativeLibLoaded) {
            try {
                val nativeResult = extractFramesNative(
                    videoPath = videoPath,
                    timestampsMs = timestampsMs.toLongArray(),
                    targetWidth = targetWidth,
                    targetHeight = targetHeight
                )
                if (nativeResult != null && nativeResult.isNotEmpty()) {
                    return@withContext nativeResult.filterNotNull()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Native extraction failed, using fallback", t)
            }
        }

        // Fallback: MediaMetadataRetriever
        fallbackExtraction(videoPath, timestampsMs, targetWidth, targetHeight)
    }

    private fun fallbackExtraction(
        videoPath: String,
        timestampsMs: List<Long>,
        targetWidth: Int,
        targetHeight: Int
    ): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoPath)
            for (timeMs in timestampsMs) {
                val timeMicros = timeMs * 1000L
                val frame = retriever.getScaledFrameAtTime(
                    timeMicros,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidth,
                    targetHeight
                ) ?: retriever.getFrameAtTime(timeMicros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                if (frame != null) {
                    bitmaps.add(frame)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback thumbnail extraction failed: ${e.message}", e)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
        return bitmaps
    }
}
