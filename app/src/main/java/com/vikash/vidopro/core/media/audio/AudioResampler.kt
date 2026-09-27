package com.vikash.vidopro.core.media.audio

object AudioResampler {

    /**
     * Resamples audio float samples from inRate to outRate using linear interpolation.
     */
    fun resample(input: FloatArray, inRate: Int, outRate: Int, channels: Int = 2): FloatArray {
        if (inRate == outRate || input.isEmpty()) return input

        val ratio = inRate.toDouble() / outRate.toDouble()
        val inFrames = input.size / channels
        val outFrames = (inFrames / ratio).toInt()
        val output = FloatArray(outFrames * channels)

        for (outIdx in 0 until outFrames) {
            val inExact = outIdx * ratio
            val inFloor = inExact.toInt()
            val frac = (inExact - inFloor).toFloat()
            val inNext = if (inFloor + 1 < inFrames) inFloor + 1 else inFloor

            for (ch in 0 until channels) {
                val s0 = input[inFloor * channels + ch]
                val s1 = input[inNext * channels + ch]
                output[outIdx * channels + ch] = s0 + frac * (s1 - s0)
            }
        }
        return output
    }
}
