package uk.codecymru.drawbox.controller

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.codecymru.drawbox.model.CanvasTool
import uk.codecymru.drawbox.util.combineStates
import uk.codecymru.drawbox.util.mapState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class BitmapDrawController(private val fillScope: CoroutineScope? = null): DrawController {
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

    override val canUndo = _actions.mapState { it.isNotEmpty() }
    override val canRedo = _undoneActions.mapState { it.isNotEmpty() }

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
        if (size.width > 0 && size.height > 0) {
            state.value = DrawBoxConnectionState.Connected(size = size)

            val newBitmap = ImageBitmap(size.width, size.height, ImageBitmapConfig.Argb8888)
            internalBitmap = newBitmap
            internalCanvas = Canvas(newBitmap)
            redrawHistory()
            deleteCroppedPoints()
        }
    }

    override fun open(image: ImageBitmap) {
        reset()
        openedImage.value = image

        val canvas = internalCanvas ?: return
        val size = (state.value as? DrawBoxConnectionState.Connected)?.size ?: IntSize(1, 1)
        canvas.drawImageRect(
            image = image,
            srcOffset = IntOffset.Zero,
            dstSize = size,
            paint = Paint()
        )
    }

    override fun reset() { // TODO or could reset be an action of its own, to be undone and redone??
        _actions.value = emptyList()
        _undoneActions.value = emptyList()
        redrawHistory()
    }

    private fun deleteCroppedPoints() {
        if (state.value !is DrawBoxConnectionState.Connected) return

        val size = (state.value as DrawBoxConnectionState.Connected).size
        val maxWidth = size.width.toFloat()
        val maxHeight = size.height.toFloat()

        _actions.value = _actions.value.map { action ->
            when (action) {
                is DrawAction.Path -> {
                    action.copy(
                        points = action.points.filter { (from, to) ->
                            from.x <= maxWidth && from.y <= maxHeight && to.x <= maxWidth && to.y <= maxHeight
                        }
                    )
                }

                is DrawAction.Fill -> {
                    action.copy(
                        points = action.points.filter {
                            it.x <= maxWidth && it.y <= maxHeight
                        }
                    )
                }

                is DrawAction.Spray -> {
                    action.copy(
                        points = action.points.filter {
                            it.x <= maxWidth && it.y <= maxHeight
                        }
                    )
                }

                is DrawAction.Shape -> {
                    action // Let us not bother with shape, would be too much work for very little gain
                }
            }
        }.filter { it.points.isNotEmpty() }
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
            val paint = PaintOptions(
                color.value,
                strokeWidth.value,
                opacity.value,
                canvasTool.value
            )

            val action = when (canvasTool.value){
                CanvasTool.BRUSH, CanvasTool.ERASER -> DrawAction.Path(
                    points = _currentAction.value.toList(),
                    paintOptions = paint
                )
                CanvasTool.SPRAY_CAN -> drawSpraySegment(_currentAction.value.toList().map { it.first })
                CanvasTool.SHAPE_LINE, CanvasTool.SHAPE_RECT, CanvasTool.SHAPE_CIRCLE -> {
                    DrawAction.Shape(
                        start = _currentAction.value.first().first,
                        end = _currentAction.value.first().second,
                        shapeType = canvasTool.value,
                        paintOptions = paint
                    )
                }
                else -> DrawAction.Fill(points = emptyList(), color = Color.Black) // TODO not the best way of doing this
            }

            applyDrawActionToInternalCanvas(action)
            _actions.value += action
        }
        lastPoint = null
        _currentAction.value = emptyList()
    }

    override fun onTap(offset: Offset) {
        if (!enabled.value) return

        when (canvasTool.value){
            CanvasTool.SHAPE_LINE, CanvasTool.SHAPE_RECT, CanvasTool.SHAPE_CIRCLE -> return
            else -> {
                drawSegment(offset, offset)
                onDragEnd()
            }
        }
    }

    /////////////////////////////
    // PRIVATE DRAWING METHODS //
    /////////////////////////////

    private fun applyDrawActionToInternalCanvas(action: DrawAction){
        val canvas = internalCanvas ?: return

        when (action) {
            is DrawAction.Path -> {
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
            is DrawAction.Fill -> {
                canvas.drawPoints(
                    pointMode = PointMode.Points,
                    points = action.points,
                    paint = Paint().apply {
                        color = action.color
                        strokeWidth = 1f
                        blendMode = BlendMode.SrcOver
                        isAntiAlias = false
                    }
                )
            }
            is DrawAction.Spray -> {
                val paint = createPaint(
                    action.paintOptions.color,
                    action.paintOptions.strokeWidth,
                    action.paintOptions.opacity,
                    action.paintOptions.tool
                )

                canvas.drawPoints(
                    pointMode = PointMode.Points,
                    points = action.points,
                    paint = paint
                )
            }
            is DrawAction.Shape -> {
                val paint = createPaint(
                    action.paintOptions.color,
                    action.paintOptions.strokeWidth,
                    action.paintOptions.opacity,
                    action.paintOptions.tool
                )

                when (action.shapeType){
                    CanvasTool.SHAPE_LINE -> {
                        canvas.drawLine(action.start, action.end, paint)
                    }

                    CanvasTool.SHAPE_RECT -> {
                        canvas.drawRect(
                            left = minOf(action.start.x, action.end.x),
                            top = minOf(action.start.y, action.end.y),
                            right = maxOf(action.start.x, action.end.x),
                            bottom = maxOf(action.start.y, action.end.y),
                            paint = paint
                        )
                    }

                    CanvasTool.SHAPE_CIRCLE -> {
                        canvas.drawOval(
                            left = minOf(action.start.x, action.end.x),
                            top = minOf(action.start.y, action.end.y),
                            right = maxOf(action.start.x, action.end.x),
                            bottom = maxOf(action.start.y, action.end.y),
                            paint = paint
                        )
                    }

                    else -> {}
                }
            }
        }
    }

    private fun drawSegment(from: Offset, to: Offset) {
        if (state.value !is DrawBoxConnectionState.Connected) return

        val start = if (_currentAction.value.isEmpty()) lastPoint ?: from else _currentAction.value.first().first

        when (canvasTool.value){
            CanvasTool.BRUSH, CanvasTool.ERASER, CanvasTool.SPRAY_CAN -> _currentAction.value += from to to // TODO WOULD THIS BE BETTER IF currentAction TOOK A REAL ACTION RATHER THAN WHAT IT DOES NOW?
            CanvasTool.FILL -> fillScope?.launch { _actions.value += fillSegment(to, color.value) }
            CanvasTool.EYEDROPPER -> fillScope?.launch { setColorFromPixel(to) }
            CanvasTool.SHAPE_LINE, CanvasTool.SHAPE_RECT, CanvasTool.SHAPE_CIRCLE -> _currentAction.value = listOf(start to to)
        }
    }

    private fun drawSpraySegment(points: List<Offset>): DrawAction.Spray {
        val radius = strokeWidth.value
        val density = (radius * 2).toInt()
        val sprayPoints = mutableListOf<Offset>()

        points.forEach { (x, y) ->
            repeat(density) {
                val angle = (0..360).random().toDouble() * (PI / 180)
                val distance = (0..100).random().toFloat() / 100f * radius

                val dX = x + (cos(angle) * distance).toFloat()
                val dY = y + (sin(angle) * distance).toFloat()

                sprayPoints.add(Offset(dX, dY))
            }
        }

        val action = DrawAction.Spray(
            points = sprayPoints,
            paintOptions = PaintOptions(
                color.value,
                1f,
                opacity.value,
                canvasTool.value
            )
        )

        return action
    }

    private suspend fun fillSegment(startOffset: Offset, targetColor: Color): DrawAction.Fill {
        val bitmap = getBitmap(null, DrawBoxSubscription.DynamicUpdate).first()
        val action = fillSegment(bitmap, startOffset, targetColor)
        applyDrawActionToInternalCanvas(action)

        return action
    }

    private fun getPixelArray(bitmap: ImageBitmap): IntArray{
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.readPixels(pixels, startX = 0, startY = 0, width = bitmap.width, height = bitmap.height)

        return pixels
    }

    private fun fillSegment(bitmap: ImageBitmap, startOffset: Offset, targetColor: Color): DrawAction.Fill {
        val x = startOffset.x.toInt().coerceIn(0, bitmap.width - 1)
        val y = startOffset.y.toInt().coerceIn(0, bitmap.height - 1)

        val pixels = getPixelArray(bitmap)

        val startColor = pixels[y * bitmap.width + x]
        if (startColor == targetColor.toArgb()) return DrawAction.Fill(emptyList(), targetColor)

        val fillPoints = mutableListOf<Offset>()

        // Breadth-First Search
        val queue = ArrayDeque<IntOffset>()
        queue.add(IntOffset(x, y))

        val visited = BooleanArray(bitmap.width * bitmap.height)

        while (queue.isNotEmpty()) { // TODO following BFS has quite a lot of boxing
            val curr = queue.removeFirst()
            val cx = curr.x
            val cy = curr.y

            if (cx !in 0 until bitmap.width || cy !in 0 until bitmap.height) continue
            val index = cy * bitmap.width + cx

            if (!visited[index] && pixels[index] == startColor) {
                visited[index] = true
                fillPoints.add(Offset(cx.toFloat(), cy.toFloat()))

                queue.add(IntOffset(cx + 1, cy))
                queue.add(IntOffset(cx - 1, cy))
                queue.add(IntOffset(cx, cy + 1))
                queue.add(IntOffset(cx, cy - 1))
            }
        }

        return DrawAction.Fill(fillPoints, targetColor)
    }

    private suspend fun setColorFromPixel(offset: Offset) {
        val bitmap = getBitmap(null, DrawBoxSubscription.DynamicUpdate).first()

        val x = offset.x.toInt().coerceIn(0, bitmap.width - 1)
        val y = offset.y.toInt().coerceIn(0, bitmap.height - 1)

        val pixels = getPixelArray(bitmap)
        color.value = Color(pixels[y * bitmap.width + x])
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

    private fun redrawHistory(){
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

        _actions.value.forEach { action ->
            applyDrawActionToInternalCanvas(action)
        }
    }

    ///////////////////////////
    // SUBSCRIPTION & BITMAP //
    ///////////////////////////

    // TODO perhaps this whole method should use an IntOffset?(?)
    override fun getBitmap(size: Int?, subscription: DrawBoxSubscription): StateFlow<ImageBitmap> {
        val canvasSize = when {
            size != null -> IntSize(size, size)
            state.value is DrawBoxConnectionState.Connected -> (state.value as DrawBoxConnectionState.Connected).size
            else -> IntSize(1, 1)
        }

        return combineStates(_actions, _currentAction) { actions, currentAction ->
            val bitmap = ImageBitmap(canvasSize.width, canvasSize.height, ImageBitmapConfig.Argb8888)
            val canvas = Canvas(bitmap)

            internalBitmap?.let {
                canvas.drawImage(it, Offset.Zero, Paint())
            }

            if (subscription is DrawBoxSubscription.DynamicUpdate){
                val paint = createPaint(
                    color.value,
                    strokeWidth.value,
                    opacity.value,
                    canvasTool.value
                )

                when (canvasTool.value){
                    CanvasTool.SHAPE_RECT -> {
                        currentAction.forEach { (from, to) ->
                            canvas.drawRect(
                                minOf(from.x, to.x),
                                minOf(from.y, to.y),
                                maxOf(from.x, to.x),
                                maxOf(from.y, to.y),
                                paint
                            )
                        }
                    }
                    CanvasTool.SHAPE_CIRCLE -> {
                        currentAction.forEach { (from, to) ->
                            canvas.drawOval(
                                minOf(from.x, to.x),
                                minOf(from.y, to.y),
                                maxOf(from.x, to.x),
                                maxOf(from.y, to.y),
                                paint
                            )
                        }
                    }

                    else -> {
                        currentAction.forEach { (from, to) ->
                            canvas.drawLine(from, to, paint)
                        }
                    }
                }
            }

            bitmap
        }
    }
}

// TODO once we're happy with this implementation, move these somewhere
private sealed interface DrawAction {
    val points: List<Any>

    data class Path(
        override val points: List<Pair<Offset, Offset>>, // TODO would it be better to switch to a path for this?
        val paintOptions: PaintOptions
    ) : DrawAction

    data class Fill(
        override val points: List<Offset>,
        val color: Color
    ) : DrawAction

    data class Spray(
        override val points: List<Offset>,
        val paintOptions: PaintOptions
    ) : DrawAction

    data class Shape(
        val start: Offset,
        val end: Offset,
        val shapeType: CanvasTool,
        val paintOptions: PaintOptions
    ) : DrawAction {
        override val points: List<Offset> = listOf(start, end)
    }
}

private data class PaintOptions(
    val color: Color,
    val strokeWidth: Float,
    val opacity: Float,
    val tool: CanvasTool
)

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