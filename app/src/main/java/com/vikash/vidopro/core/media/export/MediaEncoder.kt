package com.vikash.vidopro.core.media.export

import com.vikash.vidopro.core.media.api.ContainerFormat
import com.vikash.vidopro.core.media.api.VideoCodec
import java.io.File

data class ExportConfiguration(
    val outputFile: File,
    val containerFormat: ContainerFormat = ContainerFormat.MP4,
    val targetVideoCodec: VideoCodec = VideoCodec.H264,
    val targetWidth: Int = 1920,
    val targetHeight: Int = 1080,
    val targetBitrate: Long = 8_000_000L,
    val targetFps: Int = 30,
    val targetAudioBitrate: Int = 192_000,
    val targetSampleRate: Int = 44100,
    val preferHardwareAcceleration: Boolean = true
)

interface MediaEncoder {
    suspend fun start(config: ExportConfiguration, onProgress: (Float) -> Unit): Boolean
    fun cancel()
}
