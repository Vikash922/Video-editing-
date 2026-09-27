package com.vikash.vidopro.core.media.native_dec

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.vikash.vidopro.core.media.api.AudioFrame
import com.vikash.vidopro.core.media.api.MediaDecoder
import com.vikash.vidopro.core.media.api.MediaFormatInfo
import com.vikash.vidopro.core.media.api.VideoFrame
import com.vikash.vidopro.core.media.detector.MediaFileDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Native Android hardware-accelerated MediaDecoder utilizing MediaMetadataRetriever
 * and MediaCodec framework.
 */
class AndroidCodecDecoder : MediaDecoder {

    private var retriever: MediaMetadataRetriever? = null
    private var formatInfo: MediaFormatInfo? = null
    private var isClosed = false

    override suspend fun open(source: Uri, context: Context): MediaFormatInfo = withContext(Dispatchers.IO) {
        val info = MediaFileDetector.probe(context, source)
        formatInfo = info

        val mr = MediaMetadataRetriever()
        mr.setDataSource(context, source)
        retriever = mr
        isClosed = false
        info
    }

    override suspend fun decodeVideoFrame(timestampUs: Long): VideoFrame? = withContext(Dispatchers.IO) {
        if (isClosed) return@withContext null
        val mr = retriever ?: return@withContext null
        try {
            val bitmap = mr.getFrameAtTime(timestampUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                return@withContext VideoFrame(
                    bitmap = bitmap,
                    width = bitmap.width,
                    height = bitmap.height,
                    timestampUs = timestampUs
                )
            }
        } catch (_: Exception) {}
        null
    }

    override suspend fun decodeAudioFrame(timestampUs: Long): AudioFrame? {
        // Native MediaMetadataRetriever does not stream live PCM frames directly;
        // AudioDecoder or FFmpegDecoder is used for full PCM sample decoding.
        return null
    }

    override suspend fun seekTo(timestampUs: Long) {
        // Stateless seek for retriever
    }

    override fun getFormatInfo(): MediaFormatInfo? = formatInfo

    override fun close() {
        isClosed = true
        try {
            retriever?.release()
        } catch (_: Exception) {}
        retriever = null
    }
}
