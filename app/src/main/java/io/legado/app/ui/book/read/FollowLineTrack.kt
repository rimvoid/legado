package io.legado.app.ui.book.read

/** Unroll a native spread in reading order; vertical coordinates repeat in the right column. */
internal class FollowLineTrack(lines: List<NativeFollowLine>) {
    private data class Slot(val line: NativeFollowLine, val top: Float, val bottom: Float)
    private val slots = buildList {
        var y = 0f
        var previous: NativeFollowLine? = null
        for (line in lines) {
            val last = previous
            if (last != null) y += if (last.chapter == line.chapter && last.page == line.page)
                (line.top - last.bottom).coerceAtLeast(0f) else 12f
            val bottom = y + (line.bottom - line.top).coerceAtLeast(1f)
            add(Slot(line, y, bottom))
            y = bottom
            previous = line
        }
    }
    val start get() = slots.firstOrNull()?.top ?: 0f
    val end get() = slots.lastOrNull()?.bottom ?: 0f
    fun anchor(line: NativeFollowLine?): Float = slots.firstOrNull {
        line != null && it.line.chapter == line.chapter && it.line.page == line.page && it.line.position == line.position
    }?.let { (it.top + it.bottom) / 2 } ?: start
    fun physicalY(y: Float): Float {
        val line = target(y) ?: return 0f
        val slot = slots.first { it.line === line }
        return line.top + (y - slot.top).coerceIn(0f, slot.bottom - slot.top)
    }
    fun edge(y: Float): Int = if (slots.isEmpty()) 0 else when {
        y < start -> -1
        y > end -> 1
        else -> 0
    }
    fun target(y: Float): NativeFollowLine? = slots.minByOrNull {
        when { y < it.top -> it.top - y; y > it.bottom -> y - it.bottom; else -> 0f }
    }?.line
}
