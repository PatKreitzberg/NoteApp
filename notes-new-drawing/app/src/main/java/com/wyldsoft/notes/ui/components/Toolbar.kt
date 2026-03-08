package com.wyldsoft.notes.ui.components

import android.graphics.Rect
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.presentation.viewmodel.Tool

enum class ToolbarTab { DRAW, EDIT }

@Composable
fun Toolbar(
    viewModel: EditorViewModel,
    currentPenProfile: PenProfile,
    isStrokeOptionsOpen: Boolean,
    onSettingsClick: () -> Unit = {},
    onCollapsedChanged: (Boolean) -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    onNavigateForward: (() -> Unit)? = null,
    canGoBack: Boolean = false,
    canGoForward: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var selectedProfileIndex by remember { mutableStateOf(0) }
    var isStrokeSelectionOpen by remember { mutableStateOf(isStrokeOptionsOpen) }
    var strokePanelRect by remember { mutableStateOf<Rect?>(null) }
    var isCollapsed by remember { mutableStateOf(false) }
    var toolbarHeightPx by remember { mutableStateOf(0) }
    var profiles by remember { mutableStateOf(PenProfile.createDefaultProfiles()) }

    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(ToolbarTab.DRAW) }

    // Sync tab with current tool
    LaunchedEffect(uiState.selectedTool) {
        selectedTab = if (uiState.selectedTool == Tool.SELECTOR) ToolbarTab.EDIT else ToolbarTab.DRAW
    }

    LaunchedEffect(isStrokeOptionsOpen) { isStrokeSelectionOpen = isStrokeOptionsOpen }
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
        val currentTool = viewModel.uiState.value.selectedTool
        if (currentTool == Tool.SELECTOR && viewModel.selectionManager.hasSelection) {
            val profile = profiles[profileIndex]
            viewModel.applyPenProfileToSelection(profile)
            selectedProfileIndex = profileIndex
            viewModel.updatePenProfile(profile)
            return
        }
        if (currentTool == Tool.SELECTOR) viewModel.cancelSelection()
        else if (currentTool == Tool.GEOMETRY) viewModel.selectTool(Tool.PEN)

        if (selectedProfileIndex == profileIndex && isStrokeSelectionOpen) {
            closeStrokeOptionsPanel()
        } else if (selectedProfileIndex == profileIndex && !isStrokeSelectionOpen) {
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

                    Spacer(modifier = Modifier.weight(1f))

                    ToolbarActionButtons(
                        viewModel = viewModel,
                        onSettingsClick = onSettingsClick,
                        onNavigateBack = onNavigateBack,
                        onNavigateForward = onNavigateForward,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        isStrokeSelectionOpen = isStrokeSelectionOpen,
                        onCollapse = {
                            if (isStrokeSelectionOpen) closeStrokeOptionsPanel()
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
                            ToolbarToolButtons(
                                viewModel = viewModel,
                                isStrokeSelectionOpen = isStrokeSelectionOpen,
                                onCloseStrokePanel = { closeStrokeOptionsPanel() }
                            )
                        }
                        ToolbarTab.EDIT -> {
                            ToolbarEditButtons(
                                viewModel = viewModel,
                                isStrokeSelectionOpen = isStrokeSelectionOpen,
                                onCloseStrokePanel = { closeStrokeOptionsPanel() }
                            )
                        }
                    }
                }
            }

            if (isStrokeSelectionOpen) {
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
                                if (isStrokeSelectionOpen) {
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        PenButton(
            penType = profile.penType,
            isSelected = isSelected,
            onClick = onClick,
            selectedColor = profile.strokeColor,
            size = 48.dp,
            iconSize = 24.dp
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .border(width = 1.dp, color = profile.strokeColor, shape = CircleShape)
                .then(if (isActive) Modifier.background(profile.strokeColor, CircleShape) else Modifier)
        )
    }
}
