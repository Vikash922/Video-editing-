package com.vikash.vidopro.editor.export

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.vikash.vidopro.R
import com.vikash.vidopro.editor.media.MediaFileHelper
import com.vikash.vidopro.services.ExportService
import com.vikash.vidopro.services.FFmpegRenderEngine
import com.vikash.vidopro.utils.ErrorCode
import com.vikash.vidopro.utils.setBounceClickListener
import com.vikash.vidopro.viewmodels.VideoEditingViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ExportController(
    private val activity: ComponentActivity,
    private val viewModel: VideoEditingViewModel,
    private val ffmpegEngine: FFmpegRenderEngine,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun getFontFilePath(): String?
        fun getTempInputFilePath(): String?
        fun getVideoUri(): Uri?
        fun getTotalSequenceDurationMs(): Long
        fun commitActiveEditsIfAny()
        fun isShowingPreview(): Boolean
        fun dismissPreview()
        fun showError(message: String)
        fun showProErrorDialog(errorCode: ErrorCode, technicalLog: String)
        fun onSaveProjectRequested()
    }

    companion object {
        private const val TAG = "ExportController"
        const val PICK_DIRECTORY_REQUEST = 5
    }

    private var exportJob: Job? = null
    private var activeDirectoryTitleView: TextView? = null
    private var activeDirectoryPathView: TextView? = null

    private val exportReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ExportService.ACTION_EXPORT_PROGRESS -> {
                    val progress = intent.getIntExtra(ExportService.EXTRA_PROGRESS, 0)
                    viewModel.updateExportProgress(progress)
                }
                ExportService.ACTION_EXPORT_SUCCESS -> {
                    val uri = intent.getStringExtra(ExportService.EXTRA_SAVED_URI)
                    viewModel.finishExport()
                    Toast.makeText(activity, R.string.toast_video_exported_to_gallery_succ, Toast.LENGTH_LONG).show()
                }
                ExportService.ACTION_EXPORT_FAILURE -> {
                    val error = intent.getStringExtra(ExportService.EXTRA_ERROR) ?: "Unknown Error"
                    viewModel.exportError(error)
                    callbacks.showProErrorDialog(ErrorCode.FFMPEG_EXECUTION_FAILED, error)
                }
            }
        }
    }

    fun register() {
        LocalBroadcastManager.getInstance(activity).registerReceiver(
            exportReceiver,
            IntentFilter().apply {
                addAction(ExportService.ACTION_EXPORT_PROGRESS)
                addAction(ExportService.ACTION_EXPORT_SUCCESS)
                addAction(ExportService.ACTION_EXPORT_FAILURE)
            }
        )
    }

    fun unregister() {
        LocalBroadcastManager.getInstance(activity).unregisterReceiver(exportReceiver)
    }

    fun handleDirectoryPickerResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            activity.contentResolver.takePersistableUriPermission(uri, takeFlags)

            val sharedPreferences = activity.getSharedPreferences("vidopro_prefs", Context.MODE_PRIVATE)
            val isAudioOnly = viewModel.exportAudioOnly.value
            val prefKey = if (isAudioOnly) "export_audio_directory_uri" else "export_directory_uri"
            sharedPreferences.edit().putString(prefKey, uri.toString()).apply()

            val activeTitle = activeDirectoryTitleView
            val activePath = activeDirectoryPathView
            if (activeTitle != null && activePath != null) {
                updateExportDirectoryUi(activeTitle, activePath)
            }
            Toast.makeText(activity, R.string.toast_export_location_updated_succes, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving export directory: ${e.message}", e)
            Toast.makeText(activity, R.string.toast_failed_to_select_folder, Toast.LENGTH_SHORT).show()
        }
    }

    fun startSaveAction() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
                Toast.makeText(activity, "Please grant notification permission and try exporting again.", Toast.LENGTH_LONG).show()
                return
            }
        }

        callbacks.commitActiveEditsIfAny()
        if (callbacks.isShowingPreview()) callbacks.dismissPreview()

        val project = viewModel.project.value
        if (project == null) {
            callbacks.showError("No project loaded")
            return
        }

        if (!project.hasOperations()) {
            val targetUri = callbacks.getVideoUri()
            if (targetUri != null) {
                exportVideoFile(targetUri)
            } else {
                val tempPath = callbacks.getTempInputFilePath()
                if (tempPath != null) {
                    exportVideoFile(Uri.fromFile(File(tempPath)))
                } else {
                    callbacks.showError("No video file to export")
                }
            }
            return
        }

        val fontFilePath = callbacks.getFontFilePath()
        if (fontFilePath == null) {
            Log.w(TAG, "fontFilePath is null — text overlays will be skipped. " +
                    "Check that assets/fonts/Roboto-Regular.ttf exists.")
        }

        activity.lifecycleScope.launch(Dispatchers.Main) {
            var tempOutputFile: File? = null
            var concatFile: File? = null
            try {
                viewModel.startExport()

                val sourceFilePath = callbacks.getTempInputFilePath() ?: ""
                val isAudioOnly = viewModel.exportAudioOnly.value
                val ext = if (isAudioOnly) ".mp3" else ".mp4"
                tempOutputFile = File(activity.cacheDir, "temp_video_${System.currentTimeMillis()}$ext")
                val tempOutputPath = tempOutputFile.absolutePath

                var ffmpegCommand = viewModel.buildConsolidatedFFmpegCommand(
                    sourceFilePath = sourceFilePath,
                    outputFilePath = tempOutputPath,
                    fontFilePath = fontFilePath,
                    context = activity
                )

                if (ffmpegCommand == null) {
                    viewModel.exportError("Failed to build FFmpeg command")
                    return@launch
                }

                Log.d(TAG, "Raw FFmpeg command: $ffmpegCommand")

                val currentProject = viewModel.project.value
                if (currentProject != null && ffmpegCommand.contains("(CONCAT_LIST:")) {
                    val cmdSnapshot = ffmpegCommand
                    concatFile = withContext(Dispatchers.IO) {
                        val concatListStart = cmdSnapshot.indexOf("(CONCAT_LIST:") + "(CONCAT_LIST:".length
                        val concatListEnd = cmdSnapshot.lastIndexOf(")")
                        if (concatListStart > 13 && concatListEnd > concatListStart) {
                            val concatList = cmdSnapshot.substring(concatListStart, concatListEnd)
                            val processedConcatList = MediaFileHelper.processConcatList(activity, concatList)

                            val file = File(activity.cacheDir, "concat_${System.currentTimeMillis()}.txt")
                            file.writeText(processedConcatList)
                            file
                        } else null
                    }

                    if (concatFile != null) {
                        ffmpegCommand = ffmpegCommand
                            .replace("{CONCAT_FILE_PATH}", concatFile.absolutePath)
                            .substring(0, ffmpegCommand.indexOf("(CONCAT_LIST:"))
                            .trim()
                    }
                }

                Log.d(TAG, "Final FFmpeg command: $ffmpegCommand")
                val totalDurationSecs = callbacks.getTotalSequenceDurationMs() / 1000.0

                // Start ExportService
                val intent = Intent(activity, ExportService::class.java).apply {
                    putExtra(ExportService.EXTRA_COMMAND, ffmpegCommand)
                    putExtra(ExportService.EXTRA_TEMP_OUTPUT_PATH, tempOutputPath)
                    putExtra(ExportService.EXTRA_CONCAT_FILE_PATH, concatFile?.absolutePath)
                    putExtra(ExportService.EXTRA_TOTAL_DURATION_SECS, totalDurationSecs)
                    putExtra(ExportService.EXTRA_IS_AUDIO_ONLY, isAudioOnly)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activity.startForegroundService(intent)
                } else {
                    activity.startService(intent)
                }

                Toast.makeText(activity, "Export started in background...", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                tempOutputFile?.let { if (it.exists()) it.delete() }
                concatFile?.let { if (it.exists()) it.delete() }
                viewModel.exportError(e.message ?: "Unknown error")
                Log.e(TAG, "Export start exception: ${e.message}", e)
            }
        }
    }

    fun showQualitySettingsDialog() {
        callbacks.commitActiveEditsIfAny()
        if (callbacks.isShowingPreview()) callbacks.dismissPreview()
        val bottomSheetDialog = BottomSheetDialog(activity)
        val sheetView = activity.layoutInflater.inflate(R.layout.export_quality_bottom_sheet_dialog, null)

        val cgResolution = sheetView.findViewById<ChipGroup>(R.id.cgResolution)
        val cgFps = sheetView.findViewById<ChipGroup>(R.id.cgFps)
        val switchAudioOnly = sheetView.findViewById<MaterialSwitch>(R.id.switchAudioOnly)
        val btnClose = sheetView.findViewById<ImageButton>(R.id.btnCloseSheet)

        // Initialize state
        val currentRes = viewModel.exportResolution.value
        val currentFps = viewModel.exportFps.value
        val isAudioOnly = viewModel.exportAudioOnly.value

        when (currentRes) {
            360 -> cgResolution.check(R.id.chipRes360)
            480 -> cgResolution.check(R.id.chipRes480)
            720 -> cgResolution.check(R.id.chipRes720)
            1080 -> cgResolution.check(R.id.chipRes1080)
            1440 -> cgResolution.check(R.id.chipRes1440)
            2160 -> cgResolution.check(R.id.chipRes2160)
            else -> cgResolution.check(R.id.chipRes1080)
        }

        when (currentFps) {
            24 -> cgFps.check(R.id.chipFps24)
            25 -> cgFps.check(R.id.chipFps25)
            30 -> cgFps.check(R.id.chipFps30)
            50 -> cgFps.check(R.id.chipFps50)
            60 -> cgFps.check(R.id.chipFps60)
            else -> cgFps.check(R.id.chipFps30)
        }

        switchAudioOnly.isChecked = isAudioOnly

        val layoutExportDirectory = sheetView.findViewById<LinearLayout>(R.id.layoutExportDirectory)
        val tvExportDirectoryTitle = sheetView.findViewById<TextView>(R.id.tvExportDirectoryTitle)
        val tvExportDirectoryPath = sheetView.findViewById<TextView>(R.id.tvExportDirectoryPath)

        activeDirectoryTitleView = tvExportDirectoryTitle
        activeDirectoryPathView = tvExportDirectoryPath
        updateExportDirectoryUi(tvExportDirectoryTitle, tvExportDirectoryPath)

        fun saveSettings() {
            val res = when (cgResolution.checkedChipId) {
                R.id.chipRes360 -> 360
                R.id.chipRes480 -> 480
                R.id.chipRes720 -> 720
                R.id.chipRes1080 -> 1080
                R.id.chipRes1440 -> 1440
                R.id.chipRes2160 -> 2160
                else -> 1080
            }
            val fps = when (cgFps.checkedChipId) {
                R.id.chipFps24 -> 24
                R.id.chipFps25 -> 25
                R.id.chipFps30 -> 30
                R.id.chipFps50 -> 50
                R.id.chipFps60 -> 60
                else -> 30
            }
            viewModel.setExportSettings(res, fps, switchAudioOnly.isChecked)
            updateExportDirectoryUi(tvExportDirectoryTitle, tvExportDirectoryPath)
        }

        cgResolution.setOnCheckedStateChangeListener { _, _ -> saveSettings() }
        cgFps.setOnCheckedStateChangeListener { _, _ -> saveSettings() }
        switchAudioOnly.setOnCheckedChangeListener { _, _ -> saveSettings() }

        btnClose.setBounceClickListener {
            bottomSheetDialog.dismiss()
        }

        layoutExportDirectory.setBounceClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            try {
                activity.startActivityForResult(intent, PICK_DIRECTORY_REQUEST)
            } catch (e: android.content.ActivityNotFoundException) {
                Toast.makeText(activity, R.string.toast_failed_to_select_folder, Toast.LENGTH_SHORT).show()
            }
        }

        bottomSheetDialog.setOnDismissListener {
            activeDirectoryTitleView = null
            activeDirectoryPathView = null
        }

        sheetView.findViewById<View>(R.id.btnSaveProject)?.setBounceClickListener {
            bottomSheetDialog.dismiss()
            callbacks.onSaveProjectRequested()
        }

        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
    }

    fun exportVideoFile(uri: Uri) {
        exportJob = CoroutineScope(Dispatchers.Main + Job()).launch {
            var outputFile: File? = null
            var customFileUri: Uri? = null
            try {
                viewModel.startExport()

                withContext(Dispatchers.IO) {
                    val sharedPreferences = activity.getSharedPreferences("vidopro_prefs", Context.MODE_PRIVATE)
                    val isAudioOnly = viewModel.exportAudioOnly.value
                    val prefKey = if (isAudioOnly) "export_audio_directory_uri" else "export_directory_uri"
                    val customUriString = sharedPreferences.getString(prefKey, null)
                    val sourcePath = callbacks.getTempInputFilePath()
                    if (sourcePath == null) {
                        throw IllegalStateException("Source file not found")
                    }
                    val sourceFile = File(sourcePath)
                    val totalSize = sourceFile.length()

                    val mimeType = if (isAudioOnly) "audio/mpeg" else "video/mp4"
                    val ext = if (isAudioOnly) ".mp3" else ".mp4"
                    val prefix = if (isAudioOnly) "VidoPRO_Audio_" else "VidoPRO_"

                    if (customUriString != null) {
                        try {
                            val treeUri = Uri.parse(customUriString)
                            val parentUri = DocumentsContract.buildDocumentUriUsingTree(
                                treeUri,
                                DocumentsContract.getTreeDocumentId(treeUri)
                            )
                            customFileUri = DocumentsContract.createDocument(
                                activity.contentResolver,
                                parentUri,
                                mimeType,
                                "${prefix}${System.currentTimeMillis()}$ext"
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to create SAF file: ${e.message}, falling back to default", e)
                        }
                    }

                    val outputStream = if (customFileUri != null) {
                        activity.contentResolver.openOutputStream(customFileUri!!)
                    } else {
                        // Default fallback
                        val defaultDir = if (isAudioOnly) Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) else Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val outputDir = File(defaultDir, "VidoPRO")
                        if (!outputDir.exists()) outputDir.mkdirs()
                        val file = File(outputDir, "${prefix}${System.currentTimeMillis()}$ext")
                        outputFile = file
                        FileOutputStream(file)
                    }

                    val input = FileInputStream(sourceFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    var lastProgressUpdate = System.currentTimeMillis()

                    input.use { i ->
                        outputStream?.use { o ->
                            while (i.read(buffer).also { bytesRead = it } >= 0) {
                                if (!isActive) {
                                    throw CancellationException("Export cancelled")
                                }
                                o.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead

                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 100) { // Update UI at most every 100ms
                                    val progress = ((totalBytesRead.toFloat() / totalSize) * 100).toInt()
                                    withContext(Dispatchers.Main) {
                                        viewModel.updateExportProgress(progress)
                                    }
                                    lastProgressUpdate = now
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        viewModel.updateExportProgress(100)
                        viewModel.finishExport()
                        val displayPath = if (customFileUri != null) {
                            "Custom Folder"
                        } else {
                            outputFile?.absolutePath ?: "Downloads/VidoPRO"
                        }
                        Toast.makeText(
                            activity,
                            "Video exported: $displayPath",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: CancellationException) {
                outputFile?.let { if (it.exists()) it.delete() }
                customFileUri?.let {
                    try {
                        DocumentsContract.deleteDocument(activity.contentResolver, it)
                    } catch (ex: Exception) {
                        Log.w(TAG, "Failed to delete cancelled custom file: ${ex.message}")
                    }
                }
                withContext(Dispatchers.Main) {
                    viewModel.exportError("Export cancelled")
                    Toast.makeText(activity, R.string.toast_export_cancelled, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                outputFile?.let { if (it.exists()) it.delete() }
                customFileUri?.let {
                    try {
                        DocumentsContract.deleteDocument(activity.contentResolver, it)
                    } catch (ex: Exception) {
                        Log.w(TAG, "Failed to delete failed custom file: ${ex.message}")
                    }
                }
                withContext(Dispatchers.Main) {
                    viewModel.exportError(e.message ?: "Export failed")
                }
            }
        }
    }

    fun cancelExport() {
        activity.lifecycleScope.launch {
            try {
                ffmpegEngine.cancelAllSessions()
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling FFmpeg sessions: ${e.message}")
            }
        }
        exportJob?.cancel()
        viewModel.exportError("Export cancelled")
        Toast.makeText(activity, R.string.toast_export_cancelled, Toast.LENGTH_SHORT).show()
    }

    fun updateExportDirectoryUi(titleView: TextView, pathView: TextView) {
        val sharedPreferences = activity.getSharedPreferences("vidopro_prefs", Context.MODE_PRIVATE)
        val isAudioOnly = viewModel.exportAudioOnly.value
        val prefKey = if (isAudioOnly) "export_audio_directory_uri" else "export_directory_uri"
        val customUriString = sharedPreferences.getString(prefKey, null)

        if (customUriString != null) {
            val customUri = Uri.parse(customUriString)
            val displayName = getDocumentFolderName(customUri) ?: "Custom Folder"
            titleView.text = displayName
            pathView.text = customUri.path ?: customUriString
        } else {
            titleView.text = "VidoPRO (Default)"
            pathView.text = if (isAudioOnly) "Music/VidoPRO" else "Movies/VidoPRO"
        }
    }

    fun getDocumentFolderName(uri: Uri): String? {
        return try {
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri)
            )
            activity.contentResolver.query(documentUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
