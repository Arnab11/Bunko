package com.bunko.reader.reader.internal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
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
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import kotlin.math.roundToInt
import com.bunko.reader.EPaperMode
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.bunko.reader.ReaderImageScaleType
import com.bunko.reader.ReaderNavigationMode
import com.bunko.reader.ReaderReadingDirection
import com.bunko.reader.ReaderTappingInvertMode
import com.bunko.reader.ui.ValueBubbleSlider
import kotlin.math.roundToInt

private enum class MenuOptionTab {
    Text,
    Lighting
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
/** Internal to reader, not for external use. */
@Composable
internal fun ReaderMenuOverlay(
    visible: Boolean = true,
    menuFraction: Float = 1f,
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
    ePaperMode: EPaperMode = EPaperMode.Off,
    onSetEPaperMode: (EPaperMode) -> Unit = {},
    readerBrightness: Float = -1f,
    onSetReaderBrightness: (Float) -> Unit = {},
    nightModeEnabled: Boolean = false,
    onSetNightModeEnabled: (Boolean) -> Unit = {},
    nightLightIntensity: Float = 0.45f,
    onSetNightLightIntensity: (Float) -> Unit = {},
    onNextSingle: () -> Unit,
    onPreviousSingle: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    pageBackground: PageBackground = PageBackground.Paper,
    onSetPageBackground: (PageBackground) -> Unit = {},
    usePureColors: Boolean = false,
    onSetUsePureColors: (Boolean) -> Unit = {},
    pageTransitionAnimation: Boolean = true,
    onSetPageTransitionAnimation: (Boolean) -> Unit = {},
    pageTurnMode: PageTurnMode = PageTurnMode.Slide,
    onSetPageTurnMode: (PageTurnMode) -> Unit = {},
    imageScaleType: ReaderImageScaleType = ReaderImageScaleType.FitScreen,
    onSetImageScaleType: (ReaderImageScaleType) -> Unit = {},
    cropBorders: Boolean = false,
    onSetCropBorders: (Boolean) -> Unit = {},
    navigationMode: ReaderNavigationMode = ReaderNavigationMode.Default,
    onSetNavigationMode: (ReaderNavigationMode) -> Unit = {},
    tappingInvertMode: ReaderTappingInvertMode = ReaderTappingInvertMode.None,
    onSetTappingInvertMode: (ReaderTappingInvertMode) -> Unit = {},
    autoWebtoonMode: Boolean = true,
    onSetAutoWebtoonMode: (Boolean) -> Unit = {},
    webtoonSidePadding: Int = 0,
    onSetWebtoonSidePadding: (Int) -> Unit = {},
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

    val context = LocalContext.current
    val density = LocalDensity.current
    val resStatusBarHeight = remember(context) {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }
    val stableStatusBarHeight = maxOf(
        WindowInsets.statusBarsIgnoringVisibility
            .union(WindowInsets.displayCutout)
            .asPaddingValues()
            .calculateTopPadding(),
        with(density) { resStatusBarHeight.toDp() }
    )
    val liveStatusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val statusBarTopPadding = maxOf(stableStatusBarHeight, liveStatusBarHeight)
    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dialogBottomPadding = if (isTablet) 24.dp else (navBarBottomPadding + 96.dp)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Scrim to dismiss options when open
        if (showDisplayOptions && visible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { showDisplayOptions = false })
                    }
            )
        }
        // TOP APP BAR (animates down from above with status bar)
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(180)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(150)
            ) + fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = -(1f - menuFraction) * size.height
                    alpha = menuFraction
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barBg)
                    .padding(top = statusBarTopPadding)
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
        }

        // GOOGLE PLAY BOOKS STYLE DISPLAY OPTIONS POPUP
        AnimatedVisibility(
            visible = visible && showDisplayOptions,
            enter = fadeIn(animationSpec = tween(180)) + slideInVertically(
                initialOffsetY = { -it / 4 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = fadeOut(animationSpec = tween(120)) + slideOutVertically(
                targetOffsetY = { -it / 4 },
                animationSpec = tween(120)
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    translationY = -(1f - menuFraction) * size.height
                    alpha = menuFraction
                }
        ) {
            Surface(
                modifier = Modifier
                    .padding(
                        top = statusBarTopPadding + 64.dp,
                        bottom = dialogBottomPadding,
                        end = 12.dp,
                        start = 12.dp
                    )
                    .widthIn(max = 350.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(BorderStroke(1.dp, dialogBorder), RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* consume taps inside */ },
                shape = RoundedCornerShape(20.dp),
                color = dialogSurfaceBg,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
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
                                    text = if (isEpub) "Text" else "Layout",
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

                    val layoutScrollState = rememberScrollState()
                    val lightingScrollState = rememberScrollState()

                    // TAB CONTENT (Scrollable on phones with bounded height)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        when (selectedTab) {
                            MenuOptionTab.Text -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(layoutScrollState)
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
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
                                            ReaderReadingDirection.Webtoon to "Webtoon",
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
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }

                                // Auto Webtoon Mode
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Auto webtoon mode",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = unselectedText
                                        )
                                    }
                                    ToggleButton(
                                        checked = autoWebtoonMode,
                                        onCheckedChange = { onSetAutoWebtoonMode(it) },
                                        modifier = Modifier
                                            .height(28.dp)
                                            .semantics { role = Role.Checkbox }
                                    ) {
                                        Text(
                                            text = if (autoWebtoonMode) "On" else "Off",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }

                                // Webtoon Side Padding (when in Webtoon mode)
                                if (readingDirection == ReaderReadingDirection.Webtoon) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Webtoon side padding",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = unselectedText
                                        )
                                        val paddingOptions = listOf(0, 5, 10, 15, 20, 25)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                                        ) {
                                            paddingOptions.forEachIndexed { index, paddingVal ->
                                                val isSelected = webtoonSidePadding == paddingVal
                                                ToggleButton(
                                                    checked = isSelected,
                                                    onCheckedChange = { onSetWebtoonSidePadding(paddingVal) },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .semantics { role = Role.RadioButton },
                                                    shapes = when (index) {
                                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                                        paddingOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                                    }
                                                ) {
                                                    Text(
                                                        text = "$paddingVal%",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Turn Animation (Off, Slide, 3D)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Turn animation",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = unselectedText
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                                    ) {
                                        val animOptions = listOf("Off", "Slide", "3D")
                                        animOptions.forEachIndexed { index, option ->
                                            val isSelected = when (option) {
                                                "Off" -> !pageTransitionAnimation
                                                "Slide" -> pageTransitionAnimation && pageTurnMode == PageTurnMode.Slide
                                                "3D" -> pageTransitionAnimation && pageTurnMode == PageTurnMode.Curl
                                                else -> false
                                            }
                                            ToggleButton(
                                                checked = isSelected,
                                                onCheckedChange = {
                                                    when (option) {
                                                        "Off" -> onSetPageTransitionAnimation(false)
                                                        "Slide" -> {
                                                            onSetPageTransitionAnimation(true)
                                                            onSetPageTurnMode(PageTurnMode.Slide)
                                                        }
                                                        "3D" -> {
                                                            onSetPageTransitionAnimation(true)
                                                            onSetPageTurnMode(PageTurnMode.Curl)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .semantics { role = Role.RadioButton },
                                                shapes = when (index) {
                                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                                    animOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                                }
                                            ) {
                                                Text(
                                                    text = option,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
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

                                if (!isEpub) {
                                    // Scale Type
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Scale type",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = unselectedText
                                        )
                                        val scaleRows = listOf(
                                            listOf(
                                                ReaderImageScaleType.FitScreen to "Fit screen",
                                                ReaderImageScaleType.Stretch to "Stretch",
                                                ReaderImageScaleType.FitWidth to "Fit width"
                                            ),
                                            listOf(
                                                ReaderImageScaleType.FitHeight to "Fit height",
                                                ReaderImageScaleType.OriginalSize to "Original",
                                                ReaderImageScaleType.SmartFit to "Smart fit"
                                            )
                                        )
                                        scaleRows.forEach { rowItems ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                                            ) {
                                                rowItems.forEachIndexed { index, (type, label) ->
                                                    val isSelected = imageScaleType == type
                                                    ToggleButton(
                                                        checked = isSelected,
                                                        onCheckedChange = { onSetImageScaleType(type) },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .semantics { role = Role.RadioButton },
                                                        shapes = when (index) {
                                                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                                            rowItems.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                                        }
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Crop Borders
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Crop borders",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = unselectedText
                                        )
                                        ToggleButton(
                                            checked = cropBorders,
                                            onCheckedChange = { onSetCropBorders(it) },
                                            modifier = Modifier
                                                .height(28.dp)
                                                .semantics { role = Role.Checkbox }
                                        ) {
                                            Text(
                                                text = if (cropBorders) "On" else "Off",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }

                                // Tap Zones
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Tap zones",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = unselectedText
                                    )
                                    val tapRows = listOf(
                                        listOf(
                                            ReaderNavigationMode.Default to "Default",
                                            ReaderNavigationMode.LShaped to "L-shaped",
                                            ReaderNavigationMode.Kindlish to "Kindle-ish"
                                        ),
                                        listOf(
                                            ReaderNavigationMode.Edge to "Edge",
                                            ReaderNavigationMode.RightAndLeft to "Right & Left",
                                            ReaderNavigationMode.Disabled to "Disabled"
                                        )
                                    )
                                    tapRows.forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                                        ) {
                                            rowItems.forEachIndexed { index, (mode, label) ->
                                                val isSelected = navigationMode == mode
                                                ToggleButton(
                                                    checked = isSelected,
                                                    onCheckedChange = { onSetNavigationMode(mode) },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .semantics { role = Role.RadioButton },
                                                    shapes = when (index) {
                                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                                        rowItems.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                                    }
                                                ) {
                                                    Text(
                                                        text = label,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Invert Tap Zone
                                if (navigationMode != ReaderNavigationMode.Disabled) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Invert tap zone",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = unselectedText
                                        )
                                        val invertRows = listOf(
                                            listOf(
                                                ReaderTappingInvertMode.None to "None",
                                                ReaderTappingInvertMode.Horizontal to "Horizontal"
                                            ),
                                            listOf(
                                                ReaderTappingInvertMode.Vertical to "Vertical",
                                                ReaderTappingInvertMode.Both to "Both"
                                            )
                                        )
                                        invertRows.forEach { rowItems ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                                            ) {
                                                rowItems.forEachIndexed { index, (invert, label) ->
                                                    val isSelected = tappingInvertMode == invert
                                                    ToggleButton(
                                                        checked = isSelected,
                                                        onCheckedChange = { onSetTappingInvertMode(invert) },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .semantics { role = Role.RadioButton },
                                                        shapes = when (index) {
                                                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                                            rowItems.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                                        }
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
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
                                    .verticalScroll(lightingScrollState)
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
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
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "E-Paper Mode",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = unselectedText
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                                    ) {
                                        val epModes = EPaperMode.entries
                                        epModes.forEachIndexed { index, mode ->
                                            ToggleButton(
                                                checked = ePaperMode == mode,
                                                onCheckedChange = { onSetEPaperMode(mode) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .semantics { role = Role.RadioButton },
                                                shapes = when (index) {
                                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                                    epModes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                                }
                                            ) {
                                                Text(
                                                    when (mode) {
                                                        EPaperMode.Off -> "Off"
                                                        EPaperMode.BlackAndWhite -> "B&W"
                                                        EPaperMode.Color -> "Color"
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Brightness Control Section
                                    Spacer(modifier = Modifier.height(14.dp))
                                    val isAuto = readerBrightness < 0f
                                    val currentSystemBrightness = remember(isAuto) {
                                        systemBrightnessToSlider(getSystemBrightness(context))
                                    }
                                    val currentBrightnessValue = if (isAuto) currentSystemBrightness else readerBrightness

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Brightness",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = unselectedText
                                        )
                                        ToggleButton(
                                            checked = isAuto,
                                            onCheckedChange = { auto ->
                                                onSetReaderBrightness(if (auto) -1f else currentSystemBrightness)
                                            },
                                            modifier = Modifier
                                                .height(28.dp)
                                                .semantics { role = Role.Checkbox }
                                        ) {
                                            Text(
                                                text = if (isAuto) "Auto" else "Custom",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BrightnessLow,
                                            contentDescription = "Low Brightness",
                                            tint = unselectedText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Slider(
                                            value = currentBrightnessValue.coerceIn(0f, 1f),
                                            onValueChange = { onSetReaderBrightness(it.coerceIn(0f, 1f)) },
                                            valueRange = 0f..1f,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = accent,
                                                activeTrackColor = accent,
                                                inactiveTrackColor = dialogBorder
                                            )
                                        )
                                        Icon(
                                            imageVector = Icons.Default.BrightnessHigh,
                                            contentDescription = "High Brightness",
                                            tint = unselectedText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "${(currentBrightnessValue * 100).roundToInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = unselectedText,
                                            modifier = Modifier.widthIn(min = 36.dp),
                                            textAlign = TextAlign.End
                                        )
                                    }

                                    // Night Mode / Night Light Section
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NightlightRound,
                                                contentDescription = "Night Light",
                                                tint = unselectedText,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "Night Light",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (nightModeEnabled) onSurface else unselectedText
                                            )
                                        }
                                        ToggleButton(
                                            checked = nightModeEnabled,
                                            onCheckedChange = onSetNightModeEnabled,
                                            modifier = Modifier
                                                .height(28.dp)
                                                .semantics { role = Role.Checkbox }
                                        ) {
                                            Text(
                                                text = if (nightModeEnabled) "On" else "Off",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                    AnimatedVisibility(visible = nightModeEnabled) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Warmth",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = unselectedText
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.WbSunny,
                                                    contentDescription = "Mild Warmth",
                                                    tint = unselectedText,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Slider(
                                                    value = nightLightIntensity,
                                                    onValueChange = onSetNightLightIntensity,
                                                    valueRange = 0f..1f,
                                                    modifier = Modifier.weight(1f),
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = accent,
                                                        activeTrackColor = accent,
                                                        inactiveTrackColor = dialogBorder
                                                    )
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.NightlightRound,
                                                    contentDescription = "Deep Warmth",
                                                    tint = unselectedText,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "${(nightLightIntensity * 100).roundToInt()}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = unselectedText,
                                                    modifier = Modifier.widthIn(min = 36.dp),
                                                    textAlign = TextAlign.End
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
        }

        // BOTTOM BAR (CHAPTERS BUTTON + PAGE SLIDER + PAGE COUNT)
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(180)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(150)
            ) + fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    translationY = (1f - menuFraction) * size.height
                    alpha = menuFraction
                }
        ) {
            val batteryState = rememberBatteryState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barBg)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

                val menuProgressPercent = if (safePageCount > 0) {
                    (((jumpPage + 1).toFloat() / safePageCount) * 100).roundToInt()
                } else 0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Page ${jumpPage + 1} of $safePageCount",
                        color = onBarVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "$menuProgressPercent% completed",
                        color = onBarVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        BatteryIcon(
                            batteryState = batteryState,
                            tint = onBarVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${batteryState.level}%",
                            color = onBarVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
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

private fun systemBrightnessToSlider(systemBrightness: Float): Float {
    val h = systemBrightness.coerceIn(0.01f, 1f)
    val fraction = ((h - 0.01f) / 0.99f).coerceIn(0f, 1f)
    val t = kotlin.math.sqrt(fraction)
    return (0.20f + t * 0.80f).coerceIn(0f, 1f)
}

private fun getSystemBrightness(context: android.content.Context): Float {
    return try {
        val floatVal = android.provider.Settings.System.getFloat(
            context.contentResolver,
            "screen_brightness_float",
            -1f
        )
        if (floatVal in 0.01f..1f) {
            floatVal
        } else {
            val intVal = android.provider.Settings.System.getInt(
                context.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            (intVal / 255f).coerceIn(0.01f, 1f)
        }
    } catch (e: Throwable) {
        0.5f
    }
}
