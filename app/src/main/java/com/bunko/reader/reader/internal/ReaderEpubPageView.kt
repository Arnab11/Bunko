package com.bunko.reader.reader.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.imageLoader
import com.bunko.reader.EPaperMode
import com.bunko.reader.EpubTextAlign
import com.bunko.reader.InvertMode

@Composable
internal fun ReaderEpubPageView(
    subpage: EpubSubpage,
    fontSizeSp: Float,
    pageBackground: Color,
    invertMode: InvertMode = InvertMode.Off,
    ePaperMode: EPaperMode = EPaperMode.Off,
    epubFontFamily: String = "Serif",
    epubTextAlign: EpubTextAlign = EpubTextAlign.Left,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader? = null,
    contentPadding: PaddingValues? = null,
    blockSpacingDp: Dp = 10.dp
) {
    val isDark = pageBackground.luminance() < 0.5f
    val textColor = if (isDark) Color(0xFFEDEDED) else Color(0xFF141414)
    val dividerColor = if (isDark) Color(0xFF333333) else Color(0xFFDCD7CC)
    val quoteBarColor = if (isDark) Color(0xFF666666) else Color(0xFF9E988D)
    val imageColorFilter = readerColorFilter(ePaperMode, invertMode == InvertMode.Always)
    val activeFontFamily = when (epubFontFamily.lowercase()) {
        "sans", "sansserif", "sans-serif" -> FontFamily.SansSerif
        "mono", "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.Serif
    }
    val activeTextAlign = when (epubTextAlign) {
        EpubTextAlign.Left -> TextAlign.Start
        EpubTextAlign.Center -> TextAlign.Center
        EpubTextAlign.Right -> TextAlign.End
        EpubTextAlign.Justify -> TextAlign.Justify
    }

    val insets = WindowInsets.navigationBars.union(WindowInsets.displayCutout).asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val effectivePadding = contentPadding ?: PaddingValues(
        start = insets.calculateStartPadding(layoutDirection).coerceAtLeast(20.dp),
        top = (insets.calculateTopPadding() + 16.dp).coerceAtLeast(28.dp),
        end = insets.calculateEndPadding(layoutDirection).coerceAtLeast(20.dp),
        bottom = (insets.calculateBottomPadding() + 36.dp).coerceAtLeast(48.dp)
    )

    val baseTextStyle = TextStyle(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        )
    )

    val isSingleImagePage = subpage.blocks.size == 1 && subpage.blocks.first() is EpubBlock.ImageBlock

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackground)
            .ePaperGrain(ePaperMode)
            .padding(effectivePadding)
    ) {
        if (isSingleImagePage) {
            val imageBlock = subpage.blocks.first() as EpubBlock.ImageBlock
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageBlock.url,
                    imageLoader = imageLoader ?: LocalContext.current.imageLoader,
                    contentDescription = imageBlock.alt,
                    contentScale = ContentScale.Fit,
                    colorFilter = imageColorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(blockSpacingDp)
            ) {
                for (block in subpage.blocks) {
                    when (block) {
                        is EpubBlock.DividerBlock -> {
                            HorizontalDivider(
                                color = dividerColor,
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        is EpubBlock.ImageBlock -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val imageModifier = if (block.aspectRatio > 0f) {
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f / block.aspectRatio, matchHeightConstraintsFirst = false)
                                        .clip(RoundedCornerShape(4.dp))
                                } else {
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                }

                                AsyncImage(
                                    model = block.url,
                                    imageLoader = imageLoader ?: LocalContext.current.imageLoader,
                                    contentDescription = block.alt,
                                    contentScale = ContentScale.Fit,
                                    colorFilter = imageColorFilter,
                                    modifier = imageModifier
                                )
                            }
                        }

                        is EpubBlock.TextBlock -> {
                            if (block.isHeading) {
                                val headingScale = when (block.headingLevel) {
                                    1 -> 1.45f
                                    2 -> 1.3f
                                    3 -> 1.2f
                                    else -> 1.1f
                                }
                                Text(
                                    text = block.text,
                                    fontSize = (fontSizeSp * headingScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = activeFontFamily,
                                    color = textColor,
                                    textAlign = activeTextAlign,
                                    lineHeight = (fontSizeSp * headingScale * 1.35f).sp,
                                    style = baseTextStyle,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                )
                            } else if (block.isQuote) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(24.dp)
                                            .background(quoteBarColor)
                                    )
                                    Text(
                                        text = block.text,
                                        fontSize = fontSizeSp.sp,
                                        fontStyle = FontStyle.Italic,
                                        fontFamily = activeFontFamily,
                                        color = textColor,
                                        textAlign = activeTextAlign,
                                        lineHeight = (fontSizeSp * 1.45f).sp,
                                        style = baseTextStyle,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                Text(
                                    text = block.text,
                                    fontSize = fontSizeSp.sp,
                                    fontFamily = activeFontFamily,
                                    color = textColor,
                                    textAlign = activeTextAlign,
                                    lineHeight = (fontSizeSp * 1.45f).sp,
                                    style = baseTextStyle,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
