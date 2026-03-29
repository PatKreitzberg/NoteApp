package com.wyldsoft.notes.touchhandling
//
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.MutableState
//import androidx.compose.runtime.getValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import kotlinx.coroutines.delay
//
///**
// * Compose overlay that displays the most recently recognized gesture near the bottom
// * of the screen. Auto-dismisses after 2 seconds.
// */
//@Composable
//fun GestureDisplay(gestureLabel: MutableState<String>) {
//    val label by gestureLabel
//
//    // Auto-clear after 2 seconds whenever the label changes
//    if (label.isNotEmpty()) {
//        LaunchedEffect(label) {
//            delay(2000)
//            gestureLabel.value = ""
//        }
//    }
//
//    Box(
//        modifier = Modifier.fillMaxSize(),
//        contentAlignment = Alignment.BottomCenter
//    ) {
//        if (label.isNotEmpty()) {
//            Text(
//                text = label,
//                modifier = Modifier
//                    .padding(bottom = 48.dp)
//                    .background(
//                        color = Color(0xCC333333),
//                        shape = RoundedCornerShape(8.dp)
//                    )
//                    .padding(horizontal = 16.dp, vertical = 8.dp),
//                color = Color.White,
//                fontSize = 16.sp
//            )
//        }
//    }
//}
