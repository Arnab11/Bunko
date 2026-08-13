package li.mof.kamigura.reader

import li.mof.kamigura.ReaderReadingDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderDirectionResolverTest {
    @Test
    fun perSeriesOverrideWinsOverServerAndGlobal() {
        assertEquals(
            ReaderReadingDirection.Vertical,
            resolveReaderReadingDirection(
                globalDirection = ReaderReadingDirection.LeftToRight,
                cachedDirection = ReaderReadingDirection.Vertical,
                profileKind = 1,
                profileDirection = KavitaReadingDirectionRtl
            )
        )
    }

    @Test
    fun explicitServerProfileWinsOverVerticalGlobalFallback() {
        assertEquals(
            ReaderReadingDirection.RightToLeft,
            resolveReaderReadingDirection(
                globalDirection = ReaderReadingDirection.Vertical,
                cachedDirection = null,
                profileKind = 1,
                profileDirection = KavitaReadingDirectionRtl
            )
        )
    }

    @Test
    fun defaultServerProfileUsesGlobalDirection() {
        assertEquals(
            ReaderReadingDirection.Vertical,
            resolveReaderReadingDirection(
                globalDirection = ReaderReadingDirection.Vertical,
                cachedDirection = null,
                profileKind = KavitaReadingProfileKindDefault,
                profileDirection = KavitaReadingDirectionLtr
            )
        )
    }
}
