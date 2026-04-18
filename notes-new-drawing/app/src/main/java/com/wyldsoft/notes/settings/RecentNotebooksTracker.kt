package com.wyldsoft.notes.settings

import android.content.Context
import android.util.Log
import org.json.JSONArray

class RecentNotebooksTracker(context: Context) {
    companion object {
        private const val TAG = "RecentNotebooksTracker"
        private const val PREFS_NAME = "recent_notebooks"
        private const val KEY_RECENTS = "recents"
        private const val MAX_RECENTS = 10
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordOpen(notebookId: String) {
        Log.d(TAG, "recordOpen")
        val list = loadRaw().toMutableList()
        list.removeAll { it.first == notebookId }
        list.add(0, Pair(notebookId, System.currentTimeMillis()))
        val trimmed = list.take(MAX_RECENTS)
        val json = JSONArray().apply { trimmed.forEach { (id, ts) -> put("$id|$ts") } }
        prefs.edit().putString(KEY_RECENTS, json.toString()).apply()
    }

    fun getRecentIds(): List<String> = loadRaw().map { it.first }

    private fun loadRaw(): List<Pair<String, Long>> {
        val raw = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val parts = arr.getString(i).split("|")
                Pair(parts[0], parts.getOrNull(1)?.toLongOrNull() ?: 0L)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
