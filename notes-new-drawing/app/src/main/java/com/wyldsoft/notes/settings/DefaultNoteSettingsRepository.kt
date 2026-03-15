package com.wyldsoft.notes.settings

import android.content.Context
import android.content.SharedPreferences
import com.wyldsoft.notes.domain.models.PaperSize
import com.wyldsoft.notes.domain.models.PaperTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DefaultNoteSettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("default_note_settings", Context.MODE_PRIVATE)

    private val _isPaginationEnabled = MutableStateFlow(prefs.getBoolean(KEY_PAGINATION, true))
    val isPaginationEnabled: StateFlow<Boolean> = _isPaginationEnabled

    private val _paperSize = MutableStateFlow(
        PaperSize.entries.find { it.name == prefs.getString(KEY_PAPER_SIZE, "LETTER") } ?: PaperSize.LETTER
    )
    val paperSize: StateFlow<PaperSize> = _paperSize

    private val _paperTemplate = MutableStateFlow(
        PaperTemplate.entries.find { it.name == prefs.getString(KEY_PAPER_TEMPLATE, "BLANK") } ?: PaperTemplate.BLANK
    )
    val paperTemplate: StateFlow<PaperTemplate> = _paperTemplate

    fun setPaginationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PAGINATION, enabled).apply()
        _isPaginationEnabled.value = enabled
    }

    fun setPaperSize(size: PaperSize) {
        prefs.edit().putString(KEY_PAPER_SIZE, size.name).apply()
        _paperSize.value = size
    }

    fun setPaperTemplate(template: PaperTemplate) {
        prefs.edit().putString(KEY_PAPER_TEMPLATE, template.name).apply()
        _paperTemplate.value = template
    }

    companion object {
        private const val KEY_PAGINATION = "pagination_enabled"
        private const val KEY_PAPER_SIZE = "paper_size"
        private const val KEY_PAPER_TEMPLATE = "paper_template"
    }
}
