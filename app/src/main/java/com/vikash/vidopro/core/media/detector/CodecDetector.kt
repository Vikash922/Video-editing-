package com.vikash.vidopro.core.media.detector

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import com.vikash.vidopro.core.media.api.AudioCodec
import com.vikash.vidopro.core.media.api.DecoderType
import com.vikash.vidopro.core.media.api.VideoCodec

object CodecDetector {

    /**
     * Determines whether Android has native hardware or software decoder for the given video MIME.
     */
    fun findVideoDecoder(mimeType: String, width: Int = 0, height: Int = 0): DecoderType {
        if (mimeType.isEmpty()) return DecoderType.UNSUPPORTED

        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        var foundSoftware = false

        for (info in codecList.codecInfos) {
            if (info.isEncoder) continue
            val types = info.supportedTypes
            for (type in types) {
                if (type.equals(mimeType, ignoreCase = true)) {
                    val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        info.isHardwareAccelerated
                    } else {
                        !info.name.startsWith("omx.google.", ignoreCase = true) &&
                        !info.name.startsWith("c2.android.", ignoreCase = true)
                    }

                    if (isHw) {
                        // Check resolution capabilities if provided
                        if (width > 0 && height > 0) {
                            try {
                                val caps = info.getCapabilitiesForType(type)
                                val videoCaps = caps.videoCapabilities
                                if (videoCaps != null && videoCaps.isSizeSupported(width, height)) {
                                    return DecoderType.NATIVE_HARDWARE
                                }
                            } catch (_: Exception) {}
                        } else {
                            return DecoderType.NATIVE_HARDWARE
                        }
                    } else {
                        foundSoftware = true
                    }
                }
            }
        }

        return if (foundSoftware) DecoderType.NATIVE_SOFTWARE else DecoderType.FFMPEG_SOFTWARE
    }

    /**
     * Determines whether Android has a native audio decoder for the given MIME.
     */
    fun hasNativeAudioDecoder(mimeType: String): Boolean {
        if (mimeType.isEmpty()) return false
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (info.isEncoder) continue
            for (type in info.supportedTypes) {
                if (type.equals(mimeType, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }
}
