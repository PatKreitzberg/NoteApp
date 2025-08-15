package com.wyldsoft.notes.ui.components

import android.graphics.Rect
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.PenIconUtils
import kotlinx.coroutines.launch

import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel


@Composable
fun Toolbar(
    viewModel: EditorViewModel,
    currentPenProfile: PenProfile,
    isStrokeOptionsOpen: Boolean,
    onSettingsClick: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var selectedProfileIndex by remember { mutableStateOf(0) }
    var isStrokeSelectionOpen by remember { mutableStateOf(isStrokeOptionsOpen) }
    var strokePanelRect by remember { mutableStateOf<Rect?>(null) }

    // Store 5 profiles
    var profiles by remember {
        mutableStateOf(PenProfile.createDefaultProfiles())
    }

    // Sync with ViewModel state
    LaunchedEffect(isStrokeOptionsOpen) {
        isStrokeSelectionOpen = isStrokeOptionsOpen
    }
    
    // Force refresh counter for debugging
    val refreshTriggerForUI by viewModel.refreshUi.collectAsState()

    fun forceUIRefresh() {
        Log.d("Toolbar:", "UI Refresh triggered WARNING: CURRENTLY DOES NOTHING")
        viewModel.forceRefresh()
    }

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

    fun openStrokeOptionsPanel() {
        println("Opening stroke options panel for profile $selectedProfileIndex")
        viewModel.toggleStrokeOptions()
    }

    fun closeStrokeOptionsPanel() {
        println("Closing stroke options panel")
        if (isStrokeSelectionOpen) {
            viewModel.toggleStrokeOptions()
        }
        removeStrokeOptionPanelRect()
        forceUIRefresh()
    }

    fun handleProfileClick(profileIndex: Int) {
        if (selectedProfileIndex == profileIndex && isStrokeSelectionOpen) {
            // Same profile clicked - close panel
            closeStrokeOptionsPanel()
        } else if (selectedProfileIndex == profileIndex && !isStrokeSelectionOpen) {
            // Same profile clicked - open panel
            openStrokeOptionsPanel()
        } else {
            // Different profile - switch profile and update
            if (isStrokeSelectionOpen) {
                closeStrokeOptionsPanel()
            }
            selectedProfileIndex = profileIndex
            val newProfile = profiles[profileIndex]
            viewModel.updatePenProfile(newProfile)
        }
    }

    fun updateProfile(newProfile: PenProfile) {
        val updatedProfiles = profiles.toMutableList()
        updatedProfiles[selectedProfileIndex] = newProfile
        profiles = updatedProfiles

        // Immediately apply the new profile
        viewModel.updatePenProfile(newProfile)

        println("Profile $selectedProfileIndex updated: $newProfile")
    }

    // Listen for drawing events to close panel
    val isDrawing by viewModel.isDrawing.collectAsState()
    LaunchedEffect(isDrawing) {
        if (isDrawing && isStrokeSelectionOpen) {
            println("Drawing started - closing stroke options panel")
            closeStrokeOptionsPanel()
        }
    }


    // Initialize with default profile
    LaunchedEffect(Unit) {
        viewModel.updatePenProfile(profiles[selectedProfileIndex])
    }

    Column {
        // Main toolbar - single row with 5 profile buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .border(1.dp, Color.Gray)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Profiles:", color = Color.Black, fontSize = 12.sp)

            // 5 Profile buttons
            profiles.forEachIndexed { index, profile ->
                ProfileButton(
                    profile = profile,
                    isSelected = selectedProfileIndex == index,
                    onClick = { handleProfileClick(index) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Settings icon
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Note Settings",
                    tint = Color.Black
                )
            }

            // Debug info
            Text(
                text = "Profile: ${selectedProfileIndex + 1} | ${currentPenProfile.penType.displayName}",
                color = Color.Gray,
                fontSize = 10.sp
            )
        }

        // Stroke options panel with disposal detection
        if (isStrokeSelectionOpen) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                DisposableEffect(Unit) {
                    onDispose {
                        // This runs when the panel is actually removed from composition
                        println("StrokeOptionsPanel removed from composition")
                        removeStrokeOptionPanelRect()
                        forceUIRefresh()
                    }
                }

                StrokeOptionsPanel(
                    viewModel = viewModel,
                    currentProfile = currentPenProfile,
                    onProfileChanged = { newProfile ->
                        updateProfile(newProfile)
                    },
                    onPanelPositioned = { rect ->
                        if (rect != strokePanelRect) {
                            strokePanelRect = rect
                            if (isStrokeSelectionOpen) {
                                scope.launch {
                                    addStrokeOptionPanelRect()
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileButton(
    profile: PenProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) profile.strokeColor else Color.Transparent,
            contentColor = if (isSelected) Color.White else Color.Black
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color.Black else Color.Gray
        ),
        modifier = Modifier.size(48.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Icon(
            imageVector = PenIconUtils.getIconForPenType(profile.penType),
            contentDescription = PenIconUtils.getContentDescriptionForPenType(profile.penType),
            modifier = Modifier.size(24.dp)
        )
    }
}