package com.xennmap.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xennmap.domain.model.PlaceCategory
import com.xennmap.ui.theme.CategoryColorDanger
import com.xennmap.ui.theme.CategoryColorDock
import com.xennmap.ui.theme.CategoryColorFishing
import com.xennmap.ui.theme.CategoryColorFavorite
import com.xennmap.ui.theme.CategoryColorHome
import com.xennmap.ui.theme.CategoryColorOther
import com.xennmap.ui.theme.CategoryColorWaypoint
import com.xennmap.ui.theme.XennThemeExtended

/** Translucent "glass" panel used for floating map chrome. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val ext = XennThemeExtended.colors
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = ext.glassSurface,
        border = BorderStroke(1.dp, ext.glassBorder),
        shadowElevation = 6.dp,
        content = content,
    )
}

enum class StatusTone { SAFE, INFO, WARNING, DANGER, NEUTRAL }

@Composable
private fun toneColor(tone: StatusTone): Color = when (tone) {
    StatusTone.SAFE -> XennThemeExtended.colors.safe
    StatusTone.INFO -> MaterialTheme.colorScheme.primary
    StatusTone.WARNING -> XennThemeExtended.colors.warning
    StatusTone.DANGER -> XennThemeExtended.colors.danger
    StatusTone.NEUTRAL -> XennThemeExtended.colors.textSecondary
}

/** Small pill with a status dot + label. Never relies on color alone: label is required. */
@Composable
fun StatusChip(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val color = toneColor(tone)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = XennThemeExtended.colors.textSecondary,
        modifier = modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = XennThemeExtended.colors.textSecondary,
            modifier = Modifier.size(44.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = XennThemeExtended.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = XennThemeExtended.colors.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

fun categoryColor(category: PlaceCategory): Color = when (category) {
    PlaceCategory.FISHING_SPOT -> CategoryColorFishing
    PlaceCategory.HOME_PORT -> CategoryColorHome
    PlaceCategory.DOCK -> CategoryColorDock
    PlaceCategory.DANGER -> CategoryColorDanger
    PlaceCategory.WAYPOINT -> CategoryColorWaypoint
    PlaceCategory.FAVORITE -> CategoryColorFavorite
    PlaceCategory.OTHER -> CategoryColorOther
}
