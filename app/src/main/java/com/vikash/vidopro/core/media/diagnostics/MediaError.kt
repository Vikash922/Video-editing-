package com.vikash.vidopro.core.media.diagnostics

/**
 * Detailed error hierarchy for media decoding, probing, and rendering.
 */
sealed class MediaError(open val userMessage: String, open val technicalDetails: String) : Exception(userMessage) {

    data class UnsupportedContainer(
        val container: String,
        override val technicalDetails: String = "Container format '$container' is not supported by standard Android media frameworks."
    ) : MediaError("The container format ($container) is unsupported.", technicalDetails)

    data class UnsupportedVideoCodec(
        val codecName: String,
        val mimeType: String,
        override val technicalDetails: String = "Video codec '$codecName' ($mimeType) has no compatible hardware or software decoder on this device."
    ) : MediaError("The video codec ($codecName) cannot be decoded.", technicalDetails)

    data class UnsupportedAudioCodec(
        val codecName: String,
        val mimeType: String,
        override val technicalDetails: String = "Audio codec '$codecName' ($mimeType) is unsupported or missing license components."
    ) : MediaError("The audio stream ($codecName) cannot be played.", technicalDetails)

    data class CorruptedMediaFile(
        val pathOrUri: String,
        val reason: String
    ) : MediaError("The media file appears to be damaged or incomplete.", "File: $pathOrUri, Reason: $reason")

    object DrmProtectedMedia : MediaError(
        "This video is protected by DRM and cannot be edited.",
        "Widevine/ClearKey DRM encryption detected in media tracks."
    )

    data class ResolutionTooHigh(
        val width: Int,
        val height: Int,
        val maxSupportedWidth: Int,
        val maxSupportedHeight: Int
    ) : MediaError(
        "Video resolution (${width}x${height}) exceeds maximum hardware capabilities (${maxSupportedWidth}x${maxSupportedHeight}).",
        "Resolution constraint violation on MediaCodec capabilities."
    )

    data class DecoderInitializationFailed(
        val decoderName: String,
        val underlyingException: Throwable?
    ) : MediaError(
        "Failed to initialize $decoderName decoder: ${underlyingException?.message ?: "Unknown error"}",
        underlyingException?.stackTraceToString() ?: "No stacktrace"
    )
}
