package com.vikash.vidopro.ui.editor

import android.view.ScaleGestureDetector
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SeekParameters
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.coroutines.delay

@Composable
fun VideoPlayer(
    videoUri: String,
    currentTimeMs: Long,
    isScrubbing: Boolean,
    onDurationKnown: (Long) -> Unit,
    onTimeUpdate: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Low latency load control: Fast seeking without buffering delays
    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                500,    // minBufferMs
                2000,   // maxBufferMs
                250,    // bufferForPlaybackMs
                500     // bufferForPlaybackAfterRebufferMs
            )
            .build()
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                if (videoUri.isNotEmpty()) {
                    setMediaItem(MediaItem.fromUri(videoUri))
                    prepare()
                }
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    onDurationKnown(exoPlayer.duration.coerceAtLeast(0L))
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Switch seek parameters for smooth scrubbing
    LaunchedEffect(isScrubbing) {
        if (isScrubbing) {
            exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            exoPlayer.pause()
        } else {
            exoPlayer.setSeekParameters(SeekParameters.EXACT)
        }
    }

    // Trigger seek on scrubbing
    LaunchedEffect(currentTimeMs, isScrubbing) {
        if (isScrubbing) {
            exoPlayer.seekTo(currentTimeMs)
        }
    }

    // Playhead sync during active playback
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying && !isScrubbing) {
                onTimeUpdate(exoPlayer.currentPosition)
            }
            delay(16L) // ~60 FPS
        }
    }

    // Pinch-to-zoom state with strict [1.0f, 5.0f] bounds
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clipToBounds()
            .pointerInput(Unit) {
                // Double tap resets zoom to 1.0f
                detectTapGestures(
                    onDoubleTap = {
                        zoomScale = 1.0f
                        panOffsetX = 0f
                        panOffsetY = 0f
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // Limit zoom strictly between 1.0f and 5.0f to prevent OOM
                    val newScale = (zoomScale * zoom).coerceIn(1.0f, 5.0f)
                    zoomScale = newScale
                    if (newScale > 1.0f) {
                        val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                        val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                        panOffsetX = (panOffsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                        panOffsetY = (panOffsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                    } else {
                        panOffsetX = 0f
                        panOffsetY = 0f
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                StyledPlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false

                    // ScaleGestureDetector on Video View
                    val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            val factor = detector.scaleFactor
                            val updatedScale = (scaleX * factor).coerceIn(1.0f, 5.0f)
                            scaleX = updatedScale
                            scaleY = updatedScale
                            return true
                        }
                    })

                    setOnTouchListener { v, event ->
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        scaleDetector.onTouchEvent(event)
                        if (scaleDetector.isInProgress || event.pointerCount >= 2) {
                            true
                        } else {
                            v.onTouchEvent(event)
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoomScale,
                    scaleY = zoomScale,
                    translationX = panOffsetX,
                    translationY = panOffsetY
                )
        )
    }
}
