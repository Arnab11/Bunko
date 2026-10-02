package com.bunko.reader.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BunkoSheetDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 4.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

/**
 * mpvRx-style flyout bottom sheet:
 * Includes centered drag handle, header with title/subtitle and actions,
 * bounded adaptive height for portrait and landscape, and full edge-to-edge support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BunkoPickerSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    titleContent: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    warning: String? = null,
    scrollContent: Boolean = true,
    sheetMaxWidth: Dp = 640.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    headerActions: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val bodyMaxHeight =
        (configuration.screenHeightDp.dp - if (isLandscape) 132.dp else 180.dp)
            .coerceAtLeast(160.dp)
            .coerceAtMost(if (isLandscape) 460.dp else 600.dp)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = contentColor,
        scrimColor = BottomSheetDefaults.ScrimColor,
        sheetMaxWidth = sheetMaxWidth,
        dragHandle = { BunkoSheetDragHandle() },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (titleContent != null) {
                        titleContent()
                    } else if (title.isNotBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }

                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (warning != null) {
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = headerActions
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = bodyMaxHeight)
                    .then(if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            ) {
                content()
            }
        }
    }
}
