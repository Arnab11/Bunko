package li.mof.kamigura.reader.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.imageLoader
import li.mof.kamigura.InvertMode

private val NegativeColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

@Composable
internal fun ReaderEpubPageView(
    subpage: EpubSubpage,
    fontSizeSp: Float,
    pageBackground: Color,
    invertMode: InvertMode = InvertMode.Off,
    epubFontFamily: String = "Serif",
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader? = null,
    contentPadding: PaddingValues? = null
) {
    val isDark = pageBackground.luminance() < 0.5f
    val textColor = if (isDark) Color(0xFFEDEDED) else Color(0xFF141414)
    val dividerColor = if (isDark) Color(0xFF333333) else Color(0xFFDCD7CC)
    val quoteBarColor = if (isDark) Color(0xFF666666) else Color(0xFF9E988D)
    val imageColorFilter = if (invertMode == InvertMode.Always) NegativeColorFilter else null
    val activeFontFamily = when (epubFontFamily.lowercase()) {
        "sans", "sansserif", "sans-serif" -> FontFamily.SansSerif
        "mono", "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.Serif
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val effectivePadding = contentPadding ?: PaddingValues(
        start = insets.calculateStartPadding(layoutDirection).coerceAtLeast(20.dp),
        top = (insets.calculateTopPadding() + 16.dp).coerceAtLeast(28.dp),
        end = insets.calculateEndPadding(layoutDirection).coerceAtLeast(20.dp),
        bottom = (insets.calculateBottomPadding() + 24.dp).coerceAtLeast(36.dp)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackground)
            .padding(effectivePadding)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                .weight(1f, fill = false),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = block.url,
                                imageLoader = imageLoader ?: LocalContext.current.imageLoader,
                                contentDescription = block.alt,
                                contentScale = ContentScale.Fit,
                                colorFilter = imageColorFilter,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
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
                                lineHeight = (fontSizeSp * headingScale * 1.35f).sp,
                                modifier = Modifier.padding(vertical = 4.dp)
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
                                    lineHeight = (fontSizeSp * 1.45f).sp
                                )
                            }
                        } else {
                            Text(
                                text = block.text,
                                fontSize = fontSizeSp.sp,
                                fontFamily = activeFontFamily,
                                color = textColor,
                                lineHeight = (fontSizeSp * 1.45f).sp
                            )
                        }
                    }
                }
            }
        }
    }
}
