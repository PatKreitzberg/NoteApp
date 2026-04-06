package com.wyldsoft.notes.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class AppSettings(context: Context) {
    companion object {
        private const val TAG = "AppSettings"
        private const val PREFS_NAME = "app_settings"
        private const val KEY_DEFAULT_PAGINATION = "default_pagination_enabled"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var defaultPaginationEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEFAULT_PAGINATION, true)
        set(value) {
            Log.d(TAG, "setDefaultPaginationEnabled value=$value")
            prefs.edit().putBoolean(KEY_DEFAULT_PAGINATION, value).apply()
        }
}
