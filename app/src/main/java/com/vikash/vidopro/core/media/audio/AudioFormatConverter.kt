package com.vikash.vidopro.core.media.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioFormatConverter {

    /**
     * Converts a 16-bit little-endian PCM byte array to a float array [-1.0f..1.0f].
     */
    fun pcm16ToFloat(pcmBytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(pcmBytes.size / 2)
        var i = 0
        while (buffer.remaining() >= 2) {
            floats[i++] = buffer.short / 32768.0f
        }
        return floats
    }

    /**
     * Converts a float array [-1.0f..1.0f] back to 16-bit PCM little-endian bytes with soft clamping.
     */
    fun floatToPcm16(floats: FloatArray): ByteArray {
        val out = ByteArray(floats.size * 2)
        val buffer = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) {
            // Soft clipping
            val clamped = f.coerceIn(-1.0f, 1.0f)
            val sample = (clamped * 32767.0f).toInt().toShort()
            buffer.putShort(sample)
        }
        return out
    }
}
