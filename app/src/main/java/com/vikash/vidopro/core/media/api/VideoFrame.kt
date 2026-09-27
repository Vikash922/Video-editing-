package com.vikash.vidopro.core.media.api

import android.graphics.Bitmap

/**
 * Normalized video frame representation returned by any decoder (Native or FFmpeg).
 */
data class VideoFrame(
    val bitmap: Bitmap?,
    val width: Int,
    val height: Int,
    val timestampUs: Long,
    val isKeyframe: Boolean = false,
    val yuvData: ByteArray? = null
) {
    val timestampMs: Long get() = timestampUs / 1000L

    fun recycle() {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VideoFrame
        if (timestampUs != other.timestampUs) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (bitmap != other.bitmap) return false
        return true
    }

    override fun hashCode(): Int {
        var result = timestampUs.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + (bitmap?.hashCode() ?: 0)
        return result
    }
}
