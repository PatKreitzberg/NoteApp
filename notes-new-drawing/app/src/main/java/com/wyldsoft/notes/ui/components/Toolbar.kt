package com.wyldsoft.notes.ui.components

import android.graphics.Rect
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.presentation.viewmodel.EditorMode
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel

enum class ToolbarTab { DRAW, EDIT, TEXT }

@Composable
fun Toolbar(
    viewModel: EditorViewModel,
    currentPenProfile: PenProfile,
    onSettingsClick: () -> Unit = {},
    onCollapsedChanged: (Boolean) -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    onNavigateForward: (() -> Unit)? = null,
    canGoBack: Boolean = false,
    canGoForward: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var selectedProfileIndex by remember { mutableStateOf(0) }
    var strokePanelRect by remember { mutableStateOf<Rect?>(null) }
    var isCollapsed by remember { mutableStateOf(false) }
    var toolbarHeightPx by remember { mutableStateOf(0) }
    var profiles by remember { mutableStateOf(PenProfile.createDefaultProfiles()) }

    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(ToolbarTab.DRAW) }

    // Sync tab with current mode
    LaunchedEffect(uiState.mode) {
        selectedTab = when (uiState.mode) {
            is EditorMode.Select -> ToolbarTab.EDIT
            is EditorMode.Text -> ToolbarTab.TEXT
            is EditorMode.Draw -> ToolbarTab.DRAW
        }
    }

    LaunchedEffect(Unit) { viewModel.updatePenProfile(profiles[selectedProfileIndex]) }

    fun forceUIRefresh() { viewModel.forceRefresh() }

    fun addStrokeOptionPanelRect() {
        strokePanelRect?.let { rect ->
            val currentRects = viewModel.excludeRects.value.toMutableList()
            currentRects.add(rect)
            viewModel.updateExclusionZones(currentRects)
            forceUIRefresh()
        }
    }

    fun removeStrokeOptionPanelRect() {
        strokePanelRect?.let { rect ->
            val currentRects = viewModel.excludeRects.value.toMutableList()
            currentRects.remove(rect)
            viewModel.updateExclusionZones(currentRects)
        }
    }

    fun openStrokeOptionsPanel() { viewModel.toggleStrokeOptions() }

    fun closeStrokeOptionsPanel() {
        viewModel.closeStrokeOptions()
        removeStrokeOptionPanelRect()
        forceUIRefresh()
    }

    fun handleProfileClick(profileIndex: Int) {
        val currentMode = viewModel.uiState.value.mode
        if (currentMode is EditorMode.Select && viewModel.selectionManager.hasSelection) {
            val profile = profiles[profileIndex]
            viewModel.applyPenProfileToSelection(profile)
            selectedProfileIndex = profileIndex
            viewModel.updatePenProfile(profile)
            return
        }
        if (currentMode is EditorMode.Select) viewModel.cancelSelection()
        else if (!viewModel.uiState.value.isPenMode) viewModel.switchMode(EditorMode.Draw())

        val isStrokeOpen = viewModel.uiState.value.isStrokeOptionsOpen
        if (selectedProfileIndex == profileIndex && isStrokeOpen) {
            closeStrokeOptionsPanel()
        } else if (selectedProfileIndex == profileIndex && !isStrokeOpen) {
            openStrokeOptionsPanel()
        } else {
            selectedProfileIndex = profileIndex
            viewModel.updatePenProfile(profiles[profileIndex])
        }
    }

    fun updateProfile(newProfile: PenProfile) {
        val updatedProfiles = profiles.toMutableList()
        updatedProfiles[selectedProfileIndex] = newProfile
        profiles = updatedProfiles
        viewModel.updatePenProfile(newProfile)
    }

    Column {
        if (isCollapsed) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { isCollapsed = false; onCollapsedChanged(false) }) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Show Toolbar", tint = Color.Black)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { toolbarHeightPx = it.boundsInWindow().bottom.toInt() }
                    .background(Color.White)
                    .border(1.dp, Color.Gray)
            ) {
                // Tab row + always-visible action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TabButton(
                        label = "Draw",
                        selected = selectedTab == ToolbarTab.DRAW,
                        onClick = { selectedTab = ToolbarTab.DRAW }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    TabButton(
                        label = "Edit",
                        selected = selectedTab == ToolbarTab.EDIT,
                        onClick = { selectedTab = ToolbarTab.EDIT }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    TabButton(
                        label = "Text",
                        selected = selectedTab == ToolbarTab.TEXT,
                        onClick = { selectedTab = ToolbarTab.TEXT }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    ToolbarActionButtons(
                        viewModel = viewModel,
                        onSettingsClick = onSettingsClick,
                        onNavigateBack = onNavigateBack,
                        onNavigateForward = onNavigateForward,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        onCollapse = {
                            closeStrokeOptionsPanel()
                            isCollapsed = true
                            onCollapsedChanged(true)
                        }
                    )
                }

                // Tab content row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (selectedTab) {
                        ToolbarTab.DRAW -> {
                            profiles.forEachIndexed { index, profile ->
                                ProfileButton(
                                    profile = profile,
                                    isSelected = selectedProfileIndex == index,
                                    isActive = currentPenProfile.profileId == profile.profileId,
                                    onClick = { handleProfileClick(index) }
                                )
                            }
                            ToolbarToolButtons(viewModel = viewModel)
                        }
                        ToolbarTab.EDIT -> {
                            ToolbarEditButtons(viewModel = viewModel)
                        }
                        ToolbarTab.TEXT -> {
                            ToolbarTextButtons(viewModel = viewModel)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    LayerDropdown(viewModel = viewModel)
                }
            }

            if (uiState.isStrokeOptionsOpen) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, toolbarHeightPx),
                    properties = PopupProperties(focusable = false)
                ) {
                    DisposableEffect(Unit) {
                        onDispose {
                            Log.d("Toolbar", "StrokeOptionsPanel removed from composition")
                            removeStrokeOptionPanelRect()
                            forceUIRefresh()
                        }
                    }
                    StrokeOptionsPanel(
                        viewModel = viewModel,
                        currentProfile = currentPenProfile,
                        onProfileChanged = { updateProfile(it) },
                        onPanelPositioned = { rect ->
                            if (rect != strokePanelRect) {
                                strokePanelRect = rect
                                if (uiState.isStrokeOptionsOpen) {
                                    scope.launch { addStrokeOptionPanelRect() }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor = if (selected) Color.Black else Color.Transparent
    val textColor = if (selected) Color.White else Color.Gray
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = bgColor),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(label, color = textColor, fontSize = 12.sp)
    }
}

@Composable
fun ProfileButton(profile: PenProfile, isSelected: Boolean, isActive: Boolean, onClick: () -> Unit) {
    PenButton(
        penType = profile.penType,
        isSelected = isSelected,
        onClick = onClick,
        selectedColor = profile.strokeColor,
        size = 48.dp,
        iconSize = 24.dp
    )
}
