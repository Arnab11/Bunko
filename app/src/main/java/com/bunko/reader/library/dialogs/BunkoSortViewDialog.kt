package com.bunko.reader.library.dialogs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.bunko.reader.library.internal.LocalBookSort
import com.bunko.reader.series.SeriesLibrarySort

private data class SortTypeOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector
)

@Composable
internal fun BunkoSortViewDialog(
    isOffline: Boolean,
    selectedLocalSort: LocalBookSort,
    onLocalSortChange: (LocalBookSort) -> Unit,
    selectedKavitaSort: SeriesLibrarySort,
    onKavitaSortChange: (SeriesLibrarySort) -> Unit,
    isSortDescending: Boolean,
    onSortDescendingChange: (Boolean) -> Unit,
    isGridView: Boolean,
    onGridViewChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Sort and view options",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    DialogSectionTitle(text = "Sort by")

                    if (isOffline) {
                        val localOptions = listOf(
                            SortTypeOption(LocalBookSort.Title, "Title", Icons.Filled.SortByAlpha),
                            SortTypeOption(LocalBookSort.Recent, "Recent", Icons.Filled.History),
                            SortTypeOption(LocalBookSort.Modified, "Date", Icons.Filled.Update),
                            SortTypeOption(LocalBookSort.Unread, "Unread", Icons.Filled.BookmarkBorder)
                        )
                        CombinedSortRow(
                            options = localOptions,
                            selected = selectedLocalSort,
                            onSelect = onLocalSortChange,
                            isSortDescending = isSortDescending,
                            onSortDescendingChange = onSortDescendingChange
                        )
                    } else {
                        val kavitaOptions = listOf(
                            SortTypeOption(SeriesLibrarySort.Title, "Title", Icons.Filled.SortByAlpha),
                            SortTypeOption(SeriesLibrarySort.InProgressFirst, "Recent", Icons.Filled.History),
                            SortTypeOption(SeriesLibrarySort.ReadFirst, "Date", Icons.Filled.Update),
                            SortTypeOption(SeriesLibrarySort.UnreadFirst, "Unread", Icons.Filled.BookmarkBorder)
                        )
                        CombinedSortRow(
                            options = kavitaOptions,
                            selected = selectedKavitaSort,
                            onSelect = onKavitaSortChange,
                            isSortDescending = isSortDescending,
                            onSortDescendingChange = onSortDescendingChange
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(top = 14.dp))

                    DialogSectionTitle(text = "View mode")

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = isGridView,
                            onClick = { onGridViewChange(true) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            colors = themedSegmentedButtonColors(),
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.GridView,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Grid") }
                        )

                        SegmentedButton(
                            selected = !isGridView,
                            onClick = { onGridViewChange(false) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            colors = themedSegmentedButtonColors(),
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ViewList,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("List") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "Done")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
            .widthIn(max = 500.dp)
            .fillMaxWidth(0.92f),
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    )
}

@Composable
private fun DialogSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun <T> CombinedSortRow(
    options: List<SortTypeOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    isSortDescending: Boolean,
    onSortDescendingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val totalSlots = options.size + 2 // 4 sort options + 2 asc/desc options = 6
        val spacing = 6.dp
        val dividerSpace = 12.dp
        val totalNonItemSpace = (spacing * (totalSlots - 1)) + dividerSpace
        val calculatedSize = (maxWidth - totalNonItemSpace) / totalSlots
        val boxSize = calculatedSize.coerceIn(38.dp, 56.dp)
        val cornerRadius = (boxSize * 0.25f).coerceIn(8.dp, 14.dp)
        val iconSize = (boxSize * 0.5f).coerceIn(18.dp, 26.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sort Type Options
            options.forEach { option ->
                val isSelected = option.value == selected
                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "sortSelectionColor"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(boxSize)
                            .clip(RoundedCornerShape(cornerRadius))
                            .background(containerColor)
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = {
                                    if (!isSelected) {
                                        onSelect(option.value)
                                    }
                                },
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = option.label,
                            modifier = Modifier.size(iconSize),
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Vertical Separator |
            VerticalDivider(
                modifier = Modifier
                    .height(boxSize * 0.68f)
                    .padding(horizontal = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Ascending Icon Option
            val ascSelected = !isSortDescending
            val ascBg by animateColorAsState(
                targetValue = if (ascSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                animationSpec = tween(durationMillis = 200),
                label = "ascSelectionColor"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(boxSize)
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(ascBg)
                        .selectable(
                            selected = ascSelected,
                            role = Role.RadioButton,
                            onClick = {
                                if (isSortDescending) {
                                 onSortDescendingChange(false)
                                }
                            },
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Ascending",
                        modifier = Modifier.size(iconSize),
                        tint = if (ascSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                // Alignment spacer matching label height
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Descending Icon Option
            val descSelected = isSortDescending
            val descBg by animateColorAsState(
                targetValue = if (descSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                animationSpec = tween(durationMillis = 200),
                label = "descSelectionColor"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(boxSize)
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(descBg)
                        .selectable(
                            selected = descSelected,
                            role = Role.RadioButton,
                            onClick = {
                                if (!isSortDescending) {
                                    onSortDescendingChange(true)
                                }
                            },
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Descending",
                        modifier = Modifier.size(iconSize),
                        tint = if (descSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                // Alignment spacer matching label height
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun themedSegmentedButtonColors(
    activeContainerAlpha: Float = 0.18f,
    borderAlpha: Float = 0.3f
): SegmentedButtonColors {
    val colorScheme = MaterialTheme.colorScheme
    val activeContentColor = if (colorScheme.onSurface.luminance() > colorScheme.surface.luminance()) Color.White else Color.Black
    return SegmentedButtonDefaults.colors(
        activeContainerColor = colorScheme.primary.copy(alpha = activeContainerAlpha),
        activeContentColor = activeContentColor,
        activeBorderColor = colorScheme.outline.copy(alpha = borderAlpha),
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = colorScheme.onSurfaceVariant,
        inactiveBorderColor = colorScheme.outline.copy(alpha = borderAlpha)
    )
}
