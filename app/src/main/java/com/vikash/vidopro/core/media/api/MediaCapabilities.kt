package com.vikash.vidopro.core.media.api

/**
 * Representation of video and audio capabilities, codecs, and container formats.
 */
enum class VideoCodec(val standardName: String, val mimeTypes: List<String>) {
    H264("H.264 / AVC", listOf("video/avc")),
    H265("H.265 / HEVC", listOf("video/hevc")),
    VP8("VP8", listOf("video/x-vnd.on2.vp8")),
    VP9("VP9", listOf("video/x-vnd.on2.vp9")),
    AV1("AV1", listOf("video/av01")),
    MPEG4("MPEG-4 Part 2", listOf("video/mp4v-es")),
    MPEG2("MPEG-2", listOf("video/mpeg2")),
    MJPEG("Motion JPEG", listOf("video/mjpeg")),
    PRORES("Apple ProRes", listOf("video/prores")),
    UNKNOWN("Unknown Codec", emptyList());

    companion object {
        fun fromMime(mime: String?): VideoCodec {
            if (mime == null) return UNKNOWN
            return values().firstOrNull { it.mimeTypes.any { m -> m.equals(mime, ignoreCase = true) } } ?: UNKNOWN
        }
    }
}

enum class AudioCodec(val standardName: String, val mimeTypes: List<String>) {
    AAC("AAC", listOf("audio/mp4a-latm", "audio/aac")),
    MP3("MP3", listOf("audio/mpeg", "audio/mp3")),
    OPUS("Opus", listOf("audio/opus")),
    VORBIS("Vorbis", listOf("audio/vorbis")),
    FLAC("FLAC", listOf("audio/flac")),
    PCM_16BIT("WAV / Raw PCM", listOf("audio/raw", "audio/wav", "audio/x-wav")),
    ALAC("Apple Lossless (ALAC)", listOf("audio/alac")),
    AC3("Dolby AC-3", listOf("audio/ac3")),
    EAC3("Dolby Digital Plus (E-AC-3)", listOf("audio/eac3")),
    UNKNOWN("Unknown Audio Codec", emptyList());

    companion object {
        fun fromMime(mime: String?): AudioCodec {
            if (mime == null) return UNKNOWN
            return values().firstOrNull { it.mimeTypes.any { m -> m.equals(mime, ignoreCase = true) } } ?: UNKNOWN
        }
    }
}

enum class ContainerFormat(val extension: String, val containerName: String) {
    MP4("mp4", "MPEG-4 Part 14"),
    MOV("mov", "QuickTime Movie"),
    MKV("mkv", "Matroska"),
    WEBM("webm", "WebM"),
    AVI("avi", "Audio Video Interleave"),
    TS("ts", "MPEG Transport Stream"),
    M4V("m4v", "Apple Video Container"),
    THREE_GP("3gp", "3GPP Container"),
    OGG("ogg", "Ogg Multimedia Container"),
    WAV("wav", "Waveform Audio File"),
    FLV("flv", "Flash Video"),
    UNKNOWN("", "Unknown Container");

    companion object {
        fun fromExtension(ext: String?): ContainerFormat {
            val cleanExt = ext?.trimStart('.')?.lowercase() ?: return UNKNOWN
            return values().firstOrNull { it.extension.equals(cleanExt, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

enum class DecoderType {
    NATIVE_HARDWARE,
    NATIVE_SOFTWARE,
    FFMPEG_SOFTWARE,
    UNSUPPORTED
}

data class MediaFormatInfo(
    val uriString: String,
    val containerFormat: ContainerFormat = ContainerFormat.UNKNOWN,
    val videoCodec: VideoCodec = VideoCodec.UNKNOWN,
    val audioCodec: AudioCodec = AudioCodec.UNKNOWN,
    val width: Int = 0,
    val height: Int = 0,
    val durationUs: Long = 0L,
    val bitrate: Long = 0L,
    val frameRate: Float = 0f,
    val rotationDegrees: Int = 0,
    val channelCount: Int = 0,
    val sampleRate: Int = 0,
    val isDrmProtected: Boolean = false,
    val chosenDecoder: DecoderType = DecoderType.NATIVE_HARDWARE
) {
    val durationMs: Long get() = durationUs / 1000L
    val hasVideo: Boolean get() = width > 0 && height > 0
    val hasAudio: Boolean get() = channelCount > 0 && sampleRate > 0
}
