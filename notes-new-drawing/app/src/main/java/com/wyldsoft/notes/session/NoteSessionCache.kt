package com.wyldsoft.notes.session

import android.util.Log
import com.wyldsoft.notes.data.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LRU cache for NoteSession objects. Avoids reloading shapes from DB
 * and re-converting them on every note switch.
 */
class NoteSessionCache(private val maxSize: Int = 5) {
    private val TAG = "NoteSessionCache"

    private val cache = object : LinkedHashMap<String, NoteSession>(maxSize + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NoteSession>?): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    fun get(noteId: String): NoteSession? = cache[noteId]

    @Synchronized
    fun put(noteId: String, session: NoteSession) {
        cache[noteId] = session
    }

    @Synchronized
    fun contains(noteId: String): Boolean = cache.containsKey(noteId)

    @Synchronized
    fun invalidate(noteId: String) {
        cache.remove(noteId)
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    suspend fun preload(noteId: String, noteRepository: NoteRepository) {
        if (contains(noteId)) return
        withContext(Dispatchers.IO) {
            try {
                val note = noteRepository.getNote(noteId)
                val session = NoteSession.createFromNote(note)
                put(noteId, session)
                Log.d(TAG, "Pre-cached session for note $noteId (${note.shapes.size} shapes)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pre-cache note $noteId", e)
            }
        }
    }
}
