package com.vikash.vidopro

import android.widget.ImageView
import com.vikash.vidopro.editor.media.MediaFileHelper
import kotlinx.coroutines.isActive
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.vikash.vidopro.utils.setBounceClickListener
import com.vikash.vidopro.utils.performHapticLight
import com.vikash.vidopro.utils.performHapticClick
import com.vikash.vidopro.utils.performAppHapticFeedback
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.vikash.vidopro.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.RangeSlider
import com.google.android.material.textfield.TextInputEditText
import android.graphics.BitmapFactory
import android.graphics.Color
import android.widget.SeekBar
import com.vikash.vidopro.customviews.HandwritingCanvasView
import com.vikash.vidopro.customviews.BrushSizeDotView
import com.google.android.material.slider.Slider
import com.vikash.vidopro.customviews.CustomVideoSeeker
import com.vikash.vidopro.customviews.DraggableTextOverlayView
import com.vikash.vidopro.customviews.DraggableImageOverlayView
import com.vikash.vidopro.customviews.ImageOverlayView
import com.vikash.vidopro.models.EditOperation
import com.vikash.vidopro.models.VideoProject
import com.vikash.vidopro.models.TextPosition
import com.vikash.vidopro.models.id
import kotlinx.coroutines.Job
import com.vikash.vidopro.services.FFmpegRenderEngine
import com.vikash.vidopro.utils.ErrorCode

import com.vikash.vidopro.viewmodels.*
import com.vikash.vidopro.viewmodels.VideoEditingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout


@Suppress("DEPRECATION")
class VideoEditingActivity : AppCompatActivity() {

    // UI Components
    private lateinit var player: ExoPlayer
    private lateinit var playerView: StyledPlayerView
    private lateinit var tvDuration: TextView
    private lateinit var sequenceTrackContainer: FrameLayout
    private lateinit var textTrackContainer: LinearLayout
    private lateinit var imageTrackContainer: LinearLayout
    private lateinit var playerContainer: FrameLayout
    private lateinit var audioTrackContainer: LinearLayout
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnMoveLayerUp: ImageButton
    private lateinit var btnMoveLayerDown: ImageButton

    private lateinit var canvasContainer: FrameLayout
    private lateinit var btnFullscreen: ImageButton
    private lateinit var btnExpandTimeline: ImageButton
    private lateinit var pipPlayerContainer: FrameLayout
    private lateinit var fullscreenOverlay: FrameLayout
    private lateinit var fullscreenCanvasHolder: FrameLayout
    private lateinit var fullscreenControls: android.widget.RelativeLayout
    private lateinit var btnFullscreenPlayPause: ImageButton
    private lateinit var btnExitFullscreen: ImageButton
    private lateinit var btnExitFullscreenTop: ImageButton
    private lateinit var sbFullscreenSeeker: SeekBar
    private lateinit var tvFullscreenCurrentTime: TextView
    private lateinit var tvFullscreenTotalTime: TextView

    private var isFullscreenMode = false
    private var isTimelineExpanded = false
    private var isUserScrubbingFullscreenSeeker = false
    private val fullscreenHideHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideFullscreenControlsRunnable = Runnable {
        if (::fullscreenControls.isInitialized && isFullscreenMode) {
            fullscreenControls.animate().alpha(0f).setDuration(300).withEndAction {
                fullscreenControls.visibility = View.GONE
            }.start()
        }
    }

    private lateinit var timelineHorizontalScroll: android.widget.HorizontalScrollView
    private lateinit var timelineVerticalScroll: android.widget.ScrollView
    private lateinit var btnTimelineAdd: View
    private lateinit var btnTimelineMute: View
    private var btnKeyframeDiamond: ImageButton? = null
    private lateinit var timelineContainer: FrameLayout
    private var isUserScrollingTimeline = false
    private var isTrackDragging = false
    private var isProgrammaticScroll = false
    private var pixelsPerMs: Float = 0.3f
    private var lastSnappedTargetMs: Long = -1L
    private enum class ZoomMode { FIT, MEDIUM, PRECISION }
    private lateinit var scaleDetector: android.view.ScaleGestureDetector
    private var videoPinchScale: Float = 1.0f
    private lateinit var videoScaleDetector: android.view.ScaleGestureDetector

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    private lateinit var customVideoSeeker: CustomVideoSeeker
    private lateinit var timeRulerView: com.vikash.vidopro.customviews.TimeRulerView
    private lateinit var loadingScreen: View
    private lateinit var exportScreen: View
    private lateinit var previewLoadingOverlay: View
    private lateinit var lottieAnimationView: LottieAnimationView
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnPreview: ImageButton
    private lateinit var layoutSaveSplit: View
    private lateinit var btnSaveText: View
    private lateinit var btnSaveDropdown: View
    private lateinit var editingControlsWrapper: LinearLayout
    private lateinit var emptyProjectState: View

    private var tvPreviewBadge: TextView? = null
    private var textOverlayView: com.vikash.vidopro.customviews.TextOverlayView? = null
    private var transitionPreviewOverlayView: com.vikash.vidopro.customviews.TransitionPreviewOverlayView? = null
    private var activeTransitionIndex: Int = -1
    private var cachedTransitionBitmap: android.graphics.Bitmap? = null

    // ViewModel, Services and Controllers
    private lateinit var viewModel: VideoEditingViewModel
    private lateinit var ffmpegEngine: FFmpegRenderEngine
    private lateinit var exportController: com.vikash.vidopro.editor.export.ExportController

    // State
    private var videoUri: Uri? = null
    private var videoFileName: String = ""
    private lateinit var tempInputFile: File
    private var frameExtractionJob: Job? = null
    private val activeRenderJobs = mutableListOf<Job>()
    private var pendingRenderRunnable: Runnable? = null
    private var isVideoLoaded = false
    private var isImportLoading = true
    private var activeExtractionCount = 0
    private var chunkDurationsMs = listOf<Long>()
    private var originalMainVideoDurationMs: Long = 0L
    private var primaryVideoAspectRatio: Float = 0f
    
    private var bgPreviewImageView: ImageView? = null
    private var lastActiveClipIndexForBlur: Int = 0
    private var currentBlurJob: Job? = null
    
    private val activeExtractionUris = mutableSetOf<Uri>()
    private val frameExtractionSemaphore = kotlinx.coroutines.sync.Semaphore(2)
    private val frameCache = object : android.util.LruCache<Uri, List<Bitmap>>(15) {
        override fun entryRemoved(evicted: Boolean, key: Uri?, oldValue: List<Bitmap>?, newValue: List<Bitmap>?) {
            super.entryRemoved(evicted, key, oldValue, newValue)
            if (evicted && oldValue != null) {
                for (bmp in oldValue) {
                    if (!bmp.isRecycled) bmp.recycle()
                }
            }
        }
    }

    private fun clearFrameCache() {
        val snapshot = frameCache.snapshot()
        frameCache.evictAll()
        for (list in snapshot.values) {
            for (bmp in list) {
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
    }
    
    private val proxyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == com.vikash.vidopro.services.ProxyGenerationService.ACTION_PROXY_GENERATED) {
                val proxyUriStr = intent.getStringExtra(com.vikash.vidopro.services.ProxyGenerationService.EXTRA_PROXY_URI)
                val dependencyId = intent.getStringExtra(com.vikash.vidopro.services.ProxyGenerationService.EXTRA_DEPENDENCY_ID)
                if (proxyUriStr != null && dependencyId != null) {
                    val proxyUri = Uri.parse(proxyUriStr)
                    viewModel.setScrubProxyUri(dependencyId, proxyUri)
                    Log.d(TAG, "Scrub proxy set for $dependencyId")
                }
            }
        }
    }

    // Drag-to-rearrange clips state
    private var isDraggingSegment = false
    private var draggedIndex = -1
    private var draggedView: View? = null
    private var initialTouchX = 0f
    private var initialScrollX = 0
    private var segmentViews = mutableListOf<View>()
    private var originalLefts = mutableListOf<Int>()
    private var currentDragOrder = listOf<Int>()
    private var activeSegmentViews = mutableListOf<View>()


    // ── FONT: cached absolute path to Roboto-Regular.ttf in cacheDir ──────────
    // Populated in onCreate via ffmpegEngine.copyFontToCache().
    // Passed to buildConsolidatedFFmpegCommand() so drawtext can find the font.
    private var fontFilePath: String? = null

    private fun setupTextFontSelector(formatContainer: View?) {
        val container = formatContainer ?: return
        val btnSelectFont = container.findViewById<View>(R.id.btnSelectFont) ?: return
        val tvSelectedFontName = container.findViewById<TextView>(R.id.tvSelectedFontName)

        val allFonts = com.vikash.vidopro.utils.FontManager.getAllFonts(this, fontFilePath)
        val currentPath = draggableTextOverlay?.currentFontPath ?: fontFilePath
        val activeFont = allFonts.find { it.path == currentPath } ?: allFonts.firstOrNull()
        if (activeFont != null && tvSelectedFontName != null) {
            tvSelectedFontName.text = activeFont.name
        }

        btnSelectFont.setBounceClickListener {
            showFontManagerBottomSheet { selectedFont ->
                tvSelectedFontName?.text = selectedFont.name
                draggableTextOverlay?.setFontPath(selectedFont.path)
            }
        }
    }

    private fun setupSubtitleFontSelector(subtitlesToolbar: View?) {
        val toolbar = subtitlesToolbar ?: return
        val btnSelectSubtitleFont = toolbar.findViewById<View>(R.id.btnSelectSubtitleFont) ?: return
        val tvSelectedSubtitleFontName = toolbar.findViewById<TextView>(R.id.tvSelectedSubtitleFontName)

        val allFonts = com.vikash.vidopro.utils.FontManager.getAllFonts(this, fontFilePath)
        val project = viewModel.project.value
        val subOp = project?.operations?.find { it is EditOperation.AddSubtitles } as? EditOperation.AddSubtitles
        val currentPath = subOp?.fontPath ?: fontFilePath
        val activeFont = allFonts.find { it.path == currentPath } ?: allFonts.firstOrNull()
        if (activeFont != null && tvSelectedSubtitleFontName != null) {
            tvSelectedSubtitleFontName.text = activeFont.name
        }

        btnSelectSubtitleFont.setBounceClickListener {
            showFontManagerBottomSheet { selectedFont ->
                tvSelectedSubtitleFontName?.text = selectedFont.name
                val currentSubOp = viewModel.project.value?.operations?.find { it is EditOperation.AddSubtitles } as? EditOperation.AddSubtitles
                if (currentSubOp != null) {
                    updateSubtitleOp(currentSubOp.copy(fontPath = selectedFont.path))
                }
            }
        }
    }

    private fun showFontManagerBottomSheet(onFontSelected: ((com.vikash.vidopro.utils.FontItem) -> Unit)? = null) {
        com.vikash.vidopro.editor.dialogs.FontSelectionDialog.show(
            context = this,
            defaultFontFilePath = fontFilePath,
            currentFontPath = draggableTextOverlay?.currentFontPath,
            onFontSelected = { fontItem ->
                onFontSelected?.invoke(fontItem)
                draggableTextOverlay?.setFontPath(fontItem.path)
                textEditingToolbar?.findViewById<View>(R.id.formatSettingsContainer)?.let { setupTextFontSelector(it) }
                setupSubtitleFontSelector(subtitlesEditingToolbar)
            },
            onImportFontRequested = {
                activeFontSelectionCallback = { importedFont ->
                    onFontSelected?.invoke(importedFont)
                    draggableTextOverlay?.setFontPath(importedFont.path)
                }
                importFontLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype", "*/*"))
            },
            onFontsChanged = {
                textEditingToolbar?.findViewById<View>(R.id.formatSettingsContainer)?.let { setupTextFontSelector(it) }
                setupSubtitleFontSelector(subtitlesEditingToolbar)
            }
        )
    }

    // Cache for frame extraction
    private val extractedFrames = mutableListOf<Bitmap>()

    // Inline text editing state
    private var draggableTextOverlay: DraggableTextOverlayView? = null
    private var textEditingToolbar: View? = null
    private var isTextEditingActive = false

    // Inline image overlay editing state
    private var draggableImageOverlay: DraggableImageOverlayView? = null
    private var imageOverlayView: ImageOverlayView? = null
    private var imageEditingToolbar: View? = null
    private var isImageEditingActive = false
    private var isMagnetEnabled = true

    // Video layer selection state
    private var selectedVideoIndex: Int? = null
    private var videoEditingToolbar: View? = null
    private var audioEditingToolbar: View? = null
    private var backgroundEditingToolbar: View? = null
    private var speedEditingToolbar: View? = null
    private var cropEditingToolbar: View? = null
    private var cropOverlayView: com.vikash.vidopro.customviews.CropOverlayView? = null
    private var videoMaskOverlayView: com.vikash.vidopro.customviews.VideoMaskOverlayView? = null
    private var mainVideoMaskContainer: com.vikash.vidopro.customviews.MaskedFrameLayout? = null
    private var initialCropOperation: com.vikash.vidopro.models.EditOperation.Crop? = null
    private var subtitlesEditingToolbar: View? = null
    private var isSubtitlesEditingActive = false

    private var keyframeEditingToolbar: View? = null
    private var isKeyframeEditingMode = false
    private var activeKeyframeProperty = "Position" // "Position", "Opacity", or "Speed"

    // Segmented preview state
    private var previewJob: Job? = null
    private var isShowingPreview = false
    private var isRenderingPreview = false
    private var previewFile: File? = null
    
    private var audioPreviewPlayer: android.media.MediaPlayer? = null
    
    private var isInitialFitDone = false

    private var mediaRecorder: android.media.MediaRecorder? = null
    private var isRecordingVoiceOver = false
    private var voiceOverFile: File? = null
    private var voiceOverStartTimeMs = 0L

    private val requestRecordAudioPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showVoiceOverDialog()
        } else {
            Toast.makeText(this, R.string.toast_microphone_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    private var shouldQuitAfterSave = false

    private val saveProjectLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val project = viewModel.project.value
                if (project != null) {
                    val recipe = com.vikash.vidopro.models.EditRecipe.fromVideoProject(project.sourceName, project)
                    val json = com.vikash.vidopro.utils.ProjectSerializer.serialize(recipe)
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(json.toByteArray())
                    }
                    viewModel.markProjectSaved()
                    Toast.makeText(this, "Project saved successfully", Toast.LENGTH_SHORT).show()
                    if (shouldQuitAfterSave) {
                        shouldQuitAfterSave = false
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save project", e)
                Toast.makeText(this, "Failed to save project", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var activeFontSelectionCallback: ((com.vikash.vidopro.utils.FontItem) -> Unit)? = null

    private val importFontLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val importedFont = com.vikash.vidopro.utils.FontManager.importFontFromUri(this, uri)
            if (importedFont != null) {
                Toast.makeText(this, "Font '${importedFont.name}' imported", Toast.LENGTH_SHORT).show()
                textEditingToolbar?.findViewById<View>(R.id.formatSettingsContainer)?.let { setupTextFontSelector(it) }
                setupSubtitleFontSelector(subtitlesEditingToolbar)
                activeFontSelectionCallback?.invoke(importedFont)
            } else {
                Toast.makeText(this, "Failed to import font file. Must be a valid .ttf or .otf", Toast.LENGTH_LONG).show()
            }
        }
    }
    private var photoSequencePreviewImageView: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_editing)
        photoSequencePreviewImageView = findViewById(R.id.photoSequencePreviewImageView)
        pixelsPerMs = 0.15f * resources.displayMetrics.density

        // Apply system UI visibility based on user setting
        val prefs = getSharedPreferences("vidopro_prefs", MODE_PRIVATE)
        val isFullscreen = prefs.getBoolean("fullscreen_editor", true)
        if (isFullscreen) {
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }

        // Initialize ViewModel and engine
        viewModel = ViewModelProvider(this).get(VideoEditingViewModel::class.java)
        ffmpegEngine = FFmpegRenderEngine(this)
        
        exportController = com.vikash.vidopro.editor.export.ExportController(
            activity = this,
            viewModel = viewModel,
            ffmpegEngine = ffmpegEngine,
            callbacks = object : com.vikash.vidopro.editor.export.ExportController.Callbacks {
                override fun getFontFilePath(): String? = fontFilePath
                override fun getTempInputFilePath(): String? = if (::tempInputFile.isInitialized) tempInputFile.absolutePath else null
                override fun getVideoUri(): Uri? = videoUri
                override fun getTotalSequenceDurationMs(): Long = getTotalSequenceDuration()
                override fun commitActiveEditsIfAny() = this@VideoEditingActivity.commitActiveEditsIfAny()
                override fun isShowingPreview(): Boolean = isShowingPreview
                override fun dismissPreview() = this@VideoEditingActivity.dismissPreview()
                override fun showError(message: String) = this@VideoEditingActivity.showError(message)
                override fun showProErrorDialog(errorCode: ErrorCode, technicalLog: String) = this@VideoEditingActivity.showProErrorDialog(errorCode, technicalLog)
                override fun onSaveProjectRequested() {
                    saveProjectLauncher.launch("project.lcprj")
                }
            }
        )
        exportController.register()
        
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).registerReceiver(
            proxyReceiver,
            android.content.IntentFilter(com.vikash.vidopro.services.ProxyGenerationService.ACTION_PROXY_GENERATED)
        )

        // Register back-press callback to prompt for quit confirmation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFullscreenMode) {
                    exitFullscreenMode()
                } else if (isHandwritingActive) {
                    val canvasView = findViewById<HandwritingCanvasView>(R.id.handwritingCanvasView)
                    if (canvasView != null && !canvasView.isEmpty()) {
                        MaterialAlertDialogBuilder(this@VideoEditingActivity)
                            .setTitle("Discard Handwriting?")
                            .setMessage("Are you sure you want to discard your drawing?")
                            .setPositiveButton("Discard") { _, _ -> closeHandwritingMode() }
                            .setNegativeButton("Keep Editing", null)
                            .show()
                    } else {
                        closeHandwritingMode()
                    }
                } else {
                    showQuitConfirmationDialog()
                }
            }
        })

        // ── Copy the bundled font from assets → cacheDir so FFmpeg can read it ──
        // The font must exist at:  app/src/main/assets/fonts/Roboto-Regular.ttf
        fontFilePath = ffmpegEngine.copyFontToCache("fonts/Roboto-Regular.ttf")
        Log.d(TAG, "Font path: $fontFilePath")
        Log.d(TAG, "Font file exists: ${fontFilePath?.let { File(it).exists() }}")
        if (fontFilePath == null) {
            Log.e(TAG, "Font copy failed — text overlays will not render. " +
                    "Make sure assets/fonts/Roboto-Regular.ttf exists in the project.")
        }

        // Initialize UI components
        initializeViews()
        setupExoPlayer()
        setupCustomSeeker()
        setupHandwritingControls()
        observeViewModelState()

        // Play/Pause button logic
        btnPlayPause.setBounceClickListener {
            if (::player.isInitialized && isVideoLoaded) {
                if (player.isPlaying) {
                    player.pause()
                    btnPlayPause.setImageResource(R.drawable.ic_play_24)
                } else {
                    val totalDuration = getTotalSequenceDuration()
                    val currentGlobalPos = getGlobalPosition()
                    
                    // If we reached the end of the timeline, restart from beginning
                    if (currentGlobalPos >= totalDuration - 100L || player.playbackState == Player.STATE_ENDED) {
                        seekToGlobalPosition(0L)
                        timelineHorizontalScroll.scrollTo(0, 0)
                        updateDurationDisplay(0, totalDuration.toInt())
                    }
                    
                    player.play()
                    btnPlayPause.setImageResource(R.drawable.ic_pause_24)
                }
            }
        }

        // Previous Frame / Cutpoint logic
        findViewById<ImageButton>(R.id.btnPrevFrame)?.setBounceClickListener {
            if (::player.isInitialized && isVideoLoaded) {
                if (player.isPlaying) player.pause()
                val totalDuration = getTotalSequenceDuration()
                val currentPos = getGlobalPosition()
                val newPos = (currentPos - 33L).coerceAtLeast(0L)
                seekToGlobalPosition(newPos, force = true)
                updateDurationDisplay(newPos.toInt(), totalDuration.toInt())
                isProgrammaticScroll = true
                timelineHorizontalScroll.scrollTo((newPos * pixelsPerMs).toInt(), 0)
                isProgrammaticScroll = false
            }
        }
        findViewById<ImageButton>(R.id.btnPrevFrame)?.setOnLongClickListener {
            if (::player.isInitialized && isVideoLoaded) {
                if (player.isPlaying) player.pause()
                val totalDuration = getTotalSequenceDuration()
                val currentPos = getGlobalPosition()
                var cutPoints = mutableListOf(0L)
                var acc = 0L
                for (item in getSequenceItems()) {
                    acc += item.trimmedDurationMs
                    cutPoints.add(acc)
                }
                val prevCut = cutPoints.filter { it < currentPos - 60L }.maxOrNull() ?: 0L
                seekToGlobalPosition(prevCut, force = true)
                updateDurationDisplay(prevCut.toInt(), totalDuration.toInt())
                isProgrammaticScroll = true
                timelineHorizontalScroll.scrollTo((prevCut * pixelsPerMs).toInt(), 0)
                isProgrammaticScroll = false
                true
            } else false
        }

        // Next Frame / Cutpoint logic
        findViewById<ImageButton>(R.id.btnNextFrame)?.setBounceClickListener {
            if (::player.isInitialized && isVideoLoaded) {
                if (player.isPlaying) player.pause()
                val totalDuration = getTotalSequenceDuration()
                val currentPos = getGlobalPosition()
                val newPos = (currentPos + 33L).coerceAtMost(totalDuration)
                seekToGlobalPosition(newPos, force = true)
                updateDurationDisplay(newPos.toInt(), totalDuration.toInt())
                isProgrammaticScroll = true
                timelineHorizontalScroll.scrollTo((newPos * pixelsPerMs).toInt(), 0)
                isProgrammaticScroll = false
            }
        }
        findViewById<ImageButton>(R.id.btnNextFrame)?.setOnLongClickListener {
            if (::player.isInitialized && isVideoLoaded) {
                if (player.isPlaying) player.pause()
                val totalDuration = getTotalSequenceDuration()
                val currentPos = getGlobalPosition()
                var cutPoints = mutableListOf(0L)
                var acc = 0L
                for (item in getSequenceItems()) {
                    acc += item.trimmedDurationMs
                    cutPoints.add(acc)
                }
                val nextCut = cutPoints.filter { it > currentPos + 60L }.minOrNull() ?: totalDuration
                seekToGlobalPosition(nextCut, force = true)
                updateDurationDisplay(nextCut.toInt(), totalDuration.toInt())
                isProgrammaticScroll = true
                timelineHorizontalScroll.scrollTo((nextCut * pixelsPerMs).toInt(), 0)
                isProgrammaticScroll = false
                true
            } else false
        }

        // Keyframe diamond button above timeline (with same function as previous bottom sheet keyframe button)
        btnKeyframeDiamond = findViewById(R.id.btnKeyframeDiamond)
        btnKeyframeDiamond?.setBounceClickListener {
            if (!isVideoLoaded) return@setBounceClickListener
            if (isKeyframeEditingMode) {
                handleKeyframeActionClick()
            } else {
                val selectedId = viewModel.selectedOperationId.value
                val project = viewModel.project.value
                val op = project?.operations?.find { it.id == selectedId }
                when {
                    op is EditOperation.AddText -> {
                        enterKeyframeEditingMode(isText = true)
                    }
                    op is EditOperation.AddImageOverlay -> {
                        enterKeyframeEditingMode(isText = false)
                    }
                    selectedVideoIndex != null -> {
                        enterKeyframeEditingMode(isText = false)
                    }
                    else -> {
                        val currentPos = getGlobalPosition()
                        val sequenceItems = getSequenceItems()
                        var acc = 0L
                        var foundIndex: Int? = null
                        for ((idx, item) in sequenceItems.withIndex()) {
                            if (currentPos >= acc && currentPos <= acc + item.trimmedDurationMs) {
                                foundIndex = idx
                                break
                            }
                            acc += item.trimmedDurationMs
                        }
                        val targetIndex = foundIndex ?: 0
                        if (sequenceItems.isNotEmpty()) {
                            selectedVideoIndex = targetIndex
                            enterVideoEditingMode()
                            viewModel.project.value?.let { renderTracks(it) }
                            enterKeyframeEditingMode(isText = false)
                        }
                    }
                }
            }
        }

        // Timeline Mute button (positioned to the left of the video, scrolls along with it)
        btnTimelineMute = findViewById(R.id.btnTimelineMute)
        btnTimelineMute.setBounceClickListener {
            if (::player.isInitialized && isVideoLoaded) {
                val isCurrentlyMuted = player.volume == 0f
                if (isCurrentlyMuted) {
                    player.volume = 1f
                    updateTimelineMuteUi(false)
                    viewModel.project.value?.let { proj ->
                        val muteOp = proj.operations.find { it is EditOperation.MuteAudio } as? EditOperation.MuteAudio
                        if (muteOp != null) {
                            viewModel.removeOperation(muteOp.id)
                        }
                    }
                    Toast.makeText(this, "Original audio unmuted", Toast.LENGTH_SHORT).show()
                } else {
                    player.volume = 0f
                    updateTimelineMuteUi(true)
                    viewModel.addMuteAudioOperation()
                    Toast.makeText(this, "Original audio muted", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Keep stub for backwards compatibility
        findViewById<ImageButton>(R.id.btnMute)?.setOnClickListener {
            btnTimelineMute.performClick()
        }

        val btnCaptureFrame = findViewById<ImageButton>(R.id.btnCaptureFrame)
        btnCaptureFrame.setBounceClickListener {
            captureCurrentFrame()
        }

        val btnMagnet = findViewById<ImageButton>(R.id.btnMagnet)
        btnMagnet.setImageResource(if (isMagnetEnabled) R.drawable.ic_magnet_24 else R.drawable.ic_magnet_off_24)
        btnMagnet.setBounceClickListener {
            isMagnetEnabled = !isMagnetEnabled
            if (isMagnetEnabled) {
                btnMagnet.setImageResource(R.drawable.ic_magnet_24)
                Toast.makeText(this, R.string.toast_snapping_enabled, Toast.LENGTH_SHORT).show()
            } else {
                btnMagnet.setImageResource(R.drawable.ic_magnet_off_24)
                Toast.makeText(this, R.string.toast_snapping_disabled, Toast.LENGTH_SHORT).show()
            }
            draggableTextOverlay?.isSnappingEnabled = isMagnetEnabled
            draggableImageOverlay?.isSnappingEnabled = isMagnetEnabled
        }

        btnMoveLayerUp = findViewById(R.id.btnMoveLayerUp)
        btnMoveLayerDown = findViewById(R.id.btnMoveLayerDown)
        btnMoveLayerUp.setBounceClickListener {
            viewModel.selectedOperationId.value?.let { id ->
                viewModel.moveOverlayOperation(id, moveUp = true)
            }
        }
        btnMoveLayerDown.setBounceClickListener {
            viewModel.selectedOperationId.value?.let { id ->
                viewModel.moveOverlayOperation(id, moveUp = false)
            }
        }

        scaleDetector = android.view.ScaleGestureDetector(this, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val density = resources.displayMetrics.density
                val totalDuration = getTotalSequenceDuration()
                if (totalDuration <= 0L) return true

                val minPixelsPerMs = maxOf(0.001f * density, (timelineHorizontalScroll.width.toFloat() - 32.dpToPx()) / totalDuration)
                val maxPixelsPerMs = 1.5f * density

                val newPixelsPerMs = pixelsPerMs * scaleFactor
                pixelsPerMs = newPixelsPerMs.coerceIn(minPixelsPerMs, maxPixelsPerMs)

                viewModel.project.value?.let { renderTracks(it) }

                val currentPos = getGlobalPosition()
                isProgrammaticScroll = true
                timelineHorizontalScroll.scrollTo((currentPos * pixelsPerMs).toInt(), 0)
                isProgrammaticScroll = false
                return true
            }
        })

        // Update play/pause button icon on playback state change
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    btnPlayPause.setImageResource(R.drawable.ic_pause_24)
                    if (::btnFullscreenPlayPause.isInitialized) {
                        btnFullscreenPlayPause.setImageResource(R.drawable.ic_pause_24)
                    }
                } else {
                    btnPlayPause.setImageResource(R.drawable.ic_play_24)
                    if (::btnFullscreenPlayPause.isInitialized) {
                        btnFullscreenPlayPause.setImageResource(R.drawable.ic_play_24)
                    }
                }
            }
        })

        // Process incoming video URI
        val incomingUri = intent.getParcelableExtra<Uri>("VIDEO_URI") ?: intent.data
        if (incomingUri != null) {
            handleMainSelectedVideo(incomingUri)
        } else {
            clearProjectAndShowImportScreen()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incomingUri = intent.getParcelableExtra<Uri>("VIDEO_URI") ?: intent.data
        if (incomingUri != null) {
            handleMainSelectedVideo(incomingUri)
        }
    }


    private fun showProErrorDialog(errorCode: ErrorCode, technicalLog: String) {
        val intent = Intent(this, ErrorDisplayActivity::class.java).apply {
            putExtra("ERROR_CODE", errorCode.code)
            putExtra("ERROR_DESCRIPTION", errorCode.description)
            putExtra("ERROR_LOG", technicalLog)
        }
        startActivity(intent)
    }

    private fun initializeViews() {
        playerView = findViewById(R.id.playerView)
        playerContainer = findViewById(R.id.playerContainer)
        bgPreviewImageView = findViewById(R.id.bgPreviewImageView)
        transitionPreviewOverlayView = findViewById(R.id.transitionPreviewOverlayView)
        canvasContainer = findViewById(R.id.canvasContainer)

        btnFullscreen = findViewById(R.id.btnFullscreen)
        btnExpandTimeline = findViewById(R.id.btnExpandTimeline)
        pipPlayerContainer = findViewById(R.id.pipPlayerContainer)
        fullscreenOverlay = findViewById(R.id.fullscreenOverlay)
        fullscreenCanvasHolder = findViewById(R.id.fullscreenCanvasHolder)
        fullscreenControls = findViewById(R.id.fullscreenControls)
        btnFullscreenPlayPause = findViewById(R.id.btnFullscreenPlayPause)
        btnExitFullscreen = findViewById(R.id.btnExitFullscreen)
        btnExitFullscreenTop = findViewById(R.id.btnExitFullscreenTop)
        sbFullscreenSeeker = findViewById(R.id.sbFullscreenSeeker)
        tvFullscreenCurrentTime = findViewById(R.id.tvFullscreenCurrentTime)
        tvFullscreenTotalTime = findViewById(R.id.tvFullscreenTotalTime)

        setupFullscreenAndPipControls()

        val canvasLayoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            triggerCanvasLayoutUpdate()
        }
        playerContainer.viewTreeObserver.addOnGlobalLayoutListener(canvasLayoutListener)
        pipPlayerContainer.viewTreeObserver.addOnGlobalLayoutListener(canvasLayoutListener)
        fullscreenCanvasHolder.viewTreeObserver.addOnGlobalLayoutListener(canvasLayoutListener)

        videoScaleDetector = android.view.ScaleGestureDetector(this, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val newScale = (videoPinchScale * factor).coerceIn(1.0f, 5.0f)
                videoPinchScale = newScale
                playerView.scaleX = newScale
                playerView.scaleY = newScale
                return true
            }
        })

        val videoDoubleTapDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                videoPinchScale = 1.0f
                playerView.scaleX = 1.0f
                playerView.scaleY = 1.0f
                playerView.translationX = 0f
                playerView.translationY = 0f
                return true
            }
        })

        var lastTouchX = 0f
        var lastTouchY = 0f
        playerContainer.setOnTouchListener { v, event ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            videoDoubleTapDetector.onTouchEvent(event)
            videoScaleDetector.onTouchEvent(event)

            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (videoPinchScale > 1.0f && !videoScaleDetector.isInProgress && event.pointerCount == 1) {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        val maxTransX = (v.width * (videoPinchScale - 1f)) / 2f
                        val maxTransY = (v.height * (videoPinchScale - 1f)) / 2f
                        playerView.translationX = (playerView.translationX + dx).coerceIn(-maxTransX, maxTransX)
                        playerView.translationY = (playerView.translationY + dy).coerceIn(-maxTransY, maxTransY)
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                }
            }

            if (videoScaleDetector.isInProgress || event.pointerCount >= 2) {
                true
            } else {
                videoPinchScale > 1.0f
            }
        }

        tvDuration = findViewById(R.id.tvDuration)
        sequenceTrackContainer = findViewById(R.id.sequenceTrackContainer)
        customVideoSeeker = findViewById(R.id.customVideoSeeker)
        timeRulerView = findViewById(R.id.timeRulerView)
        loadingScreen = findViewById(R.id.loadingScreen)
        showLoading(getString(R.string.loading), getString(R.string.loading_tag))
        isImportLoading = true
        exportScreen = findViewById(R.id.exportScreen)
        exportScreen.findViewById<View>(R.id.btnCancelExport)?.setBounceClickListener {
            cancelExport()
        }
        previewLoadingOverlay = findViewById(R.id.previewLoadingOverlay)
        lottieAnimationView = findViewById(R.id.lottieAnimation)
        textTrackContainer = findViewById(R.id.textTrackContainer)
        imageTrackContainer = findViewById(R.id.imageTrackContainer)
        audioTrackContainer = findViewById(R.id.audioTrackContainer)

        btnPlayPause = findViewById(R.id.btnPlayPause)
        timelineHorizontalScroll = findViewById(R.id.timelineHorizontalScroll)
        timelineContainer = findViewById(R.id.timelineContainer)
        timelineVerticalScroll = findViewById(R.id.timelineVerticalScroll)
        btnTimelineAdd = findViewById(R.id.btnTimelineAdd)
        
        emptyProjectState = findViewById(R.id.emptyProjectState)
        emptyProjectState.setBounceClickListener {
            openFilePickerMain()
        }

        btnTimelineAdd.setBounceClickListener {
            openFilePickerMerge()
        }

        timelineContainer.post {
            val halfWidth = timelineContainer.width / 2
            timelineHorizontalScroll.setPadding(halfWidth, 0, halfWidth, 0)
            updateTimelineAddButtonPosition()
            updateTimelineMuteButtonPosition()
        }

        timelineVerticalScroll.setOnScrollChangeListener { _, _, _, _, _ ->
            updateTimelineAddButtonPosition()
            updateTimelineMuteButtonPosition()
        }

        timelineHorizontalScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            if (!isProgrammaticScroll) {
                var targetMs = (scrollX / pixelsPerMs).toLong()

                if (isMagnetEnabled) {
                    val snapTargets = getSnapTargetsMs()
                    val thresholdPx = 10f
                    val thresholdMs = (thresholdPx / pixelsPerMs).toLong()

                    var snapped = false
                    for (target in snapTargets) {
                        if (Math.abs(targetMs - target) <= thresholdMs) {
                            targetMs = target
                            snapped = true
                            if (lastSnappedTargetMs != target) {
                                timelineHorizontalScroll.performAppHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                lastSnappedTargetMs = target
                            }
                            if (!isUserScrollingTimeline) {
                                val snappedScrollX = (targetMs * pixelsPerMs).toInt()
                                if (scrollX != snappedScrollX) {
                                    isProgrammaticScroll = true
                                    timelineHorizontalScroll.scrollTo(snappedScrollX, 0)
                                    isProgrammaticScroll = false
                                }
                            }
                            break
                        }
                    }
                    if (!snapped) {
                        lastSnappedTargetMs = -1L
                    }
                } else {
                    lastSnappedTargetMs = -1L
                }

                seekToGlobalPosition(targetMs)
            }
            updateTimelineAddButtonPosition()
            updateTimelineMuteButtonPosition()
        }

        timelineHorizontalScroll.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (scaleDetector.isInProgress) {
                true
            } else {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        isUserScrollingTimeline = true
                        if (::player.isInitialized && player.isPlaying) {
                            player.pause()
                            btnPlayPause.setImageResource(R.drawable.ic_play_24)
                        }
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        isUserScrollingTimeline = true
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        isUserScrollingTimeline = false
                        if (isMagnetEnabled) {
                            val scrollX = timelineHorizontalScroll.scrollX
                            val targetMs = (scrollX / pixelsPerMs).toLong()
                            val snapTargets = getSnapTargetsMs()
                            val thresholdPx = 10f
                            val thresholdMs = (thresholdPx / pixelsPerMs).toLong()
                            for (target in snapTargets) {
                                if (Math.abs(targetMs - target) <= thresholdMs) {
                                    val snappedScrollX = (target * pixelsPerMs).toInt()
                                    if (scrollX != snappedScrollX) {
                                        isProgrammaticScroll = true
                                        timelineHorizontalScroll.scrollTo(snappedScrollX, 0)
                                        isProgrammaticScroll = false
                                        seekToGlobalPosition(target)
                                    }
                                    break
                                }
                            }
                        }
                    }
                }
                false
            }
        }

        textOverlayView = try {
            findViewById<com.vikash.vidopro.customviews.TextOverlayView>(R.id.textOverlayView)?.also { overlay ->
                overlay.onSubtitlePositionChanged = { relX, relY ->
                    val project = viewModel.project.value
                    val subOp = project?.operations?.find { it is EditOperation.AddSubtitles } as? EditOperation.AddSubtitles
                    if (subOp != null) {
                        updateSubtitleOp(subOp.copy(relativeX = relX, relativeY = relY))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TextOverlayView not found in layout: ${e.message}")
            null
        }

        imageOverlayView = try {
            findViewById(R.id.imageOverlayView)
        } catch (e: Exception) {
            Log.w(TAG, "ImageOverlayView not found in layout: ${e.message}")
            null
        }

        draggableTextOverlay = try {
            findViewById<DraggableTextOverlayView>(R.id.draggableTextOverlay)?.also { overlay ->
                overlay.isSnappingEnabled = isMagnetEnabled
                overlay.onPositionChanged = { rx, ry ->
                    if (isKeyframeEditingMode && activeKeyframeProperty == "Position") {
                        val selectedId = viewModel.selectedOperationId.value
                        if (selectedId != null) {
                            val op = viewModel.project.value?.operations?.find { (it as? EditOperation.AddText)?.id == selectedId } as? EditOperation.AddText
                            if (op != null) {
                                val globalTimeMs = getGlobalPosition()
                                val start = op.startTimeMs ?: 0L
                                val relativeTimeMs = globalTimeMs - start
                                val currentList = op.positionKeyframes.toMutableList()
                                val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150 }
                                val newPoint = EditOperation.KeyframePoint(relativeTimeMs, rx, ry)
                                if (existingIndex != -1) {
                                    currentList[existingIndex] = newPoint
                                } else {
                                    currentList.add(newPoint)
                                }
                                val newOp = op.copy(positionKeyframes = currentList)
                                viewModel.updateOperation(newOp)
                            }
                        }
                    }
                }
                overlay.onTextCommitted = { text, fontSize, relX, relY, color, fontPath, opacity, borderThickness, borderColor, textAlign, letterSpacing, lineSpacing ->
                    val selectedId = viewModel.selectedOperationId.value
                    if (isKeyframeEditingMode) {
                        if (selectedId != null) {
                            val op = viewModel.project.value?.operations?.find { (it as? EditOperation.AddText)?.id == selectedId } as? EditOperation.AddText
                            if (op != null) {
                                val globalTimeMs = getGlobalPosition()
                                val start = op.startTimeMs ?: 0L
                                val relativeTimeMs = globalTimeMs - start
                                val currentList = op.positionKeyframes.toMutableList()
                                val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150 }
                                val newPoint = EditOperation.KeyframePoint(relativeTimeMs, relX, relY)
                                if (existingIndex != -1) {
                                    currentList[existingIndex] = newPoint
                                } else {
                                    currentList.add(newPoint)
                                }
                                val newOp = op.copy(positionKeyframes = currentList)
                                viewModel.updateOperation(newOp)
                            }
                        }
                    } else {
                        if (selectedId != null) {
                            val op = viewModel.project.value?.operations?.find { (it as? EditOperation.AddText)?.id == selectedId } as? EditOperation.AddText
                            if (op != null) {
                                viewModel.updateOperation(op.copy(
                                    text = text, fontSize = fontSize, relativeX = relX, relativeY = relY, color = color,
                                    fontPath = fontPath, opacity = opacity, borderThickness = borderThickness, borderColor = borderColor,
                                    textAlign = textAlign, letterSpacing = letterSpacing, lineSpacing = lineSpacing
                                ))
                            }
                        } else {
                        val start = getGlobalPosition()
                        val end = minOf(start + 3000L, getTotalSequenceDuration())
                        viewModel.addTextOperation(
                            text = text,
                            fontSize = fontSize,
                            position = "Center Align",
                            relativeX = relX,
                            relativeY = relY,
                            color = color,
                            startTimeMs = start,
                            endTimeMs = end,
                            fontPath = fontPath,
                            opacity = opacity,
                            borderThickness = borderThickness,
                            borderColor = borderColor,
                            textAlign = textAlign,
                            letterSpacing = letterSpacing,
                            lineSpacing = lineSpacing
                        )
                    }
                    viewModel.selectOperation(null)
                    exitTextEditingMode()
                }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "DraggableTextOverlayView not found: ${e.message}")
            null
        }

        textEditingToolbar = try {
            findViewById<View>(R.id.textEditingToolbar)?.also { toolbar ->
                toolbar.findViewById<ImageButton>(R.id.btnTextCancel)?.setBounceClickListener {
                    draggableTextOverlay?.deactivate()
                    viewModel.selectOperation(null)
                    exitTextEditingMode()
                }
                toolbar.findViewById<View>(R.id.btnTextDone)?.setBounceClickListener {
                    draggableTextOverlay?.commitText()
                }
                toolbar.findViewById<View>(R.id.btnTextDelete)?.setBounceClickListener {
                    MaterialAlertDialogBuilder(this@VideoEditingActivity)
                        .setTitle("Delete Text")
                        .setMessage("Are you sure you want to delete this text overlay?")
                        .setPositiveButton("Delete") { _, _ ->
                            draggableTextOverlay?.deactivate()
                            viewModel.selectedOperationId.value?.let { id ->
                                viewModel.removeOperation(id)
                            }
                            exitTextEditingMode()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                toolbar.findViewById<View>(R.id.btnTextDuplicate)?.setBounceClickListener {
                    duplicateSelectedOverlay()
                }


                val btnKeyboard = toolbar.findViewById<ImageButton>(R.id.btnTextKeyboardTab)
                val btnPalette = toolbar.findViewById<ImageButton>(R.id.btnTextPaletteTab)
                val btnFormat = toolbar.findViewById<ImageButton>(R.id.btnTextFormatTab)
                val colorContainer = toolbar.findViewById<View>(R.id.colorPickerContainer)
                val formatContainer = toolbar.findViewById<View>(R.id.formatSettingsContainer)

                fun resetTabs() {
                    colorContainer?.visibility = View.GONE
                    formatContainer?.visibility = View.GONE
                    btnKeyboard?.setColorFilter(getColor(R.color.toolTextInactive))
                    btnPalette?.setColorFilter(getColor(R.color.toolTextInactive))
                    btnFormat?.setColorFilter(getColor(R.color.toolTextInactive))
                }

                draggableTextOverlay?.onEditingFocused = {
                    resetTabs()
                    btnKeyboard?.setColorFilter(getColor(R.color.colorPrimary))
                }

                btnKeyboard?.setBounceClickListener {
                    resetTabs()
                    btnKeyboard.setColorFilter(getColor(R.color.colorPrimary))
                    draggableTextOverlay?.requestEditingFocus()
                }

                btnPalette?.setBounceClickListener {
                    draggableTextOverlay?.hideKeyboard()

                    resetTabs()
                    colorContainer?.visibility = View.VISIBLE
                    btnPalette.setColorFilter(getColor(R.color.colorPrimary))
                    setupColorPicker(toolbar)
                }

                btnFormat?.setBounceClickListener {
                    draggableTextOverlay?.hideKeyboard()
                    
                    resetTabs()
                    formatContainer?.visibility = View.VISIBLE
                    btnFormat.setColorFilter(getColor(R.color.colorPrimary))
                }

                toolbar.findViewById<ImageButton>(R.id.btnTextAnimation)?.setBounceClickListener {
                    android.widget.Toast.makeText(this@VideoEditingActivity, "Text Animation: Fade In / Out", android.widget.Toast.LENGTH_SHORT).show()
                }

                toolbar.findViewById<ImageButton>(R.id.btnTextPosition)?.setBounceClickListener {
                    draggableTextOverlay?.hideKeyboard()
                    resetTabs()
                    formatContainer?.visibility = View.VISIBLE
                    btnFormat?.setColorFilter(getColor(R.color.colorPrimary))
                }

                toolbar.findViewById<ImageButton>(R.id.btnTextOpacity)?.setBounceClickListener {
                    draggableTextOverlay?.hideKeyboard()
                    resetTabs()
                    formatContainer?.visibility = View.VISIBLE
                    btnFormat?.setColorFilter(getColor(R.color.colorPrimary))
                }
                
                // Format UI listeners
                setupTextFontSelector(formatContainer)

                val seekOpacity = formatContainer?.findViewById<Slider>(R.id.seekOpacity)
                seekOpacity?.addOnChangeListener { _, value, _ ->
                    draggableTextOverlay?.setOpacity(value / 100f)
                }
                
                val seekBorderWidth = formatContainer?.findViewById<Slider>(R.id.seekBorderWidth)
                seekBorderWidth?.addOnChangeListener { _, value, _ ->
                    draggableTextOverlay?.setBorderThickness(value.toInt())
                }
                
                val seekLetterSpacing = formatContainer?.findViewById<Slider>(R.id.seekLetterSpacing)
                seekLetterSpacing?.addOnChangeListener { _, value, _ ->
                    draggableTextOverlay?.setLetterSpacing((value - 50f) / 100f)
                }

                val seekLineSpacing = formatContainer?.findViewById<Slider>(R.id.seekLineSpacing)
                seekLineSpacing?.addOnChangeListener { _, value, _ ->
                    draggableTextOverlay?.setLineSpacing((value - 50f) * 2f)
                }
                
                val btnAlignL = formatContainer?.findViewById<android.widget.Button>(R.id.btnAlignLeft)
                val btnAlignC = formatContainer?.findViewById<android.widget.Button>(R.id.btnAlignCenter)
                val btnAlignR = formatContainer?.findViewById<android.widget.Button>(R.id.btnAlignRight)
                
                btnAlignL?.setOnClickListener { 
                    draggableTextOverlay?.setTextAlign("left")
                    btnAlignL.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.colorPrimary))
                    btnAlignC?.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.inactiveToolBackground))
                    btnAlignR?.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.inactiveToolBackground))
                }
                btnAlignC?.setOnClickListener { 
                    draggableTextOverlay?.setTextAlign("center") 
                    btnAlignL?.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.inactiveToolBackground))
                    btnAlignC.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.colorPrimary))
                    btnAlignR?.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.inactiveToolBackground))
                }
                btnAlignR?.setOnClickListener { 
                    draggableTextOverlay?.setTextAlign("right") 
                    btnAlignL?.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.inactiveToolBackground))
                    btnAlignC?.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.inactiveToolBackground))
                    btnAlignR.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.colorPrimary))
                }

                toolbar.findViewById<View>(R.id.btnTextKeyframe)?.setBounceClickListener {
                    enterKeyframeEditingMode(isText = true)
                }

                resetTabs()
                btnKeyboard?.setColorFilter(getColor(R.color.colorPrimary))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Text editing toolbar not found: ${e.message}")
            null
        }

        draggableImageOverlay = try {
            findViewById<DraggableImageOverlayView>(R.id.draggableImageOverlay)?.also { overlay ->
                overlay.isSnappingEnabled = isMagnetEnabled
                overlay.onPositionChanged = { rx, ry ->
                    if (isKeyframeEditingMode && activeKeyframeProperty == "Position") {
                        val selectedId = viewModel.selectedOperationId.value
                        if (selectedId != null) {
                            val op = viewModel.project.value?.operations?.find { (it as? EditOperation.AddImageOverlay)?.id == selectedId } as? EditOperation.AddImageOverlay
                            if (op != null) {
                                val globalTimeMs = getGlobalPosition()
                                val start = op.startTimeMs ?: 0L
                                val relativeTimeMs = globalTimeMs - start
                                val currentList = op.positionKeyframes.toMutableList()
                                val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150 }
                                val newPoint = EditOperation.KeyframePoint(relativeTimeMs, rx, ry)
                                if (existingIndex != -1) {
                                    currentList[existingIndex] = newPoint
                                } else {
                                    currentList.add(newPoint)
                                }
                                val newOp = op.copy(positionKeyframes = currentList)
                                viewModel.updateOperation(newOp)
                            }
                        }
                    }
                }
                overlay.onImageCommitted = { uri, relX, relY, relW, relH, rotationAngle, opacity, isMirrored, maskConfig ->
                    val selectedId = viewModel.selectedOperationId.value
                    if (isKeyframeEditingMode) {
                        if (selectedId != null) {
                            val op = viewModel.project.value?.operations?.find { (it as? EditOperation.AddImageOverlay)?.id == selectedId } as? EditOperation.AddImageOverlay
                            if (op != null) {
                                val globalTimeMs = getGlobalPosition()
                                val start = op.startTimeMs ?: 0L
                                val relativeTimeMs = globalTimeMs - start
                                val currentList = op.positionKeyframes.toMutableList()
                                val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150 }
                                val newPoint = EditOperation.KeyframePoint(relativeTimeMs, relX, relY)
                                if (existingIndex != -1) {
                                    currentList[existingIndex] = newPoint
                                } else {
                                    currentList.add(newPoint)
                                }
                                val newOp = op.copy(positionKeyframes = currentList)
                                viewModel.updateOperation(newOp)
                            }
                        }
                    } else {
                        if (selectedId != null) {
                            val op = viewModel.project.value?.operations?.find { (it as? EditOperation.AddImageOverlay)?.id == selectedId } as? EditOperation.AddImageOverlay
                            if (op != null) {
                                viewModel.updateOperation(op.copy(
                                    imageUri = uri, relativeX = relX, relativeY = relY, relativeWidth = relW, relativeHeight = relH, rotationAngle = rotationAngle, opacity = opacity, isMirrored = isMirrored, maskConfig = maskConfig
                                ))
                            }
                        } else {
                            val start = getGlobalPosition()
                            val fileDuration = getOverlayFileDurationMs(uri)
                            val duration = fileDuration ?: 3000L
                            val end = minOf(start + duration, getTotalSequenceDuration())
                            val chromaColor = draggableImageOverlay?.currentChromaColor
                            val chromaSim = draggableImageOverlay?.currentChromaSimilarity ?: 0.1f
                            viewModel.addImageOverlayOperation(
                                imageUri = uri,
                                relativeX = relX,
                                relativeY = relY,
                                relativeWidth = relW,
                                relativeHeight = relH,
                                rotationAngle = rotationAngle,
                                startTimeMs = start,
                                endTimeMs = end,
                                fileDurationMs = fileDuration,
                                chromaKeyColor = chromaColor,
                                chromaKeySimilarity = chromaSim,
                                opacity = opacity,
                                isMirrored = isMirrored,
                                maskConfig = maskConfig
                            )
                        }
                        viewModel.selectOperation(null)
                        exitImageEditingMode()
                    }
                }
                overlay.onMaskChanged = { newMaskConfig ->
                    val selectedId = viewModel.selectedOperationId.value
                    if (selectedId != null) {
                        val op = viewModel.project.value?.operations?.find { (it as? EditOperation.AddImageOverlay)?.id == selectedId } as? EditOperation.AddImageOverlay
                        if (op != null) {
                            if (isKeyframeEditingMode) {
                                val globalTimeMs = getGlobalPosition()
                                val start = op.startTimeMs ?: 0L
                                val relativeTimeMs = globalTimeMs - start
                                var mc = newMaskConfig
                                if (activeKeyframeProperty == "Mask Position" || mc.positionKeyframes.isNotEmpty()) {
                                    val currentList = mc.positionKeyframes.toMutableList()
                                    val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                                    val newPoint = EditOperation.KeyframePoint(relativeTimeMs, mc.relativeX, mc.relativeY)
                                    if (existingIndex != -1) {
                                        currentList[existingIndex] = newPoint
                                    } else {
                                        currentList.add(newPoint)
                                    }
                                    mc = mc.copy(positionKeyframes = currentList)
                                }
                                viewModel.updateOperation(op.copy(maskConfig = mc))
                            } else {
                                viewModel.updateOperation(op.copy(maskConfig = newMaskConfig))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "DraggableImageOverlayView not found: ${e.message}")
            null
        }
        mainVideoMaskContainer = findViewById(R.id.mainVideoMaskContainer)
        videoMaskOverlayView = try {
            findViewById<com.vikash.vidopro.customviews.VideoMaskOverlayView>(R.id.videoMaskOverlayView)?.also { overlay ->
                overlay.onMaskChanged = { maskConfig ->
                    selectedVideoIndex?.let { index ->
                        if (isKeyframeEditingMode) {
                            val globalTimeMs = getGlobalPosition()
                            val sequenceItems = getSequenceItems()
                            val accumulatedStartMs = sequenceItems.take(index).sumOf { it.trimmedDurationMs }
                            val relativeTimeMs = globalTimeMs - accumulatedStartMs
                            var mc = maskConfig
                            if (activeKeyframeProperty == "Mask Position" || mc.positionKeyframes.isNotEmpty()) {
                                val currentList = mc.positionKeyframes.toMutableList()
                                val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                                val newPoint = EditOperation.KeyframePoint(relativeTimeMs, mc.relativeX, mc.relativeY)
                                if (existingIndex != -1) {
                                    currentList[existingIndex] = newPoint
                                } else {
                                    currentList.add(newPoint)
                                }
                                mc = mc.copy(positionKeyframes = currentList)
                            }
                            viewModel.updateMergeItemMask(index, mc)
                            mainVideoMaskContainer?.maskConfig = mc
                        } else {
                            viewModel.updateMergeItemMask(index, maskConfig)
                            mainVideoMaskContainer?.maskConfig = maskConfig
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "VideoMaskOverlayView not found: ${e.message}")
            null
        }

        cropOverlayView = try {
            findViewById<com.vikash.vidopro.customviews.CropOverlayView>(R.id.cropOverlayView)?.also { overlay ->
                overlay.onCropBoundsChanged = { x, y, w, h ->
                    val existing = viewModel.project.value?.operations
                        ?.filterIsInstance<com.vikash.vidopro.models.EditOperation.Crop>()
                        ?.lastOrNull()
                    if (existing != null) {
                        viewModel.updateOperation(existing.copy(
                            aspectRatio = "Custom",
                            xFraction = x,
                            yFraction = y,
                            wFraction = w,
                            hFraction = h
                        ))
                    } else {
                        viewModel.addCropOperation("Custom", x, y, w, h)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "CropOverlayView not found: ${e.message}")
            null
        }

        imageEditingToolbar = try {
            findViewById<View>(R.id.imageEditingToolbar)?.also { toolbar ->
                toolbar.findViewById<ImageButton>(R.id.btnImageCancel)?.setBounceClickListener {
                    draggableImageOverlay?.deactivate()
                    viewModel.selectOperation(null)
                    exitImageEditingMode()
                }
                toolbar.findViewById<View>(R.id.btnImageDone)?.setBounceClickListener {
                    draggableImageOverlay?.commitImage()
                }
                toolbar.findViewById<View>(R.id.btnImageDelete)?.setBounceClickListener {
                    MaterialAlertDialogBuilder(this@VideoEditingActivity)
                        .setTitle("Delete Image")
                        .setMessage("Are you sure you want to delete this image overlay?")
                        .setPositiveButton("Delete") { _, _ ->
                            draggableImageOverlay?.deactivate()
                            viewModel.selectedOperationId.value?.let { id ->
                                viewModel.removeOperation(id)
                            }
                            exitImageEditingMode()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                toolbar.findViewById<View>(R.id.btnImageDuplicate)?.setBounceClickListener {
                    duplicateSelectedOverlay()
                }
                toolbar.findViewById<View>(R.id.btnImageMask)?.setBounceClickListener {
                    showMaskBottomSheet()
                }
                toolbar.findViewById<View>(R.id.btnImageMirror)?.setBounceClickListener {
                    draggableImageOverlay?.toggleMirror()
                }
                toolbar.findViewById<View>(R.id.btnImageChromaKey)?.setBounceClickListener {
                    showChromaKeyDialog()
                }
                toolbar.findViewById<View>(R.id.btnImageLoop)?.setBounceClickListener {
                    val selectedId = viewModel.selectedOperationId.value
                    val op = viewModel.project.value?.operations?.find { (it as? EditOperation.AddImageOverlay)?.id == selectedId } as? EditOperation.AddImageOverlay
                    if (op != null) {
                        val newIsLooping = !op.isLooping
                        val actualDuration = op.fileDurationMs ?: 3000L
                        val newEndTimeMs = if (!newIsLooping) {
                            Math.min(op.endTimeMs ?: Long.MAX_VALUE, (op.startTimeMs ?: 0L) + actualDuration)
                        } else {
                            op.endTimeMs
                        }
                        viewModel.updateOperation(op.copy(isLooping = newIsLooping, endTimeMs = newEndTimeMs))
                        
                        val color = if (newIsLooping) androidx.core.content.ContextCompat.getColor(this@VideoEditingActivity, R.color.colorPrimary) else androidx.core.content.ContextCompat.getColor(this@VideoEditingActivity, R.color.toolTextInactive)
                        toolbar.findViewById<android.widget.ImageButton>(R.id.btnImageLoop)?.setColorFilter(color)
                        toolbar.findViewById<android.widget.TextView>(R.id.tvImageLoop)?.setTextColor(color)
                    }
                }

                val slider = toolbar.findViewById<Slider>(R.id.imageRotationSlider)
                val tvValue = toolbar.findViewById<TextView>(R.id.tvImageRotationValue)
                slider?.addOnChangeListener { _, value, _ ->
                    tvValue?.text = "${value.toInt()}°"
                    draggableImageOverlay?.setRotationAngle(value)
                }

                toolbar.findViewById<View>(R.id.btnImageOpacity)?.setBounceClickListener {
                    val opacityRow = toolbar.findViewById<View>(R.id.imageOpacitySliderRow)
                    val rotationRow = toolbar.findViewById<View>(R.id.imageRotationSliderRow)
                    if (opacityRow?.visibility == View.VISIBLE) {
                        opacityRow.visibility = View.GONE
                        rotationRow?.visibility = View.VISIBLE
                        toolbar.findViewById<android.widget.ImageButton>(R.id.btnImageOpacity)?.setColorFilter(androidx.core.content.ContextCompat.getColor(this@VideoEditingActivity, R.color.toolTextInactive))
                    } else {
                        opacityRow?.visibility = View.VISIBLE
                        rotationRow?.visibility = View.GONE
                        toolbar.findViewById<android.widget.ImageButton>(R.id.btnImageOpacity)?.setColorFilter(androidx.core.content.ContextCompat.getColor(this@VideoEditingActivity, R.color.colorPrimary))
                    }
                }

                val opacitySlider = toolbar.findViewById<Slider>(R.id.imageOpacitySlider)
                val tvOpacityValue = toolbar.findViewById<TextView>(R.id.tvImageOpacityValue)
                opacitySlider?.addOnChangeListener { _, value, _ ->
                    tvOpacityValue?.text = "${value.toInt()}%"
                    draggableImageOverlay?.setOpacity(value / 100f)
                }
                toolbar.findViewById<View>(R.id.btnImageKeyframe)?.setBounceClickListener {
                    enterKeyframeEditingMode(isText = false)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Image editing toolbar not found: ${e.message}")
            null
        }

        videoEditingToolbar = try {
            findViewById<View>(R.id.videoEditingToolbar)?.also { toolbar ->
                toolbar.findViewById<ImageButton>(R.id.btnVideoCancel)?.setBounceClickListener {
                    selectedVideoIndex = null
                    viewModel.project.value?.let { renderTracks(it) }
                    exitVideoEditingMode()
                }
                toolbar.findViewById<ImageButton>(R.id.btnVideoSplit)?.setBounceClickListener {
                    splitSelectedVideo()
                }
                toolbar.findViewById<ImageButton>(R.id.btnVideoTrim)?.setBounceClickListener {
                    selectedVideoIndex?.let { index ->
                        val items = getSequenceItems()
                        if (index >= 0 && index < items.size) {
                            showVideoSegmentTrimDialog(index, items[index])
                        }
                    }
                }
                toolbar.findViewById<ImageButton>(R.id.btnVideoDelete)?.setBounceClickListener {
                    MaterialAlertDialogBuilder(this@VideoEditingActivity)
                        .setTitle("Delete Clip")
                        .setMessage("Are you sure you want to delete this clip from the project?")
                        .setPositiveButton("Delete") { _, _ ->
                            deleteSelectedVideo()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                toolbar.findViewById<ImageButton>(R.id.btnVideoExtractAudio)?.setBounceClickListener {
                    selectedVideoIndex?.let { index ->
                        val items = getSequenceItems()
                        if (index >= 0 && index < items.size) {
                            extractAudioFromSegment(index, items[index])
                        }
                    }
                }

                toolbar.findViewById<ImageButton>(R.id.btnVideoFreezeFrame)?.setBounceClickListener {
                    freezeFrameAtCurrentPosition()
                }
                toolbar.findViewById<ImageButton>(R.id.btnVideoSpeed)?.setBounceClickListener {
                    selectedVideoIndex?.let { index ->
                        val items = getSequenceItems()
                        if (index >= 0 && index < items.size) {
                            showSpeedEditingToolbar(index, items[index])
                        }
                    }
                }
                toolbar.findViewById<ImageButton>(R.id.btnVideoReverse)?.setBounceClickListener {
                    selectedVideoIndex?.let { index ->
                        val items = getSequenceItems()
                        if (index >= 0 && index < items.size) {
                            reverseVideoSegment(index, items[index])
                        }
                    }
                }
                toolbar.findViewById<ImageButton>(R.id.btnVideoMirror)?.setBounceClickListener {
                    selectedVideoIndex?.let { index ->
                        if (index == 0) {
                            val isCurrentlyMirrored = viewModel.project.value?.operations?.any { it is EditOperation.MirrorMain && it.isMirrored } ?: false
                            viewModel.updateMainVideoMirror(!isCurrentlyMirrored)
                        } else {
                            viewModel.toggleMergeItemMirror(index - 1)
                        }
                        viewModel.project.value?.let { renderTracks(it) }
                        syncUiWithPlayer()
                    }
                }

                toolbar.findViewById<ImageButton>(R.id.btnVideoMute)?.setBounceClickListener {
                    selectedVideoIndex?.let { index ->
                        viewModel.toggleMuteClip(index)
                        viewModel.project.value?.let { renderTracks(it) }
                        updateVideoMuteButtonState(toolbar, index)
                    }
                }
                toolbar.findViewById<ImageButton>(R.id.btnVideoFilter)?.setBounceClickListener {
                    selectedVideoIndex?.let { index ->
                        showColorFilterSelectionDialog(index)
                    }
                }
                toolbar.findViewById<ImageButton>(R.id.btnVideoMask)?.setBounceClickListener {
                    showMaskBottomSheet()
                }
                toolbar.findViewById<ImageButton>(R.id.btnVideoAdjust)?.setBounceClickListener {
                    selectedVideoIndex?.let { index ->
                        showAdjustSelectionDialog(index)
                    }
                }
                toolbar.findViewById<ImageButton>(R.id.btnVideoAnimation)?.setBounceClickListener {
                    selectedVideoIndex?.let { index ->
                        showTransitionDialog(index)
                    } ?: run {
                        showTransitionDialog(0)
                    }
                }
                toolbar.findViewById<ImageButton>(R.id.btnVideoCrop)?.setBounceClickListener {
                    enterCropEditingMode()
                }
                toolbar.findViewById<ImageButton>(R.id.btnVideoKeyframe)?.setBounceClickListener {
                    enterKeyframeEditingMode(isText = false)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Video editing toolbar not found: ${e.message}")
            null
        }

        speedEditingToolbar = try {
            findViewById<View>(R.id.speedEditingToolbar)?.also { toolbar ->
                toolbar.findViewById<ImageButton>(R.id.btnCloseSpeedSheet)?.setBounceClickListener {
                    hideSpeedEditingToolbar()
                }
                toolbar.findViewById<View>(R.id.speedBtn05)?.setBounceClickListener { applySpeedToSegment(0.5f) }
                toolbar.findViewById<View>(R.id.speedBtn10)?.setBounceClickListener { applySpeedToSegment(1.0f) }
                toolbar.findViewById<View>(R.id.speedBtn15)?.setBounceClickListener { applySpeedToSegment(1.5f) }
                toolbar.findViewById<View>(R.id.speedBtn20)?.setBounceClickListener { applySpeedToSegment(2.0f) }

                val slider = toolbar.findViewById<com.google.android.material.slider.Slider>(R.id.sliderCustomSpeed)
                val tvCustomSpeed = toolbar.findViewById<TextView>(R.id.tvCustomSpeedValue)
                
                slider?.addOnChangeListener { _, value, _ ->
                    val roundedValue = String.format(java.util.Locale.US, "%.1f", value).toFloat()
                    tvCustomSpeed?.text = "${roundedValue}x"
                }

                slider?.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
                    override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                        val roundedValue = String.format(java.util.Locale.US, "%.1f", slider.value).toFloat()
                        applySpeedToSegment(roundedValue)
                    }
                })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Speed editing toolbar not found: ${e.message}")
            null
        }

        audioEditingToolbar = try {
            findViewById<View>(R.id.audioEditingToolbar)?.also { toolbar ->
                toolbar.findViewById<ImageButton>(R.id.btnAudioDone)?.setBounceClickListener {
                    exitAudioEditingMode()
                }
                toolbar.findViewById<ImageButton>(R.id.btnAudioCancel)?.setBounceClickListener {
                    exitAudioEditingMode()
                }
                toolbar.findViewById<ImageButton>(R.id.btnAudioDelete)?.setBounceClickListener {
                    MaterialAlertDialogBuilder(this@VideoEditingActivity)
                        .setTitle("Delete Audio")
                        .setMessage("Are you sure you want to delete this audio track?")
                        .setPositiveButton("Delete") { _, _ ->
                            viewModel.selectedOperationId.value?.let { id ->
                                viewModel.deleteOperation(id)
                            }
                            exitAudioEditingMode()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                toolbar.findViewById<ImageButton>(R.id.btnAudioBeats)?.setBounceClickListener {
                    val id = viewModel.selectedOperationId.value ?: return@setBounceClickListener
                    val project = viewModel.project.value ?: return@setBounceClickListener
                    val op = project.operations.find { it is com.vikash.vidopro.models.EditOperation.AddBackgroundAudio && it.id == id } as? com.vikash.vidopro.models.EditOperation.AddBackgroundAudio ?: return@setBounceClickListener
                    
                    if (op.beats.isNotEmpty()) {
                        // Toggle off if already detected
                        viewModel.updateOperation(op.copy(beats = emptyList()))
                        android.widget.Toast.makeText(this@VideoEditingActivity, R.string.toast_beats_cleared, android.widget.Toast.LENGTH_SHORT).show()
                        toolbar.findViewById<ImageButton>(R.id.btnAudioBeats)?.setColorFilter(getColor(R.color.toolTextInactive))
                        return@setBounceClickListener
                    }
                    
                    android.widget.Toast.makeText(this@VideoEditingActivity, R.string.toast_analyzing_beats, android.widget.Toast.LENGTH_SHORT).show()
                    com.vikash.vidopro.utils.AudioAnalyzer.detectBeats(this@VideoEditingActivity, op.audioUri) { beats ->
                        runOnUiThread {
                            if (beats.isNotEmpty()) {
                                viewModel.updateOperation(op.copy(beats = beats))
                                android.widget.Toast.makeText(this@VideoEditingActivity, "Found ${beats.size} beats!", android.widget.Toast.LENGTH_SHORT).show()
                                toolbar.findViewById<ImageButton>(R.id.btnAudioBeats)?.setColorFilter(getColor(R.color.colorPrimary))
                            } else {
                                android.widget.Toast.makeText(this@VideoEditingActivity, R.string.toast_no_clear_beats_found, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                toolbar.findViewById<ImageButton>(R.id.btnAudioDucking)?.setBounceClickListener {
                    val id = viewModel.selectedOperationId.value ?: return@setBounceClickListener
                    val project = viewModel.project.value ?: return@setBounceClickListener
                    val op = project.operations.find { it is com.vikash.vidopro.models.EditOperation.AddBackgroundAudio && it.id == id } as? com.vikash.vidopro.models.EditOperation.AddBackgroundAudio ?: return@setBounceClickListener

                    val newDucking = !op.ducking
                    viewModel.updateOperation(op.copy(ducking = newDucking))
                    
                    val color = if (newDucking) getColor(R.color.colorPrimary) else getColor(R.color.toolTextInactive)
                    toolbar.findViewById<ImageButton>(R.id.btnAudioDucking)?.setColorFilter(color)
                    val msg = if (newDucking) "Audio ducking enabled" else "Audio ducking disabled"
                    android.widget.Toast.makeText(this@VideoEditingActivity, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
                toolbar.findViewById<ImageButton>(R.id.btnAudioFade)?.setBounceClickListener {
                    val id = viewModel.selectedOperationId.value ?: return@setBounceClickListener
                    val project = viewModel.project.value ?: return@setBounceClickListener
                    val op = project.operations.find { it is com.vikash.vidopro.models.EditOperation.AddBackgroundAudio && it.id == id } as? com.vikash.vidopro.models.EditOperation.AddBackgroundAudio ?: return@setBounceClickListener
                    showFadeDialog(op)
                }
                toolbar.findViewById<ImageButton>(R.id.btnAudioVolume)?.setBounceClickListener {
                    val row = toolbar.findViewById<View>(R.id.audioVolumeSliderRow)
                    if (row != null) {
                        row.visibility = if (row.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                }
                toolbar.findViewById<ImageButton>(R.id.btnAudioTrim)?.setBounceClickListener {
                    val row = toolbar.findViewById<View>(R.id.audioTrimRow)
                    if (row != null) {
                        row.visibility = if (row.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                }
                toolbar.findViewById<ImageButton>(R.id.btnAudioSpeed)?.setBounceClickListener {
                    android.widget.Toast.makeText(this@VideoEditingActivity, "Audio Speed: Standard 1.0x", android.widget.Toast.LENGTH_SHORT).show()
                }
                val volumeSlider = toolbar.findViewById<com.google.android.material.slider.Slider>(R.id.audioVolumeSlider)
                val tvVolumeValue = toolbar.findViewById<TextView>(R.id.tvAudioVolumeValue)
                volumeSlider?.addOnChangeListener { _, value, _ ->
                    tvVolumeValue?.text = "${(value * 100).toInt()}%"
                    viewModel.selectedOperationId.value?.let { id ->
                        val project = viewModel.project.value ?: return@addOnChangeListener
                        val op = project.operations.find { it is com.vikash.vidopro.models.EditOperation.AddBackgroundAudio && it.id == id } as? com.vikash.vidopro.models.EditOperation.AddBackgroundAudio
                        if (op != null) {
                            viewModel.updateOperation(op.copy(volume = value))
                        }
                    }
                }

                val trimTrack = toolbar.findViewById<com.vikash.vidopro.customviews.TrackTrimView>(R.id.audioTrimTrack)
                val tvTrimValue = toolbar.findViewById<TextView>(R.id.tvAudioTrimValues)
                
                trimTrack?.onTrimChanged = { startMs, endMs, _ ->
                    tvTrimValue?.text = "${formatDuration(startMs.toInt())} - ${formatDuration(endMs.toInt())}"
                    viewModel.selectedOperationId.value?.let { id ->
                        val project = viewModel.project.value ?: return@let
                        val op = project.operations.find { it is com.vikash.vidopro.models.EditOperation.AddBackgroundAudio && it.id == id } as? com.vikash.vidopro.models.EditOperation.AddBackgroundAudio
                        if (op != null) {
                            viewModel.updateOperation(op.copy(
                                internalStartMs = startMs,
                                internalEndMs = endMs
                            ))
                        }
                    }
                }

                trimTrack?.onTrimAdjustingWithDelta = { startMs, endMs, _, _ ->
                    tvTrimValue?.text = "${formatDuration(startMs.toInt())} - ${formatDuration(endMs.toInt())}"
                }
                
                val btnAudioPreviewPlay = toolbar.findViewById<ImageButton>(R.id.btnAudioPreviewPlay)
                btnAudioPreviewPlay?.setBounceClickListener {
                    if (audioPreviewPlayer?.isPlaying == true) {
                        audioPreviewPlayer?.stop()
                        audioPreviewPlayer?.release()
                        audioPreviewPlayer = null
                        btnAudioPreviewPlay.setImageResource(R.drawable.ic_play_24)
                        return@setBounceClickListener
                    }

                    viewModel.selectedOperationId.value?.let { id ->
                        val project = viewModel.project.value ?: return@setBounceClickListener
                        val op = project.operations.find { it is com.vikash.vidopro.models.EditOperation.AddBackgroundAudio && it.id == id } as? com.vikash.vidopro.models.EditOperation.AddBackgroundAudio
                        if (op != null) {
                            try {
                                audioPreviewPlayer = android.media.MediaPlayer().apply {
                                    setDataSource(this@VideoEditingActivity, op.audioUri)
                                    prepare()
                                    seekTo(op.internalStartMs.toInt())
                                    start()
                                    btnAudioPreviewPlay.setImageResource(R.drawable.ic_pause_24)
                                    
                                    val durationToPlay = if (op.internalEndMs > 0 && op.internalEndMs > op.internalStartMs) {
                                        op.internalEndMs - op.internalStartMs
                                    } else {
                                        op.originalDurationMs - op.internalStartMs
                                    }
                                    
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        try {
                                            if (audioPreviewPlayer === this && isPlaying) {
                                                stop()
                                                release()
                                                if (audioPreviewPlayer === this) {
                                                    audioPreviewPlayer = null
                                                }
                                                btnAudioPreviewPlay.setImageResource(R.drawable.ic_play_24)
                                            }
                                        } catch (e: Exception) {
                                            // Player was already released or invalid state
                                        }
                                    }, durationToPlay)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to preview audio segment", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio editing toolbar not found: ${e.message}")
            null
        }

        cropEditingToolbar = try {
            findViewById<View>(R.id.cropEditingToolbar)?.also { toolbar ->
                toolbar.findViewById<ImageButton>(R.id.btnCloseSheet)?.setBounceClickListener {
                    val currentCrop = viewModel.project.value?.operations
                        ?.filterIsInstance<com.vikash.vidopro.models.EditOperation.Crop>()
                        ?.lastOrNull()
                    if (currentCrop != null) {
                        val init = initialCropOperation
                        if (init == null) {
                            viewModel.removeOperation(currentCrop.id)
                        } else {
                            viewModel.addCropOperation(
                                aspectRatio = init.aspectRatio,
                                xFraction = init.xFraction,
                                yFraction = init.yFraction,
                                wFraction = init.wFraction,
                                hFraction = init.hFraction
                            )
                        }
                    }
                    exitCropEditingMode()
                }
                toolbar.findViewById<ImageButton>(R.id.btnCropDone)?.setBounceClickListener {
                    exitCropEditingMode()
                }
                toolbar.findViewById<LinearLayout>(R.id.frameAspectRatio1)?.setBounceClickListener {
                    cropOverlayView?.visibility = View.GONE
                    val bounds = getPresetCropBounds("16:9")
                    viewModel.addCropOperation("16:9", bounds.left, bounds.top, bounds.width(), bounds.height())
                    updateCropUi("16:9")
                }
                toolbar.findViewById<LinearLayout>(R.id.frameAspectRatio2)?.setBounceClickListener {
                    cropOverlayView?.visibility = View.GONE
                    val bounds = getPresetCropBounds("9:16")
                    viewModel.addCropOperation("9:16", bounds.left, bounds.top, bounds.width(), bounds.height())
                    updateCropUi("9:16")
                }
                toolbar.findViewById<LinearLayout>(R.id.frameAspectRatio3)?.setBounceClickListener {
                    cropOverlayView?.visibility = View.GONE
                    val bounds = getPresetCropBounds("1:1")
                    viewModel.addCropOperation("1:1", bounds.left, bounds.top, bounds.width(), bounds.height())
                    updateCropUi("1:1")
                }
                toolbar.findViewById<LinearLayout>(R.id.frameAspectRatio4_5)?.setBounceClickListener {
                    cropOverlayView?.visibility = View.GONE
                    val bounds = getPresetCropBounds("4:5")
                    viewModel.addCropOperation("4:5", bounds.left, bounds.top, bounds.width(), bounds.height())
                    updateCropUi("4:5")
                }
                toolbar.findViewById<LinearLayout>(R.id.frameAspectRatioCustom)?.setBounceClickListener {
                    val existing = viewModel.project.value?.operations
                        ?.filterIsInstance<com.vikash.vidopro.models.EditOperation.Crop>()
                        ?.lastOrNull()
                    
                    val x: Float
                    val y: Float
                    val w: Float
                    val h: Float
                    if (existing != null) {
                        x = existing.xFraction
                        y = existing.yFraction
                        w = existing.wFraction
                        h = existing.hFraction
                    } else {
                        val bounds = getPresetCropBounds("16:9")
                        x = bounds.left
                        y = bounds.top
                        w = bounds.width()
                        h = bounds.height()
                    }
                    
                    cropOverlayView?.setCropBounds(x, y, w, h)
                    cropOverlayView?.visibility = View.VISIBLE
                    viewModel.addCropOperation("Custom", x, y, w, h)
                    updateCropUi("Custom")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Crop editing toolbar not found: ${e.message}")
            null
        }

        subtitlesEditingToolbar = try {
            findViewById<View>(R.id.subtitlesEditingToolbar)?.also { toolbar ->
                toolbar.findViewById<ImageButton>(R.id.btnCloseSubtitlesSheet)?.setBounceClickListener {
                    exitSubtitlesEditingMode()
                }
                toolbar.findViewById<Button>(R.id.btnUploadSrt)?.setBounceClickListener {
                    pickSrtFile()
                }
                toolbar.findViewById<View>(R.id.btnChangeSrt)?.setBounceClickListener {
                    pickSrtFile()
                }
                toolbar.findViewById<Button>(R.id.btnDeleteSrt)?.setBounceClickListener {
                    MaterialAlertDialogBuilder(this@VideoEditingActivity)
                        .setTitle("Delete Subtitles")
                        .setMessage("Are you sure you want to remove the subtitles track?")
                        .setPositiveButton("Delete") { _, _ ->
                            removeSubtitles()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                toolbar.findViewById<Button>(R.id.btnSaveSubtitles)?.setBounceClickListener {
                    exitSubtitlesEditingMode()
                }
                toolbar.findViewById<Slider>(R.id.subtitleFontSizeSlider)?.addOnChangeListener { _, value, fromUser ->
                    if (fromUser) {
                        val project = viewModel.project.value
                        val subOp = project?.operations?.find { it is EditOperation.AddSubtitles } as? EditOperation.AddSubtitles
                        if (subOp != null) {
                            val newSize = value.toInt()
                            updateSubtitleOp(subOp.copy(fontSize = newSize))
                            toolbar.findViewById<TextView>(R.id.tvSubtitleFontSizeValue)?.text = "$newSize"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Subtitles editing toolbar not found: ${e.message}")
            null
        }

        backgroundEditingToolbar = try {
            findViewById<View>(R.id.backgroundEditingToolbar)?.also { toolbar ->
                toolbar.findViewById<View>(R.id.btnBackFromBackground)?.setBounceClickListener {
                    exitCanvasBackgroundEditingMode()
                }
                toolbar.findViewById<View>(R.id.btnBackgroundDone)?.setBounceClickListener {
                    exitCanvasBackgroundEditingMode()
                }
                toolbar.findViewById<View>(R.id.btnBackgroundColor)?.setBounceClickListener {
                    showCustomColorPicker("#000000") { hexColor ->
                        viewModel.updateCanvasBackgroundOperation(
                            type = EditOperation.CanvasBackground.BackgroundType.COLOR,
                            colorHex = hexColor
                        )
                        updateCanvasBackgroundPreview()
                    }
                }
                toolbar.findViewById<View>(R.id.btnBackgroundBlur)?.setBounceClickListener {
                    viewModel.updateCanvasBackgroundOperation(
                        type = EditOperation.CanvasBackground.BackgroundType.BLUR,
                        blurRadius = 25
                    )
                    updateCanvasBackgroundPreview()
                }
                toolbar.findViewById<View>(R.id.btnBackgroundImage)?.setBounceClickListener {
                    pickBackgroundImage()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Background editing toolbar not found: ${e.message}")
            null
        }

        keyframeEditingToolbar = try {
            findViewById<View>(R.id.keyframeEditingToolbar)?.also { toolbar ->
                toolbar.findViewById<ImageButton>(R.id.btnKeyframeCancel)?.setBounceClickListener {
                    exitKeyframeEditingMode()
                }
                toolbar.findViewById<ImageButton>(R.id.btnKeyframeDone)?.setBounceClickListener {
                    exitKeyframeEditingMode()
                }
                toolbar.findViewById<Button>(R.id.btnKeyframeProperty)?.setBounceClickListener {
                    val btn = toolbar.findViewById<Button>(R.id.btnKeyframeProperty)
                    if (btn != null) {
                        showKeyframePropertyMenu(btn)
                    }
                }
                toolbar.findViewById<ImageButton>(R.id.btnKeyframeAction)?.setBounceClickListener {
                    handleKeyframeActionClick()
                }
                toolbar.findViewById<com.google.android.material.slider.Slider>(R.id.sliderKeyframeValue)?.addOnChangeListener { _, value, fromUser ->
                    if (fromUser) {
                        handleKeyframeSliderChange(value)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Keyframe editing toolbar not found: ${e.message}")
            null
        }

        findViewById<ImageButton>(R.id.btnHome)?.setBounceClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        findViewById<ImageView>(R.id.btnClose)?.setBounceClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        layoutSaveSplit = findViewById(R.id.layoutSaveSplit)
        btnSaveText = findViewById(R.id.btnSaveText)
        btnSaveDropdown = findViewById(R.id.btnSaveDropdown)
        editingControlsWrapper = findViewById(R.id.editingControlsWrapper)

        tvPreviewBadge = findViewById(R.id.tvPreviewBadge)

        btnSaveText.setBounceClickListener {
            saveAction()
        }

        btnSaveDropdown.setBounceClickListener {
            showQualitySettingsDialog()
        }

        // Tool Buttons with proper scoping
        findViewById<ImageButton>(R.id.btnText).setBounceClickListener {
            setActiveToolButton(R.id.btnText)
            textAction()
        }
        findViewById<ImageButton>(R.id.btnMediaOverlay)?.setBounceClickListener {
            setActiveToolButton(R.id.btnMediaOverlay)
            mediaOverlayAction()
        }

        findViewById<ImageButton>(R.id.btnAudio).setBounceClickListener {
            setActiveToolButton(R.id.btnAudio)
            audioAction()
        }
        findViewById<ImageButton>(R.id.btnCrop).setBounceClickListener {
            setActiveToolButton(R.id.btnCrop)
            cropAction()
        }

        findViewById<ImageButton>(R.id.btnSubtitles).setBounceClickListener {
            setActiveToolButton(R.id.btnSubtitles)
            subtitlesAction()
        }

        findViewById<ImageButton>(R.id.btnVoiceOver)?.setBounceClickListener {
            setActiveToolButton(R.id.btnVoiceOver)
            voiceOverAction()
        }

        findViewById<ImageButton>(R.id.btnCanvasBackground)?.setBounceClickListener {
            setActiveToolButton(R.id.btnCanvasBackground)
            canvasBackgroundAction()
        }

        findViewById<ImageButton>(R.id.btnEffects)?.setBounceClickListener {
            setActiveToolButton(R.id.btnEffects)
            selectedVideoIndex?.let { idx ->
                showTransitionDialog(idx)
            } ?: run {
                showTransitionDialog(0)
            }
        }

        findViewById<ImageButton>(R.id.btnFilters)?.setBounceClickListener {
            setActiveToolButton(R.id.btnFilters)
            selectedVideoIndex?.let { idx ->
                showColorFilterSelectionDialog(idx)
            } ?: run {
                showColorFilterSelectionDialog(0)
            }
        }

        findViewById<ImageButton>(R.id.btnDraw)?.setBounceClickListener {
            setActiveToolButton(R.id.btnDraw)
            openHandwritingMode()
        }

        try {
            btnUndo = findViewById(R.id.btnUndo)
            btnRedo = findViewById(R.id.btnRedo)
            btnPreview = findViewById(R.id.btnPreview)
            btnUndo.setBounceClickListener {
                if (isShowingPreview) dismissPreview()
                viewModel.undo()
            }
            btnRedo.setBounceClickListener {
                if (isShowingPreview) dismissPreview()
                viewModel.redo()
            }
            btnPreview.setBounceClickListener {
                if (isShowingPreview) {
                    dismissPreview()
                } else {
                    renderSegmentedPreview()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Undo/Redo/Preview buttons not found in layout (optional)")
        }

        try {
            lottieAnimationView.playAnimation()
        } catch (e: Exception) {
            Log.e("LottieError", "Error loading Lottie animation: ${e.message}")
        }
    }

    private fun updateUIInteractionState() {
        val uiState = viewModel.uiState.value
        val isBusy = uiState.isExporting || isRenderingPreview
        
        if (::previewLoadingOverlay.isInitialized) {
            previewLoadingOverlay.visibility = if (isRenderingPreview) View.VISIBLE else View.GONE
        }
        
        val alpha = if (isBusy) 0.4f else 1.0f
        
        if (::editingControlsWrapper.isInitialized) {
            editingControlsWrapper.alpha = alpha
            for (i in 0 until editingControlsWrapper.childCount) {
                editingControlsWrapper.getChildAt(i)?.isEnabled = !isBusy
            }
        }
        
        val hasOps = viewModel.project.value?.hasOperations() == true
        if (::layoutSaveSplit.isInitialized && ::btnSaveText.isInitialized && ::btnSaveDropdown.isInitialized) {
            layoutSaveSplit.alpha = if (isBusy || !hasOps) 0.4f else 1.0f
            btnSaveText.isEnabled = !isBusy && hasOps
            btnSaveDropdown.isEnabled = !isBusy && hasOps
        }
        
        if (::btnPreview.isInitialized) {
            val hasPreviewableOps = viewModel.project.value?.operations?.any { 
                it is EditOperation.Crop || it is EditOperation.AddText
            } == true
            btnPreview.isEnabled = !isBusy && hasPreviewableOps
            btnPreview.alpha = if (isBusy) 0.4f else if (hasPreviewableOps) 1.0f else 0.5f
            if (isShowingPreview) {
                btnPreview.setColorFilter(getColor(R.color.colorPrimary))
            } else {
                btnPreview.setColorFilter(getColor(R.color.colorOnPrimary))
            }
        }
        
        customVideoSeeker.isEnabled = !isBusy
    }

    private fun setActiveToolButton(activeId: Int) {
        if (isShowingPreview) dismissPreview()
        val toolIds = listOf(
            R.id.btnText,
            R.id.btnMediaOverlay,
            R.id.btnDraw,
            R.id.btnAudio,
            R.id.btnCrop,
            R.id.btnSubtitles,
            R.id.btnVoiceOver,
            R.id.btnCanvasBackground,
            R.id.btnEffects,
            R.id.btnFilters
        )
        for (id in toolIds) {
            val btn = findViewById<ImageButton>(id) ?: continue
            btn.setBackgroundResource(if (id == activeId) R.drawable.tool_button_active else R.drawable.tool_button_inactive)
            val parentLayout = btn.parent as? android.widget.LinearLayout
            val textView = parentLayout?.getChildAt(1) as? TextView
            textView?.setTextColor(
                if (id == activeId) getColor(R.color.toolTextActive) else getColor(R.color.toolTextInactive)
            )
        }
    }

    private fun observeViewModelState() {
        lifecycleScope.launch {
            viewModel.uiState.collect { uiState ->
                if (uiState.isExporting) {
                    loadingScreen.visibility = View.GONE
                    val progress = uiState.exportProgress
                    exportScreen.findViewById<TextView>(R.id.tvExportPercentage)?.text = "$progress%"
                    exportScreen.findViewById<android.widget.ProgressBar>(R.id.exportProgressBar)?.progress = progress
                } else {
                    val emptyStateVisible = findViewById<View>(R.id.emptyProjectState)?.visibility == View.VISIBLE
                    if (isImportLoading || (!isVideoLoaded && !emptyStateVisible)) {
                        showLoading(getString(R.string.loading), getString(R.string.loading_tag))
                    } else {
                        hideLoading()
                    }
                }

                exportScreen.visibility = if (uiState.isExporting) View.VISIBLE else View.GONE

                if (::btnUndo.isInitialized && ::btnRedo.isInitialized) {
                    val isProjectEmpty = findViewById<View>(R.id.emptyProjectState)?.visibility == View.VISIBLE
                    btnUndo.isEnabled = uiState.canUndo && !isProjectEmpty
                    btnUndo.alpha = if (uiState.canUndo && !isProjectEmpty) 1.0f else 0.5f
                    btnRedo.isEnabled = uiState.canRedo && !isProjectEmpty
                    btnRedo.alpha = if (uiState.canRedo && !isProjectEmpty) 1.0f else 0.5f
                }

                uiState.errorMessage?.let { error ->
                    showError(error)
                    viewModel.clearError()
                }

                if (uiState.pendingOperationCount > 0) {
                    Log.d(TAG, "Pending operations: ${uiState.pendingOperationCount}")
                }
                updateUIInteractionState()
            }
        }

        lifecycleScope.launch {
            viewModel.selectedOperationId.collect { selectedId ->
                if (selectedId != null && isShowingPreview) dismissPreview()
                textOverlayView?.hiddenOperationId = selectedId
                imageOverlayView?.hiddenOperationId = selectedId
                
                if (selectedId != null) {
                    if (isKeyframeEditingMode) {
                        exitKeyframeEditingMode()
                    }

                    // Reset editing modes to prevent multiple active editing toolbars/states
                    draggableTextOverlay?.deactivate()
                    draggableImageOverlay?.deactivate()
                    exitTextEditingMode()
                    exitImageEditingMode()

                    val op = viewModel.project.value?.operations?.find {
                        when (it) {
                            is EditOperation.AddText -> it.id == selectedId
                            is EditOperation.AddImageOverlay -> it.id == selectedId
                            is EditOperation.AddBackgroundAudio -> it.id == selectedId
                            else -> false
                        }
                    }
                    when (op) {
                        is EditOperation.AddText -> {
                            draggableTextOverlay?.activateForEdit(op)
                            enterTextEditingMode(isReEditing = true)
                        }
                        is EditOperation.AddImageOverlay -> {
                            draggableImageOverlay?.activateForEdit(op)
                            enterImageEditingModeForEdit(op.rotationAngle)
                        }
                        is EditOperation.AddBackgroundAudio -> {
                            enterAudioEditingMode(op)
                        }
                        else -> {}
                    }
                } else {
                    draggableTextOverlay?.deactivate()
                    draggableImageOverlay?.deactivate()
                    exitTextEditingMode()
                    exitImageEditingMode()
                    exitAudioEditingMode()
                }
                
                // Re-render tracks so the selection highlight updates
                viewModel.project.value?.let { renderTracks(it) }
                updateLayerReorderButtons()
            }
        }

        lifecycleScope.launch {
            viewModel.project.collect { project ->
                if (project != null) {
                    Log.d(TAG, "Project updated with ${project.getOperationCount()} operations")

                    if (project.sourceUri.scheme == "file") {
                        val projectSourcePath = project.sourceUri.path
                        if (projectSourcePath != null && (!::tempInputFile.isInitialized || tempInputFile.absolutePath != projectSourcePath)) {
                            tempInputFile = File(projectSourcePath)
                            videoFileName = tempInputFile.name
                            try {
                                val r = android.media.MediaMetadataRetriever()
                                r.setDataSource(tempInputFile.absolutePath)
                                originalMainVideoDurationMs = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                                r.release()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error getting original duration: ${e.message}")
                            }
                        }
                    }

                    updateUIInteractionState()

                    textOverlayView?.let { overlay ->
                        val overlayOps = project.operations.filter { it is EditOperation.AddText }
                        overlay.setOverlayOperations(overlayOps)
                    }

                    imageOverlayView?.let { overlay ->
                        val overlayOps = project.operations.filterIsInstance<EditOperation.AddImageOverlay>()
                        overlay.setImageOperations(overlayOps)
                    }

                    textOverlayView?.let { overlay ->
                        val subOp = project.operations.filterIsInstance<EditOperation.AddSubtitles>().firstOrNull()
                        overlay.subtitleOperation = subOp
                        overlay.setSubtitleCues(subOp?.cues ?: emptyList())
                    }

                    if (isSubtitlesEditingActive) {
                        updateSubtitlesUi()
                    }

                    val cropOps = project.operations.filterIsInstance<EditOperation.Crop>()
                    if (cropOps.isNotEmpty()) {
                        applyCropPreview(cropOps.last().aspectRatio)
                    } else {
                        resetCropPreview()
                    }
                    
                    updateCanvasBackgroundPreview()
                    renderTracks(project)
                    updateLayerReorderButtons()
                }
            }
        }
    }

    private fun updateLayerReorderButtons() {
        if (!::btnMoveLayerUp.isInitialized || !::btnMoveLayerDown.isInitialized) return
        
        val selectedId = viewModel.selectedOperationId.value
        val project = viewModel.project.value
        
        if (selectedId == null || project == null) {
            btnMoveLayerUp.visibility = View.GONE
            btnMoveLayerDown.visibility = View.GONE
            return
        }
        
        val overlayOps = project.operations.filter {
            it is EditOperation.AddText || it is EditOperation.AddImageOverlay
        }
        
        val selectedIndex = overlayOps.indexOfFirst { op ->
            when (op) {
                is EditOperation.AddText -> op.id == selectedId
                is EditOperation.AddImageOverlay -> op.id == selectedId
                else -> false
            }
        }
        
        if (selectedIndex == -1) {
            btnMoveLayerUp.visibility = View.GONE
            btnMoveLayerDown.visibility = View.GONE
            return
        }
        
        val canMoveUp = selectedIndex < overlayOps.size - 1
        val canMoveDown = selectedIndex > 0
        
        btnMoveLayerUp.visibility = if (canMoveUp) View.VISIBLE else View.GONE
        btnMoveLayerDown.visibility = if (canMoveDown) View.VISIBLE else View.GONE
    }

    private fun applyCropPreview(aspectRatio: String) {
        val cropEditingActive = cropEditingToolbar?.visibility == View.VISIBLE

        if (cropEditingActive) {
            resetCropPreview()
            return
        }

        val canvasContainer = findViewById<FrameLayout>(R.id.canvasContainer) ?: return
        val cropOp = viewModel.project.value?.operations
            ?.filterIsInstance<com.vikash.vidopro.models.EditOperation.Crop>()
            ?.lastOrNull()

        val bounds = when (aspectRatio) {
            "16:9", "9:16", "1:1", "4:5", "21:9" -> {
                if (cropOp != null && (cropOp.xFraction > 0f || cropOp.yFraction > 0f || cropOp.wFraction < 1f || cropOp.hFraction < 1f)) {
                    android.graphics.RectF(
                        cropOp.xFraction,
                        cropOp.yFraction,
                        cropOp.xFraction + cropOp.wFraction,
                        cropOp.yFraction + cropOp.hFraction
                    )
                } else {
                    getPresetCropBounds(aspectRatio)
                }
            }
            "Custom" -> {
                if (cropOp != null) {
                    android.graphics.RectF(
                        cropOp.xFraction,
                        cropOp.yFraction,
                        cropOp.xFraction + cropOp.wFraction,
                        cropOp.yFraction + cropOp.hFraction
                    )
                } else {
                    android.graphics.RectF(0f, 0f, 1f, 1f)
                }
            }
            else -> {
                resetCropPreview()
                return
            }
        }

        val xFrac = bounds.left.coerceIn(0f, 1f)
        val yFrac = bounds.top.coerceIn(0f, 1f)
        val wFrac = bounds.width().coerceIn(0.001f, 1f)
        val hFrac = bounds.height().coerceIn(0.001f, 1f)

        val activeParent = (canvasContainer.parent as? View) ?: playerContainer
        activeParent.post {
            val containerWidth = activeParent.width.toFloat()
            val containerHeight = activeParent.height.toFloat()
            if (containerWidth <= 0f || containerHeight <= 0f) return@post

            val baseVideoRatio = if (primaryVideoAspectRatio > 0f) primaryVideoAspectRatio else {
                val format = if (::player.isInitialized) player.videoFormat else null
                if (format != null && format.width > 0 && format.height > 0) {
                    val rot = format.rotationDegrees
                    val vw = if (rot == 90 || rot == 270) format.height else format.width
                    val vh = if (rot == 90 || rot == 270) format.width else format.height
                    vw.toFloat() / vh.toFloat()
                } else 16f / 9f
            }

            val croppedAspectRatio = (baseVideoRatio * wFrac) / hFrac
            val containerRatio = containerWidth / containerHeight

            val canvasW: Float
            val canvasH: Float
            if (croppedAspectRatio > containerRatio) {
                canvasW = containerWidth
                canvasH = containerWidth / croppedAspectRatio
            } else {
                canvasW = containerHeight * croppedAspectRatio
                canvasH = containerHeight
            }

            // 1. Size canvasContainer to the cropped aspect ratio box
            val canvasLp = canvasContainer.layoutParams as FrameLayout.LayoutParams
            canvasLp.width = canvasW.toInt()
            canvasLp.height = canvasH.toInt()
            canvasLp.gravity = Gravity.CENTER
            canvasContainer.layoutParams = canvasLp

            // Enable clipping on canvasContainer and mainVideoMaskContainer
            canvasContainer.clipChildren = true
            canvasContainer.clipToPadding = true
            mainVideoMaskContainer?.clipChildren = true
            mainVideoMaskContainer?.clipToPadding = true

            // 2. Scale playerView so the crop box fills canvasContainer
            val fullWidth = canvasW / wFrac
            val fullHeight = canvasH / hFrac

            val playerLp = playerView.layoutParams as FrameLayout.LayoutParams
            playerLp.width = fullWidth.toInt()
            playerLp.height = fullHeight.toInt()
            playerLp.gravity = Gravity.TOP or Gravity.START
            playerView.layoutParams = playerLp
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL

            playerView.translationX = -fullWidth * xFrac
            playerView.translationY = -fullHeight * yFrac

            val hwCanvas = findViewById<com.vikash.vidopro.customviews.HandwritingCanvasView>(R.id.handwritingCanvasView)
            val overlays = listOf(textOverlayView, draggableTextOverlay, imageOverlayView, draggableImageOverlay, cropOverlayView, videoMaskOverlayView, hwCanvas)
            for (overlay in overlays) {
                overlay?.let {
                    val overlayParams = it.layoutParams as FrameLayout.LayoutParams
                    overlayParams.width = FrameLayout.LayoutParams.MATCH_PARENT
                    overlayParams.height = FrameLayout.LayoutParams.MATCH_PARENT
                    overlayParams.gravity = Gravity.CENTER
                    it.layoutParams = overlayParams
                    it.translationX = 0f
                    it.translationY = 0f
                }
            }

            textOverlayView?.setVideoSize(canvasW.toInt(), canvasH.toInt())
            draggableTextOverlay?.setVideoSize(canvasW.toInt(), canvasH.toInt())
            imageOverlayView?.setVideoSize(canvasW.toInt(), canvasH.toInt())
            draggableImageOverlay?.setVideoSize(canvasW.toInt(), canvasH.toInt())
            cropOverlayView?.setVideoSize(canvasW.toInt(), canvasH.toInt())
        }
    }

    private fun resetCropPreview() {
        val canvasContainer = findViewById<FrameLayout>(R.id.canvasContainer) ?: return
        val activeParent = (canvasContainer.parent as? View) ?: playerContainer
        activeParent.post {
            val containerWidth = activeParent.width
            val containerHeight = activeParent.height
            if (containerWidth <= 0 || containerHeight <= 0) return@post

            val baseVideoRatio = if (primaryVideoAspectRatio > 0f) primaryVideoAspectRatio else 16f / 9f
            val containerRatio = containerWidth.toFloat() / containerHeight.toFloat()
            var finalWidth = containerWidth
            var finalHeight = containerHeight

            if (baseVideoRatio > containerRatio) {
                finalHeight = (containerWidth / baseVideoRatio).toInt()
            } else {
                finalWidth = (containerHeight * baseVideoRatio).toInt()
            }

            val canvasLp = canvasContainer.layoutParams as FrameLayout.LayoutParams
            canvasLp.width = finalWidth
            canvasLp.height = finalHeight
            canvasLp.gravity = Gravity.CENTER
            canvasContainer.layoutParams = canvasLp

            val playerLp = playerView.layoutParams as FrameLayout.LayoutParams
            playerLp.width = FrameLayout.LayoutParams.MATCH_PARENT
            playerLp.height = FrameLayout.LayoutParams.MATCH_PARENT
            playerLp.gravity = Gravity.CENTER
            playerView.layoutParams = playerLp
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            playerView.translationX = 0f
            playerView.translationY = 0f

            val hwCanvas = findViewById<com.vikash.vidopro.customviews.HandwritingCanvasView>(R.id.handwritingCanvasView)
            val overlays = listOf(textOverlayView, draggableTextOverlay, imageOverlayView, draggableImageOverlay, cropOverlayView, videoMaskOverlayView, hwCanvas)
            for (overlay in overlays) {
                overlay?.let {
                    val overlayParams = it.layoutParams as FrameLayout.LayoutParams
                    overlayParams.width = FrameLayout.LayoutParams.MATCH_PARENT
                    overlayParams.height = FrameLayout.LayoutParams.MATCH_PARENT
                    overlayParams.gravity = Gravity.CENTER
                    it.layoutParams = overlayParams
                    it.translationX = 0f
                    it.translationY = 0f
                }
            }

            textOverlayView?.setVideoSize(finalWidth, finalHeight)
            draggableTextOverlay?.setVideoSize(finalWidth, finalHeight)
            imageOverlayView?.setVideoSize(finalWidth, finalHeight)
            draggableImageOverlay?.setVideoSize(finalWidth, finalHeight)
            cropOverlayView?.setVideoSize(finalWidth, finalHeight)
        }
    }

    @SuppressLint("InflateParams")
    private fun trimAction() {
        Toast.makeText(this, R.string.toast_drag_the_handles_on_the_timeli, Toast.LENGTH_LONG).show()
    }

    private fun captureCurrentFrame() {
        closeActiveEditingModes()
        val container = findViewById<FrameLayout>(R.id.playerContainer)
        val tvDuration = findViewById<TextView>(R.id.tvDuration)
        val tvPreviewBadge = findViewById<TextView>(R.id.tvPreviewBadge)
        
        val durationVis = tvDuration?.visibility ?: View.GONE
        val badgeVis = tvPreviewBadge?.visibility ?: View.GONE
        
        tvDuration?.visibility = View.INVISIBLE
        tvPreviewBadge?.visibility = View.INVISIBLE
        
        container.post {
            if (container.width <= 0 || container.height <= 0) {
                tvDuration?.visibility = durationVis
                tvPreviewBadge?.visibility = badgeVis
                return@post
            }
            val bitmap = android.graphics.Bitmap.createBitmap(container.width, container.height, android.graphics.Bitmap.Config.ARGB_8888)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val location = IntArray(2)
                container.getLocationInWindow(location)
                val rect = android.graphics.Rect(location[0], location[1], location[0] + container.width, location[1] + container.height)
                
                try {
                    android.view.PixelCopy.request(window, rect, bitmap, { copyResult ->
                        tvDuration?.visibility = durationVis
                        tvPreviewBadge?.visibility = badgeVis
                        if (copyResult == android.view.PixelCopy.SUCCESS) {
                            saveBitmapToGallery(bitmap)
                        } else {
                            Toast.makeText(this, "Failed to capture frame", Toast.LENGTH_SHORT).show()
                        }
                    }, android.os.Handler(android.os.Looper.getMainLooper()))
                } catch (e: Exception) {
                    e.printStackTrace()
                    tvDuration?.visibility = durationVis
                    tvPreviewBadge?.visibility = badgeVis
                    Toast.makeText(this, "Failed to capture frame", Toast.LENGTH_SHORT).show()
                }
            } else {
                val canvas = android.graphics.Canvas(bitmap)
                val bg = container.background
                if (bg != null) bg.draw(canvas) else canvas.drawColor(android.graphics.Color.BLACK)
                
                val textureView = findTextureView(playerView)
                if (textureView != null) {
                    val videoBitmap = textureView.bitmap
                    if (videoBitmap != null) {
                        canvas.save()
                        canvas.translate(playerView.x + textureView.x, playerView.y + textureView.y)
                        canvas.scale(playerView.scaleX, playerView.scaleY)
                        canvas.drawBitmap(videoBitmap, 0f, 0f, null)
                        canvas.restore()
                    }
                }
                
                val overlays = listOf(R.id.textOverlayView, R.id.imageOverlayView, R.id.cropOverlayView)
                for (id in overlays) {
                    val view = container.findViewById<View>(id)
                    if (view != null && view.visibility == View.VISIBLE) {
                        canvas.save()
                        canvas.translate(view.x, view.y)
                        view.draw(canvas)
                        canvas.restore()
                    }
                }
                
                tvDuration?.visibility = durationVis
                tvPreviewBadge?.visibility = badgeVis
                saveBitmapToGallery(bitmap)
            }
        }
    }

    private fun findTextureView(viewGroup: android.view.ViewGroup): android.view.TextureView? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is android.view.TextureView) return child
            if (child is android.view.ViewGroup) {
                val tv = findTextureView(child)
                if (tv != null) return tv
            }
        }
        return null
    }

    private fun saveBitmapToGallery(bitmap: android.graphics.Bitmap) {
        MediaFileHelper.saveBitmapToGallery(this, bitmap)
    }

    @SuppressLint("InflateParams")
    private fun cropAction() {
        enterCropEditingMode()
    }

    private fun canvasBackgroundAction() {
        enterCanvasBackgroundEditingMode()
    }

    private fun enterCanvasBackgroundEditingMode() {
        closeActiveEditingModes()
        editingControlsWrapper.visibility = View.GONE
        backgroundEditingToolbar?.visibility = View.VISIBLE
    }

    private fun exitCanvasBackgroundEditingMode() {
        backgroundEditingToolbar?.visibility = View.GONE
        editingControlsWrapper.visibility = View.VISIBLE
        setActiveToolButton(-1)
    }

    private var isHandwritingActive = false

    private fun setupHandwritingControls() {
        val btnDraw = findViewById<ImageButton>(R.id.btnDraw)
        btnDraw?.setBounceClickListener {
            setActiveToolButton(R.id.btnDraw)
            openHandwritingMode()
        }

        val handwritingPanel = findViewById<View>(R.id.handwritingPanel) ?: return
        val canvasView = findViewById<HandwritingCanvasView>(R.id.handwritingCanvasView) ?: return
        val dotView = handwritingPanel.findViewById<BrushSizeDotView>(R.id.brushSizeDotView)
        val sliderBrushSize = handwritingPanel.findViewById<com.google.android.material.slider.Slider>(R.id.sliderBrushSize)
        val btnClose = handwritingPanel.findViewById<ImageButton>(R.id.btnCloseHandwriting)
        val btnUndo = handwritingPanel.findViewById<ImageButton>(R.id.btnUndoHandwriting)
        val btnRedo = handwritingPanel.findViewById<ImageButton>(R.id.btnRedoHandwriting)
        val btnClear = handwritingPanel.findViewById<ImageButton>(R.id.btnClearHandwriting)
        val btnDone = handwritingPanel.findViewById<ImageButton>(R.id.btnDoneHandwriting)
        val btnCustomColor = handwritingPanel.findViewById<View>(R.id.btnCustomColor)
        val layoutPalette = handwritingPanel.findViewById<LinearLayout>(R.id.layoutColorPalette)

        val presetHexes = listOf(
            "#FFFFFF", "#000000", "#FF007A", "#FF3B30",
            "#FF9500", "#FFCC00", "#34C759", "#32ADE6",
            "#007AFF", "#AF52DE"
        )

        var selectedHex = "#FF007A"

        fun updateColorSelection(hex: String) {
            selectedHex = hex
            val colorInt = try { Color.parseColor(hex) } catch (e: Exception) { Color.RED }
            canvasView.setBrushColor(colorInt)
            dotView?.setBrushColor(colorInt)

            if (layoutPalette != null) {
                for (i in 1 until layoutPalette.childCount) {
                    val child = layoutPalette.getChildAt(i) as? FrameLayout ?: continue
                    val tagHex = child.tag as? String
                    if (tagHex != null && tagHex.equals(hex, ignoreCase = true)) {
                        child.setBackgroundResource(R.drawable.bg_aspect_ratio_selected)
                        child.foreground = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_transition_border_selected)
                    } else {
                        child.setBackgroundResource(R.drawable.bg_aspect_ratio_item)
                        child.foreground = null
                    }
                }
            }
        }

        if (layoutPalette != null && layoutPalette.childCount <= 1) {
            val density = resources.displayMetrics.density
            val swatchSize = (36 * density).toInt()
            val innerSize = (24 * density).toInt()
            val margin = (4 * density).toInt()

            for (hex in presetHexes) {
                val frame = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(swatchSize, swatchSize).apply {
                        setMargins(margin, 0, margin, 0)
                    }
                    tag = hex
                    setBackgroundResource(R.drawable.bg_aspect_ratio_item)
                    isClickable = true
                    isFocusable = true
                }

                val circleView = View(this).apply {
                    layoutParams = FrameLayout.LayoutParams(innerSize, innerSize, Gravity.CENTER)
                    val drawable = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(try { Color.parseColor(hex) } catch (e: Exception) { Color.WHITE })
                        setStroke((1 * density).toInt(), Color.parseColor("#44FFFFFF"))
                    }
                    background = drawable
                }
                frame.addView(circleView)

                frame.setOnClickListener {
                    updateColorSelection(hex)
                }
                layoutPalette.addView(frame)
            }
        }

        val density = resources.displayMetrics.density
        val minPx = 3f * density
        val maxPx = 60f * density

        sliderBrushSize?.addOnChangeListener { _, value, _ ->
            val fraction = value / 100f
            val sizePx = minPx + fraction * (maxPx - minPx)
            canvasView.setBrushSizePx(sizePx)
            dotView?.setBrushSize(sizePx, minPx, maxPx)
        }

        val initialValue = sliderBrushSize?.value ?: 25f
        val initialSizePx = minPx + (initialValue / 100f) * (maxPx - minPx)
        canvasView.setBrushSizePx(initialSizePx)
        dotView?.setBrushSize(initialSizePx, minPx, maxPx)
        updateColorSelection(selectedHex)

        canvasView.onStrokesChangedListener = {
            btnUndo?.isEnabled = canvasView.canUndo()
            btnUndo?.alpha = if (canvasView.canUndo()) 1.0f else 0.5f
            btnRedo?.isEnabled = canvasView.canRedo()
            btnRedo?.alpha = if (canvasView.canRedo()) 1.0f else 0.5f
        }

        btnUndo?.setBounceClickListener { canvasView.undo() }
        btnRedo?.setBounceClickListener { canvasView.redo() }
        btnClear?.setBounceClickListener { canvasView.clear() }

        btnClose?.setBounceClickListener {
            if (!canvasView.isEmpty()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Discard Handwriting?")
                    .setMessage("Are you sure you want to discard your drawing?")
                    .setPositiveButton("Discard") { _, _ -> closeHandwritingMode() }
                    .setNegativeButton("Keep Editing", null)
                    .show()
            } else {
                closeHandwritingMode()
            }
        }

        btnDone?.setBounceClickListener {
            saveAndCommitHandwriting()
        }

        btnCustomColor?.setBounceClickListener {
            showCustomColorPicker(selectedHex) { customHex ->
                updateColorSelection(customHex)
            }
        }
    }

    private fun getVideoRect(): android.graphics.RectF {
        val activeParent = (canvasContainer.parent as? View) ?: playerContainer
        val containerWidth = activeParent.width.toFloat()
        val containerHeight = activeParent.height.toFloat()
        if (containerWidth <= 0f || containerHeight <= 0f) return android.graphics.RectF(0f, 0f, 1080f, 1920f)

        val baseRatio = if (primaryVideoAspectRatio > 0f) primaryVideoAspectRatio else 16f / 9f
        val containerRatio = containerWidth / containerHeight

        val finalW: Float
        val finalH: Float
        if (baseRatio > containerRatio) {
            finalW = containerWidth
            finalH = containerWidth / baseRatio
        } else {
            finalH = containerHeight
            finalW = containerHeight * baseRatio
        }

        val left = (containerWidth - finalW) / 2f
        val top = (containerHeight - finalH) / 2f
        return android.graphics.RectF(left, top, left + finalW, top + finalH)
    }

    private fun openHandwritingMode() {
        closeActiveEditingModes()
        isHandwritingActive = true
        if (::player.isInitialized && player.isPlaying) {
            player.pause()
            btnPlayPause.setImageResource(R.drawable.ic_play_24)
        }

        val canvasView = findViewById<HandwritingCanvasView>(R.id.handwritingCanvasView)
        val handwritingPanel = findViewById<View>(R.id.handwritingPanel)

        val vRect = getVideoRect()
        canvasView?.setVideoRect(vRect)
        canvasView?.clear()
        canvasView?.visibility = View.VISIBLE
        handwritingPanel?.visibility = View.VISIBLE

        findViewById<android.widget.HorizontalScrollView>(R.id.editingControlsScroll)?.visibility = View.GONE
    }

    private fun closeHandwritingMode() {
        isHandwritingActive = false
        val canvasView = findViewById<HandwritingCanvasView>(R.id.handwritingCanvasView)
        val handwritingPanel = findViewById<View>(R.id.handwritingPanel)

        canvasView?.visibility = View.GONE
        handwritingPanel?.visibility = View.GONE
        findViewById<android.widget.HorizontalScrollView>(R.id.editingControlsScroll)?.visibility = View.VISIBLE
        setActiveToolButton(-1)
    }

    private fun saveAndCommitHandwriting() {
        val canvasView = findViewById<HandwritingCanvasView>(R.id.handwritingCanvasView) ?: return
        if (canvasView.isEmpty()) {
            Toast.makeText(this, "Scribble or draw something first!", Toast.LENGTH_SHORT).show()
            return
        }

        val baseRatio = if (primaryVideoAspectRatio > 0f) primaryVideoAspectRatio else 16f / 9f
        val targetWidth: Int
        val targetHeight: Int
        if (baseRatio >= 1.0f) {
            targetWidth = 1920
            targetHeight = (1920f / baseRatio).toInt().coerceAtLeast(1)
        } else {
            targetHeight = 1920
            targetWidth = (1920f * baseRatio).toInt().coerceAtLeast(1)
        }

        val bitmap = canvasView.exportToBitmap(targetWidth, targetHeight)
        if (bitmap == null) {
            Toast.makeText(this, "Failed to render drawing", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val drawingsDir = java.io.File(cacheDir, "drawings").apply { mkdirs() }
            val outFile = java.io.File(drawingsDir, "handwriting_${System.currentTimeMillis()}.png")
            java.io.FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }

            val imageUri = Uri.fromFile(outFile)
            val startTimeMs = getGlobalPosition()
            val sequenceDuration = getTotalSequenceDuration().coerceAtLeast(3000L)
            val endTimeMs = (startTimeMs + 4000L).coerceAtMost(sequenceDuration)

            val overlayOp = com.vikash.vidopro.models.EditOperation.AddImageOverlay(
                imageUri = imageUri,
                relativeX = 0.5f,
                relativeY = 0.5f,
                relativeWidth = 1.0f,
                relativeHeight = 1.0f,
                rotationAngle = 0f,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs
            )

            viewModel.addOperation(overlayOp)
            closeHandwritingMode()
            Toast.makeText(this, "Handwriting layer added to timeline", Toast.LENGTH_SHORT).show()

            viewModel.project.value?.let { renderTracks(it) }

        } catch (e: Exception) {
            Toast.makeText(this, "Error saving handwriting: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("InflateParams")
    private fun textAction() {
        if (isTextEditingActive) {
            // Already editing — commit current text
            draggableTextOverlay?.commitText()
            return
        }
        enterTextEditingMode()
    }

    private fun closeActiveEditingModes() {
        if (isHandwritingActive) {
            closeHandwritingMode()
        }
        if (isTextEditingActive) {
            draggableTextOverlay?.commitText()
            isTextEditingActive = false
        }
        if (isImageEditingActive) {
            draggableImageOverlay?.commitImage()
            isImageEditingActive = false
        }
        if (isSubtitlesEditingActive) {
            isSubtitlesEditingActive = false
        }
        textEditingToolbar?.visibility = View.GONE
        imageEditingToolbar?.visibility = View.GONE
        videoEditingToolbar?.visibility = View.GONE
        audioEditingToolbar?.visibility = View.GONE
        cropEditingToolbar?.visibility = View.GONE
        subtitlesEditingToolbar?.visibility = View.GONE
        backgroundEditingToolbar?.visibility = View.GONE
    }

    private fun enterTextEditingMode(isReEditing: Boolean = false) {
        closeActiveEditingModes()
        selectedVideoIndex = null
        isTextEditingActive = true
        selectedTextColor = "#FFFFFF"
        if (!isReEditing) {
            draggableTextOverlay?.activate("", 36)
        }
        textEditingToolbar?.visibility = View.VISIBLE
        textEditingToolbar?.let { toolbar ->
            toolbar.findViewById<View>(R.id.colorPickerContainer)?.visibility = View.GONE
            toolbar.findViewById<ImageButton>(R.id.btnTextKeyboardTab)?.setColorFilter(getColor(R.color.colorPrimary))
            toolbar.findViewById<ImageButton>(R.id.btnTextPaletteTab)?.setColorFilter(getColor(R.color.toolTextInactive))
            setupColorPicker(toolbar)
        }
        findViewById<LinearLayout>(R.id.editingControlsWrapper)?.visibility = View.GONE
        findViewById<View>(R.id.timelineContainer)?.visibility = View.GONE
        findViewById<View>(R.id.timelineDivider)?.visibility = View.GONE
        if (::player.isInitialized && player.isPlaying) {
            player.pause()
        }
    }

    private fun exitTextEditingMode() {
        isTextEditingActive = false
        textEditingToolbar?.visibility = View.GONE
        findViewById<LinearLayout>(R.id.editingControlsWrapper)?.visibility = View.VISIBLE
        findViewById<View>(R.id.timelineContainer)?.visibility = View.VISIBLE
        findViewById<View>(R.id.timelineDivider)?.visibility = View.VISIBLE
        setActiveToolButton(0)
    }

    private fun duplicateSelectedOverlay() {
        val selectedId = viewModel.selectedOperationId.value ?: return
        val currentProject = viewModel.project.value ?: return
        val op = currentProject.operations.find {
            when (it) {
                is EditOperation.AddText -> it.id == selectedId
                is EditOperation.AddImageOverlay -> it.id == selectedId
                else -> false
            }
        } ?: return

        val newOp = when (op) {
            is EditOperation.AddText -> {
                val newX = ((op.relativeX ?: 0.5f) + 0.05f).coerceIn(0f, 1f)
                val newY = ((op.relativeY ?: 0.5f) + 0.05f).coerceIn(0f, 1f)
                op.copy(
                    id = System.nanoTime().toString(),
                    relativeX = newX,
                    relativeY = newY
                )
            }
            is EditOperation.AddImageOverlay -> {
                val newX = (op.relativeX + 0.05f).coerceIn(0f, 1f)
                val newY = (op.relativeY + 0.05f).coerceIn(0f, 1f)
                op.copy(
                    id = System.nanoTime().toString(),
                    relativeX = newX,
                    relativeY = newY
                )
            }
            else -> null
        }

        val newOpId = when (newOp) {
            is EditOperation.AddText -> newOp.id
            is EditOperation.AddImageOverlay -> newOp.id
            else -> null
        }

        if (newOp != null && newOpId != null) {
            viewModel.addOperation(newOp)
            viewModel.selectOperation(newOpId)
        }
    }

    private fun voiceOverAction() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            showVoiceOverDialog()
        } else {
            requestRecordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun showVoiceOverDialog() {
        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_voice_over, null)
        bottomSheet.setContentView(view)

        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseVoiceOver)
        val btnToggle = view.findViewById<ImageButton>(R.id.btnToggleRecording)
        val tvStatus = view.findViewById<TextView>(R.id.tvRecordingStatus)
        val ripple1 = view.findViewById<View>(R.id.ripple1)
        val ripple2 = view.findViewById<View>(R.id.ripple2)
        
        isRecordingVoiceOver = false

        val scaleX1 = android.animation.ObjectAnimator.ofFloat(ripple1, "scaleX", 1f, 1.8f)
        val scaleY1 = android.animation.ObjectAnimator.ofFloat(ripple1, "scaleY", 1f, 1.8f)
        val alpha1 = android.animation.ObjectAnimator.ofFloat(ripple1, "alpha", 0.6f, 0f)
        
        val scaleX2 = android.animation.ObjectAnimator.ofFloat(ripple2, "scaleX", 1f, 1.8f)
        val scaleY2 = android.animation.ObjectAnimator.ofFloat(ripple2, "scaleY", 1f, 1.8f)
        val alpha2 = android.animation.ObjectAnimator.ofFloat(ripple2, "alpha", 0.6f, 0f)

        scaleX1.repeatCount = android.animation.ValueAnimator.INFINITE
        scaleY1.repeatCount = android.animation.ValueAnimator.INFINITE
        alpha1.repeatCount = android.animation.ValueAnimator.INFINITE
        
        scaleX2.repeatCount = android.animation.ValueAnimator.INFINITE
        scaleY2.repeatCount = android.animation.ValueAnimator.INFINITE
        alpha2.repeatCount = android.animation.ValueAnimator.INFINITE
        
        val rippleAnimator = android.animation.AnimatorSet().apply {
            playTogether(scaleX1, scaleY1, alpha1)
            duration = 1500
        }
        val rippleAnimator2 = android.animation.AnimatorSet().apply {
            playTogether(scaleX2, scaleY2, alpha2)
            duration = 1500
            startDelay = 750
        }

        btnToggle.setBounceClickListener {
            if (!isRecordingVoiceOver) {
                // Start Recording
                voiceOverFile = File(cacheDir, "voice_over_${System.currentTimeMillis()}.m4a")
                try {
                    mediaRecorder = android.media.MediaRecorder().apply {
                        setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                        setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                        setOutputFile(voiceOverFile?.absolutePath)
                        prepare()
                        start()
                    }
                    
                    isRecordingVoiceOver = true
                    tvStatus.text = "Recording... Tap to stop"
                    btnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF4081"))
                    
                    rippleAnimator.start()
                    rippleAnimator2.start()
                    
                    // Play video while recording
                    voiceOverStartTimeMs = getGlobalPosition()
                    if (::player.isInitialized) {
                        player.play()
                        btnPlayPause.setImageResource(R.drawable.ic_pause_24)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@VideoEditingActivity, R.string.toast_failed_to_start_recording, Toast.LENGTH_SHORT).show()
                }
            } else {
                // Stop Recording
                try {
                    mediaRecorder?.stop()
                    mediaRecorder?.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                mediaRecorder = null
                isRecordingVoiceOver = false
                
                rippleAnimator.cancel()
                rippleAnimator2.cancel()
                ripple1.alpha = 0f
                ripple2.alpha = 0f
                ripple1.scaleX = 1f
                ripple1.scaleY = 1f
                ripple2.scaleX = 1f
                ripple2.scaleY = 1f
                
                // Pause video
                if (::player.isInitialized) {
                    player.pause()
                    btnPlayPause.setImageResource(R.drawable.ic_play_24)
                }
                
                tvStatus.text = "Tap to start recording"
                btnToggle.backgroundTintList = null // Reset tint
                
                // Add to audio layer
                voiceOverFile?.let { file ->
                    if (file.exists()) {
                        var durationMs = 3000L
                        try {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(file.absolutePath)
                            val timeStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                            if (timeStr != null) {
                                durationMs = timeStr.toLong()
                            }
                            retriever.release()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        
                        viewModel.addBackgroundAudioOperation(
                            audioUri = Uri.fromFile(file),
                            startTimeMs = voiceOverStartTimeMs,
                            endTimeMs = voiceOverStartTimeMs + durationMs,
                            internalStartMs = 0L,
                            internalEndMs = durationMs
                        )
                    }
                }
                bottomSheet.dismiss()
            }
        }

        btnClose.setBounceClickListener {
            bottomSheet.dismiss()
        }
        
        bottomSheet.setOnDismissListener {
            rippleAnimator.cancel()
            rippleAnimator2.cancel()
            if (isRecordingVoiceOver) {
                try { mediaRecorder?.stop(); mediaRecorder?.release() } catch(e: Exception) {}
                mediaRecorder = null
                isRecordingVoiceOver = false
                if (::player.isInitialized) {
                    player.pause()
                    btnPlayPause.setImageResource(R.drawable.ic_play_24)
                }
            }
            setActiveToolButton(-1)
        }

        bottomSheet.show()
    }

    private fun subtitlesAction() {
        enterSubtitlesEditingMode()
    }

    private fun enterSubtitlesEditingMode() {
        closeActiveEditingModes()
        selectedVideoIndex = null
        isSubtitlesEditingActive = true
        textOverlayView?.isSubtitlesEditingActive = true
        val project = viewModel.project.value
        val subOp = project?.operations?.find { it is EditOperation.AddSubtitles } as? EditOperation.AddSubtitles
        textOverlayView?.subtitleOperation = subOp
        subtitlesEditingToolbar?.visibility = View.VISIBLE
        updateSubtitlesUi()
        findViewById<LinearLayout>(R.id.editingControlsWrapper)?.visibility = View.GONE
        findViewById<View>(R.id.timelineContainer)?.visibility = View.GONE
        findViewById<View>(R.id.timelineDivider)?.visibility = View.GONE
        if (::player.isInitialized && player.isPlaying) {
            player.pause()
        }
    }

    private fun exitSubtitlesEditingMode() {
        isSubtitlesEditingActive = false
        textOverlayView?.isSubtitlesEditingActive = false
        val subOp = viewModel.project.value?.operations?.find { it is EditOperation.AddSubtitles } as? EditOperation.AddSubtitles
        textOverlayView?.subtitleOperation = subOp
        subtitlesEditingToolbar?.visibility = View.GONE
        findViewById<LinearLayout>(R.id.editingControlsWrapper)?.visibility = View.VISIBLE
        findViewById<View>(R.id.timelineContainer)?.visibility = View.VISIBLE
        findViewById<View>(R.id.timelineDivider)?.visibility = View.VISIBLE
        setActiveToolButton(0)
    }

    private fun updateSubtitlesUi() {
        val toolbar = subtitlesEditingToolbar ?: return
        val project = viewModel.project.value
        val subOp = project?.operations?.find { it is EditOperation.AddSubtitles } as? EditOperation.AddSubtitles
        val layoutNo = toolbar.findViewById<View>(R.id.layoutNoSubtitles)
        val layoutHas = toolbar.findViewById<View>(R.id.layoutHasSubtitles)
        val tvFileName = toolbar.findViewById<TextView>(R.id.tvSubtitleFileName)

        if (subOp != null) {
            layoutNo?.visibility = View.GONE
            layoutHas?.visibility = View.VISIBLE
            tvFileName?.text = subOp.fileName

            val slider = toolbar.findViewById<Slider>(R.id.subtitleFontSizeSlider)
            val tvValue = toolbar.findViewById<TextView>(R.id.tvSubtitleFontSizeValue)
            slider?.value = subOp.fontSize.toFloat().coerceIn(10f, 80f)
            tvValue?.text = "${subOp.fontSize}"

            setupSubtitleFontSelector(toolbar)
        } else {
            layoutNo?.visibility = View.VISIBLE
            layoutHas?.visibility = View.GONE
        }
    }

    private fun updateSubtitleOp(newOp: EditOperation.AddSubtitles) {
        viewModel.updateOperation(newOp)
        textOverlayView?.subtitleOperation = newOp
    }

    private fun pickSrtFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "Select SRT Subtitle File"), PICK_SRT_REQUEST)
        } catch (e: android.content.ActivityNotFoundException) {
            try {
                startActivityForResult(intent, PICK_SRT_REQUEST)
            } catch (e2: android.content.ActivityNotFoundException) {
                Toast.makeText(this, "No app found to handle this action", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pickBackgroundImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png", "image/webp"))
        }
        try {
            startActivityForResult(intent, PICK_BACKGROUND_IMAGE_REQUEST)
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to handle image selection", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeSubtitles() {
        val project = viewModel.project.value ?: return
        val subOp = project.operations.filterIsInstance<EditOperation.AddSubtitles>().firstOrNull() ?: return
        viewModel.deleteOperation(subOp.id)
        textOverlayView?.subtitleOperation = null
        textOverlayView?.setSubtitleCues(emptyList())
        textOverlayView?.isSubtitlesEditingActive = false
        
        // Immediately update toolbar UI to reflect empty state
        val toolbar = subtitlesEditingToolbar
        if (toolbar != null) {
            val layoutNo = toolbar.findViewById<View>(R.id.layoutNoSubtitles)
            val layoutHas = toolbar.findViewById<View>(R.id.layoutHasSubtitles)
            layoutNo?.visibility = View.VISIBLE
            layoutHas?.visibility = View.GONE
        }
        
        Toast.makeText(this, R.string.toast_subtitles_removed, Toast.LENGTH_SHORT).show()
    }

    private fun mediaOverlayAction() {
        if (isImageEditingActive) {
            draggableImageOverlay?.commitImage()
            return
        }
        openImagePicker()
    }


    private fun openImagePicker() {
        val picker = com.vikash.vidopro.customviews.MediaPickerBottomSheet().apply {
            initialMediaType = com.vikash.vidopro.customviews.MediaPickerBottomSheet.MediaType.IMAGE
            showCategoryTabs = true
            showAudioTab = false
            onMediaSelectedListener = { uri ->
                showImageOverlayConfig(uri)
            }
            onBrowseSystemFoldersRequested = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                try {
                    startActivityForResult(Intent.createChooser(intent, "Select Image, GIF, or Video Overlay"), PICK_IMAGE_REQUEST)
                } catch (e: android.content.ActivityNotFoundException) {
                    try {
                        startActivityForResult(intent, PICK_IMAGE_REQUEST)
                    } catch (e2: android.content.ActivityNotFoundException) {
                        Toast.makeText(this@VideoEditingActivity, "No app found to handle this action", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        picker.show(supportFragmentManager, "MediaPickerBottomSheet")
    }

    private fun showImageOverlayConfig(imageUri: Uri) {
        lifecycleScope.launch {
            val extension = withContext(Dispatchers.IO) {
                getExtensionFromUri(imageUri) ?: ".png"
            }
            val tempImageFile = withContext(Dispatchers.IO) {
                copyContentUriToTempFile(imageUri, "overlay_image", extension)
            }
            if (tempImageFile == null) {
                Toast.makeText(this@VideoEditingActivity, R.string.toast_failed_to_load_overlay_file, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val localUri = Uri.fromFile(tempImageFile)
            
            val isVideo = extension.equals(".mp4", ignoreCase = true) ||
                          extension.equals(".mkv", ignoreCase = true) ||
                          extension.equals(".mov", ignoreCase = true) ||
                          extension.equals(".3gp", ignoreCase = true)
            
            val aspect = if (isVideo) {
                withContext(Dispatchers.IO) {
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(tempImageFile.absolutePath)
                        val wStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        val hStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        val rStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                        val w = wStr?.toIntOrNull() ?: 1
                        val h = hStr?.toIntOrNull() ?: 1
                        val r = rStr?.toIntOrNull() ?: 0
                        retriever.release()
                        val isSwapped = r == 90 || r == 270
                        if (isSwapped) h.toFloat() / w else w.toFloat() / h
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting video aspect: ${e.message}", e)
                        1.0f
                    }
                }
            } else {
                var rotation = 0
                try {
                    val exif = android.media.ExifInterface(tempImageFile.absolutePath)
                    val orientation = exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )
                    when (orientation) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotation = 90
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotation = 180
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotation = 270
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading EXIF rotation: ${e.message}")
                }
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(tempImageFile.absolutePath, options)
                if (options.outHeight > 0) {
                    val isSwapped = rotation == 90 || rotation == 270
                    if (isSwapped) options.outHeight.toFloat() / options.outWidth else options.outWidth.toFloat() / options.outHeight
                } else 1.0f
            }
            
            enterImageEditingMode(localUri, aspect)
        }
    }

    private fun getExtensionFromUri(uri: Uri): String? {
        return MediaFileHelper.getExtensionFromUri(this, uri)
    }

    private fun getOverlayFileDurationMs(uri: Uri): Long? {
        return MediaFileHelper.getOverlayFileDurationMs(uri)
    }

    private fun enterImageEditingMode(uri: Uri, aspect: Float) {
        closeActiveEditingModes()
        selectedVideoIndex = null
        isImageEditingActive = true
        draggableImageOverlay?.activate(uri, aspect)
        imageEditingToolbar?.visibility = View.VISIBLE
        imageEditingToolbar?.let { toolbar ->
            val slider = toolbar.findViewById<Slider>(R.id.imageRotationSlider)
            val tvValue = toolbar.findViewById<TextView>(R.id.tvImageRotationValue)
            slider?.value = 0f
            tvValue?.text = "0°"

            val opacitySlider = toolbar.findViewById<Slider>(R.id.imageOpacitySlider)
            val tvOpacityValue = toolbar.findViewById<TextView>(R.id.tvImageOpacityValue)
            opacitySlider?.value = 100f
            tvOpacityValue?.text = "100%"

            toolbar.findViewById<View>(R.id.imageOpacitySliderRow)?.visibility = View.GONE
            toolbar.findViewById<View>(R.id.imageRotationSliderRow)?.visibility = View.VISIBLE
            toolbar.findViewById<android.widget.ImageButton>(R.id.btnImageOpacity)?.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.toolTextInactive))
        }
        findViewById<LinearLayout>(R.id.editingControlsWrapper)?.visibility = View.GONE
        if (::player.isInitialized && player.isPlaying) {
            player.pause()
        }
    }

    private fun enterImageEditingModeForEdit(rotation: Float) {
        closeActiveEditingModes()
        selectedVideoIndex = null
        isImageEditingActive = true
        imageEditingToolbar?.visibility = View.VISIBLE
        imageEditingToolbar?.let { toolbar ->
            val slider = toolbar.findViewById<com.google.android.material.slider.Slider>(R.id.imageRotationSlider)
            val tvValue = toolbar.findViewById<android.widget.TextView>(R.id.tvImageRotationValue)
            slider?.value = rotation
            tvValue?.text = "${rotation.toInt()}°"
            
            val loopContainer = toolbar.findViewById<View>(R.id.btnImageLoopContainer)
            val btnLoop = toolbar.findViewById<android.widget.ImageButton>(R.id.btnImageLoop)
            val tvLoop = toolbar.findViewById<android.widget.TextView>(R.id.tvImageLoop)
            
            val selectedId = viewModel.selectedOperationId.value
            val op = viewModel.project.value?.operations?.find { (it as? EditOperation.AddImageOverlay)?.id == selectedId } as? EditOperation.AddImageOverlay
            
            val isGif = op?.imageUri?.path?.endsWith(".gif", ignoreCase = true) == true
            val isVideo = op?.imageUri?.path?.endsWith(".mp4", ignoreCase = true) == true ||
                          op?.imageUri?.path?.endsWith(".mkv", ignoreCase = true) == true ||
                          op?.imageUri?.path?.endsWith(".mov", ignoreCase = true) == true ||
                          op?.imageUri?.path?.endsWith(".3gp", ignoreCase = true) == true
            
            if (op != null && (isGif || isVideo)) {
                loopContainer?.visibility = View.VISIBLE
                val isLooping = op.isLooping
                val color = if (isLooping) androidx.core.content.ContextCompat.getColor(this, R.color.colorPrimary) else androidx.core.content.ContextCompat.getColor(this, R.color.toolTextInactive)
                btnLoop?.setColorFilter(color)
                tvLoop?.setTextColor(color)
            } else {
                loopContainer?.visibility = View.GONE
            }

            val opacitySlider = toolbar.findViewById<Slider>(R.id.imageOpacitySlider)
            val tvOpacityValue = toolbar.findViewById<TextView>(R.id.tvImageOpacityValue)
            val currentOpacity = ((op?.opacity ?: 1.0f) * 100f).coerceIn(0f, 100f)
            opacitySlider?.value = currentOpacity
            tvOpacityValue?.text = "${currentOpacity.toInt()}%"

            toolbar.findViewById<View>(R.id.imageOpacitySliderRow)?.visibility = View.GONE
            toolbar.findViewById<View>(R.id.imageRotationSliderRow)?.visibility = View.VISIBLE
            toolbar.findViewById<android.widget.ImageButton>(R.id.btnImageOpacity)?.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.toolTextInactive))
        }
        findViewById<LinearLayout>(R.id.editingControlsWrapper)?.visibility = View.GONE
        if (::player.isInitialized && player.isPlaying) {
            player.pause()
        }
    }

    private fun exitImageEditingMode() {
        isImageEditingActive = false
        imageEditingToolbar?.visibility = View.GONE
        findViewById<LinearLayout>(R.id.editingControlsWrapper)?.visibility = View.VISIBLE
        setActiveToolButton(0)
    }

    private fun enterVideoEditingMode() {
        closeActiveEditingModes()
        viewModel.selectOperation(null)
        videoEditingToolbar?.visibility = View.VISIBLE
        editingControlsWrapper.visibility = View.GONE
        videoEditingToolbar?.findViewById<View>(R.id.btnVideoDeleteContainer)?.visibility = View.VISIBLE
        selectedVideoIndex?.let { index ->
            videoEditingToolbar?.let { toolbar ->
                updateVideoMuteButtonState(toolbar, index)
            }
        }
        if (::player.isInitialized && player.isPlaying) {
            player.pause()
        }
    }

    private fun freezeFrameAtCurrentPosition() {
        val index = selectedVideoIndex ?: return
        val sequenceItems = getSequenceItems()
        if (index < 0 || index >= sequenceItems.size) return
        val item = sequenceItems[index]

        showLoading("Creating freeze frame...")
        isImportLoading = true

        val globalPos = getGlobalPosition()

        lifecycleScope.launch {
            val resultUri = withContext(Dispatchers.IO) {
                var bitmap: android.graphics.Bitmap? = null
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(this@VideoEditingActivity, item.uri)
                    
                    var globalStartMs = 0L
                    for (i in 0 until index) {
                        globalStartMs += sequenceItems[i].trimmedDurationMs
                    }
                    val relativePosMs = globalPos - globalStartMs
                    val sourceTimeMs = item.trimStartMs + (relativePosMs * item.speed).toLong()
                    val coercedSourceTimeMs = sourceTimeMs.coerceIn(item.trimStartMs, item.trimEndMs)

                    bitmap = retriever.getFrameAtTime(coercedSourceTimeMs * 1000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to retrieve frame: ${e.message}", e)
                } finally {
                    try {
                        retriever.release()
                    } catch (e: Exception) {}
                }

                if (bitmap == null) return@withContext null

                val pngFile = File(cacheDir, "freeze_${System.nanoTime()}.png")
                try {
                    java.io.FileOutputStream(pngFile).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save temp PNG: ${e.message}", e)
                    return@withContext null
                }

                val mp4File = File(cacheDir, "freeze_vid_${System.nanoTime()}.mp4")
                val cmd = "-y -loop 1 -i \"${pngFile.absolutePath}\" -c:v h264_mediacodec -t 3 -pix_fmt yuv420p -vf \"scale=trunc(iw/2)*2:trunc(ih/2)*2\" \"${mp4File.absolutePath}\""
                val renderResult = ffmpegEngine.executeCommand(cmd)

                try {
                    pngFile.delete()
                } catch (e: Exception) {}

                if (renderResult is com.vikash.vidopro.services.FFmpegRenderEngine.RenderResult.Success) {
                    Uri.fromFile(mp4File)
                } else {
                    Log.e(TAG, "FFmpeg failed to create freeze frame video")
                    null
                }
            }

            isImportLoading = false
            loadingScreen.visibility = View.GONE

            if (resultUri != null) {
                val freezeFrameItem = com.vikash.vidopro.models.EditOperation.MergeItem(resultUri, 3000L)
                val newItems = mutableListOf<com.vikash.vidopro.models.EditOperation.MergeItem>()
                
                var globalStartMs = 0L
                for (i in 0 until index) {
                    globalStartMs += sequenceItems[i].trimmedDurationMs
                }
                val relativePosMs = globalPos - globalStartMs
                
                for (i in 0 until sequenceItems.size) {
                    if (i == index) {
                        val splitSourceMs = item.trimStartMs + (relativePosMs * item.speed).toLong()
                        if (splitSourceMs > item.trimStartMs && splitSourceMs < item.trimEndMs) {
                            val itemA = item.copy(trimEndMs = splitSourceMs)
                            val itemB = item.copy(trimStartMs = splitSourceMs)
                            newItems.add(itemA)
                            newItems.add(freezeFrameItem)
                            newItems.add(itemB)
                        } else if (splitSourceMs <= item.trimStartMs) {
                            newItems.add(freezeFrameItem)
                            newItems.add(item)
                        } else {
                            newItems.add(item)
                            newItems.add(freezeFrameItem)
                        }
                    } else {
                        newItems.add(sequenceItems[i])
                    }
                }
                
                selectedVideoIndex = null
                exitVideoEditingMode()
                if (newItems.isNotEmpty()) {
                    val primaryItem = newItems[0]
                    viewModel.updateMainVideoTrim(primaryItem.trimStartMs, primaryItem.trimEndMs)
                    viewModel.updateSequenceOrder(newItems.drop(1))
                }
                Toast.makeText(this@VideoEditingActivity, R.string.toast_freeze_frame_added_to_sequence, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@VideoEditingActivity, R.string.toast_failed_to_create_freeze_frame, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exitVideoEditingMode() {
        videoEditingToolbar?.visibility = View.GONE
        editingControlsWrapper.visibility = View.VISIBLE
        setActiveToolButton(0)
    }


    private fun enterAudioEditingMode(op: com.vikash.vidopro.models.EditOperation.AddBackgroundAudio) {
        closeActiveEditingModes()
        selectedVideoIndex = null
        audioEditingToolbar?.visibility = View.VISIBLE
        editingControlsWrapper.visibility = View.GONE
        audioEditingToolbar?.let { toolbar ->
            val slider = toolbar.findViewById<com.google.android.material.slider.Slider>(R.id.audioVolumeSlider)
            val tvValue = toolbar.findViewById<TextView>(R.id.tvAudioVolumeValue)
            slider?.value = op.volume.coerceIn(0f, 2f)
            tvValue?.text = "${(op.volume * 100).toInt()}%"
            
            val btnBeats = toolbar.findViewById<ImageButton>(R.id.btnAudioBeats)
            btnBeats?.setColorFilter(if (op.beats.isNotEmpty()) getColor(R.color.colorPrimary) else getColor(R.color.toolTextInactive))

            val btnDucking = toolbar.findViewById<ImageButton>(R.id.btnAudioDucking)
            btnDucking?.setColorFilter(if (op.ducking) getColor(R.color.colorPrimary) else getColor(R.color.toolTextInactive))

            val btnFade = toolbar.findViewById<ImageButton>(R.id.btnAudioFade)
            val isFadeActive = op.fadeInDurationMs > 0 || op.fadeOutDurationMs > 0
            btnFade?.setColorFilter(if (isFadeActive) getColor(R.color.colorPrimary) else getColor(R.color.toolTextInactive))


            val trimTrack = toolbar.findViewById<com.vikash.vidopro.customviews.TrackTrimView>(R.id.audioTrimTrack)
            val tvTrimValue = toolbar.findViewById<TextView>(R.id.tvAudioTrimValues)
            
            val maxMs = getTotalSequenceDuration().coerceAtLeast(op.originalDurationMs)
            if (op.originalDurationMs > 0) {
                trimTrack?.isMainVideoTrack = false
                trimTrack?.isAudioTrack = true
                trimTrack?.trackColor = android.graphics.Color.TRANSPARENT
                trimTrack?.maxDurationMs = op.originalDurationMs
                val seqDuration = getTotalSequenceDuration()
                trimTrack?.maxSelectionDurationMs = if (seqDuration > 0) seqDuration else null
                
                val trackWidth = resources.displayMetrics.widthPixels - 64.dpToPx()
                trimTrack?.customMsPerPixel = op.originalDurationMs.toFloat() / trackWidth
                
                val endMs = if (op.internalEndMs > 0) op.internalEndMs else op.originalDurationMs
                val initialEndMs = endMs.coerceAtMost(op.internalStartMs + seqDuration)
                trimTrack?.setRange(op.originalDurationMs, op.internalStartMs, initialEndMs)
                
                tvTrimValue?.text = "${formatDuration(op.internalStartMs.toInt())} - ${formatDuration(endMs.toInt())}"
                
                // Extract waveform
                if (trimTrack != null) {
                    val waveJob = lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val path = getFilePathFromUri(op.audioUri)
                        if (path != null) {
                            val amps = com.vikash.vidopro.utils.AudioWaveformExtractor.extractWaveform(this@VideoEditingActivity, path)
                            if (amps != null) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    trimTrack.audioAmplitudes = amps
                                    trimTrack.invalidate()
                                }
                            }
                        }
                    }
                    activeRenderJobs.add(waveJob)
                }
            } else {
                trimTrack?.setRange(maxMs, 0, maxMs)
                tvTrimValue?.text = "Unknown duration"
            }
        }
        if (::player.isInitialized && player.isPlaying) {
            player.pause()
        }
    }

    private fun exitAudioEditingMode() {
        audioEditingToolbar?.visibility = View.GONE
        editingControlsWrapper.visibility = View.VISIBLE
        viewModel.selectOperation(null)
        
        if (audioPreviewPlayer?.isPlaying == true) {
            audioPreviewPlayer?.stop()
        }
        audioPreviewPlayer?.release()
        audioPreviewPlayer = null
        
        audioEditingToolbar?.findViewById<ImageButton>(R.id.btnAudioPreviewPlay)?.setImageResource(R.drawable.ic_play_24)
        setActiveToolButton(0)
    }

    private fun showFadeDialog(op: com.vikash.vidopro.models.EditOperation.AddBackgroundAudio) {
        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_audio_fade, null)
        bottomSheet.setContentView(view)

        val fadeInSlider = view.findViewById<com.google.android.material.slider.Slider>(R.id.fadeInSlider)
        val fadeOutSlider = view.findViewById<com.google.android.material.slider.Slider>(R.id.fadeOutSlider)
        val tvFadeInValue = view.findViewById<TextView>(R.id.tvFadeInValue)
        val tvFadeOutValue = view.findViewById<TextView>(R.id.tvFadeOutValue)
        val btnFadeDone = view.findViewById<android.view.View>(R.id.btnFadeDone)

        val clipDurMs = if (op.endTimeMs != null && op.startTimeMs != null) {
            op.endTimeMs - op.startTimeMs
        } else if (op.internalEndMs > 0L) {
            op.internalEndMs - op.internalStartMs
        } else {
            op.originalDurationMs - op.internalStartMs
        }
        val maxFadeMs = if (clipDurMs > 0) clipDurMs.coerceAtMost(5000L).toFloat() / 1000f else 5.0f

        fadeInSlider.valueTo = if (maxFadeMs >= 0.1f) maxFadeMs else 0.1f
        fadeOutSlider.valueTo = if (maxFadeMs >= 0.1f) maxFadeMs else 0.1f

        val initFadeInSec = (op.fadeInDurationMs / 1000f).coerceIn(0f, fadeInSlider.valueTo)
        val initFadeOutSec = (op.fadeOutDurationMs / 1000f).coerceIn(0f, fadeOutSlider.valueTo)

        fadeInSlider.value = initFadeInSec
        fadeOutSlider.value = initFadeOutSec
        tvFadeInValue.text = String.format(java.util.Locale.US, "%.1fs", initFadeInSec)
        tvFadeOutValue.text = String.format(java.util.Locale.US, "%.1fs", initFadeOutSec)

        fadeInSlider.addOnChangeListener { _, value, _ ->
            tvFadeInValue.text = String.format(java.util.Locale.US, "%.1fs", value)
            if (clipDurMs > 0 && value + fadeOutSlider.value > clipDurMs / 1000f) {
                val newOut = (clipDurMs / 1000f - value).coerceAtLeast(0f).coerceAtMost(fadeOutSlider.valueTo)
                fadeOutSlider.value = newOut
                tvFadeOutValue.text = String.format(java.util.Locale.US, "%.1fs", newOut)
            }
        }

        fadeOutSlider.addOnChangeListener { _, value, _ ->
            tvFadeOutValue.text = String.format(java.util.Locale.US, "%.1fs", value)
            if (clipDurMs > 0 && value + fadeInSlider.value > clipDurMs / 1000f) {
                val newIn = (clipDurMs / 1000f - value).coerceAtLeast(0f).coerceAtMost(fadeInSlider.valueTo)
                fadeInSlider.value = newIn
                tvFadeInValue.text = String.format(java.util.Locale.US, "%.1fs", newIn)
            }
        }

        btnFadeDone.setBounceClickListener {
            val newFadeInMs = (fadeInSlider.value * 1000).toLong()
            val newFadeOutMs = (fadeOutSlider.value * 1000).toLong()
            viewModel.updateOperation(op.copy(fadeInDurationMs = newFadeInMs, fadeOutDurationMs = newFadeOutMs))
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun getPresetCropBounds(aspectRatio: String): android.graphics.RectF {
        val rect = android.graphics.RectF(0f, 0f, 1f, 1f)
        val format = if (::player.isInitialized) player.videoFormat else null
        val videoRatio = if (format != null && format.width > 0 && format.height > 0) {
            val rotation = format.rotationDegrees
            val videoWidth = if (rotation == 90 || rotation == 270) format.height else format.width
            val videoHeight = if (rotation == 90 || rotation == 270) format.width else format.height
            videoWidth.toFloat() / videoHeight
        } else if (primaryVideoAspectRatio > 0f) {
            primaryVideoAspectRatio
        } else {
            16f / 9f
        }
        val targetRatio = when (aspectRatio) {
            "16:9" -> 16f / 9f
            "9:16" -> 9f / 16f
            "1:1" -> 1f
            "4:5" -> 4f / 5f
            else -> return rect
        }
        if (videoRatio > targetRatio) {
            val w = targetRatio / videoRatio
            val x = (1f - w) / 2f
            rect.set(x, 0f, x + w, 1f)
        } else {
            val h = videoRatio / targetRatio
            val y = (1f - h) / 2f
            rect.set(0f, y, 1f, y + h)
        }
        return rect
    }

    private fun enterCropEditingMode() {
        closeActiveEditingModes()
        cropEditingToolbar?.visibility = View.VISIBLE
        editingControlsWrapper.visibility = View.GONE
        
        val cropOp = viewModel.project.value?.operations
            ?.filterIsInstance<com.vikash.vidopro.models.EditOperation.Crop>()
            ?.lastOrNull()

        initialCropOperation = cropOp
        val currentRatio = cropOp?.aspectRatio ?: "Original"
            
        updateCropUi(currentRatio)
        resetCropPreview()

        if (currentRatio == "Custom" && cropOp != null) {
            cropOverlayView?.setCropBounds(
                cropOp.xFraction,
                cropOp.yFraction,
                cropOp.wFraction,
                cropOp.hFraction
            )
            cropOverlayView?.visibility = View.VISIBLE
        } else {
            val bounds = getPresetCropBounds(currentRatio)
            cropOverlayView?.setCropBounds(bounds.left, bounds.top, bounds.width(), bounds.height())
            cropOverlayView?.visibility = View.GONE
        }
        
        if (::player.isInitialized && player.isPlaying) {
            player.pause()
        }
    }

    private fun exitCropEditingMode() {
        cropEditingToolbar?.visibility = View.GONE
        cropOverlayView?.visibility = View.GONE
        editingControlsWrapper.visibility = View.VISIBLE
        setActiveToolButton(0)

        val cropOp = viewModel.project.value?.operations
            ?.filterIsInstance<com.vikash.vidopro.models.EditOperation.Crop>()
            ?.lastOrNull()
        if (cropOp != null) {
            applyCropPreview(cropOp.aspectRatio)
        } else {
            resetCropPreview()
        }
    }

    private fun showSpeedEditingToolbar(index: Int, item: com.vikash.vidopro.models.EditOperation.MergeItem) {
        closeActiveEditingModes()
        speedEditingToolbar?.visibility = View.VISIBLE
        editingControlsWrapper.visibility = View.GONE
        
        updateSpeedUi(item.speed)
        
        if (::player.isInitialized && player.isPlaying) {
            player.pause()
        }
    }

    private fun hideSpeedEditingToolbar() {
        speedEditingToolbar?.visibility = View.GONE
        if (selectedVideoIndex != null) {
            videoEditingToolbar?.visibility = View.VISIBLE
        } else {
            editingControlsWrapper.visibility = View.VISIBLE
        }
    }

    private fun showLoading(message: String, subtitle: String? = null, initialPercent: Int? = null) {
        val tvLoadingTitle = loadingScreen.findViewById<TextView>(R.id.tvLoadingTitle)
        val tvLoadingSubtitle = loadingScreen.findViewById<TextView>(R.id.tvLoadingSubtitle)
        val tvLoadingPercentage = loadingScreen.findViewById<TextView>(R.id.tvLoadingPercentage)
        tvLoadingTitle?.text = message
        if (subtitle != null) {
            tvLoadingSubtitle?.text = subtitle
            tvLoadingSubtitle?.visibility = View.VISIBLE
        } else {
            tvLoadingSubtitle?.visibility = View.GONE
        }
        if (initialPercent != null) {
            tvLoadingPercentage?.text = "$initialPercent%"
            tvLoadingPercentage?.visibility = View.VISIBLE
        } else {
            tvLoadingPercentage?.visibility = View.GONE
        }
        loadingScreen.visibility = View.VISIBLE
    }

    private fun updateLoadingProgress(percent: Int) {
        val tvLoadingPercentage = loadingScreen.findViewById<TextView>(R.id.tvLoadingPercentage)
        tvLoadingPercentage?.text = "${percent.coerceIn(0, 100)}%"
        tvLoadingPercentage?.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        val tvLoadingPercentage = loadingScreen.findViewById<TextView>(R.id.tvLoadingPercentage)
        tvLoadingPercentage?.visibility = View.GONE
        loadingScreen.visibility = View.GONE
    }

    private fun updateSpeedUi(speed: Float) {
        val toolbar = speedEditingToolbar ?: return
        val speeds = mapOf(
            0.5f to Pair(R.id.bgSpeed05, R.id.txtSpeed05),
            1.0f to Pair(R.id.bgSpeed10, R.id.txtSpeed10),
            1.5f to Pair(R.id.bgSpeed15, R.id.txtSpeed15),
            2.0f to Pair(R.id.bgSpeed20, R.id.txtSpeed20)
        )
        
        speeds.forEach { (s, views) ->
            val bg = toolbar.findViewById<View>(views.first)
            val txt = toolbar.findViewById<TextView>(views.second)
            if (kotlin.math.abs(speed - s) < 0.01f) {
                bg?.setBackgroundResource(R.drawable.bg_aspect_ratio_selected)
                txt?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorOnPrimary))
            } else {
                bg?.setBackgroundResource(R.drawable.bg_aspect_ratio_item)
                txt?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.toolTextInactive))
            }
        }
        
        val slider = toolbar.findViewById<com.google.android.material.slider.Slider>(R.id.sliderCustomSpeed)
        val tvCustomSpeed = toolbar.findViewById<TextView>(R.id.tvCustomSpeedValue)
        
        slider?.let {
            val safeSpeed = speed.coerceIn(it.valueFrom, it.valueTo)
            if (kotlin.math.abs(it.value - safeSpeed) > 0.01f) {
                it.value = safeSpeed
            }
            val roundedValue = String.format(java.util.Locale.US, "%.1f", safeSpeed).toFloat()
            tvCustomSpeed?.text = "${roundedValue}x"
        }
    }

    private fun applySpeedToSegment(speed: Float) {
        val index = selectedVideoIndex ?: return
        val items = getSequenceItems()
        if (index < 0 || index >= items.size) return
        
        val item = items[index]
        if (item.speed == speed) {
            hideSpeedEditingToolbar()
            return
        }
        
        updateSpeedUi(speed)
        
        if (speed == 1.0f) {
            // Reset to normal speed, but preserve reverse if it is reversed!
            if (item.isReversed) {
                lifecycleScope.launch {
                    showLoading("Resetting speed...")
                    val outputFileName = "proxy_reverse_${System.currentTimeMillis()}.mp4"
                    val outputFilePath = java.io.File(cacheDir, outputFileName).absolutePath
                    val result = ffmpegEngine.reverseVideo(
                        sourceFilePath = getFilePathFromUri(item.uri) ?: "",
                        startMs = item.trimStartMs,
                        endMs = item.trimEndMs,
                        outputFilePath = outputFilePath
                    )
                    hideLoading()
                    if (result is com.vikash.vidopro.services.FFmpegRenderEngine.RenderResult.Success) {
                        val proxyUri = android.net.Uri.fromFile(java.io.File(result.outputPath))
                        if (index == 0) {
                            viewModel.updateMainVideoSpeed(1.0f, null)
                            viewModel.updateMainVideoReverse(isReversed = true, proxyUri = proxyUri)
                        } else {
                            updateVideoSegment(index, item.copy(speed = 1.0f, proxyUri = proxyUri, isReversed = true))
                        }
                        hideSpeedEditingToolbar()
                    } else {
                        android.widget.Toast.makeText(this@VideoEditingActivity, R.string.toast_failed_to_apply_speed_reset, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                if (index == 0) {
                    viewModel.updateMainVideoSpeed(1.0f, null)
                } else {
                    updateVideoSegment(index, item.copy(speed = 1.0f, proxyUri = null))
                }
                hideSpeedEditingToolbar()
            }
            return
        }

        lifecycleScope.launch {
            showLoading("Applying speed changes...")
            val outputFileName = "proxy_speed_${System.currentTimeMillis()}.mp4"
            val outputFilePath = java.io.File(cacheDir, outputFileName).absolutePath

            val result = ffmpegEngine.generateSpeedProxy(
                sourceFilePath = getFilePathFromUri(item.uri) ?: "",
                startMs = item.trimStartMs,
                endMs = item.trimEndMs,
                speed = speed,
                outputFilePath = outputFilePath
            )

            if (result is com.vikash.vidopro.services.FFmpegRenderEngine.RenderResult.Success) {
                if (item.isReversed) {
                    showLoading("Reversing speed-adjusted video...")
                    val reverseFileName = "proxy_reverse_${System.currentTimeMillis()}.mp4"
                    val reverseFilePath = java.io.File(cacheDir, reverseFileName).absolutePath
                    val speedProxyPath = result.outputPath

                    val durationMs = ((item.trimEndMs - item.trimStartMs) / speed).toLong()
                    val reverseResult = ffmpegEngine.reverseVideo(
                        sourceFilePath = speedProxyPath,
                        startMs = 0L,
                        endMs = durationMs,
                        outputFilePath = reverseFilePath
                    )

                    hideLoading()

                    if (reverseResult is com.vikash.vidopro.services.FFmpegRenderEngine.RenderResult.Success) {
                        val finalProxyUri = android.net.Uri.fromFile(java.io.File(reverseResult.outputPath))
                        if (index == 0) {
                            viewModel.updateMainVideoSpeed(speed, null)
                            viewModel.updateMainVideoReverse(isReversed = true, proxyUri = finalProxyUri)
                        } else {
                            updateVideoSegment(index, item.copy(speed = speed, proxyUri = finalProxyUri, isReversed = true))
                        }
                        hideSpeedEditingToolbar()
                    } else {
                        android.widget.Toast.makeText(this@VideoEditingActivity, R.string.toast_failed_to_reverse_speed_proxy, android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    hideLoading()
                    val proxyUri = android.net.Uri.fromFile(java.io.File(result.outputPath))
                    if (index == 0) {
                        viewModel.updateMainVideoSpeed(speed, proxyUri)
                    } else {
                        updateVideoSegment(index, item.copy(speed = speed, proxyUri = proxyUri))
                    }
                    hideSpeedEditingToolbar()
                }
            } else {
                hideLoading()
                android.widget.Toast.makeText(this@VideoEditingActivity, R.string.toast_failed_to_apply_speed, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun reverseVideoSegment(index: Int, item: com.vikash.vidopro.models.EditOperation.MergeItem) {
        if (item.isReversed) {
            // Un-reverse the clip
            if (index == 0) {
                if (item.speed == 1.0f) {
                    viewModel.updateMainVideoReverse(isReversed = false, proxyUri = null)
                } else {
                    regenerateSpeedProxyForUnreverse(index, item)
                }
            } else {
                if (item.speed == 1.0f) {
                    updateVideoSegment(index, item.copy(isReversed = false, proxyUri = null))
                } else {
                    regenerateSpeedProxyForUnreverse(index, item)
                }
            }
            return
        }

        lifecycleScope.launch {
            showLoading("Reversing video...")
            val outputFileName = "proxy_reverse_${System.currentTimeMillis()}.mp4"
            val outputFilePath = java.io.File(cacheDir, outputFileName).absolutePath

            val sourceFilePath: String
            val startMs: Long
            val endMs: Long

            if (item.proxyUri != null) {
                sourceFilePath = getFilePathFromUri(item.proxyUri) ?: ""
                startMs = 0L
                endMs = item.trimmedDurationMs
            } else {
                sourceFilePath = getFilePathFromUri(item.uri) ?: ""
                startMs = item.trimStartMs
                endMs = item.trimEndMs
            }

            val result = ffmpegEngine.reverseVideo(
                sourceFilePath = sourceFilePath,
                startMs = startMs,
                endMs = endMs,
                outputFilePath = outputFilePath
            )

            hideLoading()

            if (result is com.vikash.vidopro.services.FFmpegRenderEngine.RenderResult.Success) {
                val reversedProxyUri = android.net.Uri.fromFile(java.io.File(result.outputPath))
                if (index == 0) {
                    viewModel.updateMainVideoReverse(isReversed = true, proxyUri = reversedProxyUri)
                } else {
                    updateVideoSegment(index, item.copy(isReversed = true, proxyUri = reversedProxyUri))
                }
            } else {
                android.widget.Toast.makeText(this@VideoEditingActivity, R.string.toast_failed_to_reverse_video, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun regenerateSpeedProxyForUnreverse(index: Int, item: com.vikash.vidopro.models.EditOperation.MergeItem) {
        lifecycleScope.launch {
            showLoading("Restoring forward video...")
            val outputFileName = "proxy_speed_${System.currentTimeMillis()}.mp4"
            val outputFilePath = java.io.File(cacheDir, outputFileName).absolutePath

            val result = ffmpegEngine.generateSpeedProxy(
                sourceFilePath = getFilePathFromUri(item.uri) ?: "",
                startMs = item.trimStartMs,
                endMs = item.trimEndMs,
                speed = item.speed,
                outputFilePath = outputFilePath
            )

            hideLoading()

            if (result is com.vikash.vidopro.services.FFmpegRenderEngine.RenderResult.Success) {
                val proxyUri = android.net.Uri.fromFile(java.io.File(result.outputPath))
                if (index == 0) {
                    viewModel.updateMainVideoSpeed(item.speed, proxyUri)
                    viewModel.updateMainVideoReverse(isReversed = false, proxyUri = null)
                } else {
                    updateVideoSegment(index, item.copy(isReversed = false, speed = item.speed, proxyUri = proxyUri))
                }
            } else {
                android.widget.Toast.makeText(this@VideoEditingActivity, R.string.toast_failed_to_restore_video_speed, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    
    private fun updateVideoSegment(index: Int, newItem: com.vikash.vidopro.models.EditOperation.MergeItem) {
        val project = viewModel.project.value ?: return
        val currentMergeOp = project.operations.find { it is com.vikash.vidopro.models.EditOperation.Merge } as? com.vikash.vidopro.models.EditOperation.Merge ?: return
        val updatedItems = currentMergeOp.items.toMutableList()
        updatedItems[index - 1] = newItem
        val newMergeOp = currentMergeOp.copy(items = updatedItems)
        viewModel.updateOperation(newMergeOp)
        viewModel.project.value?.let { renderTracks(it) }
    }
    
    private fun updateCanvasBackgroundPreview(activeClipIndex: Int = lastActiveClipIndexForBlur) {
        val project = viewModel.project.value ?: return
        val bgOp = project.operations.filterIsInstance<EditOperation.CanvasBackground>().lastOrNull()
        
        bgPreviewImageView?.setImageBitmap(null)
        bgPreviewImageView?.background = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bgPreviewImageView?.setRenderEffect(null)
        }
        
        if (bgOp == null) {
            bgPreviewImageView?.setBackgroundColor(android.graphics.Color.BLACK)
            return
        }
        
        when (bgOp.type) {
            EditOperation.CanvasBackground.BackgroundType.COLOR -> {
                try {
                    val color = android.graphics.Color.parseColor(bgOp.colorHex)
                    bgPreviewImageView?.setBackgroundColor(color)
                } catch (e: Exception) {
                    bgPreviewImageView?.setBackgroundColor(android.graphics.Color.BLACK)
                }
            }
            EditOperation.CanvasBackground.BackgroundType.BLUR -> {
                val clipUri = getSequenceItems().getOrNull(activeClipIndex)?.uri ?: return
                currentBlurJob?.cancel()
                currentBlurJob = lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val path = getFilePathFromUri(clipUri)
                        if (path != null) {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(path)
                            val bitmap = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            retriever.release()
                            
                            if (bitmap != null) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    bgPreviewImageView?.setImageBitmap(bitmap)
                                    bgPreviewImageView?.scaleType = ImageView.ScaleType.CENTER_CROP
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        val radius = bgOp.blurRadius.toFloat()
                                        bgPreviewImageView?.setRenderEffect(
                                            android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            EditOperation.CanvasBackground.BackgroundType.IMAGE -> {
                bgOp.imageUri?.let { uri ->
                    try {
                        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                            android.graphics.ImageDecoder.decodeBitmap(source)
                        } else {
                            @Suppress("DEPRECATION")
                            android.provider.MediaStore.Images.Media.getBitmap(contentResolver, uri)
                        }
                        bgPreviewImageView?.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun updateCropUi(ratio: String) {
        val toolbar = cropEditingToolbar ?: return
        val ratios = mapOf(
            "16:9" to Triple(R.id.bg16_9, R.id.ic16_9, R.id.txt16_9),
            "9:16" to Triple(R.id.bg9_16, R.id.ic9_16, R.id.txt9_16),
            "1:1" to Triple(R.id.bg1_1, R.id.ic1_1, R.id.txt1_1),
            "4:5" to Triple(R.id.bg4_5, R.id.ic4_5, R.id.txt4_5),
            "Custom" to Triple(R.id.bgCustom, R.id.icCustom, R.id.txtCustom)
        )

        ratios.forEach { (key, views) ->
            val isActive = key == ratio
            toolbar.findViewById<View>(views.first)?.setBackgroundResource(
                if (isActive) R.drawable.bg_aspect_ratio_selected else R.drawable.bg_aspect_ratio_item
            )
            toolbar.findViewById<ImageView>(views.second)?.setColorFilter(
                resources.getColor(if (isActive) R.color.onPrimaryContainer else R.color.iconSecondary, null)
            )
            toolbar.findViewById<TextView>(views.third)?.apply {
                setTextColor(resources.getColor(if (isActive) R.color.colorOnPrimary else R.color.toolTextInactive, null))
                paint.isFakeBoldText = isActive
            }
        }

        cropOverlayView?.visibility = if (ratio == "Custom") View.VISIBLE else View.GONE
    }

    private fun splitSelectedVideo() {
        val index = selectedVideoIndex ?: return
        val globalPos = getGlobalPosition()
        
        val sequenceItems = getSequenceItems()
        if (index < 0 || index >= sequenceItems.size) return
        
        val item = sequenceItems[index]
        
        // Calculate the clip's starting global position
        var clipStartGlobal = 0L
        for (i in 0 until index) {
            clipStartGlobal += sequenceItems[i].trimmedDurationMs
        }
        val clipEndGlobal = clipStartGlobal + item.trimmedDurationMs
        
        // Check if the seeker is inside this clip
        if (globalPos <= clipStartGlobal || globalPos >= clipEndGlobal) {
            Toast.makeText(this, R.string.toast_seeker_is_not_over_the_selecte, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Calculate the local split time inside the clip's own timeline
        val localOffset = globalPos - clipStartGlobal
        val localSplitTimeMs = item.trimStartMs + localOffset
        
        viewModel.splitVideoSegment(index, localSplitTimeMs, item.uri, item.durationMs)
        selectedVideoIndex = null
        exitVideoEditingMode()
    }
    
    private fun clearProjectAndShowImportScreen() {
        if (::player.isInitialized) {
            if (player.isPlaying) player.pause()
            player.clearMediaItems()
        }
        isVideoLoaded = false
        videoUri = null
        videoPinchScale = 1.0f
        if (::playerView.isInitialized) {
            playerView.scaleX = 1.0f
            playerView.scaleY = 1.0f
            playerView.translationX = 0f
            playerView.translationY = 0f
        }
        viewModel.clearAllOperations() // wait, no initialize is better
        
        // Keep timeline container visible, but hide its normal contents
        timelineContainer.visibility = View.VISIBLE
        playerContainer.visibility = View.INVISIBLE
        findViewById<View>(R.id.seekerContainer).visibility = View.VISIBLE
        findViewById<View>(R.id.editingControlsScroll).visibility = View.INVISIBLE
        
        // Hide normal timeline elements inside the container
        findViewById<View>(R.id.timelineHorizontalScroll)?.visibility = View.INVISIBLE
        findViewById<View>(R.id.btnTimelineAdd)?.visibility = View.INVISIBLE
        findViewById<View>(R.id.customVideoSeeker)?.visibility = View.INVISIBLE
        
        // Hide toolbars
        exitVideoEditingMode()
        exitAudioEditingMode()
        exitImageEditingMode()
        exitTextEditingMode()
        
        // Show empty state
        findViewById<View>(R.id.emptyProjectState)?.visibility = View.VISIBLE
        setEditingButtonsEnabled(false)
        
        // Ensure loading screen is hidden and reset
        isImportLoading = false
        loadingScreen.visibility = View.GONE
    }

    private fun setEditingButtonsEnabled(enabled: Boolean) {
        val alpha = if (enabled) 1.0f else 0.5f
        if (::btnPlayPause.isInitialized) {
            btnPlayPause.isEnabled = enabled
            btnPlayPause.alpha = alpha
        }
        findViewById<View>(R.id.btnMagnet)?.apply {
            isEnabled = enabled
            this.alpha = alpha
        }
        findViewById<View>(R.id.btnKeyframeDiamond)?.apply {
            isEnabled = enabled
            this.alpha = alpha
        }
        if (::btnTimelineMute.isInitialized) {
            btnTimelineMute.isEnabled = enabled
            btnTimelineMute.alpha = alpha
        }
        findViewById<View>(R.id.btnMute)?.apply {
            isEnabled = enabled
            this.alpha = alpha
        }
        findViewById<View>(R.id.btnCaptureFrame)?.apply {
            isEnabled = enabled
            this.alpha = alpha
        }
        findViewById<View>(R.id.layoutSaveSplit)?.apply {
            isEnabled = enabled
            this.alpha = alpha
        }
        if (!enabled) {
            if (::btnUndo.isInitialized) {
                btnUndo.isEnabled = false
                btnUndo.alpha = 0.5f
            }
            if (::btnRedo.isInitialized) {
                btnRedo.isEnabled = false
                btnRedo.alpha = 0.5f
            }
        }
    }

    private fun deleteSelectedVideo() {
        val index = selectedVideoIndex ?: return
        val items = getSequenceItems()
        
        if (items.size <= 1) {
            clearProjectAndShowImportScreen()
            return
        }
        
        if (index == 0) {
            val nextItem = items[1]
            val newSourceFile = File(nextItem.uri.path ?: nextItem.uri.toString())
            tempInputFile = newSourceFile
            originalMainVideoDurationMs = nextItem.durationMs
            videoFileName = tempInputFile.name
            videoUri = nextItem.uri
        }

        viewModel.deleteSequenceSegment(index)
        selectedVideoIndex = null
        exitVideoEditingMode()
    }

    private fun extractAudioFromSegment(index: Int, item: com.vikash.vidopro.models.EditOperation.MergeItem) {
        lifecycleScope.launch {
            showLoading("Extracting audio...")
            val ffmpegEngine = com.vikash.vidopro.services.FFmpegRenderEngine(this@VideoEditingActivity)
            
            // Generate a unique output file path
            val outputFileName = "extracted_audio_${System.currentTimeMillis()}.m4a"
            val outputFilePath = File(cacheDir, outputFileName).absolutePath
            val sourceFilePath = item.uri.path ?: item.uri.toString()

            val result = withContext(Dispatchers.IO) {
                ffmpegEngine.extractAudio(sourceFilePath, outputFilePath)
            }

            hideLoading()

            if (result is com.vikash.vidopro.services.FFmpegRenderEngine.RenderResult.Success) {
                val sequenceItems = getSequenceItems()
                var clipStartGlobal = 0L
                for (i in 0 until index) {
                    clipStartGlobal += sequenceItems[i].trimmedDurationMs
                }
                val clipEndGlobal = clipStartGlobal + item.trimmedDurationMs

                val tempUri = Uri.fromFile(File(outputFilePath))
                viewModel.addOperation(
                    com.vikash.vidopro.models.EditOperation.AddBackgroundAudio(
                        audioUri = tempUri,
                        internalStartMs = item.trimStartMs,
                        internalEndMs = item.trimEndMs,
                        startTimeMs = clipStartGlobal,
                        endTimeMs = clipEndGlobal,
                        originalDurationMs = item.durationMs,
                        extractedFromSegmentIndex = index
                    )
                )
                Toast.makeText(this@VideoEditingActivity, R.string.toast_audio_extracted_to_new_layer, Toast.LENGTH_SHORT).show()
            } else if (result is com.vikash.vidopro.services.FFmpegRenderEngine.RenderResult.Failure) {
                Toast.makeText(this@VideoEditingActivity, R.string.toast_failed_to_extract_audio, Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Audio extraction failed: ${result.error}")
            }
            
            selectedVideoIndex = null
            exitVideoEditingMode()
        }
    }

    private fun mergeAction() {
        openFilePickerMerge()
    }

    private fun openFilePickerMerge() {
        val picker = com.vikash.vidopro.customviews.MediaPickerBottomSheet().apply {
            initialMediaType = com.vikash.vidopro.customviews.MediaPickerBottomSheet.MediaType.VIDEO
            showCategoryTabs = true
            showAudioTab = false
            onMediaSelectedListener = { uri ->
                processMergeVideoUris(listOf(uri))
            }
            onBrowseSystemFoldersRequested = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "image/*"))
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                try {
                    startActivityForResult(Intent.createChooser(intent, "Select Media"), PICK_VIDEO_REQUEST)
                } catch (e: android.content.ActivityNotFoundException) {
                    try {
                        startActivityForResult(intent, PICK_VIDEO_REQUEST)
                    } catch (e2: android.content.ActivityNotFoundException) {
                        Toast.makeText(this@VideoEditingActivity, "No app found to handle this action", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        picker.show(supportFragmentManager, "MediaPickerBottomSheet")
    }

    private fun processMergeVideoUris(selectedVideoUris: List<Uri>) {
        if (selectedVideoUris.isEmpty()) return
        lifecycleScope.launch {
            val mergeItems = withContext(Dispatchers.IO) {
                selectedVideoUris.mapNotNull { uri ->
                    if (isImageUri(uri)) {
                        val ext = if (uri.path?.endsWith(".png", true) == true) ".png" else ".jpg"
                        val cachedFile = copyContentUriToTempFile(uri, "merge_image", ext)
                        val itemUri = if (cachedFile != null) Uri.fromFile(cachedFile) else uri
                        com.vikash.vidopro.models.EditOperation.MergeItem(
                            uri = itemUri,
                            durationMs = 5000L,
                            trimStartMs = 0L,
                            trimEndMs = 5000L,
                            isImage = true
                        )
                    } else {
                        val tempFile = copyContentUriToTempFile(uri, "merge_video", ".mp4")
                        if (tempFile != null) {
                            val tempUri = Uri.fromFile(tempFile)
                            val duration = try {
                                val retriever = MediaMetadataRetriever()
                                try {
                                    retriever.setDataSource(tempFile.absolutePath)
                                    val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    durStr?.toLong() ?: 0L
                                } finally {
                                    retriever.release()
                                }
                            } catch (e: Exception) {
                                0L
                            }
                            val isImg = isImageUri(uri) || duration <= 0L
                            val itemDur = if (isImg) 5000L else duration
                            com.vikash.vidopro.models.EditOperation.MergeItem(
                                uri = tempUri,
                                durationMs = itemDur,
                                trimStartMs = 0L,
                                trimEndMs = if (isImg) 5000L else itemDur,
                                isImage = isImg
                            )
                        } else null
                    }
                }
            }
            if (mergeItems.isNotEmpty()) {
                isImportLoading = true
                showLoading("Importing media...")
                viewModel.addMergeOperation(mergeItems)
                Toast.makeText(this@VideoEditingActivity, "${mergeItems.size} clip(s) added to sequence", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@VideoEditingActivity, R.string.toast_failed_to_load_selected_video, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleMainSelectedVideo(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take persistable permission", e)
        }
        emptyProjectState.visibility = View.GONE
        setEditingButtonsEnabled(true)
        playerContainer.visibility = View.VISIBLE
        timelineContainer.visibility = View.VISIBLE
        findViewById<View>(R.id.seekerContainer).visibility = View.VISIBLE
        findViewById<View>(R.id.editingControlsScroll).visibility = View.VISIBLE
        
        // Restore timeline children visibility
        findViewById<View>(R.id.timelineHorizontalScroll)?.visibility = View.VISIBLE
        findViewById<View>(R.id.btnTimelineAdd)?.visibility = View.VISIBLE
        findViewById<View>(R.id.customVideoSeeker)?.visibility = View.VISIBLE
        
        showLoading(getString(R.string.loading), getString(R.string.loading_tag))
        isImportLoading = true
        isVideoLoaded = false
        videoUri = uri
        videoPinchScale = 1.0f
        if (::playerView.isInitialized) {
            playerView.scaleX = 1.0f
            playerView.scaleY = 1.0f
            playerView.translationX = 0f
            playerView.translationY = 0f
        }
        
        lifecycleScope.launch {
            val projectSourcePath = getFilePathFromUri(uri)
            if (projectSourcePath != null) {
                tempInputFile = File(projectSourcePath)
                videoFileName = tempInputFile.name
            }
            
            viewModel.initializeProject(uri, videoFileName)
            initializeVideoData()
        }
    }

    private fun openFilePickerMain() {
        val picker = com.vikash.vidopro.customviews.MediaPickerBottomSheet().apply {
            initialMediaType = com.vikash.vidopro.customviews.MediaPickerBottomSheet.MediaType.VIDEO
            showCategoryTabs = true
            showAudioTab = false
            onMediaSelectedListener = { uri ->
                handleMainSelectedVideo(uri)
            }
            onBrowseSystemFoldersRequested = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "image/*"))
                }
                mainFilePickerLauncher.launch(intent)
            }
        }
        picker.show(supportFragmentManager, "MediaPickerBottomSheet")
    }

    private val mainFilePickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleMainSelectedVideo(uri)
            }
        }
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_VIDEO_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.let {
                val selectedVideoUris = mutableListOf<Uri>()
                if (it.clipData != null) {
                    val itemCount = it.clipData!!.itemCount
                    for (i in 0 until itemCount) {
                        selectedVideoUris.add(it.clipData!!.getItemAt(i).uri)
                    }
                } else {
                    it.data?.let { uri -> selectedVideoUris.add(uri) }
                }
                processMergeVideoUris(selectedVideoUris)
            }
        } else if (requestCode == PICK_AUDIO_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { audioUri ->
                processAudioSelected(audioUri)
            }
        } else if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { imageUri ->
                showImageOverlayConfig(imageUri)
            }
        } else if (requestCode == PICK_SRT_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { srtUri ->
                lifecycleScope.launch {
                    val tempFile = withContext(Dispatchers.IO) { copyContentUriToTempFile(srtUri, "subtitles", ".srt") }
                    if (tempFile != null) {
                        val tempUri = Uri.fromFile(tempFile)
                        val content = withContext(Dispatchers.IO) {
                            try {
                                val bytes = tempFile.readBytes()
                                if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                                    String(bytes, 2, bytes.size - 2, kotlin.text.Charsets.UTF_16LE)
                                } else if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                                    String(bytes, 2, bytes.size - 2, kotlin.text.Charsets.UTF_16BE)
                                } else if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                                    String(bytes, 3, bytes.size - 3, kotlin.text.Charsets.UTF_8)
                                } else {
                                    String(bytes, kotlin.text.Charsets.UTF_8)
                                }
                            } catch (e: Exception) {
                                ""
                            }
                        }
                        val cues = withContext(Dispatchers.IO) {
                            try {
                                com.vikash.vidopro.utils.SubtitleParser.parse(content)
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }
                        if (cues.isNotEmpty()) {
                            // First remove any existing subtitles operations so we only have one subtitles track
                            val project = viewModel.project.value
                            project?.operations?.filterIsInstance<EditOperation.AddSubtitles>()?.forEach { op ->
                                viewModel.deleteOperation(op.id)
                            }
                            
                            val name = try {
                                var displayName = "subtitles.srt"
                                contentResolver.query(srtUri, null, null, null, null)?.use { cursor ->
                                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                    if (nameIndex != -1 && cursor.moveToFirst()) {
                                        displayName = cursor.getString(nameIndex)
                                    }
                                }
                                displayName
                            } catch (e: Exception) {
                                "subtitles.srt"
                            }

                            viewModel.addOperation(
                                EditOperation.AddSubtitles(
                                    subtitlesUri = tempUri,
                                    srtContent = content,
                                    fileName = name,
                                    cues = cues
                                )
                            )
                            Toast.makeText(this@VideoEditingActivity, "Subtitles added: ${cues.size} captions", Toast.LENGTH_SHORT).show()
                            updateSubtitlesUi()
                            textOverlayView?.setSubtitleCues(cues)
                        } else {
                            Toast.makeText(this@VideoEditingActivity, R.string.toast_failed_to_parse_subtitle_file, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } else if (requestCode == PICK_DIRECTORY_REQUEST) {
            exportController.handleDirectoryPickerResult(resultCode, data)
        } else if (requestCode == PICK_BACKGROUND_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { imageUri ->
                lifecycleScope.launch {
                    val tempFile = withContext(Dispatchers.IO) { copyContentUriToTempFile(imageUri, "bg_image", ".png") }
                    if (tempFile != null) {
                        val tempUri = Uri.fromFile(tempFile)
                        viewModel.updateCanvasBackgroundOperation(
                            type = EditOperation.CanvasBackground.BackgroundType.IMAGE,
                            imageUri = tempUri
                        )
                        updateCanvasBackgroundPreview()
                    } else {
                        Toast.makeText(this@VideoEditingActivity, "Failed to load image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun audioAction() {
        val picker = com.vikash.vidopro.customviews.MediaPickerBottomSheet().apply {
            initialMediaType = com.vikash.vidopro.customviews.MediaPickerBottomSheet.MediaType.AUDIO
            showCategoryTabs = false
            onMediaSelectedListener = { uri ->
                processAudioSelected(uri)
            }
            onBrowseSystemFoldersRequested = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "audio/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                try {
                    startActivityForResult(Intent.createChooser(intent, "Select Audio"), PICK_AUDIO_REQUEST)
                } catch (e: android.content.ActivityNotFoundException) {
                    try {
                        startActivityForResult(intent, PICK_AUDIO_REQUEST)
                    } catch (e2: android.content.ActivityNotFoundException) {
                        Toast.makeText(this@VideoEditingActivity, "No app found to handle this action", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        picker.show(supportFragmentManager, "MediaPickerBottomSheet")
    }

    private fun processAudioSelected(audioUri: Uri) {
        lifecycleScope.launch {
            val tempFile = withContext(Dispatchers.IO) { copyContentUriToTempFile(audioUri, "audio", ".m4a") }
            if (tempFile != null) {
                val tempUri = Uri.fromFile(tempFile)
                val realDurationMs = withContext(Dispatchers.IO) {
                    try {
                        val r = android.media.MediaMetadataRetriever()
                        r.setDataSource(this@VideoEditingActivity, tempUri)
                        val d = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                        r.release()
                        d
                    } catch(e: Exception) { 0L }
                }
                val totalSeqDur = getTotalSequenceDuration()
                val endGlobalMs = if (realDurationMs > 0) minOf(totalSeqDur, realDurationMs) else totalSeqDur
                viewModel.addOperation(
                    com.vikash.vidopro.models.EditOperation.AddBackgroundAudio(
                        audioUri = tempUri,
                        internalStartMs = 0L,
                        internalEndMs = realDurationMs,
                        startTimeMs = 0L,
                        endTimeMs = endGlobalMs,
                        originalDurationMs = realDurationMs
                    )
                )
                Toast.makeText(this@VideoEditingActivity, R.string.toast_audio_track_added, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveAction() {
        exportController.startSaveAction()
    }

    private fun showQualitySettingsDialog() {
        exportController.showQualitySettingsDialog()
    }

    private fun exportVideoFile(uri: Uri) {
        exportController.exportVideoFile(uri)
    }

    private fun cancelExport() {
        exportController.cancelExport()
    }

    private fun setupExoPlayer() {
        val projectUri = intent.getParcelableExtra<Uri>("PROJECT_URI")
        if (projectUri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    projectUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.d(TAG, "Could not take persistable URI permission for projectUri: ${e.message}")
            }

            try {
                contentResolver.openInputStream(projectUri)?.use { inputStream ->
                    val editRecipe = inputStream.bufferedReader().use { reader ->
                        com.vikash.vidopro.utils.ProjectSerializer.deserialize(reader)
                    }
                    val project = editRecipe.toVideoProject()
                    viewModel.loadProject(project)
                    videoUri = project.sourceUri
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load project", e)
                Toast.makeText(this, "Failed to load project", Toast.LENGTH_SHORT).show()
            }
        } else {
            videoUri = intent.getParcelableExtra("VIDEO_URI")
        }

        if (videoUri != null && videoUri?.scheme == "content") {
            try {
                contentResolver.takePersistableUriPermission(videoUri!!, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Log.d(TAG, "Could not take persistable URI permission for videoUri: ${e.message}")
            }
        }
        
        if (videoUri != null) {
            player = ExoPlayer.Builder(this).build()
            playerView.player = player
            showLoading("Loading...")

            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        // Reset UI components when video reaches the end
                        val btnPlayPause = findViewById<ImageButton>(R.id.btnPlayPause)
                        btnPlayPause.setImageResource(R.drawable.ic_play_24)
                        isProgrammaticScroll = true
                        timelineHorizontalScroll.scrollTo((getTotalSequenceDuration() * pixelsPerMs).toInt(), 0)
                        isProgrammaticScroll = false

                        // OPTIONAL: If you want the seeker to jump back to 0 immediately
                        // once it finishes, uncomment the next lines:
                        // player.seekTo(0)
                        // customVideoSeeker.setSeekPosition(0f)

                        stopProgressUpdater()
                        if (isShowingPreview) dismissPreview()
                    }
                    if (state == Player.STATE_READY) {
                        isVideoLoaded = true
                        val totalDuration = getTotalSequenceDuration()
                        customVideoSeeker.setVideoDuration(totalDuration)
                        timeRulerView.setVideoDuration(totalDuration)
                        updateDurationDisplay(getGlobalPosition().toInt(), totalDuration.toInt())
                        
                        if (!isInitialFitDone && totalDuration > 0L) {
                            timelineHorizontalScroll.post {
                                val scrollWidth = timelineHorizontalScroll.width.toFloat()
                                if (scrollWidth > 0) {
                                    val safeDuration = totalDuration
                                    val newPixelsPerMs = (scrollWidth - 32.dpToPx()) / safeDuration
                                    val density = resources.displayMetrics.density
                                    pixelsPerMs = newPixelsPerMs.coerceIn(0.001f * density, 2.0f * density)
                                    viewModel.project.value?.let { renderTracks(it) }
                                    isInitialFitDone = true
                                }
                            }
                        }

                        // We no longer rely on dynamic ExoPlayer video size for overlays.
                        // Overlays will be sized to match the canvasContainer, which reflects the final output canvas.
                        updateUIInteractionState()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        startProgressUpdater()
                    } else {
                        stopProgressUpdater()
                    }
                }

                override fun onVideoSizeChanged(videoSize: com.google.android.exoplayer2.video.VideoSize) {
                    // Do nothing here for overlays because ExoPlayer's surface changes size dynamically when clips 
                    // have different aspect ratios, but we want our overlays to stick to the primary video's canvas.
                }

                override fun onPositionDiscontinuity(reason: Int) {
                    // Update UI immediately on manual seek
                    if (isVideoLoaded) {
                        syncUiWithPlayer()
                    }
                }

                override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                    super.onPlayerError(error)
                    Log.e(TAG, "ExoPlayer Error: ${error.message}", error)
                    showError("Unsupported video format or corrupted file.")
                    isImportLoading = false
                    isVideoLoaded = true // Prevent UI loop from showing loading screen again
                    loadingScreen.visibility = View.GONE
                    
                    // Exit the editing activity since the main video cannot be played
                    finish()
                }
            })

            initializeVideoData()
            if (projectUri == null) {
                val displayName = videoUri!!.lastPathSegment ?: "video"
                viewModel.initializeProject(videoUri!!, displayName)
            }
        }
    }

    private fun startProgressUpdater() {
        customVideoSeeker.removeCallbacks(updateSeekerRunnable)
        customVideoSeeker.post(updateSeekerRunnable)
    }

    private fun stopProgressUpdater() {
        customVideoSeeker.removeCallbacks(updateSeekerRunnable)
    }

    private fun getSequenceItems(): List<com.vikash.vidopro.models.EditOperation.MergeItem> {
        val items = mutableListOf<com.vikash.vidopro.models.EditOperation.MergeItem>()
        if (!::tempInputFile.isInitialized) return items
        
        val sourceUri = android.net.Uri.fromFile(tempInputFile)
        val sourceDuration = if (originalMainVideoDurationMs > 0L) {
            originalMainVideoDurationMs
        } else {
            try {
                val r = android.media.MediaMetadataRetriever()
                try {
                    r.setDataSource(tempInputFile.absolutePath)
                    val duration = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                    originalMainVideoDurationMs = duration
                    duration
                } finally {
                    r.release()
                }
            } catch (e: Exception) { 0L }
        }

        val trimOp = viewModel.project.value?.operations?.filterIsInstance<com.vikash.vidopro.models.EditOperation.Trim>()?.lastOrNull()
        val sTrimStart = trimOp?.startMs ?: 0L
        val sTrimEnd = trimOp?.endMs ?: sourceDuration
        val speedOp = viewModel.project.value?.operations?.filterIsInstance<com.vikash.vidopro.models.EditOperation.SpeedMain>()?.lastOrNull()
        val speed = speedOp?.speed ?: 1.0f
        val reverseOp = viewModel.project.value?.operations?.filterIsInstance<com.vikash.vidopro.models.EditOperation.ReverseMain>()?.lastOrNull()
        val isReversed = reverseOp?.isReversed ?: false
        val proxyUri = reverseOp?.proxyUri ?: speedOp?.proxyUri
        val mirrorOp = viewModel.project.value?.operations?.filterIsInstance<com.vikash.vidopro.models.EditOperation.MirrorMain>()?.lastOrNull()
        val isMirrored = mirrorOp?.isMirrored ?: false
        val maskMainOp = viewModel.project.value?.operations?.filterIsInstance<com.vikash.vidopro.models.EditOperation.MaskMain>()?.lastOrNull()
        val maskConfig = maskMainOp?.maskConfig ?: com.vikash.vidopro.models.EditOperation.MaskConfig()

        val isMainImg = isImageUri(sourceUri) || (::tempInputFile.isInitialized && isImageUri(Uri.fromFile(tempInputFile)))
        items.add(com.vikash.vidopro.models.EditOperation.MergeItem(
            uri = sourceUri,
            durationMs = sourceDuration,
            trimStartMs = sTrimStart,
            trimEndMs = sTrimEnd,
            speed = speed,
            isReversed = isReversed,
            isMirrored = isMirrored,
            proxyUri = proxyUri,
            scrubProxyUri = viewModel.project.value?.scrubProxyUri,
            maskConfig = maskConfig,
            isImage = isMainImg
        ))
        
        val mergeOp = viewModel.project.value?.operations?.filterIsInstance<com.vikash.vidopro.models.EditOperation.Merge>()?.firstOrNull()
        if (mergeOp != null) {
            items.addAll(mergeOp.items)
        }
        return items
    }

    private fun getGlobalPosition(): Long {
        if (!::player.isInitialized) return 0L
        val index = player.currentMediaItemIndex
        var pos = 0L
        for (i in 0 until minOf(index, chunkDurationsMs.size)) {
            pos += chunkDurationsMs[i]
        }
        return pos + player.currentPosition
    }

    private fun getTotalSequenceDuration(): Long {
        return getSequenceItems().sumOf { it.trimmedDurationMs }
    }

    private var lastAppliedFilterName: String? = null
    private var lastAppliedAdjust: com.vikash.vidopro.models.EditOperation.Adjust? = null

    private fun applyColorFilterToPlayer(filterName: String) {
        val idx = selectedVideoIndex ?: 0
        val activeAdjust = viewModel.project.value?.operations?.filterIsInstance<com.vikash.vidopro.models.EditOperation.Adjust>()
            ?.find { it.index == idx }
        applyColorFilterAndAdjustToPlayer(filterName, activeAdjust)
    }

    private fun applyColorFilterAndAdjustToPlayer(
        filterName: String,
        adjust: com.vikash.vidopro.models.EditOperation.Adjust?
    ) {
        if (lastAppliedFilterName == filterName && lastAppliedAdjust == adjust) return
        lastAppliedFilterName = filterName
        lastAppliedAdjust = adjust

        val filterMatrix = when (filterName.lowercase()) {
            "vintage" -> floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f,     0f,     0f,     1f, 0f
            )
            "warm" -> floatArrayOf(
                1.1f, 0f, 0f, 0f, 10f,
                0f, 1.0f, 0f, 0f, 5f,
                0f, 0f, 0.9f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )
            "cool" -> floatArrayOf(
                0.9f, 0f, 0f, 0f, -10f,
                0f, 1.0f, 0f, 0f, 0f,
                0f, 0f, 1.2f, 0f, 15f,
                0f, 0f, 0f, 1f, 0f
            )
            "contrast" -> floatArrayOf(
                1.4f, 0f, 0f, 0f, -50f,
                0f, 1.4f, 0f, 0f, -50f,
                0f, 0f, 1.4f, 0f, -50f,
                0f, 0f, 0f, 1f, 0f
            )
            "monochrome" -> floatArrayOf(
                0.33f, 0.59f, 0.11f, 0f, 0f,
                0.33f, 0.59f, 0.11f, 0f, 0f,
                0.33f, 0.59f, 0.11f, 0f, 0f,
                0f,    0f,    0f,    1f, 0f
            )
            "vignette" -> floatArrayOf(
                0.8f, 0f, 0f, 0f, 0f,
                0f, 0.8f, 0f, 0f, 0f,
                0f, 0f, 0.8f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            "negative" -> floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
            "crossprocess" -> floatArrayOf(
                1.2f, 0f, 0f, 0f, 0f,
                0f, 1.0f, 0f, 0f, 10f,
                0f, 0f, 1.4f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            )
            else -> null
        }

        val baseMatrix = if (filterMatrix != null) {
            android.graphics.ColorMatrix(filterMatrix)
        } else {
            android.graphics.ColorMatrix()
        }

        if (adjust != null && !adjust.isDefault()) {
            if (adjust.saturation != 0) {
                val satFactor = 1.0f + (adjust.saturation / 100f)
                val satMatrix = android.graphics.ColorMatrix()
                satMatrix.setSaturation(satFactor)
                baseMatrix.postConcat(satMatrix)
            }
            if (adjust.contrast != 0) {
                val scale = 1.0f + (adjust.contrast / 100f)
                val translate = 127.5f * (1.0f - scale)
                val contrastMatrix = android.graphics.ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                baseMatrix.postConcat(contrastMatrix)
            }
            if (adjust.brightness != 0) {
                val bOffset = (adjust.brightness / 100f) * 255f
                val brightMatrix = android.graphics.ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, bOffset,
                    0f, 1f, 0f, 0f, bOffset,
                    0f, 0f, 1f, 0f, bOffset,
                    0f, 0f, 0f, 1f, 0f
                ))
                baseMatrix.postConcat(brightMatrix)
            }
            if (adjust.exposure != 0) {
                val ev = (adjust.exposure / 100f) * 3.0f
                val scale = Math.pow(2.0, ev.toDouble()).toFloat()
                val expMatrix = android.graphics.ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, 0f,
                    0f, scale, 0f, 0f, 0f,
                    0f, 0f, scale, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                baseMatrix.postConcat(expMatrix)
            }
            if (adjust.warmth != 0) {
                val rOffset = (adjust.warmth / 100f) * 30f
                val bOffset = -(adjust.warmth / 100f) * 30f
                val warmthMatrix = android.graphics.ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, rOffset,
                    0f, 1f, 0f, 0f, rOffset / 2f,
                    0f, 0f, 1f, 0f, bOffset,
                    0f, 0f, 0f, 1f, 0f
                ))
                baseMatrix.postConcat(warmthMatrix)
            }
        }

        val hasAdjustments = (adjust != null && !adjust.isDefault())
        if (filterMatrix != null || hasAdjustments) {
            val paint = android.graphics.Paint()
            paint.colorFilter = android.graphics.ColorMatrixColorFilter(baseMatrix)
            playerView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        } else {
            playerView.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    private fun syncUiWithPlayer() {
        val currentGlobalPos = getGlobalPosition()
        val totalDuration = getTotalSequenceDuration()

        if (totalDuration > 0) {
            updateDurationDisplay(currentGlobalPos.toInt(), totalDuration.toInt())
            
            if (!isUserScrollingTimeline && !isTrackDragging) {
                val targetScrollX = (currentGlobalPos * pixelsPerMs).toInt()
                isProgrammaticScroll = true
                timelineHorizontalScroll.scrollTo(targetScrollX, 0)
                isProgrammaticScroll = false
            }

            textOverlayView?.currentPositionMs = currentGlobalPos
            imageOverlayView?.currentPositionMs = currentGlobalPos

            if (isKeyframeEditingMode) {
                updateDraggableOverlayFromKeyframes(currentGlobalPos)
            }
            updateKeyframeActionButtonState(currentGlobalPos)
            updateTimelineMuteButtonPosition()

            // Apply color filter corresponding to the active clip
            val sequenceItems = getSequenceItems()
            var accumulatedStartMs = 0L
            var activeClipIndex = 0
            for ((index, item) in sequenceItems.withIndex()) {
                val start = accumulatedStartMs
                val end = accumulatedStartMs + item.trimmedDurationMs
                if (currentGlobalPos >= start && currentGlobalPos <= end) {
                    activeClipIndex = index
                    break
                }
                accumulatedStartMs += item.trimmedDurationMs
            }
            val activeFilterName = viewModel.project.value?.operations?.filterIsInstance<com.vikash.vidopro.models.EditOperation.ColorFilter>()
                ?.find { it.index == activeClipIndex }?.filterName ?: "none"
            val activeAdjust = viewModel.project.value?.operations?.filterIsInstance<com.vikash.vidopro.models.EditOperation.Adjust>()
                ?.find { it.index == activeClipIndex }
            applyColorFilterAndAdjustToPlayer(activeFilterName, activeAdjust)
            
            if (activeClipIndex >= 0 && activeClipIndex < sequenceItems.size) {
                val relTimeMs = currentGlobalPos - accumulatedStartMs
                val evaluatedMask = sequenceItems[activeClipIndex].maskConfig.evaluatedAt(relTimeMs)
                mainVideoMaskContainer?.maskConfig = evaluatedMask

                val activeItem = sequenceItems[activeClipIndex]
                if (activeItem.isImage || isImageUri(activeItem.uri)) {
                    photoSequencePreviewImageView?.visibility = View.VISIBLE
                    playerView.visibility = View.INVISIBLE
                    photoSequencePreviewImageView?.setImageURI(activeItem.uri)
                } else {
                    photoSequencePreviewImageView?.visibility = View.GONE
                    playerView.visibility = View.VISIBLE
                }
            }
            
            if (activeClipIndex != lastActiveClipIndexForBlur) {
                lastActiveClipIndexForBlur = activeClipIndex
                updateCanvasBackgroundPreview(activeClipIndex)
            }

            val isMirrored = if (activeClipIndex == 0) {
                viewModel.project.value?.operations?.any { it is com.vikash.vidopro.models.EditOperation.MirrorMain && it.isMirrored } == true
            } else {
                viewModel.project.value?.operations?.filterIsInstance<com.vikash.vidopro.models.EditOperation.Merge>()
                    ?.firstOrNull()?.items?.getOrNull(activeClipIndex - 1)?.isMirrored == true
            }
            
            val videoSurface = playerView.videoSurfaceView
            if (videoSurface != null) {
                videoSurface.scaleX = if (isMirrored) -1f else 1f
            } else {
                findTextureView(playerView)?.scaleX = if (isMirrored) -1f else 1f
            }

            // Real-time transition preview overlay update
            val transitionOps = viewModel.project.value?.operations?.filterIsInstance<com.vikash.vidopro.models.EditOperation.Transition>() ?: emptyList()
            var transitionActiveThisFrame = false

            if (transitionOps.isNotEmpty() && sequenceItems.size > 1) {
                var accumBoundaryMs = 0L
                for (i in 0 until sequenceItems.size - 1) {
                    accumBoundaryMs += sequenceItems[i].trimmedDurationMs
                    val transOp = transitionOps.find { it.index == i }
                    if (transOp != null && transOp.type.lowercase() != "none") {
                        val windowMs = 1000L
                        val halfWindow = windowMs / 2
                        val transStart = accumBoundaryMs - halfWindow
                        val transEnd = accumBoundaryMs + halfWindow

                        if (currentGlobalPos in transStart..transEnd) {
                            transitionActiveThisFrame = true
                            val prog = (currentGlobalPos - transStart).toFloat() / windowMs.toFloat()

                            if (activeTransitionIndex != i || cachedTransitionBitmap == null || cachedTransitionBitmap?.isRecycled == true) {
                                activeTransitionIndex = i
                                val textureView = findTextureView(playerView)
                                if (textureView != null && textureView.width > 0 && textureView.height > 0) {
                                    try {
                                        cachedTransitionBitmap = textureView.getBitmap(textureView.width / 2, textureView.height / 2)
                                    } catch (e: Exception) {
                                        cachedTransitionBitmap = null
                                    }
                                }
                            }

                            transitionPreviewOverlayView?.visibility = View.VISIBLE
                            transitionPreviewOverlayView?.updateTransition(transOp.type, prog, cachedTransitionBitmap)
                            break
                        }
                    }
                }
            }

            if (!transitionActiveThisFrame) {
                if (activeTransitionIndex != -1) {
                    activeTransitionIndex = -1
                    cachedTransitionBitmap?.recycle()
                    cachedTransitionBitmap = null
                    transitionPreviewOverlayView?.clearSnapshot()
                    transitionPreviewOverlayView?.visibility = View.GONE
                }
            }
        }
    }

    private var lastSoughtGlobalPos = -1L
    private var lastSeekCallTimeMs = 0L
    private var pendingSeekRunnable: Runnable? = null

    private fun seekToGlobalPosition(globalPos: Long, force: Boolean = false) {
        if (!::player.isInitialized) return
        
        val currentTime = System.currentTimeMillis()
        if (!force && (currentTime - lastSeekCallTimeMs) < 60) {
            pendingSeekRunnable?.let { customVideoSeeker.removeCallbacks(it) }
            val runnable = Runnable {
                seekToGlobalPosition(globalPos, force = true)
            }
            pendingSeekRunnable = runnable
            customVideoSeeker.postDelayed(runnable, 60 - (currentTime - lastSeekCallTimeMs))
            return
        }
        
        pendingSeekRunnable?.let { customVideoSeeker.removeCallbacks(it) }
        lastSoughtGlobalPos = globalPos
        lastSeekCallTimeMs = currentTime

        var remainingPos = globalPos
        var index = 0
        while (index < chunkDurationsMs.size && remainingPos > chunkDurationsMs[index]) {
            remainingPos -= chunkDurationsMs[index]
            index++
        }
        if (index < chunkDurationsMs.size) {
            player.seekTo(index, remainingPos)
        } else if (chunkDurationsMs.isNotEmpty()) {
            val lastIndex = chunkDurationsMs.size - 1
            player.seekTo(lastIndex, chunkDurationsMs[lastIndex])
        }
        val totalDuration = getTotalSequenceDuration()
        updateDurationDisplay(globalPos.toInt(), totalDuration.toInt())
        textOverlayView?.currentPositionMs = globalPos
        imageOverlayView?.currentPositionMs = globalPos

        if (isKeyframeEditingMode) {
            updateDraggableOverlayFromKeyframes(globalPos)
        }
    }

    // Update your existing Runnable to include the text display update
    private val updateSeekerRunnable = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying) {
                syncUiWithPlayer()
                // 50ms (20fps) is a good balance for smooth seeker movement
                // without slamming the main thread
                customVideoSeeker.postDelayed(this, 50)
            }
        }
    }

    private fun isImageUri(uri: Uri): Boolean {
        return MediaFileHelper.isImageUri(this, uri)
    }

    private fun initializeVideoData() {
        lifecycleScope.launch {
            try {
                val targetUri = videoUri ?: return@launch
                val isImg = isImageUri(targetUri)

                if (isImg) {
                    val ext = if (targetUri.path?.endsWith(".png", true) == true) ".png" else ".jpg"
                    val cachedImageFile = copyContentUriToTempFile(targetUri, "main_image", ext)
                        ?: File(cacheDir, "main_image_${System.currentTimeMillis()}$ext")
                    tempInputFile = cachedImageFile
                    videoFileName = cachedImageFile.name
                    originalMainVideoDurationMs = 5000L

                    try {
                        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        contentResolver.openInputStream(targetUri)?.use { input ->
                            android.graphics.BitmapFactory.decodeStream(input, null, options)
                        }
                        if (options.outWidth > 0 && options.outHeight > 0) {
                            primaryVideoAspectRatio = options.outWidth.toFloat() / options.outHeight.toFloat()
                        } else {
                            primaryVideoAspectRatio = 16f / 9f
                        }
                    } catch (e: Exception) {
                        primaryVideoAspectRatio = 16f / 9f
                    }

                    val hasTrim = viewModel.project.value?.operations?.any { it is com.vikash.vidopro.models.EditOperation.Trim } == true
                    if (!hasTrim) {
                        viewModel.updateMainVideoTrim(0L, 5000L)
                    }
                } else {
                    val videoFilePath = getFilePathFromUri(targetUri)
                    if (videoFilePath != null) {
                        tempInputFile = File(videoFilePath)
                        videoFileName = tempInputFile.name
                        var dur = 0L

                        try {
                            val r = android.media.MediaMetadataRetriever()
                            r.setDataSource(tempInputFile.absolutePath)
                            dur = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                            originalMainVideoDurationMs = dur

                            val width = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 1280
                            val height = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 720
                            val rotation = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0

                            if (rotation == 90 || rotation == 270) {
                                primaryVideoAspectRatio = height.toFloat() / width.toFloat()
                            } else {
                                primaryVideoAspectRatio = width.toFloat() / height.toFloat()
                            }

                            r.release()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting original duration: ${e.message}")
                            primaryVideoAspectRatio = 16f / 9f
                        }

                        if (dur <= 0L) {
                            originalMainVideoDurationMs = 5000L
                            val hasTrim = viewModel.project.value?.operations?.any { it is com.vikash.vidopro.models.EditOperation.Trim } == true
                            if (!hasTrim) {
                                viewModel.updateMainVideoTrim(0L, 5000L)
                            }
                        }

                        // Now load it into ExoPlayer
                        val mediaItem = com.google.android.exoplayer2.MediaItem.fromUri(Uri.fromFile(tempInputFile))
                        player.setMediaItem(mediaItem)
                        player.prepare()
                    } else {
                        showError("Could not process the selected video file.")
                        isImportLoading = false
                        isVideoLoaded = true
                        loadingScreen.visibility = View.GONE
                        finish()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                showError("Error initializing video: ${e.message}")
                isImportLoading = false
                isVideoLoaded = true
                loadingScreen.visibility = View.GONE
                finish()
            }
        }
    }

    private val uriToFilePathCache = mutableMapOf<Uri, String>()

    private suspend fun getFilePathFromUri(uri: Uri): String? {
        if (uriToFilePathCache.containsKey(uri)) {
            val cached = uriToFilePathCache[uri]
            if (cached != null && File(cached).exists()) {
                return cached
            }
        }
        var filePath: String? = null
        when (uri.scheme) {
            "content" -> {
                // For content URIs, always copy to cache to avoid Scoped Storage issues
                filePath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val tempFile = File(cacheDir, "imported_video_${System.currentTimeMillis()}.mp4")
                        val inputStream = contentResolver.openInputStream(uri)
                        if (inputStream == null) {
                            Log.e("PathError", "openInputStream returned null for URI: $uri")
                            return@withContext null
                        }
                        inputStream.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 0) {
                            tempFile.absolutePath
                        } else {
                            Log.e("PathError", "File copy failed or file is empty")
                            null
                        }
                    } catch (e: Exception) {
                        Log.e("PathError", "Failed to copy file from URI: ${e.message}")
                        null
                    }
                }
            }
            "file" -> filePath = uri.path
            else -> Log.e("PathError", "Unsupported URI scheme: ${uri.scheme}")
        }
        Log.d("PathInfo", "File path: $filePath")
        if (filePath != null) {
            uriToFilePathCache[uri] = filePath
        }
        return filePath
    }

    private fun renderTracks(project: VideoProject) {
        if (!::sequenceTrackContainer.isInitialized) return
        val isMuted = project.operations.any { it is EditOperation.MuteAudio }
        updateTimelineMuteUi(isMuted)
        pendingRenderRunnable?.let { sequenceTrackContainer.removeCallbacks(it) }
        val runnable = Runnable {
            performRenderTracks(project)
        }
        pendingRenderRunnable = runnable
        sequenceTrackContainer.post(runnable)
    }

    private fun startDragSession(view: View, index: Int, rawX: Float) {
        if (isDraggingSegment) return
        isDraggingSegment = true
        draggedIndex = index
        draggedView = view
        initialTouchX = rawX
        initialScrollX = timelineHorizontalScroll.scrollX

        timelineHorizontalScroll.requestDisallowInterceptTouchEvent(true)
        view.performAppHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

        view.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .alpha(0.8f)
            .setDuration(100)
            .start()
        view.elevation = 20f * resources.displayMetrics.density

        segmentViews = activeSegmentViews.toMutableList()
        originalLefts.clear()
        for (i in 0 until segmentViews.size) {
            val lp = segmentViews[i].layoutParams as FrameLayout.LayoutParams
            originalLefts.add(lp.leftMargin)
        }
        currentDragOrder = (0 until segmentViews.size).toList()
    }

    private fun updateDragPosition(rawX: Float) {
        val draggedView = draggedView ?: return
        
        // Auto-scroll logic when dragging near screen edges
        val screenWidth = resources.displayMetrics.widthPixels
        val edgeThreshold = 100f * resources.displayMetrics.density
        if (rawX < edgeThreshold) {
            timelineHorizontalScroll.scrollBy(-15, 0)
        } else if (rawX > screenWidth - edgeThreshold) {
            timelineHorizontalScroll.scrollBy(15, 0)
        }
        
        val scrollDelta = timelineHorizontalScroll.scrollX - initialScrollX
        val dx = rawX - initialTouchX + scrollDelta
        draggedView.translationX = dx

        val draggedCenter = originalLefts[draggedIndex] + draggedView.width / 2f + dx
        val centers = List(segmentViews.size) { i ->
            if (i == draggedIndex) {
                draggedCenter
            } else {
                originalLefts[i] + segmentViews[i].width / 2f
            }
        }

        val newOrder = (0 until segmentViews.size).sortedBy { centers[it] }
        if (newOrder != currentDragOrder) {
            currentDragOrder = newOrder
            var currentLeft = 0f
            for (index in newOrder) {
                val view = segmentViews[index]
                if (index == draggedIndex) {
                    currentLeft += view.width
                } else {
                    val targetTranslationX = currentLeft - originalLefts[index]
                    view.animate()
                        .translationX(targetTranslationX)
                        .setDuration(150)
                        .start()
                    currentLeft += view.width
                }
            }
        }
    }

    private fun endDragSession() {
        if (!isDraggingSegment) return
        isDraggingSegment = false
        val draggedView = draggedView ?: return

        timelineHorizontalScroll.requestDisallowInterceptTouchEvent(false)

        var targetLeft = 0f
        for (index in currentDragOrder) {
            if (index == draggedIndex) {
                break
            }
            targetLeft += segmentViews[index].width
        }
        val targetTranslationX = targetLeft - originalLefts[draggedIndex]

        draggedView.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .translationX(targetTranslationX)
            .setDuration(150)
            .withEndAction {
                draggedView.elevation = 0f
                val sequenceItems = getSequenceItems()
                val finalItems = currentDragOrder.map { sequenceItems[it] }

                for (view in segmentViews) {
                    view.translationX = 0f
                }

                val originalOrder = (0 until segmentViews.size).toList()
                if (currentDragOrder != originalOrder) {
                    viewModel.updateSequenceOrder(finalItems)
                } else {
                    viewModel.project.value?.let { renderTracks(it) }
                }

                this.draggedView = null
                draggedIndex = -1
            }
            .start()
    }

    private fun performRenderTracks(project: VideoProject) {
        activeRenderJobs.forEach { it.cancel() }
        activeRenderJobs.clear()
        activeSegmentViews.clear()

        val sequenceItems = getSequenceItems()

        val totalSequenceDuration = getTotalSequenceDuration()
        if (totalSequenceDuration <= 0L) {
            updateTimelineAddButtonPosition()
            updateTimelineMuteButtonPosition()
            return
        }

        val mediaSourceFactory = com.google.android.exoplayer2.source.DefaultMediaSourceFactory(this)
        
        val exoAudioOps = viewModel.project.value?.operations?.filterIsInstance<com.vikash.vidopro.models.EditOperation.AddBackgroundAudio>() ?: emptyList()
        
        val boundaries = mutableSetOf<Long>()
        boundaries.add(0L)
        boundaries.add(totalSequenceDuration)
        
        var currentGlobalPosMs = 0L
        sequenceItems.forEach { item ->
            boundaries.add(currentGlobalPosMs)
            currentGlobalPosMs += item.trimmedDurationMs
            boundaries.add(currentGlobalPosMs)
        }
        
        exoAudioOps.forEach { op ->
            boundaries.add(op.startTimeMs ?: 0L)
            boundaries.add(op.endTimeMs ?: totalSequenceDuration)
        }
        
        val sortedBoundaries = boundaries.filter { it in 0..totalSequenceDuration }.sorted()
        val chunkedSources = mutableListOf<com.google.android.exoplayer2.source.MediaSource>()
        val newChunkDurations = mutableListOf<Long>()
        
        for (i in 0 until sortedBoundaries.size - 1) {
            val chunkStartMs = sortedBoundaries[i]
            val chunkEndMs = sortedBoundaries[i + 1]
            val chunkDurationMs = chunkEndMs - chunkStartMs
            if (chunkDurationMs <= 0L) continue
            
            var videoSourceForChunk: com.google.android.exoplayer2.source.MediaSource? = null
            var vGlobalMs = 0L
            for ((index, item) in sequenceItems.withIndex()) {
                val vStart = vGlobalMs
                val vEnd = vGlobalMs + item.trimmedDurationMs
                if (chunkStartMs >= vStart && chunkEndMs <= vEnd) {
                    val offsetInVideoMs = chunkStartMs - vStart
                    
                    val clipStartUs: Long
                    val videoUri: Uri
                    if (item.proxyUri != null) {
                        clipStartUs = offsetInVideoMs * 1000L
                        videoUri = item.proxyUri
                    } else if (item.scrubProxyUri != null) {
                        clipStartUs = (item.trimStartMs + offsetInVideoMs) * 1000L
                        videoUri = item.scrubProxyUri
                    } else {
                        clipStartUs = (item.trimStartMs + offsetInVideoMs) * 1000L
                        videoUri = item.uri
                    }
                    val clipEndUs = clipStartUs + (chunkDurationMs * 1000L)
                    
                    val videoMediaItem = com.google.android.exoplayer2.MediaItem.Builder()
                        .setUri(videoUri)
                        .build()
                    
                    val isClipMuted = viewModel.project.value?.operations?.filterIsInstance<com.vikash.vidopro.models.EditOperation.MuteClip>()
                        ?.find { it.index == index }?.isMuted ?: false

                    videoSourceForChunk = if (item.isImage || isImageUri(item.uri)) {
                        com.google.android.exoplayer2.source.SilenceMediaSource(chunkDurationMs * 1000L)
                    } else if (clipStartUs < clipEndUs) {
                        val baseVideoSource = mediaSourceFactory.createMediaSource(videoMediaItem)
                        val clippedSource = com.google.android.exoplayer2.source.ClippingMediaSource(
                            baseVideoSource, clipStartUs, clipEndUs, false, false, true
                        )
                        if (isClipMuted) {
                            com.google.android.exoplayer2.source.FilteringMediaSource(
                                clippedSource,
                                com.google.android.exoplayer2.C.TRACK_TYPE_VIDEO
                            )
                        } else {
                            clippedSource
                        }
                    } else {
                        com.google.android.exoplayer2.source.SilenceMediaSource(chunkDurationMs * 1000L)
                    }
                    break
                }
                vGlobalMs += item.trimmedDurationMs
            }
            
            if (videoSourceForChunk == null) {
                videoSourceForChunk = com.google.android.exoplayer2.source.SilenceMediaSource(chunkDurationMs * 1000L)
            }
            
            val chunkSourcesToMerge = mutableListOf<com.google.android.exoplayer2.source.MediaSource>(videoSourceForChunk)
            
            exoAudioOps.forEach { op ->
                val aStart = op.startTimeMs ?: 0L
                val aEnd = op.endTimeMs ?: totalSequenceDuration
                
                if (chunkStartMs >= aStart && chunkEndMs <= aEnd) {
                    val offsetInAudioMs = chunkStartMs - aStart
                    val clipStartUs = (op.internalStartMs + offsetInAudioMs) * 1000L
                    val clipEndUs = clipStartUs + (chunkDurationMs * 1000L)
                    
                    val actualAudioDurationUs = if (op.internalEndMs > 0) op.internalEndMs * 1000L else Long.MAX_VALUE
                    if (clipStartUs >= actualAudioDurationUs && actualAudioDurationUs != Long.MAX_VALUE) {
                        chunkSourcesToMerge.add(com.google.android.exoplayer2.source.SilenceMediaSource(chunkDurationMs * 1000L))
                    } else {
                        var safeClipEndUs = clipEndUs
                        if (actualAudioDurationUs != Long.MAX_VALUE && safeClipEndUs > actualAudioDurationUs) {
                            safeClipEndUs = actualAudioDurationUs
                        }
                        
                        if (clipStartUs < safeClipEndUs) {
                            val audioMediaItem = com.google.android.exoplayer2.MediaItem.Builder()
                                .setUri(op.audioUri)
                                .build()
                            val baseAudioSource = mediaSourceFactory.createMediaSource(audioMediaItem)
                            
                            try {
                                val audioSlice = com.google.android.exoplayer2.source.ClippingMediaSource(
                                    baseAudioSource, clipStartUs, safeClipEndUs, false, false, true
                                )
                                chunkSourcesToMerge.add(audioSlice)
                            } catch (e: Exception) {
                                chunkSourcesToMerge.add(com.google.android.exoplayer2.source.SilenceMediaSource(chunkDurationMs * 1000L))
                            }
                        } else {
                            chunkSourcesToMerge.add(com.google.android.exoplayer2.source.SilenceMediaSource(chunkDurationMs * 1000L))
                        }
                    }
                } else {
                    chunkSourcesToMerge.add(com.google.android.exoplayer2.source.SilenceMediaSource(chunkDurationMs * 1000L))
                }
            }
            
            val mergedChunk = if (chunkSourcesToMerge.size == 1) {
                chunkSourcesToMerge[0]
            } else {
                com.google.android.exoplayer2.source.MergingMediaSource(true, false, *chunkSourcesToMerge.toTypedArray())
            }
            chunkedSources.add(mergedChunk)
            newChunkDurations.add(chunkDurationMs)
        }
        
        chunkDurationsMs = newChunkDurations
        val finalSource = com.google.android.exoplayer2.source.ConcatenatingMediaSource(*chunkedSources.toTypedArray())

        val globalPos = getGlobalPosition()
        val wasPlaying = player.isPlaying
        
        player.setMediaSource(finalSource)
        player.prepare()
        
        seekToGlobalPosition(globalPos, force = true)
        if (wasPlaying) player.play()


        val timelineWidth = (totalSequenceDuration * pixelsPerMs).toInt()

        // Set explicit widths for ruler and track containers
        timeRulerView.layoutParams = timeRulerView.layoutParams.apply {
            width = timelineWidth
        }
        timeRulerView.setVideoDuration(totalSequenceDuration)

        customVideoSeeker.setVideoDuration(totalSequenceDuration)

        sequenceTrackContainer.layoutParams = sequenceTrackContainer.layoutParams.apply {
            width = timelineWidth
        }
        textTrackContainer.layoutParams = textTrackContainer.layoutParams.apply {
            width = timelineWidth
        }
        imageTrackContainer.layoutParams = imageTrackContainer.layoutParams.apply {
            width = timelineWidth
        }
        audioTrackContainer.layoutParams = audioTrackContainer.layoutParams.apply {
            width = timelineWidth
        }

        // Render Sequence Track
        sequenceTrackContainer.clipChildren = false
        sequenceTrackContainer.clipToPadding = false
        sequenceTrackContainer.removeAllViews()
        var accumulatedStartMs = 0L
        val transitionViewsToLayout = mutableListOf<Pair<View, FrameLayout.LayoutParams>>()

        sequenceItems.forEachIndexed { index, item ->
            val segmentView = layoutInflater.inflate(R.layout.item_sequence_segment, sequenceTrackContainer, false) as FrameLayout
            segmentView.clipChildren = false
            segmentView.clipToPadding = false
            val segmentWidth = (item.trimmedDurationMs * pixelsPerMs).toInt()
            val segmentLeft = (accumulatedStartMs * pixelsPerMs).toInt()
            val params = FrameLayout.LayoutParams(segmentWidth, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                leftMargin = segmentLeft
            }
            segmentView.layoutParams = params
            activeSegmentViews.add(segmentView)

            val isPhoto = item.isImage || isImageUri(item.uri)
            val effectiveMaxDur = if (isPhoto) maxOf(item.durationMs, maxOf(item.trimEndMs, 600000L)) else item.durationMs
            val stretchedMaxDurationMs = (effectiveMaxDur / item.speed).toLong()
            val stretchedDurationMs = (item.durationMs / item.speed).toLong()
            val stretchedTrimStartMs = (item.trimStartMs / item.speed).toLong()
            val stretchedTrimEndMs = (item.trimEndMs / item.speed).toLong()

            val rv = segmentView.findViewById<RecyclerView>(R.id.segmentFrameRecyclerView)
            val lm = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
            rv.layoutManager = lm
            val frameSpanMs = if (isPhoto) stretchedMaxDurationMs else stretchedDurationMs
            val itemWidth = maxOf(1, ((frameSpanMs * pixelsPerMs) / 15).toInt())
            val adapter = FrameAdapter(emptyList(), itemWidth)
            rv.adapter = adapter
            
            // Align frames precisely with the trim bounds by offsetting the RecyclerView scroll
            rv.post {
                lm.scrollToPositionWithOffset(0, -(stretchedTrimStartMs * pixelsPerMs).toInt())
            }

            if (isPhoto) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val bmp = try {
                        contentResolver.openInputStream(item.uri)?.use { input ->
                            android.graphics.BitmapFactory.decodeStream(input)
                        }
                    } catch (e: Exception) { null }
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            adapter.updateFrames(List(15) { bmp })
                        }
                    }
                }
            } else {
                val job = extractFramesForSegment(item.uri, item.durationMs, adapter)
                if (job != null) {
                    activeRenderJobs.add(job)
                    rv.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {}
                        override fun onViewDetachedFromWindow(v: View) {
                            job.cancel()
                            activeRenderJobs.remove(job)
                        }
                    })
                }
            }

            val trackTrimView = segmentView.findViewById<com.vikash.vidopro.customviews.TrackTrimView>(R.id.segmentTrimTrack)
            trackTrimView.isMainVideoTrack = true
            trackTrimView.trackColor = android.graphics.Color.TRANSPARENT
            trackTrimView.maxDurationMs = stretchedMaxDurationMs
            trackTrimView.customMsPerPixel = 1.0f / pixelsPerMs
            trackTrimView.isSelectedTrack = (selectedVideoIndex == index)
            trackTrimView.isTrimEnabled = (selectedVideoIndex == index)
            
            // Set the full untrimmed width on TrackTrimView and offset it
            val trackWidth = (stretchedMaxDurationMs * pixelsPerMs).toInt()
            trackTrimView.layoutParams = FrameLayout.LayoutParams(trackWidth, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                leftMargin = -(stretchedTrimStartMs * pixelsPerMs).toInt()
            }
            
            trackTrimView.activeStartMs = stretchedTrimStartMs
            trackTrimView.activeEndMs = stretchedTrimEndMs
            trackTrimView.setRange(stretchedMaxDurationMs, stretchedTrimStartMs, stretchedTrimEndMs)
            
            // Selection highlight is drawn by TrackTrimView in the foreground
            segmentView.background = null
            
            if (!isPhoto) {
                val viewRef = trackTrimView
                val waveJob = lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val path = getFilePathFromUri(item.uri)
                    if (path != null) {
                        val amps = com.vikash.vidopro.utils.AudioWaveformExtractor.extractWaveform(this@VideoEditingActivity, path)
                        if (amps != null) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                viewRef.audioAmplitudes = amps
                                viewRef.isAudioTrack = true 
                                viewRef.invalidate()
                            }
                        }
                    }
                }
                activeRenderJobs.add(waveJob)
            }
            
            trackTrimView.onTrimAdjustingWithDelta = { startMs, endMs, deltaL, deltaR ->
                val rawStart = (startMs * item.speed).toLong()
                val rawEnd = (endMs * item.speed).toLong()
                val sequenceItems = getSequenceItems()
                val clipStartGlobal = sequenceItems.take(index).sumOf { it.trimmedDurationMs }
                if (deltaL != 0L) {
                    seekToGlobalPosition(clipStartGlobal + rawStart)
                } else if (deltaR != 0L) {
                    seekToGlobalPosition(clipStartGlobal + rawEnd)
                }

                trackTrimView.layoutParams = (trackTrimView.layoutParams as FrameLayout.LayoutParams).apply {
                    leftMargin = -(startMs * pixelsPerMs).toInt()
                }

                val rv = segmentView.findViewById<RecyclerView>(R.id.segmentFrameRecyclerView)
                (rv.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, -(startMs * pixelsPerMs).toInt())

                if (deltaL != 0L) {
                    val deltaPx = (deltaL * pixelsPerMs).toInt()
                    timelineHorizontalScroll.scrollBy(deltaPx, 0)
                }

                val currentTrimmedDur = (rawEnd - rawStart)
                val newSegmentWidth = (currentTrimmedDur * pixelsPerMs).toInt()
                
                segmentView.layoutParams = (segmentView.layoutParams as FrameLayout.LayoutParams).apply {
                    width = newSegmentWidth
                }
                segmentView.requestLayout()

                var runningLeft = (clipStartGlobal * pixelsPerMs).toInt() + newSegmentWidth
                for (i in (index + 1) until activeSegmentViews.size) {
                    val nextSegView = activeSegmentViews.getOrNull(i) ?: continue
                    val itemDur = sequenceItems.getOrNull(i)?.trimmedDurationMs ?: 0L
                    nextSegView.layoutParams = (nextSegView.layoutParams as FrameLayout.LayoutParams).apply {
                        leftMargin = runningLeft
                    }
                    nextSegView.requestLayout()
                    runningLeft += (itemDur * pixelsPerMs).toInt()
                }

                val newTotalDuration = sequenceItems.mapIndexed { idx, it ->
                    if (idx == index) currentTrimmedDur else it.trimmedDurationMs
                }.sum()
                val newTimelineWidth = (newTotalDuration * pixelsPerMs).toInt()
                
                timeRulerView.layoutParams = timeRulerView.layoutParams.apply {
                    width = newTimelineWidth
                }
                timeRulerView.setVideoDuration(newTotalDuration)
                timeRulerView.requestLayout()

                sequenceTrackContainer.layoutParams = sequenceTrackContainer.layoutParams.apply {
                    width = newTimelineWidth
                }
                sequenceTrackContainer.requestLayout()
            }

            trackTrimView.onTrimChanged = { startMs, endMs, _ ->
                val rawStart = (startMs * item.speed).toLong()
                val rawEnd = (endMs * item.speed).toLong()
                if (index == 0) {
                    viewModel.updateMainVideoTrim(rawStart, rawEnd)
                } else {
                    viewModel.updateMergeItemTrim(index - 1, rawStart, rawEnd)
                }
                viewModel.project.value?.let { renderTracks(it) }
            }

            trackTrimView.onTrackClicked = {
                if (selectedVideoIndex == index) {
                    selectedVideoIndex = null
                    exitVideoEditingMode()
                } else {
                    selectedVideoIndex = index
                    enterVideoEditingMode()
                }
                viewModel.project.value?.let { renderTracks(it) }
            }
            
            segmentView.setBounceClickListener {
                if (selectedVideoIndex == index) {
                    selectedVideoIndex = null
                    exitVideoEditingMode()
                } else {
                    selectedVideoIndex = index
                    enterVideoEditingMode()
                }
                viewModel.project.value?.let { renderTracks(it) }
            }

            var lastTouchRawX = 0f
            val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: android.view.MotionEvent) {
                    startDragSession(segmentView, index, lastTouchRawX)
                }
            })

            val touchListener = View.OnTouchListener { v, event ->
                lastTouchRawX = event.rawX
                gestureDetector.onTouchEvent(event)
                if (isDraggingSegment && draggedView != null) {
                    if (event.action == android.view.MotionEvent.ACTION_MOVE) {
                        updateDragPosition(event.rawX)
                    } else if (event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL) {
                        endDragSession()
                    }
                    true
                } else {
                    false
                }
            }
            
            segmentView.setOnTouchListener(touchListener)
            trackTrimView.setOnTouchListener(touchListener)
            


            if (index > 0) {
                val transitionView = layoutInflater.inflate(R.layout.item_transition_button, sequenceTrackContainer, false)
                val btnTransition = transitionView.findViewById<ImageView>(R.id.btnTransition)
                
                // Highlight if a transition is selected
                val transitionOp = project.operations.filterIsInstance<com.vikash.vidopro.models.EditOperation.Transition>().find { it.index == index - 1 }
                if (transitionOp != null && transitionOp.type != "none") {
                    btnTransition.setImageResource(R.drawable.ic_check_24) // Or some infinite icon
                    btnTransition.setColorFilter(android.graphics.Color.WHITE)
                    transitionView.background = null
                } else {
                    btnTransition.setImageResource(R.drawable.ic_add_24)
                }
                
                val transitionWidth = 24.dpToPx()
                val transParams = FrameLayout.LayoutParams(transitionWidth, transitionWidth).apply {
                    leftMargin = segmentLeft - transitionWidth / 2
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                transitionViewsToLayout.add(Pair(transitionView, transParams))
                transitionView.setOnClickListener {
                    showTransitionDialog(index - 1)
                }
            }

            sequenceTrackContainer.addView(segmentView)
            accumulatedStartMs += item.trimmedDurationMs
        }

        // Add transition buttons on top of segment views
        for ((view, params) in transitionViewsToLayout) {
            sequenceTrackContainer.addView(view, params)
        }
        
        if (activeRenderJobs.isNotEmpty()) {
            // Keep track if needed but don't block
        }

        // Combined Text and Image tracks (Overlays) in Z-order stack
        textTrackContainer.removeAllViews()
        imageTrackContainer.removeAllViews()
        imageTrackContainer.visibility = View.GONE

        val overlayOps = project.operations.filter { it is EditOperation.AddText || it is EditOperation.AddImageOverlay }
        if (overlayOps.isNotEmpty()) {
            textTrackContainer.visibility = View.VISIBLE
            // Reverse so that the top-most overlay is visually at the top of the timeline track stack
            for (op in overlayOps.reversed()) {
                val opId = when (op) {
                    is EditOperation.AddText -> op.id
                    is EditOperation.AddImageOverlay -> op.id
                    else -> ""
                }
                val trackView = com.vikash.vidopro.customviews.TrackTrimView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dpToPx()).apply {
                        topMargin = 4.dpToPx()
                    }
                    isSelectedTrack = (opId == viewModel.selectedOperationId.value)
                    maxDurationMs = totalSequenceDuration
                    customMsPerPixel = 1.0f / pixelsPerMs
                    
                    onTrackClicked = {
                        if (viewModel.selectedOperationId.value == opId) {
                            viewModel.selectOperation(null)
                        } else {
                            viewModel.selectOperation(opId)
                        }
                    }
                    
                    onDragStateChanged = { isDragging ->
                        isTrackDragging = isDragging
                    }
                }

                if (op is EditOperation.AddText) {
                    trackView.apply {
                        trackColor = android.graphics.Color.parseColor("#E91E63") // Pink for text
                        trackLabel = op.text
                        trackIcon = androidx.core.content.ContextCompat.getDrawable(this@VideoEditingActivity, R.drawable.ic_text_24)
                        activeStartMs = op.startTimeMs ?: 0L
                        activeEndMs = op.endTimeMs ?: totalSequenceDuration
                        setRange(totalSequenceDuration, op.startTimeMs ?: 0L, op.endTimeMs ?: totalSequenceDuration)
                        onTrimChanged = { start, end, _ ->
                            viewModel.updateOperation(op.copy(startTimeMs = start, endTimeMs = end))
                        }
                        onTrimAdjustingWithDelta = { start, end, deltaStart, deltaEnd ->
                            if (deltaStart != 0L) {
                                seekToGlobalPosition(start)
                            } else if (deltaEnd != 0L) {
                                seekToGlobalPosition(end)
                            } else {
                                seekToGlobalPosition(start)
                            }
                        }
                        keyframes = (op.positionKeyframes.map { it.timeMs } + op.opacityKeyframes.map { it.timeMs }).distinct()
                    }
                } else if (op is EditOperation.AddImageOverlay) {
                    trackView.apply {
                        trackColor = android.graphics.Color.parseColor("#FF9800") // Orange for image
                        trackLabel = op.imageUri.lastPathSegment ?: "Image Overlay"
                        trackIcon = androidx.core.content.ContextCompat.getDrawable(this@VideoEditingActivity, R.drawable.ic_image_24)
                        activeStartMs = op.startTimeMs ?: 0L
                        activeEndMs = op.endTimeMs ?: totalSequenceDuration
                        val actualDuration = op.fileDurationMs ?: 3000L
                        val isImageOverlay = isImageUri(op.imageUri)
                        maxSelectionDurationMs = if (op.isLooping || isImageOverlay) null else actualDuration
                        setRange(totalSequenceDuration, op.startTimeMs ?: 0L, op.endTimeMs ?: totalSequenceDuration)
                        onTrimChanged = { start, end, _ ->
                            viewModel.updateOperation(op.copy(startTimeMs = start, endTimeMs = end))
                        }
                        onTrimAdjustingWithDelta = { start, end, deltaStart, deltaEnd ->
                            if (deltaStart != 0L) {
                                seekToGlobalPosition(start)
                            } else if (deltaEnd != 0L) {
                                seekToGlobalPosition(end)
                            } else {
                                seekToGlobalPosition(start)
                            }
                        }
                        
                        val viewRef = this
                        val imageJob = lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val path = getFilePathFromUri(op.imageUri)
                            if (path != null) {
                                try {
                                    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                                    val bitmap = android.graphics.BitmapFactory.decodeFile(path, options)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        viewRef.trackThumbnail = bitmap
                                        viewRef.invalidate()
                                    }
                                } catch (e: Exception) {}
                            }
                        }
                        activeRenderJobs.add(imageJob)
                        keyframes = (op.positionKeyframes.map { it.timeMs } + op.opacityKeyframes.map { it.timeMs } + op.speedKeyframes.map { it.timeMs }).distinct()
                    }
                }
                textTrackContainer.addView(trackView)
            }
        } else {
            textTrackContainer.visibility = View.GONE
        }

        // Audio tracks
        audioTrackContainer.removeAllViews()
        val audioOps = project.operations.filterIsInstance<EditOperation.AddBackgroundAudio>()
        if (audioOps.isNotEmpty()) {
            audioTrackContainer.visibility = View.VISIBLE
            for (op in audioOps) {
                val trackView = com.vikash.vidopro.customviews.TrackTrimView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dpToPx()).apply {
                        topMargin = 4.dpToPx()
                    }
                    trackColor = android.graphics.Color.parseColor("#4CAF50") // Green for audio
                    trackLabel = op.audioUri.lastPathSegment ?: "Audio Track"
                    isAudioTrack = true
                    isSelectedTrack = (op.id == viewModel.selectedOperationId.value)
                    trackIcon = androidx.core.content.ContextCompat.getDrawable(this@VideoEditingActivity, R.drawable.ic_audio_24)
                    onTrackClicked = {
                        if (viewModel.selectedOperationId.value == op.id) {
                            viewModel.selectOperation(null)
                        } else {
                            viewModel.selectOperation(op.id)
                        }
                    }
                    maxDurationMs = totalSequenceDuration
                    activeStartMs = op.startTimeMs ?: 0L
                    activeEndMs = op.endTimeMs ?: totalSequenceDuration
                    customMsPerPixel = 1.0f / pixelsPerMs
                    beats = op.beats
                    internalStartMs = op.internalStartMs
                    setRange(totalSequenceDuration, op.startTimeMs ?: 0L, op.endTimeMs ?: totalSequenceDuration)
                    onTrimChanged = { start, end, target ->
                        val oldOp = viewModel.project.value?.operations?.find { 
                            (it as? com.vikash.vidopro.models.EditOperation.AddBackgroundAudio)?.id == op.id 
                        } as? com.vikash.vidopro.models.EditOperation.AddBackgroundAudio
                        if (oldOp != null) {
                            var newInternalStart = oldOp.internalStartMs
                            if (target == com.vikash.vidopro.customviews.TrackTrimView.DragTarget.LEFT) {
                                val delta = start - (oldOp.startTimeMs ?: 0L)
                                newInternalStart = (newInternalStart + delta).coerceAtLeast(0L)
                            }
                            viewModel.updateOperation(oldOp.copy(startTimeMs = start, endTimeMs = end, internalStartMs = newInternalStart))
                        }
                    }
                    onTrimAdjustingWithDelta = { start, end, deltaStart, deltaEnd ->
                        if (deltaStart != 0L) {
                            seekToGlobalPosition(start)
                        } else if (deltaEnd != 0L) {
                            seekToGlobalPosition(end)
                        } else {
                            seekToGlobalPosition(start)
                        }
                    }
                    onDragStateChanged = { isDragging ->
                        isTrackDragging = isDragging
                    }
                    
                    val viewRef = this
                    val waveJob = lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val path = getFilePathFromUri(op.audioUri)
                        if (path != null) {
                            val amps = com.vikash.vidopro.utils.AudioWaveformExtractor.extractWaveform(this@VideoEditingActivity, path)
                            if (amps != null) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    viewRef.audioAmplitudes = amps
                                    viewRef.invalidate()
                                }
                            }
                        }
                    }
                    activeRenderJobs.add(waveJob)
                }
                audioTrackContainer.addView(trackView)
            }
        } else {
            audioTrackContainer.visibility = View.GONE
        }

        // Keyframe cues tracks
        val keyframeContainer = findViewById<android.widget.LinearLayout>(R.id.keyframeTrackContainer)
        keyframeContainer?.removeAllViews()
        var hasKeyframeTracks = false
        for (op in project.operations) {
            val keyframes = mutableListOf<Long>()
            var startTimeMs = 0L
            var endTimeMs = totalSequenceDuration
            if (op is com.vikash.vidopro.models.EditOperation.AddText) {
                keyframes.addAll(op.positionKeyframes.map { it.timeMs })
                keyframes.addAll(op.opacityKeyframes.map { it.timeMs })
                startTimeMs = op.startTimeMs ?: 0L
                endTimeMs = op.endTimeMs ?: totalSequenceDuration
            } else if (op is com.vikash.vidopro.models.EditOperation.AddImageOverlay) {
                keyframes.addAll(op.positionKeyframes.map { it.timeMs })
                keyframes.addAll(op.opacityKeyframes.map { it.timeMs })
                keyframes.addAll(op.speedKeyframes.map { it.timeMs })
                keyframes.addAll(op.maskConfig.positionKeyframes.map { it.timeMs })
                keyframes.addAll(op.maskConfig.sizeKeyframes.map { it.timeMs })
                keyframes.addAll(op.maskConfig.rotationKeyframes.map { it.timeMs })
                keyframes.addAll(op.maskConfig.featherKeyframes.map { it.timeMs })
                startTimeMs = op.startTimeMs ?: 0L
                endTimeMs = op.endTimeMs ?: totalSequenceDuration
            }
            
            val distinctKeyframes = keyframes.distinct()
            if (distinctKeyframes.isNotEmpty()) {
                hasKeyframeTracks = true
                val trackView = com.vikash.vidopro.customviews.TrackTrimView(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 24.dpToPx()).apply {
                        topMargin = 4.dpToPx()
                    }
                    trackColor = android.graphics.Color.parseColor("#44FFFFFF") // Semi-transparent background
                    trackLabel = ""
                    trackIcon = null
                    trackThumbnail = null
                    this.keyframes = distinctKeyframes
                    maxDurationMs = totalSequenceDuration
                    activeStartMs = startTimeMs
                    activeEndMs = endTimeMs
                    customMsPerPixel = 1.0f / pixelsPerMs
                    setRange(totalSequenceDuration, startTimeMs, endTimeMs)
                    
                    onTrackClicked = {
                        viewModel.selectOperation(op.id)
                        enterKeyframeEditingMode(op is com.vikash.vidopro.models.EditOperation.AddText)
                    }
                }
                keyframeContainer?.addView(trackView)
            }
        }
        
        if (hasKeyframeTracks) {
            keyframeContainer?.visibility = View.VISIBLE
        } else {
            keyframeContainer?.visibility = View.GONE
        }

        if (activeExtractionCount == 0 && isImportLoading) {
            isImportLoading = false
            loadingScreen.visibility = View.GONE
        }
        updateTimelineAddButtonPosition()
        updateTimelineMuteButtonPosition()
    }

    private fun updateTimelineAddButtonPosition() {
        if (!::btnTimelineAdd.isInitialized) return
        val emptyStateVisible = findViewById<View>(R.id.emptyProjectState)?.visibility == View.VISIBLE
        if (emptyStateVisible) {
            btnTimelineAdd.visibility = View.GONE
            return
        }
        val rulerView = findViewById<View>(R.id.timeRulerView) ?: return
        val seqTrack = findViewById<View>(R.id.sequenceTrackContainer) ?: return
        val vScroll = findViewById<android.widget.ScrollView>(R.id.timelineVerticalScroll) ?: return

        val timelineWidth = timelineHorizontalScroll.width
        if (timelineWidth <= 0) return

        val halfWidth = timelineWidth / 2
        val totalDurationMs = getSequenceItems().sumOf { it.trimmedDurationMs }
        val totalTrackWidthPx = halfWidth + (totalDurationMs * pixelsPerMs).toInt()

        val scrollX = timelineHorizontalScroll.scrollX

        // Position the button 8dp to the right of the end of the last clip
        val snapX = totalTrackWidthPx - scrollX + 8.dpToPx()

        val btnWidth = btnTimelineAdd.width.takeIf { it > 0 } ?: 36.dpToPx()
        val rightMargin = 16.dpToPx()
        val maxBtnX = timelineWidth - btnWidth - rightMargin

        // The button floats at the right end of the screen, unless the end of the last clip scrolls into view,
        // in which case it snaps to the end of the clip (snapX).
        val btnX = Math.min(snapX.toFloat(), maxBtnX.toFloat())

        // If the button is scrolled off-screen to the left, hide it
        if (btnX + btnWidth < 0) {
            btnTimelineAdd.visibility = View.GONE
        } else {
            btnTimelineAdd.visibility = View.VISIBLE
            btnTimelineAdd.translationX = btnX
        }

        // Vertically center on the main video track (sequenceTrackContainer)
        // Taking into account the timeRulerView and vertical scroll of the tracks
        val rulerHeight = rulerView.height.takeIf { it > 0 } ?: 28.dpToPx()
        val seqTrackTop = seqTrack.top
        val seqTrackHeight = seqTrack.height.takeIf { it > 0 } ?: 64.dpToPx()
        val btnHeight = btnTimelineAdd.height.takeIf { it > 0 } ?: 36.dpToPx()

        val btnY = rulerHeight + seqTrackTop - vScroll.scrollY + (seqTrackHeight - btnHeight) / 2

        // Check if the button is within the visible bounds of the timeline container
        val visibleTop = rulerHeight
        val visibleBottom = timelineContainer.height
        val btnCenterY = btnY + btnHeight / 2
        if (btnCenterY < visibleTop || btnCenterY > visibleBottom) {
            btnTimelineAdd.visibility = View.GONE
        } else {
            btnTimelineAdd.translationY = btnY.toFloat()
        }
    }

    private fun updateTimelineMuteButtonPosition() {
        if (!::btnTimelineMute.isInitialized) return
        val emptyStateVisible = findViewById<View>(R.id.emptyProjectState)?.visibility == View.VISIBLE
        if (emptyStateVisible || !isVideoLoaded) {
            btnTimelineMute.visibility = View.GONE
            return
        }
        val rulerView = findViewById<View>(R.id.timeRulerView) ?: return
        val seqTrack = findViewById<View>(R.id.sequenceTrackContainer) ?: return
        val vScroll = findViewById<android.widget.ScrollView>(R.id.timelineVerticalScroll) ?: return

        val timelineWidth = timelineHorizontalScroll.width
        if (timelineWidth <= 0) return

        val halfWidth = timelineWidth / 2
        val scrollX = timelineHorizontalScroll.scrollX

        // In timelineContainer space, video start (0ms) is at: halfWidth - scrollX
        val videoStartX = halfWidth - scrollX
        val btnWidth = btnTimelineMute.width.takeIf { it > 0 } ?: 48.dpToPx()
        val gap = 6.dpToPx()

        // Place the mute button directly to the left of the video start
        val btnX = videoStartX - btnWidth - gap

        // If the button is completely scrolled off-screen to the left or past the right edge, hide it
        if (btnX + btnWidth < 0 || btnX > timelineWidth) {
            btnTimelineMute.visibility = View.GONE
        } else {
            btnTimelineMute.visibility = View.VISIBLE
            btnTimelineMute.translationX = btnX.toFloat()
        }

        // Vertically center on the main video track (sequenceTrackContainer)
        val rulerHeight = rulerView.height.takeIf { it > 0 } ?: 28.dpToPx()
        val seqTrackTop = seqTrack.top
        val seqTrackHeight = seqTrack.height.takeIf { it > 0 } ?: 52.dpToPx()
        val btnHeight = btnTimelineMute.height.takeIf { it > 0 } ?: 48.dpToPx()

        val btnY = rulerHeight + seqTrackTop - vScroll.scrollY + (seqTrackHeight - btnHeight) / 2

        val visibleTop = rulerHeight
        val visibleBottom = timelineContainer.height
        val btnCenterY = btnY + btnHeight / 2
        if (btnCenterY < visibleTop || btnCenterY > visibleBottom) {
            btnTimelineMute.visibility = View.GONE
        } else {
            btnTimelineMute.translationY = btnY.toFloat()
        }
    }

    private fun updateTimelineMuteUi(isMuted: Boolean) {
        if (!::btnTimelineMute.isInitialized) return
        val ivIcon = btnTimelineMute.findViewById<ImageView>(R.id.ivTimelineMuteIcon)
        val tvLabel = btnTimelineMute.findViewById<TextView>(R.id.tvTimelineMuteLabel)
        if (isMuted) {
            ivIcon?.setImageResource(R.drawable.ic_volume_off_24)
            ivIcon?.setColorFilter(getColor(R.color.activeTool))
            tvLabel?.text = "Muted"
            tvLabel?.setTextColor(getColor(R.color.activeTool))
            btnTimelineMute.setBackgroundResource(R.drawable.bg_timeline_mute_active)
        } else {
            ivIcon?.setImageResource(R.drawable.ic_volume_up_24)
            ivIcon?.setColorFilter(getColor(R.color.textColor))
            tvLabel?.text = "Mute"
            tvLabel?.setTextColor(getColor(R.color.textColor))
            btnTimelineMute.setBackgroundResource(R.drawable.bg_timeline_mute)
        }
    }

    private fun showVideoSegmentTrimDialog(index: Int, item: com.vikash.vidopro.models.EditOperation.MergeItem) {
        if (isShowingPreview) dismissPreview()

        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.trim_bottom_sheet_dialog, null)
        bottomSheet.setContentView(view)

        val tvDurationDisplay = view.findViewById<TextView>(R.id.tvTrimDuration)
        val trimTrackView = view.findViewById<com.vikash.vidopro.customviews.TrackTrimView>(R.id.trimTrackView)
        val recyclerView = view.findViewById<RecyclerView>(R.id.trimRecyclerView)
        val btnDone = view.findViewById<Button>(R.id.btnDoneTrim)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseTrimSheet)

        // Configure RecyclerView for 15 dynamic tiles
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        val dialogRecyclerViewWidth = resources.displayMetrics.widthPixels - 80.dpToPx()
        val dialogItemWidth = maxOf(1, dialogRecyclerViewWidth / 15)
        val adapter = FrameAdapter(emptyList(), dialogItemWidth)
        recyclerView.adapter = adapter

        val isPhoto = item.isImage || isImageUri(item.uri)
        val maxDur = if (isPhoto) maxOf(item.durationMs, 600000L) else item.durationMs

        if (isPhoto) {
            lifecycleScope.launch(Dispatchers.IO) {
                val bmp = try {
                    contentResolver.openInputStream(item.uri)?.use { input ->
                        android.graphics.BitmapFactory.decodeStream(input)
                    }
                } catch (e: Exception) { null }
                if (bmp != null) {
                    withContext(Dispatchers.Main) {
                        adapter.updateFrames(List(15) { bmp })
                    }
                }
            }
        } else {
            val job = extractFramesForSegment(item.uri, item.durationMs, adapter)
            if (job != null) {
                activeRenderJobs.add(job)
                recyclerView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}
                    override fun onViewDetachedFromWindow(v: View) {
                        job.cancel()
                        activeRenderJobs.remove(job)
                    }
                })
            }
        }

        // Configure custom TrackTrimView as the premium Range Slider
        trimTrackView.isMainVideoTrack = true
        trimTrackView.isTrimEnabled = true
        trimTrackView.trackColor = android.graphics.Color.TRANSPARENT
        trimTrackView.maxDurationMs = maxDur
        trimTrackView.customMsPerPixel = maxDur.toFloat() / dialogRecyclerViewWidth

        trimTrackView.activeStartMs = item.trimStartMs
        trimTrackView.activeEndMs = item.trimEndMs
        trimTrackView.setRange(maxDur, item.trimStartMs, item.trimEndMs)
        
        if (!isPhoto) {
            val waveJob = lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val path = getFilePathFromUri(item.uri)
                if (path != null) {
                    val amps = com.vikash.vidopro.utils.AudioWaveformExtractor.extractWaveform(this@VideoEditingActivity, path)
                    if (amps != null) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            trimTrackView.audioAmplitudes = amps
                            trimTrackView.isAudioTrack = true
                            trimTrackView.invalidate()
                        }
                    }
                }
            }
            activeRenderJobs.add(waveJob)
        }

        var currentStart = item.trimStartMs
        var currentEnd = item.trimEndMs

        fun updateTimeText(start: Long, end: Long) {
            val duration = end - start
            tvDurationDisplay.text = "${formatDuration(start.toInt())} - ${formatDuration(end.toInt())} (${formatDuration(duration.toInt())})"
        }
        updateTimeText(item.trimStartMs, item.trimEndMs)

        trimTrackView.onTrimAdjustingWithDelta = { start, end, deltaL, deltaR ->
            currentStart = start
            currentEnd = end
            updateTimeText(start, end)

            val sequenceItems = getSequenceItems()
            val clipStartGlobal = sequenceItems.take(index).sumOf { it.trimmedDurationMs }
            if (deltaL != 0L) {
                seekToGlobalPosition(clipStartGlobal + start)
            } else if (deltaR != 0L) {
                seekToGlobalPosition(clipStartGlobal + end)
            }
        }

        trimTrackView.onTrimChanged = { start, end, _ ->
            currentStart = start
            currentEnd = end
            updateTimeText(start, end)
        }

        btnDone.setBounceClickListener {
            if (index == 0) {
                viewModel.updateMainVideoTrim(currentStart, currentEnd)
            } else {
                viewModel.updateMergeItemTrim(index - 1, currentStart, currentEnd)
            }
            bottomSheet.dismiss()
        }

        btnClose.setBounceClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun updateVideoMuteButtonState(toolbar: View, index: Int) {
        val isMuted = viewModel.project.value?.operations?.filterIsInstance<com.vikash.vidopro.models.EditOperation.MuteClip>()
            ?.find { it.index == index }?.isMuted ?: false
        
        val btnMute = toolbar.findViewById<ImageButton>(R.id.btnVideoMute)
        val tvMuteLabel = toolbar.findViewById<TextView>(R.id.tvVideoMuteLabel)
        
        if (isMuted) {
            btnMute?.setImageResource(R.drawable.ic_volume_off_24)
            tvMuteLabel?.text = "Unmute"
        } else {
            btnMute?.setImageResource(R.drawable.ic_volume_up_24)
            tvMuteLabel?.text = "Mute"
        }
    }

    private fun showColorFilterSelectionDialog(clipIndex: Int) {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_color_filters, null)
        bottomSheet.setContentView(view)

        val filtersList = view.findViewById<LinearLayout>(R.id.colorFiltersList)
        val project = viewModel.project.value ?: return
        val existingOp = project.operations.filterIsInstance<com.vikash.vidopro.models.EditOperation.ColorFilter>().find { it.index == clipIndex }
        var activeFilterName = existingOp?.filterName ?: "none"

        val filters = listOf(
            Pair("none", "None"),
            Pair("vintage", "Vintage"),
            Pair("warm", "Warm"),
            Pair("cool", "Cool"),
            Pair("contrast", "Contrast"),
            Pair("monochrome", "B&W"),
            Pair("vignette", "Vignette"),
            Pair("negative", "Negative"),
            Pair("crossprocess", "Cross P")
        )

        val itemBgs = mutableMapOf<String, FrameLayout>()
        val itemTvShorts = mutableMapOf<String, TextView>()

        fun refreshSelectionStates() {
            for ((id, bgFrame) in itemBgs) {
                val tvShort = itemTvShorts[id]
                val isSelected = (id == activeFilterName)
                if (isSelected) {
                    bgFrame.setBackgroundResource(R.drawable.bg_aspect_ratio_selected)
                    bgFrame.foreground = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_transition_border_selected)
                    tvShort?.setTextColor(android.graphics.Color.WHITE)
                } else {
                    bgFrame.setBackgroundResource(R.drawable.bg_aspect_ratio_item)
                    bgFrame.foreground = null
                    tvShort?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.textColor))
                }
            }
        }

        for ((filterId, displayName) in filters) {
            val itemView = layoutInflater.inflate(R.layout.item_color_filter_option, filtersList, false)
            val tvName = itemView.findViewById<TextView>(R.id.filterName)
            val bg = itemView.findViewById<FrameLayout>(R.id.filterIconBg)
            val previewImage = itemView.findViewById<ImageView>(R.id.filterPreviewImage)
            val tvShort = itemView.findViewById<TextView>(R.id.filterShortName)

            itemBgs[filterId] = bg
            itemTvShorts[filterId] = tvShort

            tvName.text = displayName
            bg.clipToOutline = true

            if (filterId == "none") {
                previewImage.visibility = View.GONE
                tvShort.visibility = View.VISIBLE
                tvShort.text = "✖"
            } else {
                previewImage.visibility = View.VISIBLE
                tvShort.visibility = View.GONE
                val resName = "filter_preview_${filterId.lowercase()}"
                val resId = resources.getIdentifier(resName, "drawable", packageName)
                if (resId != 0) {
                    previewImage.setImageResource(resId)
                }
            }

            itemView.setOnClickListener {
                activeFilterName = filterId
                refreshSelectionStates()
                viewModel.setColorFilter(clipIndex, filterId)
                applyColorFilterToPlayer(filterId)
            }
            
            filtersList.addView(itemView)
        }

        refreshSelectionStates()

        view.findViewById<View>(R.id.btnApplyToAll)?.setOnClickListener {
            val chunkCount = chunkDurationsMs.size
            for (i in 0 until chunkCount) {
                viewModel.setColorFilter(i, activeFilterName)
            }
            applyColorFilterToPlayer(activeFilterName)
            viewModel.project.value?.let { renderTracks(it) }
            bottomSheet.dismiss()
        }

        view.findViewById<View>(R.id.btnDone)?.setOnClickListener {
            viewModel.project.value?.let { renderTracks(it) }
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun showTransitionDialog(transitionIndex: Int) {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_transitions, null)
        bottomSheet.setContentView(view)

        val transitionsList = view.findViewById<LinearLayout>(R.id.transitionsList)
        val project = viewModel.project.value ?: return
        val existingOp = project.operations.filterIsInstance<com.vikash.vidopro.models.EditOperation.Transition>().find { it.index == transitionIndex }
        var activeTransitionType = existingOp?.type ?: "none"
        var activeDurationMs = existingOp?.durationMs ?: 1000L

        fun applyTransitionToIndex(idx: Int, transType: String, durationMs: Long = activeDurationMs) {
            val proj = viewModel.project.value ?: return
            val existing = proj.operations.filterIsInstance<com.vikash.vidopro.models.EditOperation.Transition>().find { it.index == idx }
            if (transType == "none") {
                if (existing != null) {
                    viewModel.removeOperation(existing.id)
                }
            } else {
                if (existing != null) {
                    viewModel.updateOperation(existing.copy(type = transType, durationMs = durationMs))
                } else {
                    viewModel.addTransitionOperation(idx, transType, durationMs)
                }
            }
        }

        val sliderDuration = view.findViewById<com.google.android.material.slider.Slider>(R.id.sliderTransitionDuration)
        val tvDurationVal = view.findViewById<TextView>(R.id.tvTransitionDurationValue)

        val durationSec = (activeDurationMs / 1000.0f).coerceIn(0.1f, 3.0f)
        sliderDuration?.value = (Math.round(durationSec * 10f) / 10f).coerceIn(0.1f, 3.0f)
        tvDurationVal?.text = String.format(java.util.Locale.US, "%.1fs", durationSec)

        sliderDuration?.addOnChangeListener { _, value, fromUser ->
            activeDurationMs = (value * 1000f).toLong()
            tvDurationVal?.text = String.format(java.util.Locale.US, "%.1fs", value)
            if (fromUser && activeTransitionType != "none") {
                applyTransitionToIndex(transitionIndex, activeTransitionType, activeDurationMs)
            }
        }

        val transitions = listOf(
            Pair("none", "None"),
            Pair("fade", "Fade"),
            Pair("fadeblack", "Fade Black"),
            Pair("fadewhite", "Fade White"),
            Pair("dissolve", "Dissolve"),
            Pair("wipeleft", "Wipe L"),
            Pair("wiperight", "Wipe R"),
            Pair("wipeup", "Wipe Up"),
            Pair("wipedown", "Wipe Down"),
            Pair("slideleft", "Slide L"),
            Pair("slideright", "Slide R"),
            Pair("slideup", "Slide Up"),
            Pair("slidedown", "Slide Down"),
            Pair("coverleft", "Cover L"),
            Pair("coverright", "Cover R"),
            Pair("circlecrop", "Circle"),
            Pair("rectcrop", "Box"),
            Pair("zoomin", "Zoom In"),
            Pair("pixelize", "Pixelize"),
            Pair("hlslice", "H-Slice"),
            Pair("vuslice", "V-Slice")
        )

        val itemBgs = mutableMapOf<String, FrameLayout>()
        val itemTvShorts = mutableMapOf<String, TextView>()

        fun refreshSelectionStates() {
            for ((typeKey, bgFrame) in itemBgs) {
                val tvShort = itemTvShorts[typeKey]
                val isSelected = (typeKey == activeTransitionType)
                if (isSelected) {
                    bgFrame.setBackgroundResource(R.drawable.bg_aspect_ratio_selected)
                    bgFrame.foreground = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_transition_border_selected)
                    tvShort?.setTextColor(android.graphics.Color.WHITE)
                } else {
                    bgFrame.setBackgroundResource(R.drawable.bg_aspect_ratio_item)
                    bgFrame.foreground = null
                    tvShort?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.textColor))
                }
            }
        }

        for ((type, name) in transitions) {
            val itemView = layoutInflater.inflate(R.layout.item_transition_option, transitionsList, false)
            val tvName = itemView.findViewById<TextView>(R.id.transitionName)
            val tvShort = itemView.findViewById<TextView>(R.id.transitionShortName)
            val bg = itemView.findViewById<FrameLayout>(R.id.transitionIconBg)

            itemBgs[type] = bg
            itemTvShorts[type] = tvShort

            tvName.text = name
            bg.clipToOutline = true

            val ivPreview = itemView.findViewById<ImageView>(R.id.transitionPreviewImage)
            val startDrawable = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.trans_frame_start)
            val endDrawable = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.trans_frame_end)

            if (startDrawable != null && endDrawable != null) {
                if (type == "none") {
                    ivPreview.setImageDrawable(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_close_24))
                    ivPreview.visibility = View.VISIBLE
                    tvShort.visibility = View.GONE
                } else {
                    val animation = android.graphics.drawable.AnimationDrawable().apply {
                        isOneShot = false
                    }
                    val frame1Res = resources.getIdentifier("trans_preview_${type}_1", "drawable", packageName)
                    val frame2Res = resources.getIdentifier("trans_preview_${type}_2", "drawable", packageName)
                    val frame3Res = resources.getIdentifier("trans_preview_${type}_3", "drawable", packageName)

                    if (frame1Res != 0 && frame2Res != 0 && frame3Res != 0) {
                        val f1 = androidx.core.content.ContextCompat.getDrawable(this, frame1Res)
                        val f2 = androidx.core.content.ContextCompat.getDrawable(this, frame2Res)
                        val f3 = androidx.core.content.ContextCompat.getDrawable(this, frame3Res)

                        if (f1 != null && f2 != null && f3 != null) {
                            animation.addFrame(startDrawable, 600)
                            animation.addFrame(f1, 100)
                            animation.addFrame(f2, 100)
                            animation.addFrame(f3, 100)
                            animation.addFrame(endDrawable, 600)

                            ivPreview.setImageDrawable(animation)
                            ivPreview.visibility = View.VISIBLE
                            tvShort.visibility = View.GONE
                            ivPreview.post { animation.start() }
                        } else {
                            ivPreview.visibility = View.GONE
                            tvShort.visibility = View.VISIBLE
                            tvShort.text = name.substring(0, minOf(2, name.length)).uppercase()
                        }
                    } else {
                        ivPreview.visibility = View.GONE
                        tvShort.visibility = View.VISIBLE
                        tvShort.text = name.substring(0, minOf(2, name.length)).uppercase()
                    }
                }
            } else {
                ivPreview.visibility = View.GONE
                tvShort.visibility = View.VISIBLE
                tvShort.text = name.substring(0, minOf(2, name.length)).uppercase()
            }

            itemView.setOnClickListener {
                activeTransitionType = type
                refreshSelectionStates()
                applyTransitionToIndex(transitionIndex, type, activeDurationMs)
            }

            transitionsList.addView(itemView)
        }

        refreshSelectionStates()

        view.findViewById<View>(R.id.btnApplyToAll)?.setOnClickListener {
            val transitionsCount = chunkDurationsMs.size - 1
            for (i in 0 until transitionsCount) {
                applyTransitionToIndex(i, activeTransitionType, activeDurationMs)
            }
            viewModel.project.value?.let { renderTracks(it) }
            bottomSheet.dismiss()
        }

        view.findViewById<View>(R.id.btnDone)?.setOnClickListener {
            viewModel.project.value?.let { renderTracks(it) }
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun setupCustomSeeker() {
        customVideoSeeker.onSeekListener = { seekPosition ->
            // Dismiss preview when user manually seeks
            if (isShowingPreview) dismissPreview()

            val totalDuration = getTotalSequenceDuration()
            val newSeekTime = (totalDuration * seekPosition).toLong()
            if (newSeekTime >= 0 && newSeekTime <= totalDuration) {
                seekToGlobalPosition(newSeekTime)
                updateDurationDisplay(newSeekTime.toInt(), totalDuration.toInt())
                
                textOverlayView?.currentPositionMs = newSeekTime
                imageOverlayView?.currentPositionMs = newSeekTime
            } else {
                Log.d("SeekError", "Seek position out of bounds.")
            }
        }
    }

    private fun setupTrackInitializers() {
        // Nothing here anymore since sequence track is dynamic
    }

    // Inside VideoEditingActivity

    private fun extractFramesForSegment(uri: Uri, durationMs: Long, adapter: FrameAdapter): kotlinx.coroutines.Job? {
        if (durationMs <= 0) return null
        
        val cached = frameCache.get(uri)
        if (cached != null) {
            adapter.updateFrames(cached)
            return null
        }

        if (activeExtractionUris.contains(uri)) {
            return lifecycleScope.launch {
                while (activeExtractionUris.contains(uri) && frameCache.get(uri) == null) {
                    kotlinx.coroutines.delay(100)
                }
                frameCache.get(uri)?.let { adapter.updateFrames(it) }
            }
        }

        activeExtractionUris.add(uri)
        activeExtractionCount++
        if (isImportLoading) {
            showLoading(getString(R.string.loading), getString(R.string.loading_tag))
        }

        return lifecycleScope.launch(Dispatchers.IO) {
            frameExtractionSemaphore.acquire()
            val retriever = MediaMetadataRetriever()
            val tempFrames = mutableListOf<Bitmap>()
            try {
                val path = getFilePathFromUri(uri)
                if (path != null) {
                    retriever.setDataSource(path)
                } else {
                    retriever.setDataSource(this@VideoEditingActivity, uri)
                }

                val frameCount = 15
                val intervalUs = (durationMs * 1000) / frameCount

                for (i in 0 until frameCount) {
                    if (!coroutineContext.isActive) break
                    val timeUs = i * intervalUs
                    val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 200, 150)
                    } else {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }

                    bitmap?.let {
                        val finalBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) it else processBitmap(it)
                        tempFrames.add(finalBitmap)
                        val progressPercent = ((i + 1) * 100) / frameCount
                        withContext(Dispatchers.Main) {
                            adapter.addFrame(finalBitmap)
                            if (isImportLoading) {
                                updateLoadingProgress(progressPercent)
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (tempFrames.isNotEmpty()) {
                        frameCache.put(uri, tempFrames)
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Extraction error for segment: ${e.message}")
                }
            } finally {
                retriever.release()
                frameExtractionSemaphore.release()
                withContext(kotlinx.coroutines.NonCancellable) {
                    withContext(Dispatchers.Main) {
                        activeExtractionUris.remove(uri)
                        activeExtractionCount--
                        if (activeExtractionCount <= 0) {
                            activeExtractionCount = 0
                            if (isImportLoading) {
                                isImportLoading = false
                                loadingScreen.visibility = View.GONE
                            }
                        }
                    }
                }
            }
        }
    }

    // Helper to keep scaling logic clean and maintain aspect ratio
    private fun processBitmap(source: Bitmap): Bitmap {
        val aspectRatio = source.width.toFloat() / source.height.toFloat()
        val targetHeight = 150
        val targetWidth = (targetHeight * aspectRatio).toInt()
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    @SuppressLint("SetTextI18n")
    private fun updateDurationDisplay(current: Int, total: Int) {
        if (total <= 0) return
        tvDuration.text = "${formatDuration(current)} / ${formatDuration(total)}"
        if (::tvFullscreenCurrentTime.isInitialized) {
            tvFullscreenCurrentTime.text = formatDuration(current)
        }
        if (::tvFullscreenTotalTime.isInitialized) {
            tvFullscreenTotalTime.text = formatDuration(total)
        }
        if (::sbFullscreenSeeker.isInitialized && !isUserScrubbingFullscreenSeeker) {
            val progress = ((current.toFloat() / total.toFloat()) * 1000).toInt().coerceIn(0, 1000)
            sbFullscreenSeeker.progress = progress
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFullscreenAndPipControls() {
        btnFullscreen.setBounceClickListener {
            enterFullscreenMode()
        }

        btnExitFullscreen.setBounceClickListener {
            exitFullscreenMode()
        }
        btnExitFullscreenTop.setBounceClickListener {
            exitFullscreenMode()
        }

        btnFullscreenPlayPause.setBounceClickListener {
            if (::player.isInitialized && isVideoLoaded) {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    val totalDuration = getTotalSequenceDuration()
                    val currentGlobalPos = getGlobalPosition()
                    if (currentGlobalPos >= totalDuration - 100L || player.playbackState == Player.STATE_ENDED) {
                        seekToGlobalPosition(0L)
                        timelineHorizontalScroll.scrollTo(0, 0)
                        updateDurationDisplay(0, totalDuration.toInt())
                    }
                    player.play()
                }
            }
            showFullscreenControlsTemporarily()
        }

        fullscreenOverlay.setOnClickListener {
            if (fullscreenControls.visibility == View.VISIBLE && fullscreenControls.alpha > 0.5f) {
                fullscreenHideHandler.removeCallbacks(hideFullscreenControlsRunnable)
                fullscreenControls.animate().alpha(0f).setDuration(250).withEndAction {
                    fullscreenControls.visibility = View.GONE
                }.start()
            } else {
                showFullscreenControlsTemporarily()
            }
        }

        sbFullscreenSeeker.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val totalMs = getTotalSequenceDuration()
                    if (totalMs > 0) {
                        val targetMs = ((progress.toFloat() / 1000f) * totalMs).toLong()
                        seekToGlobalPosition(targetMs)
                        isProgrammaticScroll = true
                        timelineHorizontalScroll.scrollTo((targetMs * pixelsPerMs).toInt(), 0)
                        isProgrammaticScroll = false
                        tvFullscreenCurrentTime.text = formatDuration(targetMs.toInt())
                        tvDuration.text = "${formatDuration(targetMs.toInt())} / ${formatDuration(totalMs.toInt())}"
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserScrubbingFullscreenSeeker = true
                fullscreenHideHandler.removeCallbacks(hideFullscreenControlsRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserScrubbingFullscreenSeeker = false
                showFullscreenControlsTemporarily()
            }
        })

        btnExpandTimeline.setBounceClickListener {
            toggleTimelineExpandedMode()
        }

        var pipInitialX = 0f
        var pipInitialY = 0f
        var touchStartX = 0f
        var touchStartY = 0f

        pipPlayerContainer.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    pipInitialX = view.x
                    pipInitialY = view.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchStartX
                    val dy = event.rawY - touchStartY
                    val parent = view.parent as? View ?: return@setOnTouchListener false
                    val maxW = (parent.width - view.width).toFloat()
                    val maxH = (parent.height - view.height).toFloat()
                    val newX = (pipInitialX + dx).coerceIn(0f, maxW)
                    val newY = (pipInitialY + dy).coerceIn(0f, maxH)
                    view.x = newX
                    view.y = newY
                    true
                }
                else -> false
            }
        }
    }

    private fun reparentCanvasContainer(targetContainer: ViewGroup) {
        val parent = canvasContainer.parent as? ViewGroup
        if (parent != targetContainer) {
            parent?.removeView(canvasContainer)
            targetContainer.addView(canvasContainer, 0, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        targetContainer.post {
            triggerCanvasLayoutUpdate()
        }
    }

    private fun triggerCanvasLayoutUpdate() {
        val cropOps = viewModel.project.value?.operations?.filterIsInstance<com.vikash.vidopro.models.EditOperation.Crop>()
        if (cropOps != null && cropOps.isNotEmpty() && cropEditingToolbar?.visibility != View.VISIBLE) {
            applyCropPreview(cropOps.last().aspectRatio)
        } else {
            resetCropPreview()
        }
    }

    private fun showFullscreenControlsTemporarily() {
        fullscreenHideHandler.removeCallbacks(hideFullscreenControlsRunnable)
        fullscreenControls.visibility = View.VISIBLE
        fullscreenControls.animate().cancel()
        fullscreenControls.alpha = 1f
        fullscreenHideHandler.postDelayed(hideFullscreenControlsRunnable, 3500)
    }

    private fun enterFullscreenMode() {
        isFullscreenMode = true
        fullscreenOverlay.visibility = View.VISIBLE
        fullscreenControls.visibility = View.VISIBLE
        fullscreenControls.alpha = 1f
        reparentCanvasContainer(fullscreenCanvasHolder)
        showFullscreenControlsTemporarily()
    }

    private fun exitFullscreenMode() {
        isFullscreenMode = false
        fullscreenHideHandler.removeCallbacks(hideFullscreenControlsRunnable)
        fullscreenOverlay.visibility = View.GONE
        if (isTimelineExpanded) {
            reparentCanvasContainer(pipPlayerContainer)
        } else {
            reparentCanvasContainer(playerContainer)
        }
    }

    private fun toggleTimelineExpandedMode() {
        isTimelineExpanded = !isTimelineExpanded
        val seekerContainer = findViewById<LinearLayout>(R.id.seekerContainer)

        if (isTimelineExpanded) {
            btnExpandTimeline.setImageResource(R.drawable.ic_collapse_24)
            playerContainer.visibility = View.GONE
            pipPlayerContainer.visibility = View.VISIBLE
            reparentCanvasContainer(pipPlayerContainer)

            val lpSeeker = seekerContainer.layoutParams as LinearLayout.LayoutParams
            lpSeeker.height = 0
            lpSeeker.weight = 1.0f
            seekerContainer.layoutParams = lpSeeker

            val lpTimeline = timelineContainer.layoutParams as LinearLayout.LayoutParams
            lpTimeline.height = 0
            lpTimeline.weight = 1.0f
            timelineContainer.layoutParams = lpTimeline
        } else {
            btnExpandTimeline.setImageResource(R.drawable.ic_expand_24)
            pipPlayerContainer.visibility = View.GONE
            playerContainer.visibility = View.VISIBLE
            reparentCanvasContainer(playerContainer)

            val lpSeeker = seekerContainer.layoutParams as LinearLayout.LayoutParams
            lpSeeker.height = LinearLayout.LayoutParams.WRAP_CONTENT
            lpSeeker.weight = 0f
            seekerContainer.layoutParams = lpSeeker

            val lpTimeline = timelineContainer.layoutParams as LinearLayout.LayoutParams
            lpTimeline.height = 140.dpToPx()
            lpTimeline.weight = 0f
            timelineContainer.layoutParams = lpTimeline
        }
    }

    private fun formatDuration(milliseconds: Int): String {
        return MediaFileHelper.formatDuration(milliseconds)
    }

    private fun showError(error: String) {
        Log.e(TAG, error)
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    private fun saveVideoToGallery(videoFile: File): Uri? {
        return MediaFileHelper.saveVideoToGallery(this, videoFile)
    }

    private fun processConcatList(concatList: String): String {
        return MediaFileHelper.processConcatList(this, concatList)
    }

    private fun getAudioExtension(uri: Uri): String {
        return MediaFileHelper.getAudioExtension(this, uri)
    }

    private fun copyContentUriToTempFile(contentUri: Uri): File? {
        return MediaFileHelper.copyContentUriToTempFile(this, contentUri)
    }

    private fun copyContentUriToTempFile(contentUri: Uri, prefix: String, extension: String = ".mp4"): File? {
        return MediaFileHelper.copyContentUriToTempFile(this, contentUri, prefix, extension)
    }

    // ── Segmented Preview Rendering ────────────────────────────────────────────

    /**
     * Render a fast 3-second preview segment around the current playhead.
     * Swaps ExoPlayer's source to the preview clip for immediate visual feedback.
     */
    private fun renderSegmentedPreview() {
        if (!::tempInputFile.isInitialized || !isVideoLoaded) return

        previewJob?.cancel()
        isRenderingPreview = true
        updateUIInteractionState()

        previewJob = lifecycleScope.launch {
            val seekPos = if (::player.isInitialized) player.currentPosition else 0L
            val previewOutput = File(cacheDir, "preview_segment_${System.currentTimeMillis()}.mp4")

            val cmd = viewModel.buildPreviewCommand(
                sourceFilePath = tempInputFile.absolutePath,
                previewOutputPath = previewOutput.absolutePath,
                seekPositionMs = seekPos,
                fontFilePath = fontFilePath,
                density = resources.displayMetrics.density
            ) ?: run {
                isRenderingPreview = false
                updateUIInteractionState()
                return@launch
            }

            Log.d(TAG, "Rendering segmented preview...")

            val result = withContext(Dispatchers.IO) {
                ffmpegEngine.executeCommand(cmd)
            }

            isRenderingPreview = false
            if (result is FFmpegRenderEngine.RenderResult.Success && previewOutput.exists()) {
                isShowingPreview = true
                previewFile?.delete() // Clean up previous preview
                previewFile = previewOutput

                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(previewOutput)))
                player.prepare()
                player.play()

                // Show preview badge
                try {
                    tvPreviewBadge?.visibility = View.VISIBLE
                } catch (_: Exception) {}
                updateUIInteractionState()
            } else {
                Log.w(TAG, "Preview render failed or was cancelled")
                previewOutput.delete()
                updateUIInteractionState()
            }
        }
    }

    /**
     * Dismiss the preview and restore the original video source.
     */
    private fun dismissPreview() {
        if (!isShowingPreview) return
        isShowingPreview = false
        previewJob?.cancel()

        videoUri?.let {
            player.setMediaItem(MediaItem.fromUri(it))
            player.prepare()
        }

        try {
            tvPreviewBadge?.visibility = View.GONE
        } catch (_: Exception) {}

        previewFile?.delete()
        previewFile = null
        updateUIInteractionState()
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) {
            player.pause()
        }
        activeRenderJobs.forEach { it.cancel() }
        activeRenderJobs.clear()
        frameExtractionJob?.cancel()
        previewJob?.cancel()
    }

    override fun onDestroy() {
        if (::sequenceTrackContainer.isInitialized) {
            pendingRenderRunnable?.let { sequenceTrackContainer.removeCallbacks(it) }
        }
        if (::exportController.isInitialized) {
            exportController.unregister()
        }
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).unregisterReceiver(proxyReceiver)
        frameExtractionJob?.cancel()
        previewJob?.cancel()
        clearFrameCache()
        extractedFrames.forEach { if (!it.isRecycled) it.recycle() }
        draggableTextOverlay?.deactivate()
        draggableImageOverlay?.deactivate()
        previewFile?.delete()
        super.onDestroy()
        if (::player.isInitialized) {
            player.release()
        }
        ffmpegEngine.cleanup()
    }

    private val colorsList = listOf(
        "#FFFFFF", "#000000", "#FF3B30", "#FF9500", "#FFCC00",
        "#34C759", "#30B0C7", "#007AFF", "#5856D6", "#AF52DE", "#FF2D55"
    )
    private var selectedTextColor = "#FFFFFF"

    private fun setupColorPicker(toolbar: View) {
        val colorPickerList = toolbar.findViewById<LinearLayout>(R.id.colorPickerList) ?: return
        colorPickerList.removeAllViews()

        val density = resources.displayMetrics.density
        val sizePx = (36 * density).toInt()
        val marginPx = (8 * density).toInt()

        for (colorHex in colorsList) {
            val colorView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    setMargins(marginPx, 0, marginPx, 0)
                }

                val shape = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor(colorHex))
                    if (colorHex == selectedTextColor) {
                        setStroke((3 * density).toInt(), android.graphics.Color.parseColor("#007AFF"))
                    } else {
                        if (colorHex == "#FFFFFF" || colorHex == "#000000") {
                            setStroke(1, android.graphics.Color.GRAY)
                        }
                    }
                }
                background = shape

                setBounceClickListener {
                    selectedTextColor = colorHex
                    draggableTextOverlay?.setTextColor(colorHex)
                    setupColorPicker(toolbar)
                }
            }
            colorPickerList.addView(colorView)
        }
    }

    private fun updateActiveBoundaries(activeStart: Long, activeEnd: Long) {
        for (i in 0 until textTrackContainer.childCount) {
            val track = textTrackContainer.getChildAt(i) as? com.vikash.vidopro.customviews.TrackTrimView
            track?.let {
                it.activeStartMs = activeStart
                it.activeEndMs = activeEnd
                it.invalidate()
            }
        }

        for (i in 0 until imageTrackContainer.childCount) {
            val track = imageTrackContainer.getChildAt(i) as? com.vikash.vidopro.customviews.TrackTrimView
            track?.let {
                it.activeStartMs = activeStart
                it.activeEndMs = activeEnd
                it.invalidate()
            }
        }

        for (i in 0 until audioTrackContainer.childCount) {
            val track = audioTrackContainer.getChildAt(i) as? com.vikash.vidopro.customviews.TrackTrimView
            track?.let {
                it.activeStartMs = activeStart
                it.activeEndMs = activeEnd
                it.invalidate()
            }
        }
    }

    private fun showAdjustSelectionDialog(clipIndex: Int) {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_adjustments, null)
        bottomSheet.setContentView(view)

        val optionsList = view.findViewById<LinearLayout>(R.id.adjustOptionsList)
        val slider = view.findViewById<com.google.android.material.slider.Slider>(R.id.adjustSlider)
        val valueLabel = view.findViewById<TextView>(R.id.adjustValueLabel)

        val project = viewModel.project.value ?: return
        val existingOp = project.operations.filterIsInstance<com.vikash.vidopro.models.EditOperation.Adjust>().find { it.index == clipIndex }
        
        var localAdjust = existingOp ?: com.vikash.vidopro.models.EditOperation.Adjust(index = clipIndex)
        var selectedOptionId = "brightness" // Default selection

        val options = listOf(
            Pair("reset", "Reset All"),
            Pair("brightness", "Brightness"),
            Pair("contrast", "Contrast"),
            Pair("warmth", "Warmth"),
            Pair("shadow", "Shadow"),
            Pair("highlights", "Highlights"),
            Pair("saturation", "Saturation"),
            Pair("exposure", "Exposure"),
            Pair("sharpen", "Sharpen"),
            Pair("vignette", "Vignette")
        )

        val itemBgs = mutableMapOf<String, FrameLayout>()
        val itemTvShorts = mutableMapOf<String, TextView>()
        val itemTvNames = mutableMapOf<String, TextView>()

        fun getValForOption(id: String): Int {
            return when (id) {
                "brightness" -> localAdjust.brightness
                "contrast" -> localAdjust.contrast
                "warmth" -> localAdjust.warmth
                "shadow" -> localAdjust.shadow
                "highlights" -> localAdjust.highlights
                "saturation" -> localAdjust.saturation
                "exposure" -> localAdjust.exposure
                "sharpen" -> localAdjust.sharpen
                "vignette" -> localAdjust.vignette
                else -> 0
            }
        }

        fun refreshSelectionStates() {
            for ((id, bgFrame) in itemBgs) {
                val tvName = itemTvNames[id]
                val isSelected = (id == selectedOptionId)
                val isModified = (id != "reset" && getValForOption(id) != 0)

                if (isSelected) {
                    bgFrame.setBackgroundResource(R.drawable.bg_aspect_ratio_selected)
                    bgFrame.foreground = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_transition_border_selected)
                    tvName?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorPrimary))
                } else if (isModified) {
                    bgFrame.setBackgroundResource(R.drawable.bg_aspect_ratio_item)
                    bgFrame.foreground = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_transition_border_selected)
                    tvName?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.textColor))
                } else {
                    bgFrame.setBackgroundResource(R.drawable.bg_aspect_ratio_item)
                    bgFrame.foreground = null
                    tvName?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.textColor))
                }
            }
        }

        fun updateSliderForSelection() {
            if (selectedOptionId == "reset") {
                slider.visibility = View.INVISIBLE
                valueLabel.text = "Reset All"
            } else {
                slider.visibility = View.VISIBLE
                val currentVal = getValForOption(selectedOptionId)
                slider.value = currentVal.toFloat()
                valueLabel.text = "${selectedOptionId.replaceFirstChar { it.uppercase() }}: $currentVal"
            }
        }

        val activeFilterName = project.operations.filterIsInstance<com.vikash.vidopro.models.EditOperation.ColorFilter>()
            .find { it.index == clipIndex }?.filterName ?: "none"

        for ((id, displayName) in options) {
            val itemView = layoutInflater.inflate(R.layout.item_adjust_option, optionsList, false)
            val tvName = itemView.findViewById<TextView>(R.id.adjustName)
            val bg = itemView.findViewById<FrameLayout>(R.id.adjustIconBg)
            val iconImage = itemView.findViewById<ImageView>(R.id.adjustIconImage)
            val tvShort = itemView.findViewById<TextView>(R.id.adjustShortName)

            itemBgs[id] = bg
            itemTvShorts[id] = tvShort
            itemTvNames[id] = tvName

            tvName.text = displayName
            bg.clipToOutline = true

            val resName = "ic_${id.lowercase()}_24"
            val resId = resources.getIdentifier(resName, "drawable", packageName)
            if (resId != 0) {
                iconImage.setImageResource(resId)
                iconImage.visibility = View.VISIBLE
                tvShort.visibility = View.GONE
            } else {
                iconImage.visibility = View.GONE
                tvShort.visibility = View.VISIBLE
                tvShort.text = displayName.substring(0, minOf(2, displayName.length)).uppercase()
            }

            itemView.setOnClickListener {
                if (id == "reset") {
                    localAdjust = com.vikash.vidopro.models.EditOperation.Adjust(index = clipIndex)
                    viewModel.setAdjust(
                        index = clipIndex,
                        brightness = 0,
                        contrast = 0,
                        warmth = 0,
                        shadow = 0,
                        highlights = 0,
                        saturation = 0,
                        exposure = 0,
                        sharpen = 0,
                        vignette = 0
                    )
                    applyColorFilterAndAdjustToPlayer(activeFilterName, localAdjust)
                    refreshSelectionStates()
                    updateSliderForSelection()
                } else {
                    selectedOptionId = id
                    refreshSelectionStates()
                    updateSliderForSelection()
                }
            }

            optionsList.addView(itemView)
        }

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser && selectedOptionId != "reset_all") {
                val intVal = value.toInt()
                localAdjust = when (selectedOptionId) {
                    "brightness" -> localAdjust.copy(brightness = intVal)
                    "contrast" -> localAdjust.copy(contrast = intVal)
                    "warmth" -> localAdjust.copy(warmth = intVal)
                    "shadow" -> localAdjust.copy(shadow = intVal)
                    "highlights" -> localAdjust.copy(highlights = intVal)
                    "saturation" -> localAdjust.copy(saturation = intVal)
                    "exposure" -> localAdjust.copy(exposure = intVal)
                    "sharpen" -> localAdjust.copy(sharpen = intVal)
                    "vignette" -> localAdjust.copy(vignette = intVal)
                    else -> localAdjust
                }

                valueLabel.text = "${selectedOptionId.replaceFirstChar { it.uppercase() }}: $intVal"
                
                viewModel.setAdjust(
                    index = clipIndex,
                    brightness = localAdjust.brightness,
                    contrast = localAdjust.contrast,
                    warmth = localAdjust.warmth,
                    shadow = localAdjust.shadow,
                    highlights = localAdjust.highlights,
                    saturation = localAdjust.saturation,
                    exposure = localAdjust.exposure,
                    sharpen = localAdjust.sharpen,
                    vignette = localAdjust.vignette
                )
                applyColorFilterAndAdjustToPlayer(activeFilterName, localAdjust)
                refreshSelectionStates()
            }
        }

        refreshSelectionStates()
        updateSliderForSelection()

        view.findViewById<View>(R.id.btnApplyToAll)?.setOnClickListener {
            val chunkCount = chunkDurationsMs.size
            for (i in 0 until chunkCount) {
                viewModel.setAdjust(
                    index = i,
                    brightness = localAdjust.brightness,
                    contrast = localAdjust.contrast,
                    warmth = localAdjust.warmth,
                    shadow = localAdjust.shadow,
                    highlights = localAdjust.highlights,
                    saturation = localAdjust.saturation,
                    exposure = localAdjust.exposure,
                    sharpen = localAdjust.sharpen,
                    vignette = localAdjust.vignette
                )
            }
            applyColorFilterAndAdjustToPlayer(activeFilterName, localAdjust)
            viewModel.project.value?.let { renderTracks(it) }
            bottomSheet.dismiss()
        }

        view.findViewById<View>(R.id.btnDone)?.setOnClickListener {
            viewModel.project.value?.let { renderTracks(it) }
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun showQuitConfirmationDialog() {
        if (!viewModel.hasUnsavedEdits.value) {
            finish()
            return
        }

        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_unsaved_changes, null)

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveAndQuit).setBounceClickListener {
            bottomSheet.dismiss()
            shouldQuitAfterSave = true
            saveProjectLauncher.launch("project.lcprj")
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnKeepEditing).setBounceClickListener {
            bottomSheet.dismiss()
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDiscard).setBounceClickListener {
            bottomSheet.dismiss()
            finish()
        }

        bottomSheet.setContentView(view)
        bottomSheet.show()
    }

    private fun getSnapTargetsMs(): List<Long> {
        val targets = mutableSetOf<Long>()
        targets.add(0L)

        // 1. Clip segment boundaries
        var cumulative = 0L
        for (duration in chunkDurationsMs) {
            cumulative += duration
            targets.add(cumulative)
        }

        // 2. Operations boundaries
        viewModel.project.value?.operations?.forEach { op ->
            when (op) {
                is EditOperation.AddBackgroundAudio -> {
                    op.startTimeMs?.let { targets.add(it) }
                    op.endTimeMs?.let { targets.add(it) }
                    val start = op.startTimeMs ?: 0L
                    val end = op.endTimeMs ?: getTotalSequenceDuration()
                    for (beat in op.beats) {
                        val relative = beat - op.internalStartMs
                        if (relative >= 0) {
                            val timelineMs = start + relative
                            if (timelineMs <= end) {
                                targets.add(timelineMs)
                            }
                        }
                    }
                }
                is EditOperation.AddText -> {
                    op.startTimeMs?.let { targets.add(it) }
                    op.endTimeMs?.let { targets.add(it) }
                }
                is EditOperation.AddImageOverlay -> {
                    op.startTimeMs?.let { targets.add(it) }
                    op.endTimeMs?.let { targets.add(it) }
                }
                else -> {}
            }
        }

        return targets.toList().sorted()
    }

    private fun showChromaKeyDialog() {
        val selectedId = viewModel.selectedOperationId.value
        val op = if (selectedId != null) {
            viewModel.project.value?.operations?.find { (it as? EditOperation.AddImageOverlay)?.id == selectedId } as? EditOperation.AddImageOverlay
        } else null

        if (op == null && draggableImageOverlay?.visibility != View.VISIBLE) return

        val initialColor = op?.chromaKeyColor ?: draggableImageOverlay?.currentChromaColor ?: "#00FF00"
        val initialSimilarity = op?.chromaKeySimilarity ?: draggableImageOverlay?.currentChromaSimilarity ?: 0.1f
        val originalColor = op?.chromaKeyColor ?: draggableImageOverlay?.currentChromaColor
        val originalSimilarity = op?.chromaKeySimilarity ?: draggableImageOverlay?.currentChromaSimilarity ?: 0.1f

        com.vikash.vidopro.editor.dialogs.ChromaKeyDialog.show(
            activity = this,
            initialColor = initialColor,
            initialSimilarity = initialSimilarity,
            onPreview = { color, similarity ->
                draggableImageOverlay?.setChromaKey(color, similarity)
            },
            onApply = { color, similarity ->
                if (op != null && color != null) {
                    viewModel.updateOperation(op.copy(
                        chromaKeyColor = color,
                        chromaKeySimilarity = similarity
                    ))
                }
            },
            onClear = {
                if (op != null) {
                    viewModel.updateOperation(op.copy(chromaKeyColor = null))
                }
                draggableImageOverlay?.setChromaKey(null, 0.1f)
            },
            onRequestEyedropper = { onColorPicked ->
                Toast.makeText(this@VideoEditingActivity, "Tap a color on the image to pick it", Toast.LENGTH_SHORT).show()
                draggableImageOverlay?.isColorPickingMode = true
                draggableImageOverlay?.onColorPicked = { hex ->
                    draggableImageOverlay?.isColorPickingMode = false
                    onColorPicked(hex)
                }
            },
            onDismissWithoutApply = {
                draggableImageOverlay?.setChromaKey(originalColor, originalSimilarity)
                draggableImageOverlay?.isColorPickingMode = false
            }
        )
    }

    private fun showCustomColorPicker(initialHex: String, onColorPicked: (String) -> Unit) {
        com.vikash.vidopro.editor.dialogs.CustomColorPickerDialog.show(this, initialHex, onColorPicked)
    }

    private fun commitActiveEditsIfAny() {
        if (draggableImageOverlay?.visibility == View.VISIBLE) {
            draggableImageOverlay?.commitImage()
            findViewById<View>(R.id.imageEditingToolbar)?.visibility = View.GONE
        }
        
        if (draggableTextOverlay?.visibility == View.VISIBLE) {
            draggableTextOverlay?.commitText()
            findViewById<View>(R.id.textEditingToolbar)?.visibility = View.GONE
        }
        
        findViewById<android.widget.HorizontalScrollView>(R.id.editingControlsScroll)?.visibility = View.VISIBLE
    }

    private fun enterKeyframeEditingMode(isText: Boolean) {
        val selectedId = viewModel.selectedOperationId.value ?: return
        val project = viewModel.project.value ?: return
        val op = project.operations.find { it.id == selectedId } ?: return
        
        isKeyframeEditingMode = true
        activeKeyframeProperty = "Position"
        
        // Hide overlay editing toolbars
        imageEditingToolbar?.visibility = View.GONE
        textEditingToolbar?.visibility = View.GONE
        
        // Show keyframe toolbar
        val toolbar = keyframeEditingToolbar ?: return
        toolbar.visibility = View.VISIBLE
        
        toolbar.findViewById<Button>(R.id.btnKeyframeProperty)?.text = "Position"
        toolbar.findViewById<View>(R.id.layoutKeyframeSlider)?.visibility = View.GONE
        
        // Seek to overlay start position
        val startTime = when (op) {
            is EditOperation.AddImageOverlay -> op.startTimeMs ?: 0L
            is EditOperation.AddText -> op.startTimeMs ?: 0L
            else -> 0L
        }
        seekToGlobalPosition(startTime, force = true)
        
        updateDraggableOverlayFromKeyframes(startTime)
    }

    private fun exitKeyframeEditingMode() {
        isKeyframeEditingMode = false
        keyframeEditingToolbar?.visibility = View.GONE
        
        // Restore corresponding editing toolbar
        val selectedId = viewModel.selectedOperationId.value
        val project = viewModel.project.value
        val op = project?.operations?.find { it.id == selectedId }
        
        if (op is EditOperation.AddImageOverlay) {
            imageEditingToolbar?.visibility = View.VISIBLE
            // Sync current state back to draggable overlay
            draggableImageOverlay?.activateForEdit(op)
        } else if (op is EditOperation.AddText) {
            textEditingToolbar?.visibility = View.VISIBLE
            draggableTextOverlay?.activateForEdit(op)
        }
    }

    private fun showKeyframePropertyMenu(view: View) {
        val selectedId = viewModel.selectedOperationId.value ?: return
        val project = viewModel.project.value ?: return
        val op = project.operations.find { it.id == selectedId } ?: return
        
        val popup = androidx.appcompat.widget.PopupMenu(this, view)
        popup.menu.add("Position")
        popup.menu.add("Opacity")
        if (op is EditOperation.AddImageOverlay) {
            val isVideo = op.fileDurationMs != null && op.fileDurationMs > 0
            if (isVideo) {
                popup.menu.add("Speed")
            }
            if (op.maskConfig.shape != EditOperation.MaskShape.NONE) {
                popup.menu.add("Mask Position")
                popup.menu.add("Mask Size")
                popup.menu.add("Mask Rotation")
                popup.menu.add("Mask Feather")
            }
        }
        
        popup.setOnMenuItemClickListener { item ->
            activeKeyframeProperty = item.title.toString()
            val toolbar = keyframeEditingToolbar ?: return@setOnMenuItemClickListener true
            toolbar.findViewById<Button>(R.id.btnKeyframeProperty)?.text = activeKeyframeProperty
            
            val sliderLayout = toolbar.findViewById<View>(R.id.layoutKeyframeSlider)
            val slider = toolbar.findViewById<com.google.android.material.slider.Slider>(R.id.sliderKeyframeValue)
            
            if (activeKeyframeProperty == "Position" || activeKeyframeProperty == "Mask Position") {
                sliderLayout?.visibility = View.GONE
            } else {
                sliderLayout?.visibility = View.VISIBLE
                when (activeKeyframeProperty) {
                    "Opacity" -> {
                        slider?.valueFrom = 0.0f
                        slider?.valueTo = 100.0f
                        slider?.stepSize = 1.0f
                    }
                    "Speed" -> {
                        slider?.valueFrom = 0.25f
                        slider?.valueTo = 4.0f
                        slider?.stepSize = 0.05f
                    }
                    "Mask Size" -> {
                        slider?.valueFrom = 1.0f
                        slider?.valueTo = 200.0f
                        slider?.stepSize = 1.0f
                    }
                    "Mask Rotation" -> {
                        slider?.valueFrom = -180.0f
                        slider?.valueTo = 180.0f
                        slider?.stepSize = 1.0f
                    }
                    "Mask Feather" -> {
                        slider?.valueFrom = 0.0f
                        slider?.valueTo = 100.0f
                        slider?.stepSize = 1.0f
                    }
                }
            }
            
            updateDraggableOverlayFromKeyframes(getGlobalPosition())
            true
        }
        popup.show()
    }

    private fun handleKeyframeActionClick() {
        val selectedId = viewModel.selectedOperationId.value ?: return
        val project = viewModel.project.value ?: return
        val op = project.operations.find { it.id == selectedId } ?: return
        
        val globalTimeMs = getGlobalPosition()
        if (op != null) {
            val start = when (op) {
                is EditOperation.AddImageOverlay -> op.startTimeMs ?: 0L
                is EditOperation.AddText -> op.startTimeMs ?: 0L
                else -> 0L
            }
            val relativeTimeMs = globalTimeMs - start
            
            when (op) {
                is EditOperation.AddImageOverlay -> {
                    when (activeKeyframeProperty) {
                        "Position" -> {
                            val currentList = op.positionKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            if (existingIndex != -1) currentList.removeAt(existingIndex) else currentList.add(EditOperation.KeyframePoint(relativeTimeMs, op.relativeX, op.relativeY))
                            viewModel.updateOperation(op.copy(positionKeyframes = currentList))
                        }
                        "Opacity" -> {
                            val currentList = op.opacityKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            if (existingIndex != -1) currentList.removeAt(existingIndex) else {
                                val currentOpacity = if (currentList.isNotEmpty()) interpolateKeyframes(currentList, relativeTimeMs, op.opacity) else op.opacity
                                currentList.add(EditOperation.KeyframePoint(relativeTimeMs, currentOpacity))
                            }
                            viewModel.updateOperation(op.copy(opacityKeyframes = currentList))
                        }
                        "Speed" -> {
                            val currentList = op.speedKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            if (existingIndex != -1) currentList.removeAt(existingIndex) else {
                                val currentSpeed = if (currentList.isNotEmpty()) interpolateKeyframes(currentList, relativeTimeMs, 1.0f) else 1.0f
                                currentList.add(EditOperation.KeyframePoint(relativeTimeMs, currentSpeed))
                            }
                            viewModel.updateOperation(op.copy(speedKeyframes = currentList))
                        }
                        "Mask Position" -> {
                            val mc = op.maskConfig
                            val currentList = mc.positionKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            if (existingIndex != -1) currentList.removeAt(existingIndex) else {
                                val pos = mc.getInterpolatedPos(relativeTimeMs)
                                currentList.add(EditOperation.KeyframePoint(relativeTimeMs, pos.first, pos.second))
                            }
                            viewModel.updateOperation(op.copy(maskConfig = mc.copy(positionKeyframes = currentList)))
                        }
                        "Mask Size" -> {
                            val mc = op.maskConfig
                            val currentList = mc.sizeKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            if (existingIndex != -1) currentList.removeAt(existingIndex) else {
                                val sizeVal = mc.getInterpolatedSize(relativeTimeMs)
                                currentList.add(EditOperation.KeyframePoint(relativeTimeMs, sizeVal))
                            }
                            viewModel.updateOperation(op.copy(maskConfig = mc.copy(sizeKeyframes = currentList)))
                        }
                        "Mask Rotation" -> {
                            val mc = op.maskConfig
                            val currentList = mc.rotationKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            if (existingIndex != -1) currentList.removeAt(existingIndex) else {
                                val rotVal = mc.getInterpolatedRotation(relativeTimeMs)
                                currentList.add(EditOperation.KeyframePoint(relativeTimeMs, rotVal))
                            }
                            viewModel.updateOperation(op.copy(maskConfig = mc.copy(rotationKeyframes = currentList)))
                        }
                        "Mask Feather" -> {
                            val mc = op.maskConfig
                            val currentList = mc.featherKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            if (existingIndex != -1) currentList.removeAt(existingIndex) else {
                                val featherVal = mc.getInterpolatedFeather(relativeTimeMs)
                                currentList.add(EditOperation.KeyframePoint(relativeTimeMs, featherVal))
                            }
                            viewModel.updateOperation(op.copy(maskConfig = mc.copy(featherKeyframes = currentList)))
                        }
                    }
                }
                is EditOperation.AddText -> {
                    when (activeKeyframeProperty) {
                        "Position" -> {
                            val currentList = op.positionKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            if (existingIndex != -1) currentList.removeAt(existingIndex) else currentList.add(EditOperation.KeyframePoint(relativeTimeMs, op.relativeX ?: 0.5f, op.relativeY ?: 0.5f))
                            viewModel.updateOperation(op.copy(positionKeyframes = currentList))
                        }
                        "Opacity" -> {
                            val currentList = op.opacityKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            if (existingIndex != -1) currentList.removeAt(existingIndex) else {
                                val currentOpacity = if (currentList.isNotEmpty()) interpolateKeyframes(currentList, relativeTimeMs, op.opacity) else op.opacity
                                currentList.add(EditOperation.KeyframePoint(relativeTimeMs, currentOpacity))
                            }
                            viewModel.updateOperation(op.copy(opacityKeyframes = currentList))
                        }
                    }
                }
                else -> {}
            }
        } else {
            val index = selectedVideoIndex
            if (index != null && index >= 0) {
                val sequenceItems = getSequenceItems()
                val item = sequenceItems.getOrNull(index)
                if (item != null) {
                    val accumulatedStartMs = sequenceItems.take(index).sumOf { it.trimmedDurationMs }
                    val relativeTimeMs = globalTimeMs - accumulatedStartMs
                    val mc = item.maskConfig
                    when (activeKeyframeProperty) {
                        "Mask Position" -> {
                            val currentList = mc.positionKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            if (existingIndex != -1) currentList.removeAt(existingIndex) else {
                                val pos = mc.getInterpolatedPos(relativeTimeMs)
                                currentList.add(EditOperation.KeyframePoint(relativeTimeMs, pos.first, pos.second))
                            }
                            val newMc = mc.copy(positionKeyframes = currentList)
                            viewModel.updateMergeItemMask(index, newMc)
                            mainVideoMaskContainer?.maskConfig = newMc
                        }
                        "Mask Size" -> {
                            val currentList = mc.sizeKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            if (existingIndex != -1) currentList.removeAt(existingIndex) else {
                                val sizeVal = mc.getInterpolatedSize(relativeTimeMs)
                                currentList.add(EditOperation.KeyframePoint(relativeTimeMs, sizeVal))
                            }
                            val newMc = mc.copy(sizeKeyframes = currentList)
                            viewModel.updateMergeItemMask(index, newMc)
                            mainVideoMaskContainer?.maskConfig = newMc
                        }
                        "Mask Rotation" -> {
                            val currentList = mc.rotationKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            if (existingIndex != -1) currentList.removeAt(existingIndex) else {
                                val rotVal = mc.getInterpolatedRotation(relativeTimeMs)
                                currentList.add(EditOperation.KeyframePoint(relativeTimeMs, rotVal))
                            }
                            val newMc = mc.copy(rotationKeyframes = currentList)
                            viewModel.updateMergeItemMask(index, newMc)
                            mainVideoMaskContainer?.maskConfig = newMc
                        }
                        "Mask Feather" -> {
                            val currentList = mc.featherKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            if (existingIndex != -1) currentList.removeAt(existingIndex) else {
                                val featherVal = mc.getInterpolatedFeather(relativeTimeMs)
                                currentList.add(EditOperation.KeyframePoint(relativeTimeMs, featherVal))
                            }
                            val newMc = mc.copy(featherKeyframes = currentList)
                            viewModel.updateMergeItemMask(index, newMc)
                            mainVideoMaskContainer?.maskConfig = newMc
                        }
                    }
                }
            }
        }
        updateDraggableOverlayFromKeyframes(globalTimeMs)
    }

    private fun handleKeyframeSliderChange(value: Float) {
        val selectedId = viewModel.selectedOperationId.value
        val project = viewModel.project.value
        val globalTimeMs = getGlobalPosition()
        
        if (selectedId != null && project != null) {
            val op = project.operations.find { it.id == selectedId }
            if (op != null) {
                when (op) {
                    is EditOperation.AddImageOverlay -> {
                        val start = op.startTimeMs ?: 0L
                        val relativeTimeMs = globalTimeMs - start
                        val mc = op.maskConfig
                        when (activeKeyframeProperty) {
                            "Opacity" -> {
                                val opacityValue = value / 100.0f
                                val currentList = op.opacityKeyframes.toMutableList()
                                val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                                val newPoint = EditOperation.KeyframePoint(relativeTimeMs, opacityValue)
                                if (existingIndex != -1) currentList[existingIndex] = newPoint else currentList.add(newPoint)
                                viewModel.updateOperation(op.copy(opacityKeyframes = currentList))
                            }
                            "Speed" -> {
                                val currentList = op.speedKeyframes.toMutableList()
                                val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                                val newPoint = EditOperation.KeyframePoint(relativeTimeMs, value)
                                if (existingIndex != -1) currentList[existingIndex] = newPoint else currentList.add(newPoint)
                                viewModel.updateOperation(op.copy(speedKeyframes = currentList))
                            }
                            "Mask Size" -> {
                                val currentList = mc.sizeKeyframes.toMutableList()
                                val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                                val newPoint = EditOperation.KeyframePoint(relativeTimeMs, value)
                                if (existingIndex != -1) currentList[existingIndex] = newPoint else currentList.add(newPoint)
                                val relScale = value / 200f
                                val newMc = mc.copy(relativeWidth = relScale, relativeHeight = relScale, sizeKeyframes = currentList)
                                viewModel.updateOperation(op.copy(maskConfig = newMc))
                            }
                            "Mask Rotation" -> {
                                val currentList = mc.rotationKeyframes.toMutableList()
                                val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                                val newPoint = EditOperation.KeyframePoint(relativeTimeMs, value)
                                if (existingIndex != -1) currentList[existingIndex] = newPoint else currentList.add(newPoint)
                                val newMc = mc.copy(rotationAngle = value, rotationKeyframes = currentList)
                                viewModel.updateOperation(op.copy(maskConfig = newMc))
                            }
                            "Mask Feather" -> {
                                val currentList = mc.featherKeyframes.toMutableList()
                                val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                                val newPoint = EditOperation.KeyframePoint(relativeTimeMs, value)
                                if (existingIndex != -1) currentList[existingIndex] = newPoint else currentList.add(newPoint)
                                val newMc = mc.copy(feather = value, featherKeyframes = currentList)
                                viewModel.updateOperation(op.copy(maskConfig = newMc))
                            }
                        }
                    }
                    is EditOperation.AddText -> {
                        val start = op.startTimeMs ?: 0L
                        val relativeTimeMs = globalTimeMs - start
                        if (activeKeyframeProperty == "Opacity") {
                            val opacityValue = value / 100.0f
                            val currentList = op.opacityKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            val newPoint = EditOperation.KeyframePoint(relativeTimeMs, opacityValue)
                            if (existingIndex != -1) currentList[existingIndex] = newPoint else currentList.add(newPoint)
                            viewModel.updateOperation(op.copy(opacityKeyframes = currentList))
                        }
                    }
                    else -> {}
                }
            }
        } else {
            val videoIdx = selectedVideoIndex
            if (videoIdx != null && videoIdx >= 0) {
                val sequenceItems = getSequenceItems()
                val item = sequenceItems.getOrNull(videoIdx)
                if (item != null) {
                    val accumulatedStartMs = sequenceItems.take(videoIdx).sumOf { it.trimmedDurationMs }
                    val relativeTimeMs = globalTimeMs - accumulatedStartMs
                    val mc = item.maskConfig
                    when (activeKeyframeProperty) {
                        "Mask Size" -> {
                            val currentList = mc.sizeKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            val newPoint = EditOperation.KeyframePoint(relativeTimeMs, value)
                            if (existingIndex != -1) currentList[existingIndex] = newPoint else currentList.add(newPoint)
                            val relScale = value / 200f
                            val newMc = mc.copy(relativeWidth = relScale, relativeHeight = relScale, sizeKeyframes = currentList)
                            viewModel.updateMergeItemMask(videoIdx, newMc)
                            mainVideoMaskContainer?.maskConfig = newMc
                        }
                        "Mask Rotation" -> {
                            val currentList = mc.rotationKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            val newPoint = EditOperation.KeyframePoint(relativeTimeMs, value)
                            if (existingIndex != -1) currentList[existingIndex] = newPoint else currentList.add(newPoint)
                            val newMc = mc.copy(rotationAngle = value, rotationKeyframes = currentList)
                            viewModel.updateMergeItemMask(videoIdx, newMc)
                            mainVideoMaskContainer?.maskConfig = newMc
                        }
                        "Mask Feather" -> {
                            val currentList = mc.featherKeyframes.toMutableList()
                            val existingIndex = currentList.indexOfFirst { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            val newPoint = EditOperation.KeyframePoint(relativeTimeMs, value)
                            if (existingIndex != -1) currentList[existingIndex] = newPoint else currentList.add(newPoint)
                            val newMc = mc.copy(feather = value, featherKeyframes = currentList)
                            viewModel.updateMergeItemMask(videoIdx, newMc)
                            mainVideoMaskContainer?.maskConfig = newMc
                        }
                    }
                }
            }
        }
        updateDraggableOverlayFromKeyframes(globalTimeMs)
    }

    private fun updateKeyframeActionButtonState(globalTimeMs: Long) {
        val selectedId = viewModel.selectedOperationId.value
        val project = viewModel.project.value
        var hasKeyframe = false

        if (selectedId != null && project != null) {
            val op = project.operations.find { it.id == selectedId }
            if (op != null) {
                val start = when (op) {
                    is EditOperation.AddImageOverlay -> op.startTimeMs ?: 0L
                    is EditOperation.AddText -> op.startTimeMs ?: 0L
                    else -> 0L
                }
                val relativeTimeMs = globalTimeMs - start
                hasKeyframe = when (op) {
                    is EditOperation.AddImageOverlay -> {
                        when (activeKeyframeProperty) {
                            "Position" -> op.positionKeyframes.any { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            "Opacity" -> op.opacityKeyframes.any { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            "Speed" -> op.speedKeyframes.any { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            "Mask Position" -> op.maskConfig.positionKeyframes.any { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            "Mask Size" -> op.maskConfig.sizeKeyframes.any { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            "Mask Rotation" -> op.maskConfig.rotationKeyframes.any { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            "Mask Feather" -> op.maskConfig.featherKeyframes.any { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            else -> false
                        }
                    }
                    is EditOperation.AddText -> {
                        when (activeKeyframeProperty) {
                            "Position" -> op.positionKeyframes.any { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            "Opacity" -> op.opacityKeyframes.any { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
        } else {
            val videoIdx = selectedVideoIndex
            if (videoIdx != null && videoIdx >= 0) {
                val sequenceItems = getSequenceItems()
                val item = sequenceItems.getOrNull(videoIdx)
                if (item != null) {
                    val accumulatedStartMs = sequenceItems.take(videoIdx).sumOf { it.trimmedDurationMs }
                    val relativeTimeMs = globalTimeMs - accumulatedStartMs
                    val mc = item.maskConfig
                    hasKeyframe = when (activeKeyframeProperty) {
                        "Mask Position" -> mc.positionKeyframes.any { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                        "Mask Size" -> mc.sizeKeyframes.any { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                        "Mask Rotation" -> mc.rotationKeyframes.any { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                        "Mask Feather" -> mc.featherKeyframes.any { Math.abs(it.timeMs - relativeTimeMs) < 150L }
                        else -> false
                    }
                }
            }
        }
        
        val actionBtn = keyframeEditingToolbar?.findViewById<ImageButton>(R.id.btnKeyframeAction)
        if (hasKeyframe) {
            actionBtn?.setImageResource(R.drawable.ic_keyframe_remove)
            btnKeyframeDiamond?.setImageResource(R.drawable.ic_keyframe_remove)
            btnKeyframeDiamond?.setColorFilter(getColor(R.color.colorPrimary))
        } else {
            actionBtn?.setImageResource(R.drawable.ic_keyframe_add)
            btnKeyframeDiamond?.setImageResource(R.drawable.ic_keyframe_main)
            btnKeyframeDiamond?.setColorFilter(getColor(R.color.textColor))
        }
    }

    private fun updateDraggableOverlayFromKeyframes(globalTimeMs: Long) {
        val selectedId = viewModel.selectedOperationId.value
        val project = viewModel.project.value
        val slider = keyframeEditingToolbar?.findViewById<com.google.android.material.slider.Slider>(R.id.sliderKeyframeValue)
        val tvSliderValue = keyframeEditingToolbar?.findViewById<TextView>(R.id.tvSliderValue)
        val minVal = slider?.valueFrom ?: 0.0f
        val maxVal = slider?.valueTo ?: 100.0f

        if (selectedId != null && project != null) {
            val op = project.operations.find { it.id == selectedId }
            when (op) {
                is EditOperation.AddImageOverlay -> {
                    val start = op.startTimeMs ?: 0L
                    val relativeTimeMs = globalTimeMs - start
                    val interpolatedPos = if (op.positionKeyframes.isNotEmpty()) {
                        interpolateKeyframePosition(op.positionKeyframes, relativeTimeMs, op.relativeX, op.relativeY)
                    } else {
                        Pair(op.relativeX, op.relativeY)
                    }
                    val interpolatedOpacity = if (op.opacityKeyframes.isNotEmpty()) {
                        interpolateKeyframes(op.opacityKeyframes, relativeTimeMs, op.opacity)
                    } else {
                        op.opacity
                    }
                    
                    val evaluatedMask = op.maskConfig.evaluatedAt(relativeTimeMs)
                    
                    draggableImageOverlay?.setProperties(
                        interpolatedPos.first,
                        interpolatedPos.second,
                        op.relativeWidth,
                        op.relativeHeight,
                        op.rotationAngle,
                        interpolatedOpacity,
                        op.isMirrored
                    )
                    draggableImageOverlay?.maskConfig = evaluatedMask
                    draggableImageOverlay?.isMaskEditingMode = evaluatedMask.shape != EditOperation.MaskShape.NONE

                    if (activeKeyframeProperty == "Opacity") {
                        val progress = (interpolatedOpacity * 100).toInt()
                        slider?.value = progress.toFloat().coerceIn(minVal, maxVal)
                        tvSliderValue?.text = "$progress%"
                    } else if (activeKeyframeProperty == "Speed") {
                        val speed = if (op.speedKeyframes.isNotEmpty()) {
                            interpolateKeyframes(op.speedKeyframes, relativeTimeMs, 1.0f)
                        } else {
                            1.0f
                        }
                        val safeSpeed = speed.coerceIn(0.25f, 4.0f)
                        val steps = Math.round((safeSpeed - 0.25f) / 0.05f)
                        val steppedSpeed = 0.25f + (steps * 0.05f)
                        slider?.value = steppedSpeed.coerceIn(minVal, maxVal)
                        tvSliderValue?.text = String.format(java.util.Locale.US, "%.2fx", steppedSpeed)
                    } else if (activeKeyframeProperty == "Mask Size") {
                        val sizeVal = evaluatedMask.getInterpolatedSize(relativeTimeMs)
                        val roundedSize = Math.round(sizeVal).toFloat().coerceIn(minVal, maxVal)
                        slider?.value = roundedSize
                        tvSliderValue?.text = "${roundedSize.toInt()}%"
                    } else if (activeKeyframeProperty == "Mask Rotation") {
                        val rotVal = evaluatedMask.getInterpolatedRotation(relativeTimeMs)
                        val roundedRot = Math.round(rotVal).toFloat().coerceIn(minVal, maxVal)
                        slider?.value = roundedRot
                        tvSliderValue?.text = "${roundedRot.toInt()}°"
                    } else if (activeKeyframeProperty == "Mask Feather") {
                        val featherVal = evaluatedMask.getInterpolatedFeather(relativeTimeMs)
                        val roundedFeather = Math.round(featherVal).toFloat().coerceIn(minVal, maxVal)
                        slider?.value = roundedFeather
                        tvSliderValue?.text = "${roundedFeather.toInt()}%"
                    }
                }
                is EditOperation.AddText -> {
                    val start = op.startTimeMs ?: 0L
                    val relativeTimeMs = globalTimeMs - start
                    val interpolatedPos = if (op.positionKeyframes.isNotEmpty()) {
                        interpolateKeyframePosition(op.positionKeyframes, relativeTimeMs, op.relativeX ?: 0.5f, op.relativeY ?: 0.5f)
                    } else {
                        Pair(op.relativeX ?: 0.5f, op.relativeY ?: 0.5f)
                    }
                    val interpolatedOpacity = if (op.opacityKeyframes.isNotEmpty()) {
                        interpolateKeyframes(op.opacityKeyframes, relativeTimeMs, op.opacity)
                    } else {
                        op.opacity
                    }
                    
                    draggableTextOverlay?.setProperties(
                        interpolatedPos.first,
                        interpolatedPos.second,
                        interpolatedOpacity
                    )
                    
                    if (activeKeyframeProperty == "Opacity") {
                        val progress = (interpolatedOpacity * 100).toInt()
                        slider?.value = progress.toFloat().coerceIn(minVal, maxVal)
                        tvSliderValue?.text = "$progress%"
                    }
                }
                else -> {}
            }
        } else {
            val videoIdx = selectedVideoIndex
            if (videoIdx != null && videoIdx >= 0) {
                val sequenceItems = getSequenceItems()
                val item = sequenceItems.getOrNull(videoIdx)
                if (item != null) {
                    val accumulatedStartMs = sequenceItems.take(videoIdx).sumOf { it.trimmedDurationMs }
                    val relativeTimeMs = globalTimeMs - accumulatedStartMs
                    val evaluatedMask = item.maskConfig.evaluatedAt(relativeTimeMs)
                    
                    mainVideoMaskContainer?.maskConfig = evaluatedMask
                    videoMaskOverlayView?.maskConfig = evaluatedMask

                    if (activeKeyframeProperty == "Mask Size") {
                        val sizeVal = evaluatedMask.getInterpolatedSize(relativeTimeMs)
                        val roundedSize = Math.round(sizeVal).toFloat().coerceIn(minVal, maxVal)
                        slider?.value = roundedSize
                        tvSliderValue?.text = "${roundedSize.toInt()}%"
                    } else if (activeKeyframeProperty == "Mask Rotation") {
                        val rotVal = evaluatedMask.getInterpolatedRotation(relativeTimeMs)
                        val roundedRot = Math.round(rotVal).toFloat().coerceIn(minVal, maxVal)
                        slider?.value = roundedRot
                        tvSliderValue?.text = "${roundedRot.toInt()}°"
                    } else if (activeKeyframeProperty == "Mask Feather") {
                        val featherVal = evaluatedMask.getInterpolatedFeather(relativeTimeMs)
                        val roundedFeather = Math.round(featherVal).toFloat().coerceIn(minVal, maxVal)
                        slider?.value = roundedFeather
                        tvSliderValue?.text = "${roundedFeather.toInt()}%"
                    }
                }
            }
        }
        updateKeyframeActionButtonState(globalTimeMs)
    }

    private fun interpolateKeyframes(
        keyframes: List<com.vikash.vidopro.models.EditOperation.KeyframePoint>,
        timeMs: Long,
        defaultValue: Float
    ): Float {
        return com.vikash.vidopro.editor.keyframe.KeyframeInterpolator.interpolateKeyframes(keyframes, timeMs, defaultValue)
    }

    private fun interpolateKeyframePosition(
        keyframes: List<com.vikash.vidopro.models.EditOperation.KeyframePoint>,
        timeMs: Long,
        defaultX: Float,
        defaultY: Float
    ): Pair<Float, Float> {
        return com.vikash.vidopro.editor.keyframe.KeyframeInterpolator.interpolateKeyframePosition(keyframes, timeMs, defaultX, defaultY)
    }

    private fun showMaskBottomSheet() {
        val isMainVideo = selectedVideoIndex != null && videoEditingToolbar?.visibility == View.VISIBLE
        val initialMask = if (isMainVideo) {
            val idx = selectedVideoIndex!!
            val items = getSequenceItems()
            if (idx >= 0 && idx < items.size) items[idx].maskConfig else com.vikash.vidopro.models.EditOperation.MaskConfig()
        } else {
            draggableImageOverlay?.maskConfig ?: com.vikash.vidopro.models.EditOperation.MaskConfig()
        }

        com.vikash.vidopro.editor.dialogs.MaskBottomSheet.show(
            activity = this,
            initialMask = initialMask,
            isMainVideo = isMainVideo,
            onMaskChanged = { newMask ->
                if (isMainVideo) {
                    videoMaskOverlayView?.maskConfig = newMask
                    videoMaskOverlayView?.isEditingMode = true
                    videoMaskOverlayView?.invalidate()
                    mainVideoMaskContainer?.maskConfig = newMask
                    videoMaskOverlayView?.onMaskChanged?.invoke(newMask)
                } else {
                    draggableImageOverlay?.maskConfig = newMask
                    draggableImageOverlay?.isMaskEditingMode = newMask.shape != com.vikash.vidopro.models.EditOperation.MaskShape.NONE
                    draggableImageOverlay?.invalidate()
                    draggableImageOverlay?.onMaskChanged?.invoke(newMask)
                }
            },
            onDismiss = { finalMask ->
                if (isMainVideo) {
                    videoMaskOverlayView?.isEditingMode = false
                    selectedVideoIndex?.let { index ->
                        viewModel.updateMergeItemMask(index, finalMask)
                        mainVideoMaskContainer?.maskConfig = finalMask
                    }
                } else {
                    draggableImageOverlay?.isMaskEditingMode = false
                    draggableImageOverlay?.maskConfig = finalMask
                    draggableImageOverlay?.invalidate()
                    val selectedId = viewModel.selectedOperationId.value
                    if (selectedId != null) {
                        val op = viewModel.project.value?.operations?.find { (it as? EditOperation.AddImageOverlay)?.id == selectedId } as? EditOperation.AddImageOverlay
                        if (op != null) {
                            viewModel.updateOperation(op.copy(maskConfig = finalMask))
                        }
                    }
                }
            }
        )
    }

    companion object {
        private const val TAG = "VideoEditingActivity"
        private const val PICK_VIDEO_REQUEST = 1
        private const val PICK_AUDIO_REQUEST = 2
        private const val PICK_IMAGE_REQUEST = 3
        private const val PICK_SRT_REQUEST = 4
        private const val PICK_DIRECTORY_REQUEST = 5
        private const val PICK_BACKGROUND_IMAGE_REQUEST = 6
    }
}



