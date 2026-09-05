package com.xennmap.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xennmap.domain.model.ChartDepth
import com.xennmap.domain.model.DepthUnit
import com.xennmap.domain.model.PlaceCategory
import com.xennmap.ui.theme.XennThemeExtended
import com.xennmap.utils.FormatUtils
import com.xennmap.utils.GeoUtils

/**
 * Bottom sheet for creating or editing a saved place:
 * name, category, optional note. Coordinates are captured by the caller —
 * either from the current GPS position or from a point picked on the map.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditorSheet(
    latitude: Double,
    longitude: Double,
    initialName: String = "",
    initialCategory: PlaceCategory = PlaceCategory.FISHING_SPOT,
    initialNote: String = "",
    title: String = "Save Location",
    estimatedDepth: ChartDepth? = null,
    depthUnit: DepthUnit = DepthUnit.METERS,
    onSave: (name: String, category: PlaceCategory, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initialName) }
    var note by remember { mutableStateOf(initialNote) }
    var category by remember { mutableStateOf(initialCategory) }
    var nameError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Coordinates: ${GeoUtils.formatLatitude(latitude)}, ${GeoUtils.formatLongitude(longitude)}",
                style = MaterialTheme.typography.bodyMedium,
                color = XennThemeExtended.colors.textSecondary,
            )
            Text(
                text = estimatedDepth?.let {
                    "Estimated depth: " + FormatUtils.depth(it.meters, depthUnit) +
                        if (it.downloaded) " (downloaded area)" else " (charted)"
                } ?: "Depth unavailable at this point",
                style = MaterialTheme.typography.bodySmall,
                color = XennThemeExtended.colors.textSecondary,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("Location name") },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text("Please give this place a name") }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PlaceCategory.entries.forEach { candidate ->
                    FilterChip(
                        selected = category == candidate,
                        onClick = { category = candidate },
                        label = { Text(candidate.label) },
                    )
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                placeholder = { Text("e.g. Good fishing area, rocky bottom") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            nameError = true
                        } else {
                            onSave(name.trim(), category, note.trim())
                            onDismiss()
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("Save")
                }
            }
        }
    }
}
