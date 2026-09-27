package com.vikash.vidopro.core.media.detector

import android.content.Context
import android.net.Uri
import com.vikash.vidopro.core.media.api.ContainerFormat
import java.io.InputStream

object ContainerDetector {

    /**
     * Inspects magic bytes from the stream to accurately identify the container.
     */
    fun detect(context: Context, uri: Uri): ContainerFormat {
        // Try sniffing magic bytes first
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val detected = sniffMagicBytes(stream)
                if (detected != ContainerFormat.UNKNOWN) {
                    return detected
                }
            }
        } catch (_: Exception) {}

        // Fallback to extension check
        val path = uri.path ?: uri.toString()
        val extension = path.substringAfterLast('.', "")
        return ContainerFormat.fromExtension(extension)
    }

    private fun sniffMagicBytes(stream: InputStream): ContainerFormat {
        val header = ByteArray(64)
        val read = stream.read(header)
        if (read < 12) return ContainerFormat.UNKNOWN

        // ISO Base Media File Format (MP4 / MOV / M4V / 3GP)
        // Check for 'ftyp' at offset 4..7
        if (header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()) {
            val brand = String(header, 8, 4, Charsets.US_ASCII)
            return when {
                brand.startsWith("qt") -> ContainerFormat.MOV
                brand.startsWith("3g") -> ContainerFormat.THREE_GP
                brand.startsWith("M4V") -> ContainerFormat.M4V
                else -> ContainerFormat.MP4
            }
        }

        // Matroska / WebM: EBML header 0x1A 0x45 0xDF 0xA3
        if (header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()) {
            val content = String(header, 0, read, Charsets.ISO_8859_1)
            return if (content.contains("webm")) ContainerFormat.WEBM else ContainerFormat.MKV
        }

        // RIFF Container: 'RIFF' at 0..3
        if (header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte()) {
            val riffType = String(header, 8, 4, Charsets.US_ASCII)
            return when {
                riffType.startsWith("AVI") -> ContainerFormat.AVI
                riffType.startsWith("WAVE") -> ContainerFormat.WAV
                else -> ContainerFormat.UNKNOWN
            }
        }

        // Ogg container: 'OggS'
        if (header[0] == 'O'.code.toByte() && header[1] == 'g'.code.toByte() &&
            header[2] == 'g'.code.toByte() && header[3] == 'S'.code.toByte()) {
            return ContainerFormat.OGG
        }

        // Flash Video: 'FLV'
        if (header[0] == 'F'.code.toByte() && header[1] == 'L'.code.toByte() && header[2] == 'V'.code.toByte()) {
            return ContainerFormat.FLV
        }

        // MPEG Transport Stream: 0x47 sync byte
        if (header[0] == 0x47.toByte()) {
            return ContainerFormat.TS
        }

        return ContainerFormat.UNKNOWN
    }
}
