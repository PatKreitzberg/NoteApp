package com.wyldsoft.notes.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.data.database.entities.FolderEntity

@Composable
fun BreadcrumbBar(
    breadcrumbs: List<FolderEntity>,
    onFolderClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        breadcrumbs.forEachIndexed { index, folder ->
            if (index > 0) {
                Text(
                    text = " > ",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                )
            }
            val isLast = index == breadcrumbs.lastIndex
            Text(
                text = if (folder.id == FolderEntity.ROOT_ID) "Home" else folder.name,
                style = MaterialTheme.typography.body1,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                color = if (isLast) {
                    MaterialTheme.colors.onSurface
                } else {
                    MaterialTheme.colors.primary
                },
                modifier = if (!isLast) {
                    Modifier.clickable { onFolderClick(folder.id) }
                } else {
                    Modifier
                }
            )
        }
    }
}
