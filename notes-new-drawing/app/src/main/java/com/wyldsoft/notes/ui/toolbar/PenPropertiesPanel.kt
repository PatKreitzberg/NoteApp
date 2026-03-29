package com.wyldsoft.notes.ui.toolbar
//
//import android.util.Log
//import androidx.compose.foundation.background
//import androidx.compose.foundation.border
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.Spacer
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.size
//import androidx.compose.foundation.layout.width
//import androidx.compose.material3.HorizontalDivider
//import androidx.compose.material3.Slider
//import androidx.compose.material3.Surface
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import com.wyldsoft.notes.pen.PenProfile
//import com.wyldsoft.notes.pen.PenType
//
//private const val TAG = "PenPropertiesPanel"
//
//private val colorSwatches = listOf(
//    Color.Black to "Black",
//    Color.DarkGray to "Dark Gray",
//    Color.Gray to "Gray",
//    Color.LightGray to "Light Gray"
//)
//
//@Composable
//fun PenPropertiesPanel(
//    currentProfile: PenProfile,
//    onProfileChanged: (PenProfile) -> Unit,
//    modifier: Modifier = Modifier
//) {
//    Surface(
//        modifier = modifier
//            .fillMaxWidth()
//            .border(1.dp, Color.Black),
//        shadowElevation = 4.dp
//    ) {
//        Column(modifier = Modifier.padding(12.dp)) {
//            Text("Pen Type", fontWeight = FontWeight.Bold, fontSize = 14.sp)
//            Spacer(modifier = Modifier.height(4.dp))
//
//            PenType.entries.forEach { type ->
//                val isSelected = type == currentProfile.penType
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clickable {
//                            Log.d(TAG, "Selected pen type: ${type.displayName}")
//                            val newProfile = PenProfile
//                                .getDefaultProfile(type, currentProfile.profileId)
//                                .copy(strokeColor = currentProfile.strokeColor)
//                            onProfileChanged(newProfile)
//                        }
//                        .padding(vertical = 6.dp, horizontal = 8.dp)
//                ) {
//                    Text(
//                        text = if (isSelected) "● ${type.displayName}" else type.displayName,
//                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
//                    )
//                }
//            }
//
//            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
//
//            Text(
//                "Width: ${currentProfile.strokeWidth.toInt()}",
//                fontWeight = FontWeight.Bold,
//                fontSize = 14.sp
//            )
//            Slider(
//                value = currentProfile.strokeWidth,
//                onValueChange = { width ->
//                    onProfileChanged(currentProfile.copy(strokeWidth = width))
//                },
//                onValueChangeFinished = {
//                    Log.d(TAG, "Stroke width set to: ${currentProfile.strokeWidth}")
//                },
//                valueRange = 1f..60f,
//                modifier = Modifier.fillMaxWidth()
//            )
//
//            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
//
//            Text("Color", fontWeight = FontWeight.Bold, fontSize = 14.sp)
//            Spacer(modifier = Modifier.height(4.dp))
//            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
//                colorSwatches.forEach { (color, name) ->
//                    val isSelected = color == currentProfile.strokeColor
//                    Box(
//                        modifier = Modifier
//                            .size(36.dp)
//                            .background(color)
//                            .border(
//                                width = if (isSelected) 3.dp else 1.dp,
//                                color = if (isSelected) Color.Red else Color.Black
//                            )
//                            .clickable {
//                                Log.d(TAG, "Selected color: $name")
//                                onProfileChanged(currentProfile.copy(strokeColor = color))
//                            }
//                    )
//                }
//            }
//            Spacer(modifier = Modifier.height(4.dp))
//        }
//    }
//}
