package com.vikash.vidopro.viewmodels

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vikash.vidopro.editor.media.NativeThumbnailExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

data class TimelineClip(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Clip",
    val startMs: Long = 0L,
    val durationMs: Long = 0L,
    val colorHex: Long = 0xFF6C5CE7
)

class TimelineViewModel : ViewModel() {

    private val thumbnailExtractor = NativeThumbnailExtractor()

    private val _totalDurationMs = MutableStateFlow(0L)
    val totalDurationMs: StateFlow<Long> = _totalDurationMs.asStateFlow()

    private val _currentTimeMs = MutableStateFlow(0L)
    val currentTimeMs: StateFlow<Long> = _currentTimeMs.asStateFlow()

    private val _isScrubbing = MutableStateFlow(false)
    val isScrubbing: StateFlow<Boolean> = _isScrubbing.asStateFlow()

    // Markers state: list of timestamps in milliseconds
    private val _markers = MutableStateFlow<List<Long>>(emptyList())
    val markers: StateFlow<List<Long>> = _markers.asStateFlow()

    // Timeline Thumbnails
    private val _thumbnails = MutableStateFlow<List<Bitmap>>(emptyList())
    val thumbnails: StateFlow<List<Bitmap>> = _thumbnails.asStateFlow()

    // Clips state with unique IDs for rock-solid Compose keying and reordering
    private val _clips = MutableStateFlow<List<TimelineClip>>(emptyList())
    val clips: StateFlow<List<TimelineClip>> = _clips.asStateFlow()

    fun setDuration(durationMs: Long) {
        _totalDurationMs.value = durationMs
        if (_clips.value.isEmpty() && durationMs > 0L) {
            _clips.value = listOf(
                TimelineClip(
                    id = UUID.randomUUID().toString(),
                    name = "Clip 1",
                    startMs = 0L,
                    durationMs = durationMs
                )
            )
        }
    }

    fun onScrubStart() {
        _isScrubbing.value = true
    }

    fun onScrub(timeMs: Long) {
        _currentTimeMs.value = timeMs.coerceIn(0L, _totalDurationMs.value)
    }

    fun onScrubEnd() {
        _isScrubbing.value = false
    }

    fun updatePlaybackTime(timeMs: Long) {
        if (!_isScrubbing.value) {
            _currentTimeMs.value = timeMs
        }
    }

    // Split clip at current playhead time
    fun splitAtCurrentTime(): Boolean {
        val splitTime = _currentTimeMs.value
        val list = _clips.value.toMutableList()
        val totalDur = _totalDurationMs.value

        if (list.isEmpty()) {
            if (totalDur > 0L) {
                list.add(
                    TimelineClip(
                        id = UUID.randomUUID().toString(),
                        name = "Clip 1",
                        startMs = 0L,
                        durationMs = totalDur
                    )
                )
            } else {
                return false
            }
        }

        var accumulated = 0L
        var targetIndex = -1
        var offsetInsideClip = 0L

        for (i in list.indices) {
            val clip = list[i]
            val clipEnd = accumulated + clip.durationMs
            // Check if playhead is strictly inside the clip (at least 50ms from boundaries)
            if (splitTime > (accumulated + 50L) && splitTime < (clipEnd - 50L)) {
                targetIndex = i
                offsetInsideClip = splitTime - accumulated
                break
            }
            accumulated = clipEnd
        }

        if (targetIndex != -1) {
            val oldClip = list[targetIndex]
            val duration1 = offsetInsideClip
            val duration2 = oldClip.durationMs - offsetInsideClip

            // Hamesha Unique IDs assign karein (UUID.randomUUID())
            val clip1 = TimelineClip(
                id = UUID.randomUUID().toString(),
                name = "${oldClip.name} A",
                startMs = oldClip.startMs,
                durationMs = duration1,
                colorHex = oldClip.colorHex
            )
            val clip2 = TimelineClip(
                id = UUID.randomUUID().toString(),
                name = "${oldClip.name} B",
                startMs = oldClip.startMs + offsetInsideClip,
                durationMs = duration2,
                colorHex = 0xFF00CEC9
            )

            // Purane video object ko list se hatayein aur do naye distinct objects list mein insert karein
            list.removeAt(targetIndex)
            list.add(targetIndex, clip1)
            list.add(targetIndex + 1, clip2)

            _clips.value = list
            return true
        }
        return false
    }

    // Reorder clips on drag and drop
    fun reorderClips(fromIndex: Int, toIndex: Int) {
        val list = _clips.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices && fromIndex != toIndex) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _clips.value = list
        }
    }

    // Marker Logic
    fun addCurrentMarker() {
        val current = _currentTimeMs.value
        val list = _markers.value.toMutableList()
        if (!list.contains(current)) {
            list.add(current)
            list.sort()
            _markers.value = list
        }
    }

    fun removeMarker(timestampMs: Long) {
        val list = _markers.value.toMutableList()
        // Tolerance threshold +/- 100ms
        val markerToRemove = list.firstOrNull { abs(it - timestampMs) <= 100L } ?: timestampMs
        list.remove(markerToRemove)
        _markers.value = list
    }

    fun getNextMarker(): Long? {
        val current = _currentTimeMs.value
        return _markers.value.firstOrNull { it > current + 50L }
    }

    fun getPrevMarker(): Long? {
        val current = _currentTimeMs.value
        return _markers.value.reversed().firstOrNull { it < current - 50L }
    }

    fun generateThumbnails(videoPath: String, count: Int = 16) {
        viewModelScope.launch {
            val duration = _totalDurationMs.value
            if (duration <= 0L) return@launch
            val interval = (duration / count).coerceAtLeast(1L)
            val timestamps = (0 until count).map { (it * interval).coerceAtMost(duration) }
            val extracted = thumbnailExtractor.extractThumbnails(videoPath, timestamps)
            _thumbnails.value = extracted
        }
    }
}
