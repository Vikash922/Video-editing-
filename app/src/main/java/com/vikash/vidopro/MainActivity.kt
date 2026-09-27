package com.vikash.vidopro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.vikash.vidopro.ui.home.HomeScreen
import com.vikash.vidopro.ui.home.ProjectItem
import com.vikash.vidopro.ui.theme.EditProTheme

class MainActivity : AppCompatActivity() {

    private val captureVideoLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val videoUri: Uri? = result.data?.data
                if (videoUri != null) {
                    navigateToEditingScreen(videoUri)
                }
            }
        }

    private val selectVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    Log.e("VideoSelection", "Could not take persistable permission", e)
                }
                Log.d("VideoSelection", "Video selected: $uri")
                navigateToEditingScreen(uri)
            } else {
                Log.e("VideoSelectionError", "No video selected")
            }
        }

    private val openProjectLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    Log.e("ProjectSelection", "Could not take persistable permission for project URI", e)
                }
                Log.d("ProjectSelection", "Project selected: $uri")
                val intent = Intent(this, ProjectImportActivity::class.java).apply {
                    putExtra("PROJECT_URI", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                startActivity(intent)
            } else {
                Log.e("ProjectSelectionError", "No project selected")
            }
        }

    private val selectFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)

                    val prefs = getSharedPreferences("vidopro_prefs", MODE_PRIVATE)
                    prefs.edit().putString("export_directory_uri", uri.toString()).apply()
                    showToast("Export directory updated")
                } catch (e: Exception) {
                    Log.e("FolderSelectionError", "Error securing permission for URI", e)
                    showToast("Failed to set export folder")
                }
            }
        }

    private val selectAudioFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)

                    val prefs = getSharedPreferences("vidopro_prefs", MODE_PRIVATE)
                    prefs.edit().putString("export_audio_directory_uri", uri.toString()).apply()
                    showToast("Audio directory updated")
                } catch (e: Exception) {
                    Log.e("FolderSelectionError", "Error securing permission for URI", e)
                    showToast("Failed to set audio export folder")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            EditProTheme(darkTheme = true) {
                HomeScreen(
                    onNewProjectClick = { selectVideo() },
                    onOpenProjectFileClick = { openProjectLauncher.launch(arrayOf("*/*")) },
                    onProjectSelected = { project -> handleProjectSelected(project) },
                    onCameraClick = { launchCamera() },
                    onSelectExportFolder = { selectFolderLauncher.launch(null) },
                    onSelectAudioFolder = { selectAudioFolderLauncher.launch(null) }
                )
            }
        }

        // Handle shared/intent videos
        handleIntent(intent)
    }

    private fun handleProjectSelected(project: ProjectItem) {
        if (!project.videoUriString.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(project.videoUriString)
                navigateToEditingScreen(uri)
                return
            } catch (e: Exception) {
                Log.e("ProjectSelect", "Invalid project URI", e)
            }
        }
        // If sample/mock project or no direct URI, launch video picker
        selectVideo()
    }

    private fun launchCamera() {
        try {
            val intent = Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE)
            captureVideoLauncher.launch(intent)
        } catch (e: Exception) {
            selectVideo()
        }
    }

    private fun selectVideo() {
        Log.d("VideoSelection", "Launching video picker.")
        val picker = com.vikash.vidopro.customviews.MediaPickerBottomSheet().apply {
            initialMediaType = com.vikash.vidopro.customviews.MediaPickerBottomSheet.MediaType.VIDEO
            showCategoryTabs = true
            showAudioTab = false
            onMediaSelectedListener = { uri ->
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    Log.d("VideoSelection", "Could not take persistable permission: ${e.message}")
                }
                navigateToEditingScreen(uri)
            }
            onBrowseSystemFoldersRequested = {
                selectVideoLauncher.launch(arrayOf("video/*", "image/*"))
            }
        }
        picker.show(supportFragmentManager, "MediaPickerBottomSheet")
    }

    private fun navigateToEditingScreen(videoUri: Uri) {
        Log.d("Navigation", "Navigating to editing screen with URI: $videoUri")
        val intent = Intent(this, VideoEditingActivity::class.java).apply {
            putExtra("VIDEO_URI", videoUri)
            data = videoUri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if (type.startsWith("video/") || type.startsWith("image/")) {
                (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
                    Log.d("SharedVideo", "Received SEND intent with media URI: $uri")
                    navigateToEditingScreen(uri)
                }
            }
        } else if ((Intent.ACTION_VIEW == action || Intent.ACTION_EDIT == action) && type != null) {
            if (type.startsWith("video/") || type.startsWith("image/")) {
                intent.data?.let { uri ->
                    Log.d("SharedVideo", "Received VIEW/EDIT intent with media URI: $uri")
                    navigateToEditingScreen(uri)
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
