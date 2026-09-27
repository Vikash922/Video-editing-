package com.vikash.vidopro.core.media.diagnostics

import com.vikash.vidopro.core.media.api.MediaFormatInfo

object MediaDiagnostics {

    fun generateReport(formatInfo: MediaFormatInfo?, error: MediaError?): String {
        val sb = StringBuilder()
        sb.append("=== VidoPRO Media Diagnostic Report ===\n")
        if (formatInfo != null) {
            sb.append("URI: ${formatInfo.uriString}\n")
            sb.append("Container: ${formatInfo.containerFormat.containerName} (.${formatInfo.containerFormat.extension})\n")
            sb.append("Video: ${formatInfo.videoCodec.standardName} (${formatInfo.width}x${formatInfo.height} @ ${formatInfo.frameRate}fps)\n")
            sb.append("Audio: ${formatInfo.audioCodec.standardName} (${formatInfo.channelCount}ch @ ${formatInfo.sampleRate}Hz)\n")
            sb.append("Duration: ${formatInfo.durationMs}ms\n")
            sb.append("Decoder Strategy: ${formatInfo.chosenDecoder}\n")
            sb.append("DRM Protected: ${formatInfo.isDrmProtected}\n")
        } else {
            sb.append("Media probe failed or file unreadable.\n")
        }

        if (error != null) {
            sb.append("\n[ERROR ENCOUNTERED]\n")
            sb.append("Message: ${error.userMessage}\n")
            sb.append("Technical: ${error.technicalDetails}\n")
        } else {
            sb.append("\nStatus: Media format fully compatible.\n")
        }
        sb.append("=======================================")
        return sb.toString()
    }
}
