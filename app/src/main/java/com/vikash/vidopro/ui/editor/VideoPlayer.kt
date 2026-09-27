package com.vikash.vidopro.ui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

    AndroidView(
        factory = { ctx ->
            StyledPlayerView(ctx).apply {
                player = exoPlayer
                useController = false
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
    )
}
