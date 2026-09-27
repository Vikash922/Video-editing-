package com.vikash.vidopro.core.media.api

import android.content.Context
import android.net.Uri

/**
 * Dedicated interface for audio extraction and decoding to raw PCM.
 */
interface AudioDecoder {
    /**
     * Initializes the audio decoder for the given media source.
     */
    suspend fun open(source: Uri, context: Context): MediaFormatInfo

    /**
     * Decodes and extracts PCM samples between [startUs] and [endUs].
     */
    suspend fun extractSamples(startUs: Long, endUs: Long): List<AudioFrame>

    /**
     * Closes the decoder and releases associated native buffers.
     */
    fun close()
}
