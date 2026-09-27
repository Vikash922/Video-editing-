package com.vikash.vidopro.core.media.detector

import android.content.Context
import android.net.Uri
import com.vikash.vidopro.core.media.api.DecoderType
import com.vikash.vidopro.core.media.api.MediaDecoder
import com.vikash.vidopro.core.media.api.MediaFormatInfo
import com.vikash.vidopro.core.media.diagnostics.MediaError
import com.vikash.vidopro.core.media.ffmpeg.FFmpegDecoder
import com.vikash.vidopro.core.media.native_dec.AndroidCodecDecoder

object DecoderResolver {

    /**
     * Resolves and provides the optimal decoder implementation:
     * - Native hardware/software decoder for standard formats
     * - FFmpeg fallback for complex, legacy, or container-restricted formats
     */
    suspend fun resolveDecoder(context: Context, uri: Uri): Pair<MediaDecoder, MediaFormatInfo> {
        val formatInfo = MediaFileDetector.probe(context, uri)

        if (formatInfo.isDrmProtected) {
            throw MediaError.DrmProtectedMedia
        }

        val decoder: MediaDecoder = when (formatInfo.chosenDecoder) {
            DecoderType.NATIVE_HARDWARE, DecoderType.NATIVE_SOFTWARE -> {
                try {
                    val nativeDec = AndroidCodecDecoder()
                    nativeDec.open(uri, context)
                    nativeDec
                } catch (_: Throwable) {
                    // Fallback to FFmpeg if native decoder initialization throws
                    val ffmpegDec = FFmpegDecoder()
                    ffmpegDec.open(uri, context)
                    ffmpegDec
                }
            }
            DecoderType.FFMPEG_SOFTWARE -> {
                val ffmpegDec = FFmpegDecoder()
                ffmpegDec.open(uri, context)
                ffmpegDec
            }
            DecoderType.UNSUPPORTED -> {
                // Try FFmpeg as final resort before giving up
                try {
                    val ffmpegDec = FFmpegDecoder()
                    ffmpegDec.open(uri, context)
                    ffmpegDec
                } catch (e: Throwable) {
                    throw MediaError.UnsupportedVideoCodec(
                        codecName = formatInfo.videoCodec.standardName,
                        mimeType = formatInfo.containerFormat.extension,
                        technicalDetails = "No native or FFmpeg decoder available for $uri"
                    )
                }
            }
        }

        return Pair(decoder, formatInfo)
    }
}
