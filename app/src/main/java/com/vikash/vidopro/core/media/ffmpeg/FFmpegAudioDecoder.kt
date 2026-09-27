package com.vikash.vidopro.core.media.ffmpeg

import android.content.Context
import android.net.Uri
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.antonkarpenko.ffmpegkit.ReturnCode
import com.vikash.vidopro.core.media.api.AudioDecoder
import com.vikash.vidopro.core.media.api.AudioFrame
import com.vikash.vidopro.core.media.api.MediaFormatInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class FFmpegAudioDecoder : AudioDecoder {

    private var context: Context? = null
    private var sourceUri: Uri? = null
    private var formatInfo: MediaFormatInfo? = null

    override suspend fun open(source: Uri, context: Context): MediaFormatInfo = withContext(Dispatchers.IO) {
        this@FFmpegAudioDecoder.context = context.applicationContext
        this@FFmpegAudioDecoder.sourceUri = source
        val info = FFmpegProbe.probe(context, source)
        formatInfo = info
        info
    }

    override suspend fun extractSamples(startUs: Long, endUs: Long): List<AudioFrame> = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext emptyList()
        val uri = sourceUri ?: return@withContext emptyList()

        val startSec = (startUs / 1_000_000.0).coerceAtLeast(0.0)
        val durationSec = ((endUs - startUs) / 1_000_000.0).coerceAtLeast(0.05)
        val inputPath = FFmpegKitConfig.getSafParameterForRead(ctx, uri)
        val tempPcm = File(ctx.cacheDir, "extracted_${System.currentTimeMillis()}.pcm")

        val cmd = String.format(
            Locale.US,
            "-ss %.3f -t %.3f -i %s -f s16le -acodec pcm_s16le -ar 44100 -ac 2 -y \"%s\"",
            startSec,
            durationSec,
            inputPath,
            tempPcm.absolutePath
        )

        val session = FFmpegKit.execute(cmd)
        val frames = mutableListOf<AudioFrame>()
        if (ReturnCode.isSuccess(session.returnCode) && tempPcm.exists()) {
            val bytes = tempPcm.readBytes()
            tempPcm.delete()
            frames.add(
                AudioFrame(
                    pcmData = bytes,
                    sampleRate = 44100,
                    channelCount = 2,
                    timestampUs = startUs
                )
            )
        } else {
            tempPcm.delete()
        }
        frames
    }

    override fun close() {
        context = null
        sourceUri = null
    }
}
