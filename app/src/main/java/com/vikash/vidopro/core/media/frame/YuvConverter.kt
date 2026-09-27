package com.vikash.vidopro.core.media.frame

import android.graphics.Bitmap

object YuvConverter {

    /**
     * Converts raw NV21 (YUV420SP) byte array to an Android ARGB_8888 Bitmap.
     */
    fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        val frameSize = width * height

        for (j in 0 until height) {
            val uvp = frameSize + (j shr 1) * width
            var u = 0
            var v = 0
            for (i in 0 until width) {
                var y = (0xff and nv21[j * width + i].toInt()) - 16
                if (y < 0) y = 0
                if ((i and 1) == 0) {
                    v = (0xff and nv21[uvp + (i shr 1) * 2].toInt()) - 128
                    u = (0xff and nv21[uvp + (i shr 1) * 2 + 1].toInt()) - 128
                }

                val y1192 = 1192 * y
                var r = (y1192 + 1634 * v)
                var g = (y1192 - 833 * v - 400 * u)
                var b = (y1192 + 2066 * u)

                if (r < 0) r = 0 else if (r > 262143) r = 262143
                if (g < 0) g = 0 else if (g > 262143) g = 262143
                if (b < 0) b = 0 else if (b > 262143) b = 262143

                pixels[j * width + i] = -0x1000000 or ((r shl 6) and 0xff0000) or ((g shr 2) and 0xff00) or ((b shr 10) and 0xff)
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
