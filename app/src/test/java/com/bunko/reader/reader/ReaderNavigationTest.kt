package com.bunko.reader.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import com.bunko.reader.ReaderImageScaleType
import com.bunko.reader.ReaderNavigationMode
import com.bunko.reader.ReaderTappingInvertMode
import com.bunko.reader.reader.internal.ReaderTapAction
import com.bunko.reader.reader.internal.readerContentScale
import com.bunko.reader.reader.internal.resolveTapAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderNavigationTest {

    private val viewport = IntSize(300, 600)

    @Test
    fun defaultNavigationDividesHorizontallyIntoThreeZones() {
        // Left column
        assertEquals(
            ReaderTapAction.MoveLeft,
            resolveTapAction(Offset(50f, 300f), viewport, ReaderNavigationMode.Default, ReaderTappingInvertMode.None)
        )
        // Center column
        assertEquals(
            ReaderTapAction.Menu,
            resolveTapAction(Offset(150f, 300f), viewport, ReaderNavigationMode.Default, ReaderTappingInvertMode.None)
        )
        // Right column
        assertEquals(
            ReaderTapAction.MoveRight,
            resolveTapAction(Offset(250f, 300f), viewport, ReaderNavigationMode.Default, ReaderTappingInvertMode.None)
        )
    }

    @Test
    fun lShapedNavigationRoutesCornersCorrectly() {
        // Top row -> Prev
        assertEquals(
            ReaderTapAction.Prev,
            resolveTapAction(Offset(150f, 50f), viewport, ReaderNavigationMode.LShaped, ReaderTappingInvertMode.None)
        )
        // Bottom row -> Next
        assertEquals(
            ReaderTapAction.Next,
            resolveTapAction(Offset(150f, 550f), viewport, ReaderNavigationMode.LShaped, ReaderTappingInvertMode.None)
        )
        // Middle left -> Prev
        assertEquals(
            ReaderTapAction.Prev,
            resolveTapAction(Offset(50f, 300f), viewport, ReaderNavigationMode.LShaped, ReaderTappingInvertMode.None)
        )
        // Middle right -> Next
        assertEquals(
            ReaderTapAction.Next,
            resolveTapAction(Offset(250f, 300f), viewport, ReaderNavigationMode.LShaped, ReaderTappingInvertMode.None)
        )
        // Center -> Menu
        assertEquals(
            ReaderTapAction.Menu,
            resolveTapAction(Offset(150f, 300f), viewport, ReaderNavigationMode.LShaped, ReaderTappingInvertMode.None)
        )
    }

    @Test
    fun kindlishNavigationRoutesCorrectly() {
        // Top 1/3 -> Menu
        assertEquals(
            ReaderTapAction.Menu,
            resolveTapAction(Offset(150f, 50f), viewport, ReaderNavigationMode.Kindlish, ReaderTappingInvertMode.None)
        )
        // Bottom left -> Prev
        assertEquals(
            ReaderTapAction.Prev,
            resolveTapAction(Offset(50f, 400f), viewport, ReaderNavigationMode.Kindlish, ReaderTappingInvertMode.None)
        )
        // Bottom right -> Next
        assertEquals(
            ReaderTapAction.Next,
            resolveTapAction(Offset(200f, 400f), viewport, ReaderNavigationMode.Kindlish, ReaderTappingInvertMode.None)
        )
    }

    @Test
    fun edgeNavigationRoutesCorrectly() {
        // Left column -> Next
        assertEquals(
            ReaderTapAction.Next,
            resolveTapAction(Offset(50f, 300f), viewport, ReaderNavigationMode.Edge, ReaderTappingInvertMode.None)
        )
        // Right column -> Next
        assertEquals(
            ReaderTapAction.Next,
            resolveTapAction(Offset(250f, 300f), viewport, ReaderNavigationMode.Edge, ReaderTappingInvertMode.None)
        )
        // Center bottom -> Prev
        assertEquals(
            ReaderTapAction.Prev,
            resolveTapAction(Offset(150f, 500f), viewport, ReaderNavigationMode.Edge, ReaderTappingInvertMode.None)
        )
        // Center top -> Menu
        assertEquals(
            ReaderTapAction.Menu,
            resolveTapAction(Offset(150f, 200f), viewport, ReaderNavigationMode.Edge, ReaderTappingInvertMode.None)
        )
    }

    @Test
    fun disabledNavigationAlwaysReturnsMenu() {
        assertEquals(
            ReaderTapAction.Menu,
            resolveTapAction(Offset(10f, 10f), viewport, ReaderNavigationMode.Disabled, ReaderTappingInvertMode.None)
        )
        assertEquals(
            ReaderTapAction.Menu,
            resolveTapAction(Offset(280f, 580f), viewport, ReaderNavigationMode.Disabled, ReaderTappingInvertMode.None)
        )
    }

    @Test
    fun horizontalInversionFlipsLeftAndRight() {
        // Left column without invert is MoveLeft
        assertEquals(
            ReaderTapAction.MoveLeft,
            resolveTapAction(Offset(50f, 300f), viewport, ReaderNavigationMode.Default, ReaderTappingInvertMode.None)
        )
        // Left column with horizontal invert becomes MoveRight
        assertEquals(
            ReaderTapAction.MoveRight,
            resolveTapAction(Offset(50f, 300f), viewport, ReaderNavigationMode.Default, ReaderTappingInvertMode.Horizontal)
        )
        // Right column with horizontal invert becomes MoveLeft
        assertEquals(
            ReaderTapAction.MoveLeft,
            resolveTapAction(Offset(250f, 300f), viewport, ReaderNavigationMode.Default, ReaderTappingInvertMode.Horizontal)
        )
    }

    @Test
    fun verticalInversionFlipsTopAndBottom() {
        // L-shaped: Top is Prev without invert
        assertEquals(
            ReaderTapAction.Prev,
            resolveTapAction(Offset(150f, 50f), viewport, ReaderNavigationMode.LShaped, ReaderTappingInvertMode.None)
        )
        // L-shaped: Top becomes Next with vertical invert
        assertEquals(
            ReaderTapAction.Next,
            resolveTapAction(Offset(150f, 50f), viewport, ReaderNavigationMode.LShaped, ReaderTappingInvertMode.Vertical)
        )
    }

    @Test
    fun readerContentScaleMapsAllScaleTypes() {
        assertEquals(ContentScale.Fit, readerContentScale(ReaderImageScaleType.FitScreen))
        assertEquals(ContentScale.FillBounds, readerContentScale(ReaderImageScaleType.Stretch))
        assertEquals(ContentScale.FillWidth, readerContentScale(ReaderImageScaleType.FitWidth))
        assertEquals(ContentScale.FillHeight, readerContentScale(ReaderImageScaleType.FitHeight))
        assertEquals(ContentScale.None, readerContentScale(ReaderImageScaleType.OriginalSize))
        assertTrue(readerContentScale(ReaderImageScaleType.SmartFit) is com.bunko.reader.reader.internal.SmartFitContentScale)
    }
}

