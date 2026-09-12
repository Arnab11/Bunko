package li.mof.kamigura.reader.internal

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
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
import li.mof.kamigura.InvertMode
import li.mof.kamigura.PageBackground
import li.mof.kamigura.PageTurnMode
import li.mof.kamigura.ReaderReadingDirection
import li.mof.kamigura.ui.ValueBubbleSlider
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
    showSpreadShift: Boolean,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onSetReadingDirection: (ReaderReadingDirection) -> Unit,
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
    onSetEpubFontFamily: ((String) -> Unit)? = null
) {
    val rightToLeft = readingDirection == ReaderReadingDirection.RightToLeft
    val safePageCount = pages.coerceAtLeast(1)
    val currentPage = page.coerceIn(0, safePageCount - 1)
    var jumpPage by remember(currentPage, safePageCount) { mutableIntStateOf(currentPage) }
    var showDisplayOptions by remember { mutableStateOf(false) }
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

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(onDismiss) {
                detectTapGestures(onTap = {
                    if (showDisplayOptions) {
                        showDisplayOptions = false
                    } else {
                        onDismiss()
                    }
                })
            }
    ) {
        // TOP APP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color(0xEB141518))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$chapterName  •  ${page + 1} / $pages",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Google Books "Aa" Options Button
            Surface(
                onClick = { showDisplayOptions = !showDisplayOptions },
                shape = RoundedCornerShape(12.dp),
                color = if (showDisplayOptions) Color(0xFF383B40) else Color.Transparent,
                contentColor = Color.White,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Aa",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = if (showDisplayOptions) Color(0xFF90CAF9) else Color.White
                    )
                }
            }

            // Close Menu Button
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close menu",
                    tint = Color.White
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
                    .border(BorderStroke(1.dp, Color(0x2EFFFFFF)), RoundedCornerShape(20.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { /* consume taps inside */ }
                    },
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF51E2024),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // TABS (Text & Lighting)
                    TabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                                color = Color(0xFF90CAF9),
                                height = 3.dp
                            )
                        },
                        divider = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0x22FFFFFF))
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
                                    color = if (selectedTab == MenuOptionTab.Text) Color(0xFF90CAF9) else Color(0xFFB0B4BA)
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
                                    color = if (selectedTab == MenuOptionTab.Lighting) Color(0xFF90CAF9) else Color(0xFFB0B4BA)
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
                                                        .background(if (isSelected) Color(0xFF90CAF9) else Color(0xFF2B2D33))
                                                        .border(
                                                            BorderStroke(
                                                                1.dp,
                                                                if (isSelected) Color(0xFF90CAF9) else Color(0x33FFFFFF)
                                                            ),
                                                            CircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "A",
                                                        fontSize = 24.sp,
                                                        fontFamily = fam,
                                                        color = if (isSelected) Color(0xFF101216) else Color.White
                                                    )
                                                }
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isSelected) Color(0xFF90CAF9) else Color(0xFF9EA3AC)
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
                                                .background(Color(0xFF2A2D33))
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
                                                    color = if (epubFontSizeSp > 12f) Color.White else Color(0x55FFFFFF)
                                                )
                                            }

                                            Text(
                                                text = "${((epubFontSizeSp / 18f) * 100).roundToInt()}%",
                                                color = Color.White,
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
                                                    color = if (epubFontSizeSp < 36f) Color.White else Color(0x55FFFFFF)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Page Layout / Direction
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Page layout",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFFB0B4BA)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
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
                                                        ReaderReadingDirection.LeftToRight -> "LTR"
                                                        ReaderReadingDirection.RightToLeft -> "RTL"
                                                        ReaderReadingDirection.Vertical -> "Vertical"
                                                    }
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
                                        color = Color(0xFFB0B4BA)
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
                                            color = Color(0xFFB0B4BA)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                onClick = { if (rightToLeft) onNextSingle() else onPreviousSingle() },
                                                modifier = Modifier.weight(1f).height(38.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF2A2D33),
                                                contentColor = Color.White
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("-1", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Surface(
                                                onClick = { if (rightToLeft) onPreviousSingle() else onNextSingle() },
                                                modifier = Modifier.weight(1f).height(38.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF2A2D33),
                                                contentColor = Color.White
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
                                        color = Color(0xFFB0B4BA)
                                    )
                                    val themes = listOf(
                                        Triple("White", Color.White, Color(0xFF141414)),
                                        Triple("Paper", Color(0xFFFAF7F2), Color(0xFF2A2218)),
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
                                                "Paper" -> pageBackground == PageBackground.Paper && !usePureColors
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
                                                        "Paper" -> {
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
                                                                if (isSelected) Color(0xFF90CAF9) else Color(0x44FFFFFF)
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
                                                    color = if (isSelected) Color(0xFF90CAF9) else Color(0xFF9EA3AC)
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
                                        color = Color(0xFFB0B4BA)
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

        // BOTTOM BAR (ONLY PAGE SLIDER)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xEB141518))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (rightToLeft) "$safePageCount" else "1",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
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
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

