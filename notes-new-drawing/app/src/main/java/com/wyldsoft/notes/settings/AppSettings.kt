package com.wyldsoft.notes.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.wyldsoft.notes.gestures.GestureAction
import com.wyldsoft.notes.gestures.GestureBindings
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType

class AppSettings(context: Context) {
    companion object {
        private const val TAG = "AppSettings"
        private const val PREFS_NAME = "app_settings"
        private const val KEY_DEFAULT_PAGINATION = "default_pagination_enabled"
        private const val KEY_GESTURE_PREFIX = "gesture_"
        private const val KEY_SCRIBBLE_TO_ERASE = "scribble_to_erase"
        private const val KEY_CIRCLE_TO_SELECT = "circle_to_select"
        private const val KEY_PEN_ACTIVE_SLOT = "pen_active_slot"
        private const val KEY_PEN_SLOT_PREFIX = "pen_slot_"
        val DEFAULT_PEN_PROFILES = listOf(
            PenProfile(strokeWidth = 5f, penType = PenType.BALLPEN, strokeColor = Color(0xFF000000), strokeAlpha = 1.0f, profileId = 1),
            PenProfile(strokeWidth = 5f, penType = PenType.BALLPEN, strokeColor = Color(0xFF0033CC), strokeAlpha = 1.0f, profileId = 2),
            PenProfile(strokeWidth = 5f, penType = PenType.BALLPEN, strokeColor = Color(0xFFFF0000), strokeAlpha = 1.0f, profileId = 3),
            PenProfile(strokeWidth = 5f, penType = PenType.BALLPEN, strokeColor = Color(0xFF00AA00), strokeAlpha = 1.0f, profileId = 4),
            PenProfile(strokeWidth = 5f, penType = PenType.BALLPEN, strokeColor = Color(0xFFCC00CC), strokeAlpha = 1.0f, profileId = 5),
        )
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var defaultPaginationEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEFAULT_PAGINATION, true)
        set(value) {
            Log.d(TAG, "setDefaultPaginationEnabled value=$value")
            prefs.edit().putBoolean(KEY_DEFAULT_PAGINATION, value).apply()
        }

    var scribbleToEraseEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCRIBBLE_TO_ERASE, true)
        set(value) {
            Log.d(TAG, "setScribbleToEraseEnabled value=$value")
            prefs.edit().putBoolean(KEY_SCRIBBLE_TO_ERASE, value).apply()
        }

    var circleToSelectEnabled: Boolean
        get() = prefs.getBoolean(KEY_CIRCLE_TO_SELECT, true)
        set(value) {
            Log.d(TAG, "setCircleToSelectEnabled value=$value")
            prefs.edit().putBoolean(KEY_CIRCLE_TO_SELECT, value).apply()
        }

    fun getGestureAction(gestureKey: String): GestureAction {
        val name = prefs.getString("$KEY_GESTURE_PREFIX$gestureKey", GestureAction.NONE.name)
        return GestureAction.entries.find { it.name == name } ?: GestureAction.NONE
    }

    fun getAllGestureMappings(): Map<String, GestureAction> {
        return GestureBindings.ALL_GESTURE_KEYS.associate { (key, _) ->
            key to getGestureAction(key)
        }
    }

    fun saveGestureMappings(mappings: Map<String, GestureAction>) {
        Log.d(TAG, "saveGestureMappings count=${mappings.size}")
        val editor = prefs.edit()
        mappings.forEach { (key, action) ->
            editor.putString("$KEY_GESTURE_PREFIX$key", action.name)
        }
        editor.apply()
    }

    fun savePenProfiles(profiles: List<PenProfile>, activePenSlot: Int) {
        Log.d(TAG, "savePenProfiles activeSlot=$activePenSlot count=${profiles.size}")
        val editor = prefs.edit()
        editor.putInt(KEY_PEN_ACTIVE_SLOT, activePenSlot)
        profiles.forEachIndexed { i, profile ->
            val prefix = "$KEY_PEN_SLOT_PREFIX${i + 1}"
            editor.putFloat("${prefix}_width", profile.strokeWidth)
            editor.putString("${prefix}_type", profile.penType.name)
            editor.putInt("${prefix}_color", profile.strokeColor.toArgb())
            editor.putFloat("${prefix}_alpha", profile.strokeAlpha)
        }
        editor.apply()
    }

    fun loadPenProfiles(): Pair<List<PenProfile>, Int> {
        Log.d(TAG, "loadPenProfiles")
        val activePenSlot = prefs.getInt(KEY_PEN_ACTIVE_SLOT, 1).coerceIn(1, DEFAULT_PEN_PROFILES.size)
        val profiles = DEFAULT_PEN_PROFILES.mapIndexed { i, defaultProfile ->
            val prefix = "$KEY_PEN_SLOT_PREFIX${i + 1}"
            if (!prefs.contains("${prefix}_type")) {
                defaultProfile
            } else {
                val typeName = prefs.getString("${prefix}_type", defaultProfile.penType.name)!!
                val penType = PenType.entries.find { it.name == typeName } ?: defaultProfile.penType
                val width = prefs.getFloat("${prefix}_width", defaultProfile.strokeWidth)
                val colorArgb = prefs.getInt("${prefix}_color", defaultProfile.strokeColor.toArgb())
                val alpha = prefs.getFloat("${prefix}_alpha", 1.0f)
                PenProfile(
                    strokeWidth = width,
                    penType = penType,
                    strokeColor = Color(colorArgb),
                    strokeAlpha = alpha,
                    profileId = i + 1
                )
            }
        }
        return Pair(profiles, activePenSlot)
    }
}
