package com.vikash.vidopro.core.media.frame

import android.graphics.Bitmap

object FrameConverter {

    /**
     * Ensures the given bitmap is in a mutable, software-renderable ARGB_8888 format.
     */
    fun ensureSoftwareBitmap(source: Bitmap): Bitmap {
        if (source.config == Bitmap.Config.ARGB_8888 && !source.isRecycled) {
            return source
        }
        return source.copy(Bitmap.Config.ARGB_8888, true)
    }

    /**
     * Converts an ARGB_8888 bitmap to RGB_565 to save memory for long timeline strips.
     */
    fun toRgb565(source: Bitmap): Bitmap {
        if (source.config == Bitmap.Config.RGB_565) return source
        return source.copy(Bitmap.Config.RGB_565, false)
    }
}
