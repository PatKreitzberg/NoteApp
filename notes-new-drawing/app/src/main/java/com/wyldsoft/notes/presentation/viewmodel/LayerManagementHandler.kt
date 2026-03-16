package com.wyldsoft.notes.presentation.viewmodel

import com.wyldsoft.notes.actions.ActionManager
import com.wyldsoft.notes.actions.MoveLayerAction
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages layer state: active layer, visibility, solo, naming, and stroke movement.
 * Extracted from EditorViewModel to keep it under the 300-line guideline.
 */
class LayerManagementHandler(
    private val noteRepository: NoteRepository,
    private val scope: CoroutineScope,
    private val getActionManager: () -> ActionManager,
    private val getShapesManager: () -> ShapesManager?,
    private val getBitmapManager: () -> BitmapManager?,
    private val onScreenRefreshNeeded: () -> Unit
) {
    private val _activeLayer = MutableStateFlow(1)
    val activeLayer: StateFlow<Int> = _activeLayer.asStateFlow()

    private val _createdLayers = MutableStateFlow<Set<Int>>(setOf(1))

    private val _hiddenLayers = MutableStateFlow<Set<Int>>(emptySet())
    val hiddenLayers: StateFlow<Set<Int>> = _hiddenLayers.asStateFlow()

    private val _soloLayer = MutableStateFlow<Int?>(null)
    val soloLayer: StateFlow<Int?> = _soloLayer.asStateFlow()

    private val _layerNames = MutableStateFlow<Map<Int, String>>(emptyMap())
    val layerNames: StateFlow<Map<Int, String>> = _layerNames.asStateFlow()

    fun setActiveLayer(layer: Int) { _activeLayer.value = layer }

    fun toggleLayerVisibility(layer: Int) {
        val current = _hiddenLayers.value.toMutableSet()
        if (layer in current) current.remove(layer) else current.add(layer)
        _hiddenLayers.value = current
        refreshAfterLayerChange()
    }

    fun setSoloLayer(layer: Int?) {
        _soloLayer.value = if (_soloLayer.value == layer) null else layer
        refreshAfterLayerChange()
    }

    fun addLayer(): Int {
        val allLayers = getExistingLayers()
        val newLayer = (allLayers.maxOrNull() ?: 0) + 1
        _createdLayers.value = _createdLayers.value + newLayer
        _activeLayer.value = newLayer
        return newLayer
    }

    fun getExistingLayers(): List<Int> {
        val shapeLayers = getShapesManager()?.shapes()?.map { it.layer }?.toSet() ?: emptySet()
        val all = (shapeLayers + _createdLayers.value).sorted()
        return if (all.isEmpty()) listOf(1) else all
    }

    fun isLayerVisible(layer: Int): Boolean {
        val solo = _soloLayer.value
        if (solo != null) return layer == solo
        return layer !in _hiddenLayers.value
    }

    fun getVisibleShapes(): MutableList<BaseShape>? {
        val shapes = getShapesManager()?.shapes() ?: return null
        return shapes.filter { isLayerVisible(it.layer) }.toMutableList()
    }

    fun renameLayer(layer: Int, name: String) {
        val current = _layerNames.value.toMutableMap()
        if (name.isBlank()) current.remove(layer) else current[layer] = name.trim()
        _layerNames.value = current
    }

    fun getLayerDisplayName(layer: Int): String {
        return _layerNames.value[layer] ?: "Layer $layer"
    }

    fun moveLayerStrokes(fromLayer: Int, toLayer: Int) {
        scope.launch {
            val sm = getShapesManager() ?: return@launch
            val bm = getBitmapManager() ?: return@launch
            val shapeIds = sm.shapes().filter { it.layer == fromLayer }.map { it.id }
            if (shapeIds.isEmpty()) return@launch
            val action = MoveLayerAction(shapeIds, fromLayer, toLayer, noteRepository, sm, bm)
            action.redo()
            getActionManager().recordAction(action)
            onScreenRefreshNeeded()
        }
    }

    private fun refreshAfterLayerChange() {
        val bm = getBitmapManager() ?: return
        val visibleShapes = getVisibleShapes() ?: return
        bm.recreateBitmapFromShapes(visibleShapes)
        onScreenRefreshNeeded()
    }
}
