package com.vikash.vidopro.core.media.api

import android.content.Context
import android.net.Uri

/**
 * Universal interface for video decoders.
 * The editing timeline and preview engine interact ONLY with this interface.
 */
interface MediaDecoder {
    /**
     * Prepares and initializes the media stream.
     */
    suspend fun open(source: Uri, context: Context): MediaFormatInfo

    /**
     * Extracts and decodes the nearest video frame at [timestampUs].
     */
    suspend fun decodeVideoFrame(timestampUs: Long): VideoFrame?

    /**
     * Decodes the audio frame corresponding to [timestampUs].
     */
    suspend fun decodeAudioFrame(timestampUs: Long): AudioFrame?

    /**
     * Seeks underlying decoder state to the specified timestamp.
     */
    suspend fun seekTo(timestampUs: Long)

    /**
     * Returns cached format info if open() was called successfully.
     */
    fun getFormatInfo(): MediaFormatInfo?

    /**
     * Releases native, hardware codec, or FFmpeg session resources.
     */
    fun close()
}
