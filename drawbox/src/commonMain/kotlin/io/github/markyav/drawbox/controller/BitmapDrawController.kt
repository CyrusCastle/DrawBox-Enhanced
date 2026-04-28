package io.github.markyav.drawbox.controller

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.IntSize
import io.github.markyav.drawbox.model.CanvasTool
import io.github.markyav.drawbox.util.combineStates
import io.github.markyav.drawbox.util.mapState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BitmapDrawController: DrawController {
    //////////////////////
    // CONNECTION STATE //
    //////////////////////
    private var state: MutableStateFlow<DrawBoxConnectionState> = MutableStateFlow(DrawBoxConnectionState.Disconnected)

    ////////////////
    // MAIN STATE //
    ////////////////
    private var internalBitmap: ImageBitmap? = null
    private var internalCanvas: Canvas? = null
    private val _actions = MutableStateFlow<List<DrawAction>>(emptyList())
    private val _undoneActions = MutableStateFlow<List<DrawAction>>(emptyList())
    private val _currentAction = MutableStateFlow<List<Pair<Offset, Offset>>>(emptyList())
    private var lastPoint: Offset? = null

    ///////////////////
    // DERIVED STATE //
    ///////////////////
    override val undoCount = _actions.mapState { it.size }
    override val redoCount = _undoneActions.mapState { it.size }

    ////////////////////
    // BRUSH SETTINGS //
    ////////////////////
    override var canvasTool: MutableStateFlow<CanvasTool> = MutableStateFlow(CanvasTool.BRUSH)
    override var opacity: MutableStateFlow<Float> = MutableStateFlow(1f)
    override var strokeWidth: MutableStateFlow<Float> = MutableStateFlow(10f)
    override var color: MutableStateFlow<Color> = MutableStateFlow(Color.Red)

    ////////////////////
    // OTHER SETTINGS //
    ////////////////////
    override var enabled: MutableStateFlow<Boolean> = MutableStateFlow(true)
    override var canvasOpacity: MutableStateFlow<Float> = MutableStateFlow(1f)

    /////////////////////////
    // BACKGROUND SETTINGS //
    /////////////////////////
    override val openedImage: MutableStateFlow<ImageBitmap?> = MutableStateFlow(null)
    override var background: MutableStateFlow<DrawBoxBackground> = MutableStateFlow(DrawBoxBackground.NoBackground)

    // *********** //
    // * METHODS * //
    // *********** //

    ///////////////////
    // INIT & RESET //
    ///////////////////

    override fun connectToDrawBox(size: IntSize) {
        if (size.width > 0 && size.height > 0 && size.width == size.height) { // TODO does this really need to be a box like that? hm
            state.value = DrawBoxConnectionState.Connected(size = size.width)

            val newBitmap = ImageBitmap(size.width, size.height, ImageBitmapConfig.Argb8888)
            internalBitmap = newBitmap
            internalCanvas = Canvas(newBitmap)
            redrawHistory()
        }
    }

    override fun open(image: ImageBitmap) {
        reset()
        openedImage.value = image
    }

    override fun reset() { // TODO or could reset be an action of its own, to be undone and redone??
        _actions.value = emptyList()
        _undoneActions.value = emptyList()
        redrawHistory()
    }

    ///////////////////////////
    // HANDLING INTERACTIONS //
    ///////////////////////////

    override fun onDragStart(offset: Offset) {
        if (!enabled.value) return

        lastPoint = offset
        _undoneActions.value = emptyList()
        _currentAction.value = emptyList()
        drawSegment(offset, offset)
    }

    override fun onDrag(offset: Offset) {
        if (!enabled.value) return

        val start = lastPoint ?: offset
        drawSegment(start, offset)
        lastPoint = offset
    }

    override fun onDragEnd() {
        if (!enabled.value) return

        if (_currentAction.value.isNotEmpty()) {
            val action = DrawAction(
                points = _currentAction.value.toList(),
                paintOptions = PaintOptions(
                    color.value,
                    strokeWidth.value,
                    opacity.value,
                    canvasTool.value
            ))
            _actions.value += action
        }
        lastPoint = null
        _currentAction.value = emptyList()
    }

    override fun onTap(offset: Offset) {
        if (!enabled.value) return

        drawSegment(offset, offset)
        onDragEnd()
    }

    /////////////////////////////
    // PRIVATE DRAWING METHODS //
    /////////////////////////////

    private fun drawSegment(from: Offset, to: Offset) {
        if (state.value !is DrawBoxConnectionState.Connected) return

        _currentAction.value += from to to
    }

    private fun createPaint(c: Color, sw: Float, o: Float, tool: CanvasTool): Paint {
        return Paint().apply {
            strokeWidth = sw
            style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
            strokeJoin = StrokeJoin.Round
            if (tool == CanvasTool.ERASER) {
                blendMode = BlendMode.Clear
            } else {
                color = c
                alpha = o
                blendMode = BlendMode.SrcOver
            }
        }
    }

    private fun redrawHistory() {
        if (state.value !is DrawBoxConnectionState.Connected) return

        val canvas = internalCanvas ?: return
        val bitmap = internalBitmap ?: return

        // Clear the bitmap
        canvas.drawRect(
            0f,
            0f,
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            Paint().apply { blendMode = BlendMode.Clear }
        )

        // Draw all saved actions
        _actions.value.forEach { action ->
            val paint = createPaint(
                action.paintOptions.color,
                action.paintOptions.strokeWidth,
                action.paintOptions.opacity,
                action.paintOptions.tool
            )
            action.points.forEach { (from, to) ->
                canvas.drawLine(from, to, paint)
            }
        }
    }

    /////////////////
    // UNDO & REDO //
    /////////////////

    override fun undo() {
        if (_actions.value.isNotEmpty()) {
            val undone = _actions.value[_actions.value.lastIndex]

            _undoneActions.value += undone
            _actions.value -= undone
            redrawHistory()
        }
    }

    override fun redo() {
        if (_undoneActions.value.isNotEmpty()) {
            val undone = _undoneActions.value[_undoneActions.value.lastIndex]

            _actions.value += undone
            _undoneActions.value -= undone
            redrawHistory()
        }
    }

    ///////////////////////////
    // SUBSCRIPTION & BITMAP //
    ///////////////////////////

    override fun getBitmap(size: Int?, subscription: DrawBoxSubscription): StateFlow<ImageBitmap> {
        val width = size ?: (state.value as? DrawBoxConnectionState.Connected)?.size ?: 1

        return combineStates(_actions, _currentAction) { actions, currentAction ->
            val bitmap = ImageBitmap(width, width, ImageBitmapConfig.Argb8888)
            val canvas = Canvas(bitmap)

            actions.forEach { action ->
                val paint = createPaint(
                    action.paintOptions.color,
                    action.paintOptions.strokeWidth,
                    action.paintOptions.opacity,
                    action.paintOptions.tool
                )
                action.points.forEach { (from, to) ->
                    canvas.drawLine(from, to, paint)
                }
            }

            val paint = createPaint(
                color.value,
                strokeWidth.value,
                opacity.value,
                canvasTool.value
            )

            currentAction.forEach { (from, to) ->
                canvas.drawLine(from, to, paint)
            }

            bitmap
        }
    }
}

// TODO are these two classes necessary? If no, remove. If yes, then why are they used in such a limited capacity
private data class DrawAction(
    val points: List<Pair<Offset, Offset>>,
    val paintOptions: PaintOptions
)

private data class PaintOptions(
    val color: Color,
    val strokeWidth: Float,
    val opacity: Float,
    val tool: CanvasTool
)