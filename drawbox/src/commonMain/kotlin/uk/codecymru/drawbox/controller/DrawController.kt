package uk.codecymru.drawbox.controller

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.codecymru.drawbox.model.CanvasTool
import uk.codecymru.drawbox.model.DrawAction
import uk.codecymru.drawbox.model.DrawnPath
import uk.codecymru.drawbox.model.PaintOptions
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class DrawController(private val fillScope: CoroutineScope? = null) {
    //////////////////////
    // CONNECTION STATE //
    //////////////////////
    private var state: MutableStateFlow<DrawBoxConnectionState> = MutableStateFlow(DrawBoxConnectionState.Disconnected)

    ////////////////
    // MAIN STATE //
    ////////////////
    /** The bitmap, onto which the user is drawing */
    var internalBitmap: ImageBitmap = ImageBitmap(1, 1)

    private var internalCanvas: Canvas = Canvas(internalBitmap)
    private val _actions = MutableStateFlow<List<DrawAction>>(emptyList())
    private val _undoneActions = MutableStateFlow<List<DrawAction>>(emptyList())
    internal val _currentAction = MutableStateFlow<DrawnPath>(emptyList())
    private var lastPoint: Offset? = null
    private var contentBounds: Rect = Rect.Zero
    private var maxBrushSize: Float = 0f

    ///////////////////
    // DERIVED STATE //
    ///////////////////

    /** Can we currently undo? */
    val canUndo = _actions.map { it.isNotEmpty() }

    /** Can we currently redo? */
    val canRedo = _undoneActions.map { it.isNotEmpty() }

    ////////////////////
    // BRUSH SETTINGS //
    ////////////////////

    /** What tool is currently being used (Brush, Eraser, etc.) */
    val canvasTool: MutableStateFlow<CanvasTool> = MutableStateFlow(CanvasTool.BRUSH)

    /** The current stroke width */
    val opacity: MutableStateFlow<Float> = MutableStateFlow(1f)

    /** The current stroke color */
    val strokeWidth: MutableStateFlow<Float> = MutableStateFlow(10f)

    /** The current stroke opacity */
    val color: MutableStateFlow<Color> = MutableStateFlow(Color.Red)

    ////////////////////
    // OTHER SETTINGS //
    ////////////////////
    /** Can we currently interact with this controller? */
    val enabled: MutableStateFlow<Boolean> = MutableStateFlow(true)

    /** The opacity of the canvas background */
    val canvasOpacity: MutableStateFlow<Float> = MutableStateFlow(1f)

    /////////////////////////
    // BACKGROUND SETTINGS //
    /////////////////////////
    private val _openedImage: MutableStateFlow<ImageBitmap?> = MutableStateFlow(null)

    /** An image, if there is one, which has been loaded onto the canvas (this goes IN-FRONT of any previous drawings) */
    val openedImage = _openedImage.asStateFlow()

    /** A background, perhaps, that goes BEHIND the canvas */
    val background: MutableStateFlow<DrawBoxBackground> = MutableStateFlow(DrawBoxBackground.NoBackground)

    // *********** //
    // * METHODS * //
    // *********** //

    ///////////////////
    // INIT & RESET //
    ///////////////////

    /** Initialise or update the controller with the canvas size */
    fun connectToDrawBox(size: IntSize) {
        if (size.width > 0 && size.height > 0) {
            state.value = DrawBoxConnectionState.Connected(size = size)

            val newBitmap = ImageBitmap(size.width, size.height, ImageBitmapConfig.Argb8888)
            internalBitmap = newBitmap
            internalCanvas = Canvas(newBitmap)
            redrawHistory()
            deleteCroppedPoints()
        }
    }

    /** Open a new image as a backing for the canvas */
    fun open(image: ImageBitmap) {
        reset()
        _openedImage.value = image

        val size = (state.value as? DrawBoxConnectionState.Connected)?.size ?: IntSize(1, 1)
        internalCanvas.drawImageRect( // TODO does undoing clear this opened image? Worth checking. Could add it as an action or something.
            image = image,
            srcOffset = IntOffset.Zero,
            dstSize = size,
            paint = Paint()
        )
    }

    /** Clear the canvas and history */
    fun reset() { // TODO or could reset be an action of its own, to be undone and redone??
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

    internal fun onDragStart(offset: Offset) {
        if (!enabled.value) return

        lastPoint = offset
        _undoneActions.value = emptyList()
        _currentAction.value = emptyList()
        drawSegment(offset, offset)
    }

    internal fun onDrag(offset: Offset) {
        if (!enabled.value) return

        val start = lastPoint ?: offset
        drawSegment(start, offset)
        lastPoint = offset
    }

    internal fun onDragEnd() {
        if (!enabled.value) return

        if (_currentAction.value.isNotEmpty()) {
            val paint = PaintOptions(
                color.value,
                strokeWidth.value,
                opacity.value,
                canvasTool.value
            ).createPaint()

            val action = when (canvasTool.value){
                CanvasTool.BRUSH, CanvasTool.ERASER -> DrawAction.Path(
                    points = _currentAction.value.toList(),
                    paint = paint
                )
                CanvasTool.SPRAY_CAN -> drawSpraySegment(_currentAction.value.toList().map { it.first })
                CanvasTool.SHAPE_LINE, CanvasTool.SHAPE_RECT, CanvasTool.SHAPE_CIRCLE -> {
                    DrawAction.Shape(
                        start = _currentAction.value.first().first,
                        end = _currentAction.value.first().second,
                        shapeType = canvasTool.value,
                        paint = paint
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

    internal fun onTap(offset: Offset) {
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

    private fun Rect.expandToInclude(other: Rect): Rect {
        return Rect(
            minOf(left, other.left),
            minOf(top, other.top),
            maxOf(right, other.right),
            maxOf(bottom, other.bottom)
        )
    }

    private fun applyDrawActionToInternalCanvas(action: DrawAction){
        internalCanvas.applyDrawAction(action)
        triggerRedraw()
    }

    private fun Canvas.applyDrawAction(action: DrawAction){
        val minX: Float
        val maxX: Float
        val minY: Float
        val maxY: Float

        when (action) {
            is DrawAction.Path -> {
                action.points.forEach { (from, to) ->
                    this.drawLine(from, to, action.paint)
                }

                minX = action.points.minOf { (first, second) -> minOf(first.x, second.x) }
                maxX = action.points.maxOf { (first, second) -> maxOf(first.x, second.x) }

                minY = action.points.minOf { (first, second) -> minOf(first.y, second.y) }
                maxY = action.points.maxOf { (first, second) -> maxOf(first.y, second.y) }
            }
            is DrawAction.Fill -> {
                this.drawPoints(
                    pointMode = PointMode.Points,
                    points = action.points,
                    paint = Paint().apply {
                        color = action.color
                        strokeWidth = 1f
                        blendMode = BlendMode.SrcOver
                        isAntiAlias = false
                    }
                )

                // One of the only valid early returns here. Sometimes the user might press fill and it has nowhere to go.
                if (action.points.isEmpty()){
                    return
                }

                minX = action.points.minOf { (x, y) -> x }
                maxX = action.points.maxOf { (x, y) -> x }

                minY = action.points.minOf { (x, y) -> y }
                maxY = action.points.maxOf { (x, y) -> y }
            }
            is DrawAction.Spray -> {
                this.drawPoints(
                    pointMode = PointMode.Points,
                    points = action.points,
                    paint = action.paint
                )

                minX = action.points.minOf { (x, y) -> x }
                maxX = action.points.maxOf { (x, y) -> x }

                minY = action.points.minOf { (x, y) -> y }
                maxY = action.points.maxOf { (x, y) -> y }
            }
            is DrawAction.Shape -> {
                minX = action.start.x
                maxX = action.end.x

                minY = action.start.y
                maxY = action.end.y

                when (action.shapeType){
                    CanvasTool.SHAPE_LINE -> {
                        this.drawLine(action.start, action.end, action.paint)
                    }

                    CanvasTool.SHAPE_RECT -> {
                        this.drawRect(
                            left = minOf(action.start.x, action.end.x),
                            top = minOf(action.start.y, action.end.y),
                            right = maxOf(action.start.x, action.end.x),
                            bottom = maxOf(action.start.y, action.end.y),
                            paint = action.paint
                        )
                    }

                    CanvasTool.SHAPE_CIRCLE -> {
                        this.drawOval(
                            left = minOf(action.start.x, action.end.x),
                            top = minOf(action.start.y, action.end.y),
                            right = maxOf(action.start.x, action.end.x),
                            bottom = maxOf(action.start.y, action.end.y),
                            paint = action.paint
                        )

                    }

                    else -> {}
                }
            }
        }

        maxBrushSize = maxOf(maxBrushSize, strokeWidth.value)
        val actionBounds = Rect(minX, minY, maxX + maxBrushSize, maxY + maxBrushSize)
        contentBounds = contentBounds.expandToInclude(actionBounds)
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
            paint = PaintOptions(
                color.value,
                1f,
                opacity.value,
                canvasTool.value
            ).createPaint()
        )

        return action
    }

    // TODO this no longer needs to be suspend:
    private suspend fun fillSegment(startOffset: Offset, targetColor: Color): DrawAction.Fill {
        val action = fillSegment(internalBitmap, startOffset, targetColor)
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

    // TODO this doesn't need to be suspend anymore:
    private suspend fun setColorFromPixel(offset: Offset) {
        val x = offset.x.toInt().coerceIn(0, internalBitmap.width - 1)
        val y = offset.y.toInt().coerceIn(0, internalBitmap.height - 1)

        val pixels = getPixelArray(internalBitmap)
        color.value = Color(pixels[y * internalBitmap.width + x])
    }


    /////////////////
    // UNDO & REDO //
    /////////////////

    /** Undo the last action */
    fun undo() {
        if (_actions.value.isNotEmpty()) {
            val undone = _actions.value[_actions.value.lastIndex]

            _undoneActions.value += undone
            _actions.value -= undone
            redrawHistory()
        }
    }

    /** Redo the last undone action */
    fun redo() {
        if (_undoneActions.value.isNotEmpty()) {
            val undone = _undoneActions.value[_undoneActions.value.lastIndex]

            _actions.value += undone
            _undoneActions.value -= undone
            redrawHistory()
        }
    }

    private fun redrawHistory(){
        if (state.value !is DrawBoxConnectionState.Connected) return

        // Clear the bitmap
        internalCanvas.drawRect(
            0f,
            0f,
            internalBitmap.width.toFloat(),
            internalBitmap.height.toFloat(),
            Paint().apply { blendMode = BlendMode.Clear }
        )

        _actions.value.forEach { action ->
            applyDrawActionToInternalCanvas(action)
        }
    }

    ///////////////////////////
    // SUBSCRIPTION & BITMAP //
    ///////////////////////////
    private var _version = 0
    private val _trigger = MutableSharedFlow<Int>(1, 1)
    val trigger = _trigger.asSharedFlow()

    private fun triggerRedraw(){
        _trigger.tryEmit(_version++)
    }
}