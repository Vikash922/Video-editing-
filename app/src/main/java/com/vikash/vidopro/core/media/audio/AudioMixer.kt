package com.vikash.vidopro.core.media.audio

import com.vikash.vidopro.core.media.api.AudioFrame

data class AudioTrackInput(
    val frame: AudioFrame,
    val volume: Float = 1.0f,
    val duckFactor: Float = 1.0f
)

object AudioMixer {

    /**
     * Mixes multiple audio frames into a single output frame with smooth limiting.
     */
    fun mixTracks(tracks: List<AudioTrackInput>, sampleRate: Int = 44100, channels: Int = 2): AudioFrame {
        if (tracks.isEmpty()) {
            return AudioFrame(ByteArray(0), sampleRate, channels, 0L)
        }

        // Find max duration/sample size among active tracks
        val maxSamples = tracks.maxOfOrNull { it.frame.sampleCount } ?: 0
        val mixedFloats = FloatArray(maxSamples * channels)
        val timestampUs = tracks.minOfOrNull { it.frame.timestampUs } ?: 0L

        for (track in tracks) {
            val floats = AudioFormatConverter.pcm16ToFloat(track.frame.pcmData)
            val effectiveVol = track.volume * track.duckFactor

            for (i in floats.indices) {
                if (i < mixedFloats.size) {
                    mixedFloats[i] += floats[i] * effectiveVol
                }
            }
        }

        // Apply soft limiter to prevent distortion/harsh clipping
        for (i in mixedFloats.indices) {
            val sample = mixedFloats[i]
            mixedFloats[i] = when {
                sample > 1.0f -> 1.0f - (1.0f / (sample + 1.0f))
                sample < -1.0f -> -1.0f + (1.0f / (-sample + 1.0f))
                else -> sample
            }
        }

        val pcmOut = AudioFormatConverter.floatToPcm16(mixedFloats)
        return AudioFrame(
            pcmData = pcmOut,
            sampleRate = sampleRate,
            channelCount = channels,
            timestampUs = timestampUs
        )
    }
}
