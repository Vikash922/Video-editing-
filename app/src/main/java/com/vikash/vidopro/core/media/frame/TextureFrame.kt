package com.vikash.vidopro.core.media.frame

/**
 * Hardware texture wrapper for OpenGL/Surface rendering pipelines.
 */
data class TextureFrame(
    val textureId: Int,
    val width: Int,
    val height: Int,
    val timestampUs: Long,
    val transformMatrix: FloatArray = FloatArray(16)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TextureFrame
        if (textureId != other.textureId) return false
        if (timestampUs != other.timestampUs) return false
        return transformMatrix.contentEquals(other.transformMatrix)
    }

    override fun hashCode(): Int {
        var result = textureId
        result = 31 * result + timestampUs.hashCode()
        result = 31 * result + transformMatrix.contentHashCode()
        return result
    }
}
