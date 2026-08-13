package li.mof.kamigura.reader.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.mof.kamigura.InvertMode
import li.mof.kamigura.ReaderReadingDirection
import li.mof.kamigura.ui.ValueBubbleSlider
import kotlin.math.roundToInt
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
/** Internal to reader, not for external use. */
@Composable
internal fun ReaderMenuOverlay(
    seriesName: String,
    chapterName: String,
    page: Int,
    pages: Int,
    readingDirection: ReaderReadingDirection,
    // Spread shift only means something while spreads are shown; portrait hides the row
    // (a single-page ±1 there is just a page turn).
    showSpreadShift: Boolean,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onSetReadingDirection: (ReaderReadingDirection) -> Unit,
    invertMode: InvertMode,
    onSetInvertMode: (InvertMode) -> Unit,
    onNextSingle: () -> Unit,
    onPreviousSingle: () -> Unit,
    onJumpToPage: (Int) -> Unit
) {
    val rightToLeft = readingDirection == ReaderReadingDirection.RightToLeft
    val safePageCount = pages.coerceAtLeast(1)
    val currentPage = page.coerceIn(0, safePageCount - 1)
    var jumpPage by remember(currentPage, safePageCount) { mutableIntStateOf(currentPage) }
    val sliderMax = (safePageCount - 1).toFloat()
    val sliderValue = if (rightToLeft) {
        safePageCount - 1 - jumpPage
    } else {
        jumpPage
    }.toFloat()
    fun sliderValueToPage(value: Float): Int {
        val sliderPage = value.roundToInt().coerceIn(0, safePageCount - 1)
        return if (rightToLeft) safePageCount - 1 - sliderPage else sliderPage
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(onDismiss) {
                detectTapGestures(onTap = { onDismiss() })
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color(0xCC000000))
                .statusBarsPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = seriesName,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$chapterName | ${page + 1} / $pages",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close menu",
                    tint = Color.White
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xCC000000))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Direction", color = Color.White)
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    val directions = ReaderReadingDirection.entries
                    directions.forEachIndexed { index, direction ->
                        ToggleButton(
                            checked = readingDirection == direction,
                            onCheckedChange = { onSetReadingDirection(direction) },
                            modifier = Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                directions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                        ) {
                            Text(
                                when (direction) {
                                    ReaderReadingDirection.RightToLeft -> "RTL"
                                    ReaderReadingDirection.Vertical -> "Vertical"
                                    ReaderReadingDirection.LeftToRight -> "LTR"
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Invert", color = Color.White)
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    val modes = InvertMode.entries
                    modes.forEachIndexed { index, mode ->
                        ToggleButton(
                            checked = invertMode == mode,
                            onCheckedChange = { onSetInvertMode(mode) },
                            modifier = Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                        ) { Text(mode.name) }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (rightToLeft) "$safePageCount" else "1",
                    color = Color.White
                )
                ValueBubbleSlider(
                    value = sliderValue,
                    onValueChange = { value ->
                        jumpPage = sliderValueToPage(value)
                    },
                    onValueChangeFinished = {
                        onJumpToPage(jumpPage)
                    },
                    valueRange = 0f..sliderMax,
                    valueLabel = { value -> "${sliderValueToPage(value) + 1}" },
                    enabled = safePageCount > 1,
                    reverseTrackColors = rightToLeft,
                    showStopIndicator = false,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (rightToLeft) "1" else "$safePageCount",
                    color = Color.White
                )
            }

            if (showSpreadShift) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Kept discoverable here even though edge long-press does the same:
                    // the menu is the only place a new user can find spread correction.
                    Text("Spread shift", color = Color.White)
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (rightToLeft) {
                            Button(
                                onClick = onNextSingle,
                                modifier = Modifier.weight(1f)
                            ) { Text("+1") }
                            Button(
                                onClick = onPreviousSingle,
                                modifier = Modifier.weight(1f)
                            ) { Text("-1") }
                        } else {
                            Button(
                                onClick = onPreviousSingle,
                                modifier = Modifier.weight(1f)
                            ) { Text("-1") }
                            Button(
                                onClick = onNextSingle,
                                modifier = Modifier.weight(1f)
                            ) { Text("+1") }
                        }
                    }
                }
            }
        }
    }
}

