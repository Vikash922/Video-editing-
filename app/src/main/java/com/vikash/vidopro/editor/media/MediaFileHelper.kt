package com.vikash.vidopro.editor.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

object MediaFileHelper {
    private const val TAG = "MediaFileHelper"

    fun formatDuration(milliseconds: Int): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun isImageUri(context: Context, uri: Uri): Boolean {
        val path = uri.path?.lowercase() ?: ""
        if (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
            path.endsWith(".webp") || path.endsWith(".heic") || path.endsWith(".heif") || path.endsWith(".avif")) {
            return true
        }
        val mimeType = context.contentResolver.getType(uri)?.lowercase()
        return mimeType != null && mimeType.startsWith("image/")
    }

    fun getExtensionFromUri(context: Context, uri: Uri): String? {
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType != null) {
            when (mimeType.lowercase()) {
                "image/heic", "image/heif" -> return ".heic"
                "image/avif" -> return ".avif"
                "image/webp" -> return ".webp"
                "image/gif"  -> return ".gif"
                "image/png"  -> return ".png"
                "image/jpeg", "image/jpg" -> return ".jpg"
                "video/mp4"  -> return ".mp4"
                "video/quicktime" -> return ".mov"
                "video/x-matroska" -> return ".mkv"
                "video/3gpp" -> return ".3gp"
            }
            val mime = MimeTypeMap.getSingleton()
            val ext = mime.getExtensionFromMimeType(mimeType)
            if (ext != null) {
                return ".$ext"
            }
        }
        val path = uri.path ?: return null
        val lastDot = path.lastIndexOf('.')
        if (lastDot != -1 && lastDot < path.length - 1) {
            val ext = path.substring(lastDot)
            if (ext.length in 2..5 && ext.all { it == '.' || it.isLetterOrDigit() }) {
                return ext
            }
        }
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0) ?: return@use
                    val dot = name.lastIndexOf('.')
                    if (dot != -1 && dot < name.length - 1) return name.substring(dot)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun getOverlayFileDurationMs(uri: Uri): Long? {
        val path = uri.path ?: return null
        val isGif = path.endsWith(".gif", ignoreCase = true)
        val isVideo = path.endsWith(".mp4", ignoreCase = true) ||
                      path.endsWith(".mkv", ignoreCase = true) ||
                      path.endsWith(".mov", ignoreCase = true) ||
                      path.endsWith(".3gp", ignoreCase = true)
        if (isGif) {
            try {
                val movie = android.graphics.Movie.decodeFile(path)
                if (movie != null && movie.duration() > 0) {
                    return movie.duration().toLong()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading GIF duration: ${e.message}", e)
            }
        } else if (isVideo) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(path)
                val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull()
                retriever.release()
                if (durationMs != null && durationMs > 0) {
                    return durationMs
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading video duration: ${e.message}", e)
            }
        }
        return null
    }

    fun getAudioExtension(context: Context, uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType != null) {
            val mime = MimeTypeMap.getSingleton()
            val extension = mime.getExtensionFromMimeType(mimeType)
            if (extension != null) {
                return ".$extension"
            }
        }
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        val lastDot = name.lastIndexOf('.')
                        if (lastDot != -1) {
                            return name.substring(lastDot)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return ".mp3"
    }

    fun copyContentUriToTempFile(context: Context, contentUri: Uri, prefix: String = "merge_video", extension: String = ".mp4"): File? {
        return try {
            val ext = if (prefix == "audio") {
                getAudioExtension(context, contentUri)
            } else {
                extension
            }
            val tempFile = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}$ext")
            val inputStream = if (contentUri.scheme == "file") {
                val filePath = contentUri.path
                if (filePath != null) FileInputStream(filePath) else null
            } else {
                context.contentResolver.openInputStream(contentUri)
            }
            if (inputStream == null) {
                Log.e(TAG, "openInputStream returned null for URI: $contentUri")
                return null
            }
            inputStream.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (tempFile.length() == 0L) {
                Log.e(TAG, "Copied file is empty for URI: $contentUri")
                tempFile.delete()
                return null
            }
            Log.d(TAG, "Copied content URI to temp file: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying content URI to temp file: ${e.message}", e)
            null
        }
    }

    fun processConcatList(context: Context, concatList: String): String {
        val lines = concatList.trim().split("\n")
        val processedLines = mutableListOf<String>()

        for (line in lines) {
            if (line.startsWith("file")) {
                val pathStart = line.indexOf("'") + 1
                val pathEnd = line.lastIndexOf("'")
                if (pathStart > 0 && pathEnd > pathStart) {
                    val filePath = line.substring(pathStart, pathEnd)
                    val processedPath = if (filePath.startsWith("content://")) {
                        copyContentUriToTempFile(context, Uri.parse(filePath))?.absolutePath ?: filePath
                    } else {
                        filePath
                    }
                    processedLines.add("file '$processedPath'")
                }
            } else if (line.isNotEmpty()) {
                processedLines.add(line)
            }
        }

        return processedLines.joinToString("\n").trim() + "\n"
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
        val filename = "VidoPRO_Frame_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null
        var imageUri: Uri? = null
        try {
            val sharedPreferences = context.getSharedPreferences("vidopro_prefs", Context.MODE_PRIVATE)
            val customUriString = sharedPreferences.getString("export_snapshot_directory_uri", null)

            if (customUriString != null) {
                try {
                    val treeUri = Uri.parse(customUriString)
                    val parentUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri)
                    )
                    imageUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        parentUri,
                        "image/png",
                        filename
                    )
                    fos = imageUri?.let { context.contentResolver.openOutputStream(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving snapshot to custom directory: ${e.message}, falling back to default", e)
                }
            }

            if (fos == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VidoPRO")
                    }
                    imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    fos = imageUri?.let { resolver.openOutputStream(it) }
                } else {
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val directory = File(imagesDir, "VidoPRO")
                    if (!directory.exists()) directory.mkdirs()
                    val image = File(directory, filename)
                    fos = FileOutputStream(image)
                    imageUri = Uri.fromFile(image)
                }
            }

            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                Toast.makeText(context, "Frame saved to gallery", Toast.LENGTH_SHORT).show()
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && imageUri != null) {
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, imageUri))
            }
            return imageUri
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to save frame", Toast.LENGTH_SHORT).show()
            return null
        }
    }

    fun saveVideoToGallery(context: Context, videoFile: File): Uri? {
        val isAudioOnly = videoFile.name.endsWith(".mp3")
        val mimeType = if (isAudioOnly) "audio/mpeg" else "video/mp4"
        val ext = if (isAudioOnly) ".mp3" else ".mp4"
        val prefix = if (isAudioOnly) "VidoPRO_Audio_" else "VidoPRO_"

        val sharedPreferences = context.getSharedPreferences("vidopro_prefs", Context.MODE_PRIVATE)
        val prefKey = if (isAudioOnly) "export_audio_directory_uri" else "export_directory_uri"
        val customUriString = sharedPreferences.getString(prefKey, null)
        if (customUriString != null) {
            try {
                val treeUri = Uri.parse(customUriString)
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
                val newFileUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentUri,
                    mimeType,
                    "${prefix}${System.currentTimeMillis()}$ext"
                )
                if (newFileUri != null) {
                    context.contentResolver.openOutputStream(newFileUri)?.use { output ->
                        videoFile.inputStream().use { input -> input.copyTo(output) }
                    }
                    return newFileUri
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving to custom directory: ${e.message}, falling back to default", e)
            }
        }

        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${prefix}${System.currentTimeMillis()}$ext")
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (isAudioOnly) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/VidoPRO")
                } else {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VidoPRO")
                }
            }
            val collectionUri = if (isAudioOnly) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val uri = context.contentResolver.insert(collectionUri, contentValues)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { output ->
                    videoFile.inputStream().use { input -> input.copyTo(output) }
                }
                it
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to default gallery: ${e.message}", e)
            null
        }
    }

    /**
     * Probes media capabilities and returns format information using the unified core media pipeline.
     */
    suspend fun probeMediaCapabilities(context: Context, uri: Uri): com.vikash.vidopro.core.media.api.MediaFormatInfo {
        return com.vikash.vidopro.core.media.detector.MediaFileDetector.probe(context, uri)
    }

    /**
     * Resolves the optimal decoder (Native MediaCodec or FFmpeg fallback).
     */
    suspend fun getDecoder(context: Context, uri: Uri): Pair<com.vikash.vidopro.core.media.api.MediaDecoder, com.vikash.vidopro.core.media.api.MediaFormatInfo> {
        return com.vikash.vidopro.core.media.detector.DecoderResolver.resolveDecoder(context, uri)
    }
}
