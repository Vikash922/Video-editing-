package com.vikash.vidopro.core.media.export

import android.media.MediaCodecInfo
import android.media.MediaFormat

object AudioEncoder {

    /**
     * Builds standard MediaFormat for AAC audio encoding.
     */
    fun createAudioFormat(config: ExportConfiguration, channelCount: Int = 2): MediaFormat {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            config.targetSampleRate,
            channelCount
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, config.targetAudioBitrate)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        return format
    }
}
