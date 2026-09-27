package com.vikash.vidopro.core.media.ffmpeg

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.antonkarpenko.ffmpegkit.ReturnCode
import com.vikash.vidopro.core.media.api.AudioFrame
import com.vikash.vidopro.core.media.api.MediaDecoder
import com.vikash.vidopro.core.media.api.MediaFormatInfo
import com.vikash.vidopro.core.media.api.VideoFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Universal fallback decoder using FFmpeg.
 * Provides broad coverage for codecs/containers unsupported by Android MediaCodec.
 */
class FFmpegDecoder : MediaDecoder {

    private var context: Context? = null
    private var sourceUri: Uri? = null
    private var formatInfo: MediaFormatInfo? = null
    private var isClosed = false
    private val tempDir by lazy {
        val dir = File(context?.cacheDir, "ffmpeg_decoder_frames")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    override suspend fun open(source: Uri, context: Context): MediaFormatInfo = withContext(Dispatchers.IO) {
        this@FFmpegDecoder.context = context.applicationContext
        this@FFmpegDecoder.sourceUri = source
        isClosed = false

        val info = FFmpegProbe.probe(context, source)
        formatInfo = info
        info
    }

    override suspend fun decodeVideoFrame(timestampUs: Long): VideoFrame? = withContext(Dispatchers.IO) {
        if (isClosed) return@withContext null
        val ctx = context ?: return@withContext null
        val uri = sourceUri ?: return@withContext null

        val seconds = timestampUs / 1_000_000.0
        val inputPath = FFmpegKitConfig.getSafParameterForRead(ctx, uri)
        val tempOutput = File(tempDir, "frame_${System.currentTimeMillis()}_${timestampUs}.jpg")

        val cmd = String.format(
            Locale.US,
            "-ss %.3f -i %s -vframes 1 -q:v 2 -y \"%s\"",
            seconds,
            inputPath,
            tempOutput.absolutePath
        )

        val session = FFmpegKit.execute(cmd)
        if (ReturnCode.isSuccess(session.returnCode) && tempOutput.exists()) {
            val bitmap = BitmapFactory.decodeFile(tempOutput.absolutePath)
            tempOutput.delete()
            if (bitmap != null) {
                return@withContext VideoFrame(
                    bitmap = bitmap,
                    width = bitmap.width,
                    height = bitmap.height,
                    timestampUs = timestampUs
                )
            }
        } else {
            tempOutput.delete()
        }
        null
    }

    override suspend fun decodeAudioFrame(timestampUs: Long): AudioFrame? = withContext(Dispatchers.IO) {
        if (isClosed) return@withContext null
        val ctx = context ?: return@withContext null
        val uri = sourceUri ?: return@withContext null

        val startSec = timestampUs / 1_000_000.0
        val durationSec = 0.1 // 100ms slice
        val inputPath = FFmpegKitConfig.getSafParameterForRead(ctx, uri)
        val tempPcm = File(tempDir, "audio_${System.currentTimeMillis()}_${timestampUs}.pcm")

        val cmd = String.format(
            Locale.US,
            "-ss %.3f -t %.3f -i %s -f s16le -acodec pcm_s16le -ar 44100 -ac 2 -y \"%s\"",
            startSec,
            durationSec,
            inputPath,
            tempPcm.absolutePath
        )

        val session = FFmpegKit.execute(cmd)
        if (ReturnCode.isSuccess(session.returnCode) && tempPcm.exists()) {
            val pcmBytes = tempPcm.readBytes()
            tempPcm.delete()
            return@withContext AudioFrame(
                pcmData = pcmBytes,
                sampleRate = 44100,
                channelCount = 2,
                timestampUs = timestampUs
            )
        } else {
            tempPcm.delete()
        }
        null
    }

    override suspend fun seekTo(timestampUs: Long) {
        // Fast seek via -ss parameter in next decode call
    }

    override fun getFormatInfo(): MediaFormatInfo? = formatInfo

    override fun close() {
        isClosed = true
        tempDir.deleteRecursively()
        context = null
        sourceUri = null
    }
}
