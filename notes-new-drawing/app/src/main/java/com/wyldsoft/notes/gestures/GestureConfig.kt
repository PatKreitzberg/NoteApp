package com.wyldsoft.notes.gestures

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class GestureType(val displayName: String) {
    ONE_FINGER_SINGLE_TAP("1-finger single tap"),
    ONE_FINGER_DOUBLE_TAP("1-finger double tap"),
    TWO_FINGER_SINGLE_TAP("2-finger single tap"),
    TWO_FINGER_DOUBLE_TAP("2-finger double tap"),
    THREE_FINGER_SINGLE_TAP("3-finger single tap"),
    THREE_FINGER_DOUBLE_TAP("3-finger double tap"),
    FOUR_FINGER_SINGLE_TAP("4-finger single tap"),
    FLICK_UP("Flick up"),
    FLICK_DOWN("Flick down"),
    FLICK_LEFT("Flick left"),
    FLICK_RIGHT("Flick right"),
    TWO_FINGER_FLICK_LEFT("2-finger flick left"),
    TWO_FINGER_FLICK_RIGHT("2-finger flick right"),
    PAN("Pan/scroll"),
    PINCH("Pinch/zoom");

    companion object {
        fun fromString(value: String): GestureType? {
            return entries.find { it.name == value }
        }
    }
}

enum class GestureAction(val displayName: String) {
    NONE("None"),
    SCROLL("Scroll viewport"),
    ZOOM("Zoom viewport"),
    RESET_ZOOM_AND_CENTER("Reset zoom & center"),
    TOGGLE_SELECTION_MODE("Toggle selection mode"),
    TOGGLE_TEXT_MODE("Toggle text mode"),
    SWITCH_TAB("Switch tab (Draw→Edit→Text)"),
    DRAW_GEOMETRIC_SHAPE("Draw geometric shape"),
    COPY_SELECTION("Copy selection"),
    PASTE_SELECTION("Paste selection"),
    UNDO("Undo"),
    REDO("Redo"),
    NEXT_NOTE("Next note"),
    PREVIOUS_NOTE("Previous note");

    companion object {
        fun fromString(value: String): GestureAction? {
            return entries.find { it.name == value }
        }
    }
}

data class GestureMapping(val gesture: GestureType, val action: GestureAction)

class GestureSettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gesture_settings", Context.MODE_PRIVATE)

    private val _mappings = MutableStateFlow(loadMappings())
    val mappings: StateFlow<List<GestureMapping>> = _mappings

    companion object {
        private const val KEY_MAPPING_COUNT = "mapping_count"
        private const val KEY_GESTURE_PREFIX = "gesture_"
        private const val KEY_ACTION_PREFIX = "action_"

        val DEFAULT_MAPPINGS = listOf(
            GestureMapping(GestureType.PAN, GestureAction.SCROLL),
            GestureMapping(GestureType.PINCH, GestureAction.ZOOM),
            GestureMapping(GestureType.THREE_FINGER_DOUBLE_TAP, GestureAction.RESET_ZOOM_AND_CENTER),
            GestureMapping(GestureType.TWO_FINGER_FLICK_LEFT, GestureAction.NEXT_NOTE),
            GestureMapping(GestureType.TWO_FINGER_FLICK_RIGHT, GestureAction.PREVIOUS_NOTE)
        )
    }

    private fun loadMappings(): List<GestureMapping> {
        val count = prefs.getInt(KEY_MAPPING_COUNT, -1)
        if (count == -1) return DEFAULT_MAPPINGS

        val mappings = mutableListOf<GestureMapping>()
        for (i in 0 until count) {
            val gestureName = prefs.getString("$KEY_GESTURE_PREFIX$i", null) ?: continue
            val actionName = prefs.getString("$KEY_ACTION_PREFIX$i", null) ?: continue
            val gesture = GestureType.fromString(gestureName) ?: continue
            val action = GestureAction.fromString(actionName) ?: continue
            mappings.add(GestureMapping(gesture, action))
        }
        return mappings
    }

    fun saveMappings(mappings: List<GestureMapping>) {
        prefs.edit().apply {
            clear()
            putInt(KEY_MAPPING_COUNT, mappings.size)
            mappings.forEachIndexed { index, mapping ->
                putString("$KEY_GESTURE_PREFIX$index", mapping.gesture.name)
                putString("$KEY_ACTION_PREFIX$index", mapping.action.name)
            }
            apply()
        }
        _mappings.value = mappings
    }

    fun getActionForGesture(gesture: GestureType): GestureAction? {
        return _mappings.value.find { it.gesture == gesture }?.action
    }
}
