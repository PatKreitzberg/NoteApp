package com.wyldsoft.notes.pen

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.data.database.dao.PenProfileSetDao
import com.wyldsoft.notes.data.database.entities.PenProfileSetEntity
import com.wyldsoft.notes.editor.EditorState
import com.wyldsoft.notes.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class PenProfileSetRepository(
    private val dao: PenProfileSetDao,
    private val appSettings: AppSettings
) {
    companion object {
        private const val TAG = "PenProfileSetRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        Log.d(TAG, "init")
        // Push all sets into EditorState whenever the DB changes
        dao.getAllFlow()
            .onEach { sets -> EditorState.setPenProfileSets(sets) }
            .launchIn(scope)

        // Ensure at least the Default set exists on first run
        scope.launch {
            if (dao.getCount() == 0) {
                Log.d(TAG, "No sets found — seeding Default set")
                val (profiles, _) = appSettings.loadPenProfiles()
                val defaultSet = profilesToEntity(NanoIdUtils.randomNanoId(), "Default", profiles)
                dao.insert(defaultSet)
                appSettings.saveActiveSet(defaultSet.id, defaultSet.name)
            }
        }
    }

    /** Save current EditorState profiles as a new named set. Returns the created entity. */
    suspend fun saveCurrentAsNewSet(name: String): PenProfileSetEntity {
        Log.d(TAG, "saveCurrentAsNewSet name=$name")
        val profiles = EditorState.penProfiles.map { it.value }
        val entity = profilesToEntity(NanoIdUtils.randomNanoId(), name, profiles)
        dao.insert(entity)
        appSettings.saveActiveSet(entity.id, entity.name)
        EditorState.setActiveSet(entity.id, entity.name)
        return entity
    }

    /** Load a set into EditorState, replacing all 5 pen slots. Saves active set to prefs. */
    suspend fun loadSet(setId: String) {
        Log.d(TAG, "loadSet setId=$setId")
        val entity = dao.getById(setId) ?: return
        val profiles = entityToProfiles(entity)
        appSettings.savePenProfiles(profiles, EditorState.activePenSlot.value)
        appSettings.saveActiveSet(entity.id, entity.name)
        EditorState.loadPenProfileSet(profiles, entity.id, entity.name)
    }

    /** Rename a set. */
    suspend fun renameSet(setId: String, newName: String) {
        Log.d(TAG, "renameSet setId=$setId newName=$newName")
        val entity = dao.getById(setId) ?: return
        dao.update(entity.copy(name = newName, updatedAt = System.currentTimeMillis()))
        // If this is the active set, update the display name
        if (EditorState.activeSetId.value == setId) {
            appSettings.saveActiveSet(setId, newName)
            EditorState.setActiveSetName(newName)
        }
    }

    /** Delete a set. If it was active, clears the active set. */
    suspend fun deleteSet(setId: String) {
        Log.d(TAG, "deleteSet setId=$setId")
        dao.deleteById(setId)
        if (EditorState.activeSetId.value == setId) {
            appSettings.saveActiveSet(null, "")
            EditorState.clearActiveSet()
        }
    }

    /** Called on app start — restores the last active set name from prefs into EditorState. */
    fun restoreActiveSetFromPrefs() {
        Log.d(TAG, "restoreActiveSetFromPrefs")
        val (id, name) = appSettings.loadActiveSet()
        EditorState.setActiveSet(id, name)
    }

    /** Get all sets for sync. */
    suspend fun getAllModifiedAfter(since: Long): List<PenProfileSetEntity> =
        dao.getModifiedAfter(since)

    suspend fun upsertFromSync(entity: PenProfileSetEntity) {
        Log.d(TAG, "upsertFromSync id=${entity.id}")
        dao.insert(entity)
    }

    // ── Conversion helpers ──────────────────────────────────────────────────

    fun profilesToEntity(id: String, name: String, profiles: List<PenProfile>): PenProfileSetEntity {
        val p = profiles.padded()
        return PenProfileSetEntity(
            id = id,
            name = name,
            updatedAt = System.currentTimeMillis(),
            slot1Width = p[0].strokeWidth,
            slot1PenType = p[0].penType.name,
            slot1ColorArgb = p[0].strokeColor.toArgb(),
            slot1Alpha = p[0].strokeAlpha,
            slot2Width = p[1].strokeWidth,
            slot2PenType = p[1].penType.name,
            slot2ColorArgb = p[1].strokeColor.toArgb(),
            slot2Alpha = p[1].strokeAlpha,
            slot3Width = p[2].strokeWidth,
            slot3PenType = p[2].penType.name,
            slot3ColorArgb = p[2].strokeColor.toArgb(),
            slot3Alpha = p[2].strokeAlpha,
            slot4Width = p[3].strokeWidth,
            slot4PenType = p[3].penType.name,
            slot4ColorArgb = p[3].strokeColor.toArgb(),
            slot4Alpha = p[3].strokeAlpha,
            slot5Width = p[4].strokeWidth,
            slot5PenType = p[4].penType.name,
            slot5ColorArgb = p[4].strokeColor.toArgb(),
            slot5Alpha = p[4].strokeAlpha,
        )
    }

    fun entityToProfiles(entity: PenProfileSetEntity): List<PenProfile> {
        return listOf(
            PenProfile(entity.slot1Width, PenType.valueOf(entity.slot1PenType), Color(entity.slot1ColorArgb), entity.slot1Alpha, 1),
            PenProfile(entity.slot2Width, PenType.valueOf(entity.slot2PenType), Color(entity.slot2ColorArgb), entity.slot2Alpha, 2),
            PenProfile(entity.slot3Width, PenType.valueOf(entity.slot3PenType), Color(entity.slot3ColorArgb), entity.slot3Alpha, 3),
            PenProfile(entity.slot4Width, PenType.valueOf(entity.slot4PenType), Color(entity.slot4ColorArgb), entity.slot4Alpha, 4),
            PenProfile(entity.slot5Width, PenType.valueOf(entity.slot5PenType), Color(entity.slot5ColorArgb), entity.slot5Alpha, 5),
        )
    }

    private fun List<PenProfile>.padded(): List<PenProfile> {
        val defaults = AppSettings.DEFAULT_PEN_PROFILES
        return (0 until 5).map { i -> getOrElse(i) { defaults[i] } }
    }
}
