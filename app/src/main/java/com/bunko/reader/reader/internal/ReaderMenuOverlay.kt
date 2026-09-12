package com.bunko.reader.reader.internal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import com.bunko.reader.EpubTextAlign
import com.bunko.reader.PageLayoutMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunko.reader.InvertMode
import com.bunko.reader.PageBackground
import com.bunko.reader.PageTurnMode
import com.bunko.reader.ReaderReadingDirection
import com.bunko.reader.ui.ValueBubbleSlider
import kotlin.math.roundToInt

private enum class MenuOptionTab {
    Text,
    Lighting
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
/** Internal to reader, not for external use. */
@Composable
internal fun ReaderMenuOverlay(
    seriesName: String,
    chapterName: String,
    page: Int,
    pages: Int,
    readingDirection: ReaderReadingDirection,
    pageLayoutMode: PageLayoutMode = PageLayoutMode.Auto,
    showSpreadShift: Boolean,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onSetReadingDirection: (ReaderReadingDirection) -> Unit,
    onSetPageLayoutMode: (PageLayoutMode) -> Unit = {},
    invertMode: InvertMode,
    onSetInvertMode: (InvertMode) -> Unit,
    onNextSingle: () -> Unit,
    onPreviousSingle: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    pageBackground: PageBackground = PageBackground.Paper,
    onSetPageBackground: (PageBackground) -> Unit = {},
    usePureColors: Boolean = false,
    onSetUsePureColors: (Boolean) -> Unit = {},
    pageTurnMode: PageTurnMode = PageTurnMode.Curl,
    onSetPageTurnMode: (PageTurnMode) -> Unit = {},
    isEpub: Boolean = false,
    epubFontSizeSp: Float = 18f,
    onSetEpubFontSizeSp: ((Float) -> Unit)? = null,
    epubFontFamily: String = "Serif",
    onSetEpubFontFamily: ((String) -> Unit)? = null,
    epubTextAlign: EpubTextAlign = EpubTextAlign.Left,
    onSetEpubTextAlign: ((EpubTextAlign) -> Unit)? = null,
    chapters: List<ReaderChapterEntry> = emptyList(),
    currentChapterId: Int = 0,
    onSelectChapter: ((ReaderChapterEntry) -> Unit)? = null
) {
    val rightToLeft = readingDirection == ReaderReadingDirection.RightToLeft
    val safePageCount = pages.coerceAtLeast(1)
    val currentPage = page.coerceIn(0, safePageCount - 1)
    var jumpPage by remember(currentPage, safePageCount) { mutableIntStateOf(currentPage) }
    var showDisplayOptions by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(MenuOptionTab.Text) }

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

    val isLightMode = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val barBg = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f)
    val onBar = MaterialTheme.colorScheme.onSurface
    val onBarVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val dialogSurfaceBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val dialogBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Scrim to dismiss options when open
        if (showDisplayOptions) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { showDisplayOptions = false })
                    }
            )
        }
        // TOP APP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(barBg)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = onBar
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = seriesName,
                    color = onBar,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$chapterName  •  ${page + 1} / $pages",
                    color = onBarVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Google Books "Aa" Options Button
            Surface(
                onClick = { showDisplayOptions = !showDisplayOptions },
                shape = RoundedCornerShape(12.dp),
                color = if (showDisplayOptions) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                contentColor = if (showDisplayOptions) MaterialTheme.colorScheme.onSecondaryContainer else onBar,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Aa",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = if (showDisplayOptions) MaterialTheme.colorScheme.onSecondaryContainer else onBar
                    )
                }
            }

            // Close Menu Button
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close menu",
                    tint = onBar
                )
            }
        }

        // GOOGLE PLAY BOOKS STYLE DISPLAY OPTIONS POPUP
        if (showDisplayOptions) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 64.dp, end = 12.dp, start = 12.dp)
                    .widthIn(max = 350.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(BorderStroke(1.dp, dialogBorder), RoundedCornerShape(20.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { /* consume taps inside */ }
                    },
                shape = RoundedCornerShape(20.dp),
                color = dialogSurfaceBg,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Selected states mirror Reader Settings: M3 ToggleButtons keep
                    // their defaults, custom highlights use the same selected pair.
                    val accent = MaterialTheme.colorScheme.onSecondaryContainer
                    val accentFill = MaterialTheme.colorScheme.secondaryContainer
                    val unselectedText = MaterialTheme.colorScheme.onSurfaceVariant
                    val segmentBg = MaterialTheme.colorScheme.surfaceContainerHighest
                    val onSurface = MaterialTheme.colorScheme.onSurface

                    // TABS (Text & Lighting)
                    TabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = Color.Transparent,
                        contentColor = onSurface,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                                color = accent,
                                height = 3.dp
                            )
                        },
                        divider = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(dividerColor)
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == MenuOptionTab.Text,
                            onClick = { selectedTab = MenuOptionTab.Text },
                            text = {
                                Text(
                                    text = "Text",
                                    fontWeight = if (selectedTab == MenuOptionTab.Text) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == MenuOptionTab.Text) accent else unselectedText
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == MenuOptionTab.Lighting,
                            onClick = { selectedTab = MenuOptionTab.Lighting },
                            text = {
                                Text(
                                    text = "Lighting",
                                    fontWeight = if (selectedTab == MenuOptionTab.Lighting) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == MenuOptionTab.Lighting) accent else unselectedText
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // TAB CONTENT
                    when (selectedTab) {
                        MenuOptionTab.Text -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Font Selection (if EPUB)
                                if (isEpub) {
                                    val fonts = listOf(
                                        "Serif" to ("Original" to FontFamily.Serif),
                                        "Sans" to ("Sans" to FontFamily.SansSerif),
                                        "Cursive" to ("Literata" to FontFamily.Cursive),
                                        "Mono" to ("Mono" to FontFamily.Monospace)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        fonts.forEach { (key, pair) ->
                                            val (label, fam) = pair
                                            val isSelected = epubFontFamily.equals(key, ignoreCase = true)
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.clickable { onSetEpubFontFamily?.invoke(key) }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) accentFill else segmentBg)
                                                        .border(
                                                            BorderStroke(
                                                                1.dp,
                                                                if (isSelected) accent else dialogBorder
                                                            ),
                                                            CircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "A",
                                                        fontSize = 24.sp,
                                                        fontFamily = fam,
                                                        color = if (isSelected) accent else onSurface
                                                    )
                                                }
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isSelected) accent else unselectedText
                                                )
                                            }
                                        }
                                    }

                                    // Font Size Adjuster Box
                                    if (onSetEpubFontSizeSp != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(segmentBg)
                                                .border(BorderStroke(1.dp, dialogBorder), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { onSetEpubFontSizeSp((epubFontSizeSp - 2f).coerceAtLeast(12f)) },
                                                enabled = epubFontSizeSp > 12f
                                            ) {
                                                Text(
                                                    text = "T",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (epubFontSizeSp > 12f) onSurface else onSurface.copy(alpha = 0.38f)
                                                )
                                            }

                                            Text(
                                                text = "${((epubFontSizeSp / 18f) * 100).roundToInt()}%",
                                                color = onSurface,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )

                                            IconButton(
                                                onClick = { onSetEpubFontSizeSp((epubFontSizeSp + 2f).coerceAtMost(36f)) },
                                                enabled = epubFontSizeSp < 36f
                                            ) {
                                                Text(
                                                    text = "T",
                                                    fontSize = 22.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (epubFontSizeSp < 36f) onSurface else onSurface.copy(alpha = 0.38f)
                                                )
                                            }
                                        }
                                    }
                                    // Text Alignment Options
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Text alignment",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = unselectedText
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                                        ) {
                                            val alignments = EpubTextAlign.entries
                                            alignments.forEachIndexed { index, align ->
                                                val isSelected = epubTextAlign == align
                                                ToggleButton(
                                                    checked = isSelected,
                                                    onCheckedChange = { onSetEpubTextAlign?.invoke(align) },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .semantics { role = Role.RadioButton },
                                                    shapes = when (index) {
                                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                                        alignments.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = when (align) {
                                                            EpubTextAlign.Left -> Icons.AutoMirrored.Filled.FormatAlignLeft
                                                            EpubTextAlign.Center -> Icons.Default.FormatAlignCenter
                                                            EpubTextAlign.Right -> Icons.AutoMirrored.Filled.FormatAlignRight
                                                            EpubTextAlign.Justify -> Icons.Default.FormatAlignJustify
                                                        },
                                                        contentDescription = align.name,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Page layout (Auto, 2 pages, 1 page)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Page layout",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = unselectedText
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                                    ) {
                                        val layoutOptions = listOf(
                                            Triple(PageLayoutMode.Auto, "Auto", Icons.Default.Check),
                                            Triple(PageLayoutMode.TwoPages, "2 pages", Icons.AutoMirrored.Filled.MenuBook),
                                            Triple(PageLayoutMode.SinglePage, "1 page", Icons.AutoMirrored.Filled.Article)
                                        )
                                        layoutOptions.forEachIndexed { index, (mode, label, icon) ->
                                            val isSelected = pageLayoutMode == mode
                                            ToggleButton(
                                                checked = isSelected,
                                                onCheckedChange = { onSetPageLayoutMode(mode) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .semantics { role = Role.RadioButton },
                                                shapes = when (index) {
                                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                                    layoutOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                                }
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = label,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = label,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Reading direction (LTR, Vertical, RTL)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Reading direction",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = unselectedText
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                                    ) {
                                        val directions = listOf(
                                            ReaderReadingDirection.LeftToRight to "LTR",
                                            ReaderReadingDirection.Vertical to "Vertical",
                                            ReaderReadingDirection.RightToLeft to "RTL"
                                        )
                                        directions.forEachIndexed { index, (direction, label) ->
                                            val isSelected = readingDirection == direction
                                            ToggleButton(
                                                checked = isSelected,
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
                                                    text = label,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }

                                // Turn Animation (PageTurnMode)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Turn Animation",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = unselectedText
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                                    ) {
                                        val modes = PageTurnMode.entries
                                        modes.forEachIndexed { index, mode ->
                                            ToggleButton(
                                                checked = pageTurnMode == mode,
                                                onCheckedChange = { onSetPageTurnMode(mode) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .semantics { role = Role.RadioButton },
                                                shapes = when (index) {
                                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                                    modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                                }
                                            ) {
                                                Text(
                                                    when (mode) {
                                                        PageTurnMode.Curl -> "3D Curl"
                                                        PageTurnMode.Slide -> "Slide"
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Spread Shift (when in landscape mode)
                                if (showSpreadShift) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Spread Shift",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = unselectedText
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                onClick = { if (rightToLeft) onNextSingle() else onPreviousSingle() },
                                                modifier = Modifier.weight(1f).height(38.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                color = segmentBg,
                                                border = BorderStroke(1.dp, dialogBorder),
                                                contentColor = onSurface
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("-1", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Surface(
                                                onClick = { if (rightToLeft) onPreviousSingle() else onNextSingle() },
                                                modifier = Modifier.weight(1f).height(38.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                color = segmentBg,
                                                border = BorderStroke(1.dp, dialogBorder),
                                                contentColor = onSurface
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("+1", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        MenuOptionTab.Lighting -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                // Background Color Theme Swatches
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "Theme",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = unselectedText
                                    )
                                    val themes = listOf(
                                        Triple("White", Color.White, Color(0xFF141414)),
                                        Triple("Theme", if (isLightMode) MaterialTheme.colorScheme.background else Color(0xFFFAF7F2), if (isLightMode) MaterialTheme.colorScheme.onBackground else Color(0xFF2A2218)),
                                        Triple("Dark", Color(0xFF181818), Color(0xFFE6E6E6)),
                                        Triple("Black", Color.Black, Color.White)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        themes.forEach { (name, bg, fg) ->
                                            val isSelected = when (name) {
                                                "White" -> pageBackground == PageBackground.Paper && usePureColors
                                                "Theme", "Paper" -> pageBackground == PageBackground.Paper && !usePureColors
                                                "Dark" -> pageBackground == PageBackground.Dark && !usePureColors
                                                "Black" -> pageBackground == PageBackground.Dark && usePureColors
                                                else -> false
                                            }
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.clickable {
                                                    when (name) {
                                                        "White" -> {
                                                            onSetPageBackground(PageBackground.Paper)
                                                            onSetUsePureColors(true)
                                                        }
                                                        "Theme", "Paper" -> {
                                                            onSetPageBackground(PageBackground.Paper)
                                                            onSetUsePureColors(false)
                                                        }
                                                        "Dark" -> {
                                                            onSetPageBackground(PageBackground.Dark)
                                                            onSetUsePureColors(false)
                                                        }
                                                        "Black" -> {
                                                            onSetPageBackground(PageBackground.Dark)
                                                            onSetUsePureColors(true)
                                                        }
                                                    }
                                                }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .clip(CircleShape)
                                                        .background(bg)
                                                        .border(
                                                            BorderStroke(
                                                                if (isSelected) 2.5.dp else 1.dp,
                                                                if (isSelected) accent else dialogBorder
                                                            ),
                                                            CircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Selected",
                                                            tint = fg,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "Aa",
                                                            color = fg,
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isSelected) accent else unselectedText
                                                )
                                            }
                                        }
                                    }
                                }

                                // Invert Mode Options
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Invert Mode",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = unselectedText
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
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
                                            ) {
                                                Text(
                                                    when (mode) {
                                                        InvertMode.Off -> "Off"
                                                        InvertMode.Smart -> "Smart"
                                                        InvertMode.Always -> "Always"
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // BOTTOM BAR (CHAPTERS BUTTON + PAGE SLIDER + PAGE COUNT)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(barBg)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chapters / Table of Contents Button (Left of Slider)
            IconButton(
                onClick = { showChapterList = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "Chapters",
                    tint = onBar
                )
            }

            ValueBubbleSlider(
                value = sliderValue,
                onValueChange = { value ->
                    val newPage = sliderValueToPage(value)
                    jumpPage = newPage
                    onJumpToPage(newPage)
                },
                onValueChangeFinished = {
                    onJumpToPage(jumpPage)
                },
                valueRange = 0f..sliderMax,
                valueLabel = { value -> "${sliderValueToPage(value) + 1}" },
                enabled = safePageCount > 1,
                reverseTrackColors = rightToLeft,
                showStopIndicator = false,
                roundThumb = true,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "${jumpPage + 1} / $safePageCount",
                color = onBarVariant,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        // CHAPTERS MODAL BOTTOM SHEET
        if (showChapterList) {
            ModalBottomSheet(
                onDismissRequest = { showChapterList = false },
                containerColor = dialogSurfaceBg,
                contentColor = onBar,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Chapters",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onBar,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                    HorizontalDivider(color = dividerColor)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                    ) {
                        items(chapters) { entry ->
                            val isCurrent = entry.chapterId == currentChapterId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showChapterList = false
                                        onSelectChapter?.invoke(entry)
                                    }
                                    .background(if (isCurrent) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f) else Color.Transparent)
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry.displayName,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else onBar,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Current Chapter",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

