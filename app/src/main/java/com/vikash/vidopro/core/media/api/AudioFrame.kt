package com.vikash.vidopro.core.media.api

/**
 * Normalized audio frame containing decoded linear PCM data.
 */
data class AudioFrame(
    val pcmData: ByteArray,
    val sampleRate: Int,
    val channelCount: Int,
    val timestampUs: Long,
    val bytesPerSample: Int = 2 // 16-bit PCM default
) {
    val timestampMs: Long get() = timestampUs / 1000L

    val sampleCount: Int
        get() = pcmData.size / (channelCount * bytesPerSample)

    val durationUs: Long
        get() = if (sampleRate > 0) (sampleCount * 1_000_000L) / sampleRate else 0L

    /**
     * Computes the normalized peak amplitude [0.0f..1.0f] for waveform rendering.
     */
    fun getPeakAmplitude(): Float {
        if (pcmData.isEmpty() || bytesPerSample != 2) return 0f
        var maxAmp = 0
        val buffer = java.nio.ByteBuffer.wrap(pcmData).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        while (buffer.remaining() >= 2) {
            val sample = kotlin.math.abs(buffer.short.toInt())
            if (sample > maxAmp) {
                maxAmp = sample
            }
        }
        return (maxAmp / 32767.0f).coerceIn(0f, 1f)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioFrame
        if (sampleRate != other.sampleRate) return false
        if (channelCount != other.channelCount) return false
        if (timestampUs != other.timestampUs) return false
        if (!pcmData.contentEquals(other.pcmData)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = pcmData.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channelCount
        result = 31 * result + timestampUs.hashCode()
        return result
    }
}
