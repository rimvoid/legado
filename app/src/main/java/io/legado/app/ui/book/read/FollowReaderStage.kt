package io.legado.app.ui.book.read

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import kotlin.math.abs

/** Screen coordinates of an existing reader line; never lays out a second copy of the book. */
data class NativeFollowLine(
    val position: Int, val top: Float, val bottom: Float,
    val left: Float = 0f, val right: Float = 0f,
    val chapter: Int = 0, val page: Int = 0,
    val paragraph: Int = 0, val speaking: Boolean = false,
    val columnLeft: Float = 0f, val columnRight: Float = 0f
)

@Composable
internal fun FollowReaderStage(
    state: ReadAloudPlayerPanel.PlayerUiState,
    colors: PlayerColors,
    nativeReader: ReadAloudPlayerPanel.CallBack?,
    onTogglePlayback: () -> Unit,
    onSeek: (Int) -> Unit,
    onDragging: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { NativeFollowTouchView(it) },
        update = {
            it.reader = nativeReader
            it.updatePlayback(state)
            it.showHighlight = state.followHighlight
            it.invalidate()
            it.toggle = onTogglePlayback
            it.seek = onSeek
            it.dragChanged = onDragging
            it.barPaint.color = 0x88555555.toInt()
        }
    )
}

private class NativeFollowTouchView(context: Context) : View(context) {
    var reader: ReadAloudPlayerPanel.CallBack? = null
    var playing = false
    var showHighlight = true
    var toggle: () -> Unit = {}
    var seek: (Int) -> Unit = {}
    var dragChanged: (Boolean) -> Unit = {}
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3 * resources.displayMetrics.density }
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val pointers = FollowPointerTracker()
    private var cursorY = 0f
    private var dragging = false
    private var blocked = false
    private var resumeOnCancel = false
    private var originalPosition = 0
    private var selected: NativeFollowLine? = null
    private var originalLine: NativeFollowLine? = null
    private var waitingForPlayback = false
    private var observedPause = false
    private val handoffTimeout = Runnable {
        waitingForPlayback = false
        dragChanged(false)
    }

    fun updatePlayback(state: ReadAloudPlayerPanel.PlayerUiState) {
        playing = state.playing
        if (!playing) observedPause = true
        if (waitingForPlayback && playing && observedPause) {
            waitingForPlayback = false
            removeCallbacks(handoffTimeout)
            dragChanged(false)
        }
    }

    private fun waitForPlayback() {
        waitingForPlayback = true
        removeCallbacks(handoffTimeout)
        postDelayed(handoffTimeout, 4000)
    }

    init { setWillNotDraw(false) }

    override fun onDraw(canvas: Canvas) {
        if (!showHighlight || !dragging) return
        val location = IntArray(2)
        getLocationOnScreen(location)
        val track = FollowLineTrack(reader?.followReaderLines().orEmpty())
        val target = track.target(cursorY) ?: return
        val y = track.physicalY(cursorY) - location[1]
        canvas.drawLine(target.columnLeft - location[0], y,
            target.columnRight - location[0], y, barPaint)
    }

    private fun cancelDrag() {
        if (dragging) {
            ReadBook.durChapterPos = originalPosition
            originalLine?.let { reader?.previewFollowLine(it) }
            if (resumeOnCancel) ReadAloud.resume(context)
        }
        dragging = false
        if (resumeOnCancel) waitForPlayback() else dragChanged(false)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelDrag()
        removeCallbacks(handoffTimeout)
        waitingForPlayback = false
        dragChanged(false)
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean {
        super.performClick()
        toggle()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // getRawY(index) needs Android 10; derive all screen coordinates from pointer zero
        // to support the same stable-ID handoff on the app's older supported Android versions.
        val offsetX = event.rawX - event.getX(0)
        val offsetY = event.rawY - event.getY(0)
        val samples = (0 until event.pointerCount).map {
            FollowPointerTracker.Pointer(event.getPointerId(it), event.getX(it) + offsetX, event.getY(it) + offsetY)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                pointers.begin(samples[event.actionIndex])
                blocked = false
                observedPause = !playing
                originalPosition = ReadBook.durChapterPos
                val lines = reader?.followReaderLines().orEmpty()
                selected = lines.lastOrNull { it.chapter == ReadBook.durChapterIndex && it.position <= originalPosition }
                    ?: lines.firstOrNull()
                originalLine = selected
                cursorY = FollowLineTrack(lines).anchor(selected)
            }
            MotionEvent.ACTION_MOVE -> {
                if (blocked) return true
                val movement = pointers.move(samples)
                val dx = movement.dx
                val dy = movement.dy
                if (!dragging && abs(dx) > slop && abs(dx) > abs(dy)) { blocked = true; return true }
                if (!dragging && abs(dy) > slop && selected != null) {
                    dragging = true
                    resumeOnCancel = playing
                    dragChanged(true)
                    if (playing) ReadAloud.pause(context)
                }
                if (dragging) {
                    cursorY += movement.deltaY
                    var track = FollowLineTrack(reader?.followReaderLines().orEmpty())
                    val direction = track.edge(cursorY)
                    if (direction != 0 && reader?.turnFollowPage(direction) == true) {
                        track = FollowLineTrack(reader?.followReaderLines().orEmpty())
                        cursorY = if (direction > 0) track.start else track.end
                    }
                    selected = track.target(cursorY)
                    selected?.let { reader?.previewFollowLine(it) }
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    waitForPlayback()
                    selected?.let { if (reader != null) reader?.commitFollowLine(it) else seek(it.position) }
                    invalidate()
                } else if (!blocked && !pointers.hadMultiplePointers) performClick()
                pointers.clear()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                pointers.pointerDown(samples[event.actionIndex])
                blocked = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val liftedId = event.getPointerId(event.actionIndex)
                pointers.pointerUp(liftedId, samples.filter { it.id != liftedId })
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelDrag()
                pointers.clear()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
