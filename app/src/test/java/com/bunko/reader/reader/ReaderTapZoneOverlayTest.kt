package com.bunko.reader.reader

import com.bunko.reader.ReaderNavigationMode
import com.bunko.reader.ReaderTappingInvertMode
import com.bunko.reader.reader.internal.TapZoneKind
import com.bunko.reader.reader.internal.tapZoneRegions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTapZoneOverlayTest {
    private fun kindsOf(
        mode: ReaderNavigationMode,
        invert: ReaderTappingInvertMode = ReaderTappingInvertMode.None,
        rtl: Boolean = false
    ): Map<TapZoneKind, Int> =
        tapZoneRegions(mode, invert, rtl).groupingBy { it.kind }.eachCount()

    @Test
    fun disabledShowsNothing() {
        assertTrue(
            tapZoneRegions(ReaderNavigationMode.Disabled).isEmpty()
        )
    }

    @Test
    fun defaultShowsPrevMenuNextThirds() {
        val regions = tapZoneRegions(ReaderNavigationMode.Default)
        assertEquals(3, regions.size)
        assertEquals(TapZoneKind.Prev, regions[0].kind)
        assertEquals(0f, regions[0].left)
        assertEquals(0.33f, regions[0].right, 0.001f)
        assertEquals(TapZoneKind.Menu, regions[1].kind)
        assertEquals(TapZoneKind.Next, regions[2].kind)
        assertEquals(0.66f, regions[2].left, 0.001f)
        assertEquals(1f, regions[2].right)
    }

    @Test
    fun defaultMirrorsSidesForRtl() {
        val regions = tapZoneRegions(ReaderNavigationMode.Default, rightToLeft = true)
        assertEquals(TapZoneKind.Next, regions[0].kind)
        assertEquals(TapZoneKind.Menu, regions[1].kind)
        assertEquals(TapZoneKind.Prev, regions[2].kind)
    }

    @Test
    fun lShapedMatchesHitTesting() {
        val counts = kindsOf(ReaderNavigationMode.LShaped)
        assertEquals(2, counts[TapZoneKind.Prev])
        assertEquals(1, counts[TapZoneKind.Menu])
        assertEquals(2, counts[TapZoneKind.Next])
        // Top strip is Prev, bottom strip is Next (full width).
        val top = tapZoneRegions(ReaderNavigationMode.LShaped)
            .first { it.top == 0f }
        assertEquals(TapZoneKind.Prev, top.kind)
        assertEquals(1f, top.right)
        val bottom = tapZoneRegions(ReaderNavigationMode.LShaped)
            .first { it.bottom == 1f }
        assertEquals(TapZoneKind.Next, bottom.kind)
        assertEquals(0f, bottom.left)
    }

    @Test
    fun kindlishShowsMenuTopStrip() {
        val regions = tapZoneRegions(ReaderNavigationMode.Kindlish)
        assertEquals(3, regions.size)
        val top = regions.first { it.top == 0f }
        assertEquals(TapZoneKind.Menu, top.kind)
        assertEquals(1f, top.right)
    }

    @Test
    fun edgeShowsNextSidesAndPrevBottom() {
        val regions = tapZoneRegions(ReaderNavigationMode.Edge)
        assertEquals(4, regions.size)
        assertEquals(3, regions.count { it.kind == TapZoneKind.Next || it.kind == TapZoneKind.Prev })
        val prev = regions.first { it.kind == TapZoneKind.Prev }
        assertEquals(0.33f, prev.left, 0.001f)
        assertEquals(0.66f, prev.top, 0.001f)
    }

    @Test
    fun horizontalInvertMirrorsLikeMihon() {
        val regions = tapZoneRegions(
            ReaderNavigationMode.Default,
            ReaderTappingInvertMode.Horizontal
        )
        assertEquals(TapZoneKind.Prev, regions[0].kind)
        assertEquals(0.67f, regions[0].left, 0.001f)
        assertEquals(TapZoneKind.Next, regions[2].kind)
        assertEquals(0.34f, regions[2].right, 0.001f)
    }

    @Test
    fun verticalInvertFlipsTopAndBottom() {
        val regions = tapZoneRegions(
            ReaderNavigationMode.LShaped,
            ReaderTappingInvertMode.Vertical
        )
        val top = regions.first { it.top == 0f }
        assertEquals(TapZoneKind.Next, top.kind)
        val bottom = regions.first { it.bottom == 1f }
        assertEquals(TapZoneKind.Prev, bottom.kind)
    }
}
