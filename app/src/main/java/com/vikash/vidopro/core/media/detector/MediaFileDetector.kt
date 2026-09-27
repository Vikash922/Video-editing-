package com.vikash.vidopro.core.media.detector

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.vikash.vidopro.core.media.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaFileDetector {
    private const val TAG = "MediaFileDetector"

    suspend fun probe(context: Context, uri: Uri): MediaFormatInfo = withContext(Dispatchers.IO) {
        val container = ContainerDetector.detect(context, uri)
        val retriever = MediaMetadataRetriever()
        var width = 0
        var height = 0
        var durationUs = 0L
        var bitrate = 0L
        var rotation = 0
        var videoMime: String? = null
        var audioMime: String? = null
        var channelCount = 0
        var sampleRate = 0
        var frameRate = 0f
        var isDrm = false

        try {
            retriever.setDataSource(context, uri)
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000L
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            videoMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            
            val captureFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f
            if (captureFps > 0f) frameRate = captureFps
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever initial probe warning: ${e.message}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        // Secondary deeper inspection via MediaExtractor
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("video/")) {
                    videoMime = mime
                    if (width == 0 && format.containsKey(MediaFormat.KEY_WIDTH)) {
                        width = format.getInteger(MediaFormat.KEY_WIDTH)
                    }
                    if (height == 0 && format.containsKey(MediaFormat.KEY_HEIGHT)) {
                        height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    }
                    if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                        rotation = format.getInteger(MediaFormat.KEY_ROTATION)
                    }
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        frameRate = try {
                            format.getFloat(MediaFormat.KEY_FRAME_RATE)
                        } catch (_: Exception) {
                            try {
                                format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                            } catch (_: Exception) {
                                frameRate
                            }
                        }
                    }
                } else if (mime.startsWith("audio/")) {
                    audioMime = mime
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                }
            }

            if (extractor.psshInfo != null && extractor.psshInfo!!.isNotEmpty()) {
                isDrm = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaExtractor inspection fallback: ${e.message}")
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }

        // Determine suitable decoder
        val videoCodec = VideoCodec.fromMime(videoMime)
        val audioCodec = AudioCodec.fromMime(audioMime)

        val decoderType = if (isDrm) {
            DecoderType.UNSUPPORTED
        } else if (container == ContainerFormat.AVI || container == ContainerFormat.FLV) {
            DecoderType.FFMPEG_SOFTWARE
        } else if (videoMime != null) {
            CodecDetector.findVideoDecoder(videoMime, width, height)
        } else if (audioMime != null) {
            if (CodecDetector.hasNativeAudioDecoder(audioMime)) DecoderType.NATIVE_HARDWARE else DecoderType.FFMPEG_SOFTWARE
        } else {
            DecoderType.FFMPEG_SOFTWARE
        }

        MediaFormatInfo(
            uriString = uri.toString(),
            containerFormat = container,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            width = width,
            height = height,
            durationUs = durationUs,
            bitrate = bitrate,
            frameRate = frameRate,
            rotationDegrees = rotation,
            channelCount = channelCount,
            sampleRate = sampleRate,
            isDrmProtected = isDrm,
            chosenDecoder = decoderType
        )
    }

    /**
     * Checks if media is too heavy for direct phone editing (e.g. 4K or 60fps)
     * and requires background proxy generation to prevent timeline stuttering/crashes.
     */
    fun isHeavyMedia(width: Int, height: Int, frameRate: Float, bitrate: Long = 0L): Boolean {
        val is4K = (width >= 3840 || height >= 2160) || (width >= 2160 && height >= 3840) || (width * height >= 3840 * 2000)
        val isHighFps = frameRate >= 50f
        val isHighBitrate = bitrate > 35_000_000L
        return is4K || isHighFps || isHighBitrate
    }

    suspend fun checkIsHeavyMedia(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val info = probe(context, uri)
        isHeavyMedia(info.width, info.height, info.frameRate, info.bitrate)
    }
}
