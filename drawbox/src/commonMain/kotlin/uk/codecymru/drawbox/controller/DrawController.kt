package uk.codecymru.drawbox.controller

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import uk.codecymru.drawbox.model.CanvasTool
import uk.codecymru.drawbox.model.DrawAction
import uk.codecymru.drawbox.model.DrawActionFactory
import uk.codecymru.drawbox.model.PaintOptions
import uk.codecymru.drawbox.model.applyTo

/**
 * The DrawController, it contains the bitmap & logic to draw onto bitmap
 *
 * @property startingTool Optional, specifies starting tool out of CanvasTool enum
 * @property startingOpacity Optional alpha value for brushstrokes
 * @property startingStrokeWidth Optional, 0f leads to a hairline width
 * @property startingColor Optional starting colour
 * @property startingBackground Optional starting background
 * @property startingCanvasOpacity Optional alpha value for the canvas as a whole
 * @property startingFontFamily Optional font for the TEXT tool; null uses the platform's default font
 */
class DrawController(
    startingTool: CanvasTool = CanvasTool.PENCIL,
    startingOpacity: Float = 1f,
    startingStrokeWidth: Float = 10f,
    startingColor: Color = Color.Red,
    startingBackground: DrawBoxBackground = DrawBoxBackground.NoBackground,
    startingCanvasOpacity: Float = 1f,
    startingFontFamily: FontFamily? = null
) {
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
    private val _currentAction = MutableStateFlow<DrawAction?>(null)
    internal val currentAction = _currentAction.asStateFlow()


    private var polygonFirstEdgeDragging = false

    private var curveAwaitingBend = false
    private var curveBendDragStart: Offset? = null

    ///////////////////
    // DERIVED STATE //
    ///////////////////

    /** Can we currently undo? */
    val canUndo = _actions.map { it.isNotEmpty() }

    /** Can we currently redo? */
    val canRedo = _undoneActions.map { it.isNotEmpty() }

    /////////////////
    // LAYER STATE //
    /////////////////

    ///** What layer are we drawing on? Will return null if layers are disabled */
    //val currentLayer: MutableStateFlow<Int?> = MutableStateFlow(null)
    //
    ///** What layer numbers have been drawn upon? */
    //val drawnUponLayers: MutableStateFlow<List<Int>> = MutableStateFlow(emptyList())

    ////////////////////
    // BRUSH SETTINGS //
    ////////////////////

    /** What tool is currently being used (Brush, Eraser, etc.) */
    val canvasTool: MutableStateFlow<CanvasTool> = MutableStateFlow(startingTool)

    /** The current stroke width */
    val opacity: MutableStateFlow<Float> = MutableStateFlow(startingOpacity)

    /** The current stroke color */
    val strokeWidth: MutableStateFlow<Float> = MutableStateFlow(startingStrokeWidth)

    /** The current stroke opacity */
    val color: MutableStateFlow<Color> = MutableStateFlow(startingColor)

    ////////////////////
    // OTHER SETTINGS //
    ////////////////////
    /** Can we currently interact with this controller? */
    val enabled: MutableStateFlow<Boolean> = MutableStateFlow(true)

    /** The opacity of the canvas background */
    val canvasOpacity: MutableStateFlow<Float> = MutableStateFlow(startingCanvasOpacity)

    /////////////////////////
    // BACKGROUND SETTINGS //
    /////////////////////////
    private val _openedImage: MutableStateFlow<ImageBitmap?> = MutableStateFlow(null)

    /** An image, if there is one, which has been loaded onto the canvas (this goes IN-FRONT of any previous drawings) */
    val openedImage = _openedImage.asStateFlow()

    /** A background, perhaps, that goes BEHIND the canvas */
    val background: MutableStateFlow<DrawBoxBackground> = MutableStateFlow(startingBackground)

    ////////////////
    // TEXT TOOL  //
    ////////////////

    /** Wired up by [uk.codecymru.drawbox.box.DrawBox], lets us measure text onto the bitmap from commonMain */
    internal var textMeasurer: TextMeasurer? = null

    private val _pendingText: MutableStateFlow<PendingText?> = MutableStateFlow(null)

    /** Where the user tapped with [CanvasTool.TEXT], and whatever's been typed */
    val pendingText = _pendingText.asStateFlow()

    /** The font, null uses default font */
    val fontFamily: MutableStateFlow<FontFamily?> = MutableStateFlow(startingFontFamily)

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
        drawOpenedImage(image)

        triggerRedraw()
    }

    /** Clear the canvas and history, including whatever image was open on it or action was in progress */
    fun reset() {
        _actions.value = emptyList()
        _undoneActions.value = emptyList()
        _openedImage.value = null
        _pendingText.value = null
        _currentAction.value = null
        polygonFirstEdgeDragging = false
        curveAwaitingBend = false
        curveBendDragStart = null
        redrawHistory()
        triggerRedraw()
    }

    private fun drawOpenedImage(image: ImageBitmap) {
        val size = (state.value as? DrawBoxConnectionState.Connected)?.size ?: IntSize(1, 1)

        internalCanvas.drawImageRect(
            image = image,
            srcOffset = IntOffset.Zero,
            dstSize = size,
            paint = Paint()
        )
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
                        path = action.path.filter { (from, to) ->
                            from.x <= maxWidth && from.y <= maxHeight && to.x <= maxWidth && to.y <= maxHeight
                        }
                    )
                }

                else -> {
                    action // Let us not bother with shapes etc, would be too much work for very little gain
                }
            }
        }.filter { it.points.isNotEmpty() }
    }

    ///////////////////////
    // HANDLING LAYERING //
    ///////////////////////

    //fun enableLayers(){
    //    if (currentLayer.value != null) return
    //
    //    internalCanvas.enableZ()
    //    drawnUponLayers.value += 0
    //    currentLayer.value = 0
    //}
    //
    //fun createLayer(){
    //    val new = drawnUponLayers.value.maxOrNull()
    //
    //    if (new != null){
    //        drawnUponLayers.value += new + 1
    //        currentLayer.value = new + 1
    //    }
    //}

    ///////////////////////////
    // HANDLING INTERACTIONS //
    ///////////////////////////

    fun onDragStart(offset: Offset) {
        if (!enabled.value) return

        when (canvasTool.value) {
            CanvasTool.SHAPE_POLYGON -> {
                if (_currentAction.value == null) {
                    val paint = PaintOptions(color.value, strokeWidth.value, opacity.value, canvasTool.value).createPaint()

                    _undoneActions.value = emptyList()
                    _currentAction.value = DrawAction.Polygon(listOf(offset), paint)
                    polygonFirstEdgeDragging = true
                } else {
                    addPolygonVertex(offset)
                }
            }
            CanvasTool.SHAPE_CURVE -> {
                if (curveAwaitingBend) {
                    curveBendDragStart = offset
                } else if (_currentAction.value == null) {
                    val paint = PaintOptions(color.value, strokeWidth.value, opacity.value, canvasTool.value).createPaint()

                    _undoneActions.value = emptyList()
                    _currentAction.value = DrawAction.Curve(offset, offset, offset, paint)
                }
            }
            CanvasTool.TEXT -> { }
            else -> {
                _undoneActions.value = emptyList()
                _currentAction.value = null
                drawSegment(offset, offset)
            }
        }
    }

    fun onDrag(offset: Offset) {
        if (!enabled.value) return

        when (canvasTool.value) {
            CanvasTool.SHAPE_POLYGON -> {
                if (!polygonFirstEdgeDragging) return
                val polygon = _currentAction.value as? DrawAction.Polygon ?: return

                _currentAction.value = polygon.copy(points = listOf(polygon.points.first(), offset))
            }
            CanvasTool.SHAPE_CURVE -> {
                val curve = _currentAction.value as? DrawAction.Curve ?: return

                if (curveAwaitingBend) {
                    val bendStart = curveBendDragStart ?: return
                    val midpoint = Offset((curve.start.x + curve.end.x) / 2f, (curve.start.y + curve.end.y) / 2f)

                    _currentAction.value = curve.copy(control = midpoint + (offset - bendStart))
                } else {
                    val midpoint = Offset((curve.start.x + offset.x) / 2f, (curve.start.y + offset.y) / 2f)

                    _currentAction.value = curve.copy(end = offset, control = midpoint)
                }
            }
            CanvasTool.TEXT -> { }
            else -> {
                val start = _currentAction.value?.getFinalPoint() ?: offset
                drawSegment(start, offset)
            }
        }
    }

    fun onDragEnd() {
        if (!enabled.value) return

        polygonFirstEdgeDragging = false

        if (canvasTool.value == CanvasTool.SHAPE_CURVE) {
            if (curveAwaitingBend) {
                curveAwaitingBend = false
                curveBendDragStart = null
                commitCurrentAction()
            } else {
                curveAwaitingBend = true
            }
            return
        }

        if (canvasTool.value == CanvasTool.TEXT || _currentAction.value is DrawAction.Polygon) return

        commitCurrentAction()
    }

    fun onTap(offset: Offset) {
        if (!enabled.value) return

        when (canvasTool.value){
            CanvasTool.SHAPE_LINE, CanvasTool.SHAPE_CURVE, CanvasTool.SHAPE_RECT, CanvasTool.SHAPE_ROUNDED_RECT, CanvasTool.SHAPE_CIRCLE -> return
            CanvasTool.SHAPE_POLYGON -> addPolygonVertex(offset)
            CanvasTool.TEXT -> {
                resolvePendingText()
                _pendingText.value = PendingText(offset)
            }
            else -> {
                drawSegment(offset, offset)
                onDragEnd()
            }
        }
    }

    //////////////////
    // POLYGON TOOL //
    //////////////////

    private fun addPolygonVertex(offset: Offset) {
        val existing = _currentAction.value as? DrawAction.Polygon

        if (existing != null && existing.points.size >= 2 && (existing.points.first() - offset).getDistance() <= POLYGON_CLOSE_THRESHOLD) {
            commitPolygon(closeShape = true)
            return
        }

        val paint = PaintOptions(color.value, strokeWidth.value, opacity.value, canvasTool.value).createPaint()
        val vertices = (existing?.points ?: emptyList()) + offset

        _undoneActions.value = emptyList()
        _currentAction.value = DrawAction.Polygon(vertices, paint)
    }

    /** Finish the polygon currently being built with [CanvasTool.SHAPE_POLYGON] without closing it back to the start */
    fun finishPolygon() = commitPolygon(closeShape = false)

    private fun commitPolygon(closeShape: Boolean) {
        val polygon = _currentAction.value as? DrawAction.Polygon ?: return

        if (polygon.points.size < 2) {
            _currentAction.value = null
            return
        }

        _currentAction.value = polygon.copy(closed = closeShape)
        commitCurrentAction()
    }

    /////////////////
    // CURVE TOOL  //
    /////////////////

    /**
     * Lock in the curve currently being drawn with [CanvasTool.SHAPE_CURVE] as a straight line
     */
    fun finishCurve() {
        if (!curveAwaitingBend) return

        curveAwaitingBend = false
        curveBendDragStart = null
        commitCurrentAction()
    }

    ///////////////
    // TEXT TOOL //
    ///////////////

    /** Append [input] to whatever's pending with [CanvasTool.TEXT] right now, updating the live preview */
    fun typePendingText(input: String) {
        val pending = _pendingText.value ?: return
        updatePendingText(pending.copy(text = pending.text + input))
    }

    /** Remove the last typed character from the pending text, if there is one */
    fun backspacePendingText() {
        val pending = _pendingText.value ?: return
        if (pending.text.isEmpty()) return
        updatePendingText(pending.copy(text = pending.text.dropLast(1)))
    }

    private fun updatePendingText(pending: PendingText) {
        _pendingText.value = pending

        val measurer = textMeasurer
        _currentAction.value = if (pending.text.isEmpty() || measurer == null) {
            null
        } else {
            val style = TextStyle(color = color.value, fontSize = (strokeWidth.value * 2.2f).sp, fontFamily = fontFamily.value)
            DrawAction.Text(pending.offset, measurer.measure(pending.text, style))
        }
    }

    /**
     * Confirm the pending text if anything's been typed into it, otherwise cancel it
     */
    fun resolvePendingText() {
        val pending = _pendingText.value ?: return

        if (pending.text.isBlank()) {
            cancelText()
        } else {
            commitCurrentAction()
            _pendingText.value = null
        }
    }

    /** Discards the pending text */
    fun cancelText() {
        _pendingText.value = null
        _currentAction.value = null
    }

    /////////////////////////////
    // PRIVATE DRAWING METHODS //
    /////////////////////////////

    private fun applyDrawActionToInternalCanvas(action: DrawAction){
        action.applyTo(internalCanvas)
        triggerRedraw()
    }

    /** Bakes whatever's sitting in [_currentAction] into the bitmap & history, then clears it */
    private fun commitCurrentAction() {
        val action = _currentAction.value ?: return

        applyDrawActionToInternalCanvas(action)
        _actions.value += action
        _currentAction.value = null
    }

    private fun drawSegment(from: Offset, to: Offset) {
        if (state.value !is DrawBoxConnectionState.Connected) return

        val options = PaintOptions(
            color.value,
            strokeWidth.value,
            opacity.value,
            canvasTool.value
        )

        val paint = options.createPaint()
        val haloPaint = options.createHaloPaint()

        val start = _currentAction.value?.getFinalPoint() ?: from

        when (canvasTool.value){
            CanvasTool.EYEDROPPER -> setColorFromPixel(to)
            else -> _currentAction.value = DrawActionFactory.extend(_currentAction.value, internalBitmap, canvasTool.value, paint, haloPaint, start, to)
        }
    }

    private fun setColorFromPixel(offset: Offset) {
        val x = offset.x.toInt().coerceIn(0, internalBitmap.width - 1)
        val y = offset.y.toInt().coerceIn(0, internalBitmap.height - 1)

        val pixels = internalBitmap.getPixelArray()
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
            triggerRedraw()
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

        _openedImage.value?.let { drawOpenedImage(it) }

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

internal fun ImageBitmap.getPixelArray(): IntArray{
    val pixels = IntArray(this.width * this.height)
    this.readPixels(pixels, startX = 0, startY = 0, width = this.width, height = this.height)

    return pixels
}

private const val POLYGON_CLOSE_THRESHOLD = 16f