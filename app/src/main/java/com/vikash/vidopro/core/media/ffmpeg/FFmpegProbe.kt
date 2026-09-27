package com.vikash.vidopro.core.media.ffmpeg

import android.content.Context
import android.net.Uri
import com.antonkarpenko.ffmpegkit.FFprobeKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.vikash.vidopro.core.media.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FFmpegProbe {

    /**
     * Inspects media files using FFprobe for deep codec and stream analysis.
     */
    suspend fun probe(context: Context, uri: Uri): MediaFormatInfo = withContext(Dispatchers.IO) {
        val path = FFmpegKitConfig.getSafParameterForRead(context, uri)
        val session = FFprobeKit.getMediaInformation(path)
        val mediaInfo = session.mediaInformation

        var width = 0
        var height = 0
        var durationUs = 0L
        var bitrate = 0L
        var rotation = 0
        var videoCodec = VideoCodec.UNKNOWN
        var audioCodec = AudioCodec.UNKNOWN
        var channelCount = 0
        var sampleRate = 0

        if (mediaInfo != null) {
            durationUs = (mediaInfo.duration?.toDoubleOrNull() ?: 0.0 * 1_000_000.0).toLong()
            bitrate = mediaInfo.bitrate?.toLongOrNull() ?: 0L

            for (stream in mediaInfo.streams) {
                if (stream.type.equals("video", ignoreCase = true)) {
                    width = stream.width?.toInt() ?: width
                    height = stream.height?.toInt() ?: height
                    val codecName = stream.codec ?: ""
                    videoCodec = when {
                        codecName.contains("h264", ignoreCase = true) || codecName.contains("avc", ignoreCase = true) -> VideoCodec.H264
                        codecName.contains("hevc", ignoreCase = true) || codecName.contains("h265", ignoreCase = true) -> VideoCodec.H265
                        codecName.contains("vp8", ignoreCase = true) -> VideoCodec.VP8
                        codecName.contains("vp9", ignoreCase = true) -> VideoCodec.VP9
                        codecName.contains("av1", ignoreCase = true) -> VideoCodec.AV1
                        codecName.contains("mpeg4", ignoreCase = true) -> VideoCodec.MPEG4
                        codecName.contains("mpeg2", ignoreCase = true) -> VideoCodec.MPEG2
                        codecName.contains("mjpeg", ignoreCase = true) -> VideoCodec.MJPEG
                        codecName.contains("prores", ignoreCase = true) -> VideoCodec.PRORES
                        else -> VideoCodec.UNKNOWN
                    }
                } else if (stream.type.equals("audio", ignoreCase = true)) {
                    val codecName = stream.codec ?: ""
                    sampleRate = stream.sampleRate?.toInt() ?: sampleRate
                    channelCount = stream.getNumberProperty("channels")?.toInt()
                        ?: stream.getStringProperty("channels")?.toIntOrNull()
                        ?: channelCount
                    audioCodec = when {
                        codecName.contains("aac", ignoreCase = true) -> AudioCodec.AAC
                        codecName.contains("mp3", ignoreCase = true) -> AudioCodec.MP3
                        codecName.contains("opus", ignoreCase = true) -> AudioCodec.OPUS
                        codecName.contains("vorbis", ignoreCase = true) -> AudioCodec.VORBIS
                        codecName.contains("flac", ignoreCase = true) -> AudioCodec.FLAC
                        codecName.contains("pcm", ignoreCase = true) -> AudioCodec.PCM_16BIT
                        codecName.contains("alac", ignoreCase = true) -> AudioCodec.ALAC
                        codecName.contains("eac3", ignoreCase = true) -> AudioCodec.EAC3
                        codecName.contains("ac3", ignoreCase = true) -> AudioCodec.AC3
                        else -> AudioCodec.UNKNOWN
                    }
                }
            }
        }

        val extension = uri.path?.substringAfterLast('.', "") ?: ""
        val container = ContainerFormat.fromExtension(extension)

        MediaFormatInfo(
            uriString = uri.toString(),
            containerFormat = container,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            width = width,
            height = height,
            durationUs = durationUs,
            bitrate = bitrate,
            rotationDegrees = rotation,
            channelCount = channelCount,
            sampleRate = sampleRate,
            isDrmProtected = false,
            chosenDecoder = DecoderType.FFMPEG_SOFTWARE
        )
    }
}
