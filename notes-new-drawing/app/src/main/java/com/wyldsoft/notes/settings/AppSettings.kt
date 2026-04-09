package com.wyldsoft.notes.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.wyldsoft.notes.gestures.GestureAction
import com.wyldsoft.notes.gestures.GestureBindings

class AppSettings(context: Context) {
    companion object {
        private const val TAG = "AppSettings"
        private const val PREFS_NAME = "app_settings"
        private const val KEY_DEFAULT_PAGINATION = "default_pagination_enabled"
        private const val KEY_GESTURE_PREFIX = "gesture_"
        private const val KEY_SCRIBBLE_TO_ERASE = "scribble_to_erase"
        private const val KEY_CIRCLE_TO_SELECT = "circle_to_select"
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
}
