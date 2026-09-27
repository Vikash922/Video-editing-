package com.vikash.vidopro.core.media.export

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Android MediaMuxer wrapper for MP4 container writing.
 */
class Muxer(private val outputFile: File) {

    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var isStarted = false

    fun init() {
        mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        isStarted = false
    }

    fun addVideoTrack(format: MediaFormat): Int {
        val muxer = mediaMuxer ?: throw IllegalStateException("Muxer not initialized")
        videoTrackIndex = muxer.addTrack(format)
        return videoTrackIndex
    }

    fun addAudioTrack(format: MediaFormat): Int {
        val muxer = mediaMuxer ?: throw IllegalStateException("Muxer not initialized")
        audioTrackIndex = muxer.addTrack(format)
        return audioTrackIndex
    }

    fun start() {
        val muxer = mediaMuxer ?: throw IllegalStateException("Muxer not initialized")
        muxer.start()
        isStarted = true
    }

    fun writeSampleData(trackIndex: Int, byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!isStarted) return
        mediaMuxer?.writeSampleData(trackIndex, byteBuffer, bufferInfo)
    }

    fun release() {
        try {
            if (isStarted) {
                mediaMuxer?.stop()
            }
            mediaMuxer?.release()
        } catch (_: Exception) {}
        mediaMuxer = null
        isStarted = false
    }
}
