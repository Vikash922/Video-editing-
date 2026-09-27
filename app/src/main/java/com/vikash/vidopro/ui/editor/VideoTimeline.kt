package com.vikash.vidopro.ui.editor

import android.graphics.Bitmap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@Composable
fun VideoTimeline(
    totalDurationMs: Long,
    currentTimeMs: Long,
    markers: List<Long>,
    thumbnails: List<Bitmap>,
    isScrubbing: Boolean,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    onAddMarker: () -> Unit,
    onDeleteMarker: (Long) -> Unit,
    onSeekToMarker: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Timeline Density: 1 second = 80dp
    val dpPerSecond = 80.dp
    val pxPerSecond = with(density) { dpPerSecond.toPx() }
    val totalTimelinePx = ((totalDurationMs / 1000f) * pxPerSecond).coerceAtLeast(1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141416))
            .padding(vertical = 12.dp)
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val halfWidthPx = screenWidthPx / 2f
        val halfWidthDp = with(density) { halfWidthPx.toDp() }

        fun timeToScrollPx(timeMs: Long): Int {
            val progress = (timeMs.toFloat() / totalDurationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
            return (progress * totalTimelinePx).toInt()
        }

        fun scrollPxToTime(scrollX: Int): Long {
            val progress = (scrollX / totalTimelinePx).coerceIn(0f, 1f)
            return (progress * totalDurationMs).toLong()
        }

        // Sync scroll state with playback when not scrubbing
        LaunchedEffect(currentTimeMs, isScrubbing) {
            if (!isScrubbing && !scrollState.isScrollInProgress) {
                scrollState.scrollTo(timeToScrollPx(currentTimeMs))
            }
        }

        // Detect scrubbing from scrollState
        LaunchedEffect(scrollState.isScrollInProgress) {
            if (scrollState.isScrollInProgress) {
                onScrubStart()
            } else {
                onScrubEnd()
            }
        }

        LaunchedEffect(scrollState.value) {
            if (scrollState.isScrollInProgress) {
                val newTime = scrollPxToTime(scrollState.value)
                onScrub(newTime)
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Controls: Time display and Marker Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatTimelineTime(currentTimeMs)} / ${formatTimelineTime(totalDurationMs)}",
                    color = Color.White,
                    fontSize = 13.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Previous Marker
                    IconButton(onClick = {
                        val prev = markers.reversed().firstOrNull { it < currentTimeMs - 50L }
                        if (prev != null) {
                            coroutineScope.launch {
                                scrollState.animateScrollTo(timeToScrollPx(prev), tween(250))
                                onSeekToMarker(prev)
                            }
                        }
                    }) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = "Previous Marker",
                            tint = Color.White
                        )
                    }

                    // Add Marker Button
                    FilledTonalIconButton(
                        onClick = onAddMarker,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFF6C5CE7)
                        )
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Marker",
                            tint = Color.White
                        )
                    }

                    // Next Marker
                    IconButton(onClick = {
                        val next = markers.firstOrNull { it > currentTimeMs + 50L }
                        if (next != null) {
                            coroutineScope.launch {
                                scrollState.animateScrollTo(timeToScrollPx(next), tween(250))
                                onSeekToMarker(next)
                            }
                        }
                    }) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Next Marker",
                            tint = Color.White
                        )
                    }
                }
            }

            // Timeline Track Area with fixed Center Playhead
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // 1. Scrollable Track Container
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start Half-screen padding (Aligns 00:00 to center playhead)
                    Spacer(modifier = Modifier.width(halfWidthDp))

                    // Track with Thumbnails & Marker Canvas
                    Box(
                        modifier = Modifier
                            .width(with(density) { totalTimelinePx.toDp() })
                            .height(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF202024))
                            .border(1.dp, Color(0xFF33333A), RoundedCornerShape(8.dp))
                            .pointerInput(markers) {
                                detectTapGestures { offset ->
                                    val hitTolerancePx = with(density) { 20.dp.toPx() }
                                    val matchedMarker = markers.firstOrNull { markerTime ->
                                        val markerPx = (markerTime.toFloat() / totalDurationMs.coerceAtLeast(1L)) * size.width
                                        abs(markerPx - offset.x) <= hitTolerancePx
                                    }
                                    matchedMarker?.let { onDeleteMarker(it) }
                                }
                            }
                    ) {
                        // Thumbnails rendering
                        Row(modifier = Modifier.fillMaxSize()) {
                            thumbnails.forEach { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }

                        // 2. Draw Marker Indicators on Canvas
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            markers.forEach { markerTimestamp ->
                                val markerX = (markerTimestamp.toFloat() / totalDurationMs.coerceAtLeast(1L)) * size.width

                                // Marker line
                                drawLine(
                                    color = Color(0xFFFFD166),
                                    start = Offset(markerX, 0f),
                                    end = Offset(markerX, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )

                                // Marker top bead
                                drawCircle(
                                    color = Color(0xFFFFD166),
                                    radius = 5.dp.toPx(),
                                    center = Offset(markerX, 6.dp.toPx())
                                )
                            }
                        }
                    }

                    // End Half-screen padding (Aligns video end to center playhead)
                    Spacer(modifier = Modifier.width(halfWidthDp))
                }

                // 3. Fixed Center Playhead
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .align(Alignment.Center)
                        .background(Color.White)
                )

                // Playhead top bead
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.TopCenter)
                        .offset(y = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

private fun formatTimelineTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (millis % 1000) / 10
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
}
