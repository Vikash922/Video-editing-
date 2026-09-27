package com.vikash.vidopro.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vikash.vidopro.viewmodels.TimelineViewModel

@Composable
fun VideoEditorScreen(
    videoPath: String,
    viewModel: TimelineViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val totalDurationMs by viewModel.totalDurationMs.collectAsState()
    val currentTimeMs by viewModel.currentTimeMs.collectAsState()
    val isScrubbing by viewModel.isScrubbing.collectAsState()
    val markers by viewModel.markers.collectAsState()
    val thumbnails by viewModel.thumbnails.collectAsState()

    LaunchedEffect(videoPath, totalDurationMs) {
        if (videoPath.isNotEmpty() && totalDurationMs > 0L) {
            viewModel.generateThumbnails(videoPath, count = 16)
        }
    }

    Scaffold(
        containerColor = Color.Black,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Video Player Area
            VideoPlayer(
                videoUri = videoPath,
                currentTimeMs = currentTimeMs,
                isScrubbing = isScrubbing,
                onDurationKnown = { duration -> viewModel.setDuration(duration) },
                onTimeUpdate = { progressTime -> viewModel.updatePlaybackTime(progressTime) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Timeline Scrubbing & Marker Controls
            VideoTimeline(
                totalDurationMs = totalDurationMs,
                currentTimeMs = currentTimeMs,
                markers = markers,
                thumbnails = thumbnails,
                isScrubbing = isScrubbing,
                onScrubStart = { viewModel.onScrubStart() },
                onScrub = { time -> viewModel.onScrub(time) },
                onScrubEnd = { viewModel.onScrubEnd() },
                onAddMarker = { viewModel.addCurrentMarker() },
                onDeleteMarker = { markerTime -> viewModel.removeMarker(markerTime) },
                onSeekToMarker = { markerTime -> viewModel.onScrub(markerTime) }
            )
        }
    }
}
