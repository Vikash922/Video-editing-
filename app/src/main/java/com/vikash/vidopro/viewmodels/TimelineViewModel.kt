package com.vikash.vidopro.viewmodels

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vikash.vidopro.editor.media.NativeThumbnailExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

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

    fun setDuration(durationMs: Long) {
        _totalDurationMs.value = durationMs
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
