package com.vikash.vidopro.core.media.export

import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.vikash.vidopro.core.media.api.VideoCodec

object VideoEncoder {

    /**
     * Builds standard MediaFormat for MediaCodec or FFmpeg video encoding.
     */
    fun createVideoFormat(config: ExportConfiguration): MediaFormat {
        val mime = when (config.targetVideoCodec) {
            VideoCodec.H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
            VideoCodec.VP9 -> MediaFormat.MIMETYPE_VIDEO_VP9
            VideoCodec.AV1 -> MediaFormat.MIMETYPE_VIDEO_AV1
            else -> MediaFormat.MIMETYPE_VIDEO_AVC
        }

        val format = MediaFormat.createVideoFormat(mime, config.targetWidth, config.targetHeight)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, config.targetBitrate.toInt())
        format.setInteger(MediaFormat.KEY_FRAME_RATE, config.targetFps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second keyframe interval

        return format
    }
}
