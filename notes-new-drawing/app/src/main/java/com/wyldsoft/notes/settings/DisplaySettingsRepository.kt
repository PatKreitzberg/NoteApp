package com.wyldsoft.notes.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DisplaySettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)

    private val _maxRefreshRate = MutableStateFlow(prefs.getInt(KEY_MAX_REFRESH_RATE, DEFAULT_MAX_REFRESH_RATE))
    val maxRefreshRate: StateFlow<Int> = _maxRefreshRate

    private val _smoothMotion = MutableStateFlow(prefs.getBoolean(KEY_SMOOTH_MOTION, DEFAULT_SMOOTH_MOTION))
    val smoothMotion: StateFlow<Boolean> = _smoothMotion

    private val _scrollBarVisible = MutableStateFlow(prefs.getBoolean(KEY_SCROLL_BAR_VISIBLE, DEFAULT_SCROLL_BAR_VISIBLE))
    val scrollBarVisible: StateFlow<Boolean> = _scrollBarVisible

    private val _scribbleToEraseEnabled = MutableStateFlow(prefs.getBoolean(KEY_SCRIBBLE_TO_ERASE, DEFAULT_SCRIBBLE_TO_ERASE))
    val scribbleToEraseEnabled: StateFlow<Boolean> = _scribbleToEraseEnabled

    private val _circleToSelectEnabled = MutableStateFlow(prefs.getBoolean(KEY_CIRCLE_TO_SELECT, DEFAULT_CIRCLE_TO_SELECT))
    val circleToSelectEnabled: StateFlow<Boolean> = _circleToSelectEnabled

    /** Minimum interval between screen refreshes in milliseconds */
    val minRefreshIntervalMs: Long
        get() = if (_maxRefreshRate.value > 0) 1000L / _maxRefreshRate.value else 0L

    fun setMaxRefreshRate(rate: Int) {
        prefs.edit().putInt(KEY_MAX_REFRESH_RATE, rate).apply()
        _maxRefreshRate.value = rate
    }

    fun setSmoothMotion(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMOOTH_MOTION, enabled).apply()
        _smoothMotion.value = enabled
    }

    fun setScrollBarVisible(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCROLL_BAR_VISIBLE, enabled).apply()
        _scrollBarVisible.value = enabled
    }

    fun setScribbleToEraseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCRIBBLE_TO_ERASE, enabled).apply()
        _scribbleToEraseEnabled.value = enabled
    }

    fun setCircleToSelectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CIRCLE_TO_SELECT, enabled).apply()
        _circleToSelectEnabled.value = enabled
    }

    companion object {
        private const val KEY_MAX_REFRESH_RATE = "max_refresh_rate"
        private const val KEY_SMOOTH_MOTION = "smooth_motion"
        private const val KEY_SCROLL_BAR_VISIBLE = "scroll_bar_visible"
        private const val KEY_SCRIBBLE_TO_ERASE = "scribble_to_erase"
        private const val KEY_CIRCLE_TO_SELECT = "circle_to_select"
        const val DEFAULT_MAX_REFRESH_RATE = 5
        const val DEFAULT_SMOOTH_MOTION = true
        const val DEFAULT_SCROLL_BAR_VISIBLE = true
        const val DEFAULT_SCRIBBLE_TO_ERASE = true
        const val DEFAULT_CIRCLE_TO_SELECT = true
    }
}
