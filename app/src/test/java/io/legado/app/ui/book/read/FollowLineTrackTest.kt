package io.legado.app.ui.book.read

import org.junit.Assert.*
import org.junit.Test

class FollowLineTrackTest {
    private fun line(position: Int, page: Int, top: Float, chapter: Int = 1) =
        NativeFollowLine(position, top, top + 30f, chapter = chapter, page = page)
    private val leftTop = line(0, 0, 100f)
    private val leftBottom = line(100, 0, 200f)
    private val rightTop = line(200, 1, 100f)
    private val rightBottom = line(300, 1, 200f)
    private val lines = listOf(leftTop, leftBottom, rightTop, rightBottom)

    @Test fun rightColumnHasItsOwnAnchor() {
        val track = FollowLineTrack(lines)
        assertEquals(rightTop, track.target(track.anchor(rightTop)))
    }
    @Test fun draggingBelowLeftPageEntersRightPageWithoutTurningSpread() {
        val track = FollowLineTrack(lines)
        val y = track.anchor(leftBottom) + 50f
        assertEquals(0, track.edge(y))
        assertEquals(rightTop, track.target(y))
    }
    @Test fun draggingAboveRightPageReturnsToLeftPage() {
        val track = FollowLineTrack(lines)
        assertEquals(leftBottom, track.target(track.anchor(rightTop) - 50f))
    }
    @Test fun onlyEndOfBothColumnsRequestsNextSpread() {
        val track = FollowLineTrack(lines)
        assertEquals(0, track.edge(track.anchor(rightBottom)))
        assertEquals(1, track.edge(track.end + 1))
        assertEquals(-1, track.edge(track.start - 1))
    }
    @Test fun chapterBoundaryMayResetTextPosition() {
        val next = line(0, 0, 100f, chapter = 2)
        val track = FollowLineTrack(listOf(leftBottom, next))
        assertEquals(next, track.target(track.anchor(next)))
    }
    @Test fun emptyPageCannotRequestInfiniteTurns() {
        val track = FollowLineTrack(emptyList())
        assertEquals(0, track.edge(100f))
        assertNull(track.target(100f))
    }
    @Test fun guideYUsesPhysicalRightColumnCoordinates() {
        val track = FollowLineTrack(lines)
        assertEquals(115f, track.physicalY(track.anchor(rightTop)), 0.01f)
    }
    @Test fun guideRemainsInsideSelectedLineWhenBetweenRows() {
        val track = FollowLineTrack(lines)
        val y = track.anchor(leftTop) + 30f
        val target = track.target(y)!!
        assertTrue(track.physicalY(y) in target.top..target.bottom)
    }
}
