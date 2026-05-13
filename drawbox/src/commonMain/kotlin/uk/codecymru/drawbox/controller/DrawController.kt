package uk.codecymru.drawbox.controller

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
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
 */
class DrawController(
    startingTool: CanvasTool = CanvasTool.BRUSH,
    startingOpacity: Float = 1f,
    startingStrokeWidth: Float = 10f,
    startingColor: Color = Color.Red,
    startingBackground: DrawBoxBackground = DrawBoxBackground.NoBackground,
    startingCanvasOpacity: Float = 1f
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

        triggerRedraw()
    }

    /** Clear the canvas and history */
    fun reset() {
        _actions.value = emptyList()
        _undoneActions.value = emptyList()
        redrawHistory()
        triggerRedraw()
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

                is DrawAction.Shape -> {
                    action // Let us not bother with shape, would be too much work for very little gain
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

    internal fun onDragStart(offset: Offset) {
        if (!enabled.value) return

        _undoneActions.value = emptyList()
        _currentAction.value = null
        drawSegment(offset, offset)
    }

    internal fun onDrag(offset: Offset) {
        if (!enabled.value) return

        val start = _currentAction.value?.getFinalPoint() ?: offset
        drawSegment(start, offset)
    }

    internal fun onDragEnd() {
        if (!enabled.value) return

        if (_currentAction.value != null) {
            applyDrawActionToInternalCanvas(_currentAction.value!!)
            _actions.value += _currentAction.value!!
        }
        _currentAction.value = null
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

    private fun applyDrawActionToInternalCanvas(action: DrawAction){
        action.applyTo(internalCanvas)
        triggerRedraw()
    }

    private fun drawSegment(from: Offset, to: Offset) {
        if (state.value !is DrawBoxConnectionState.Connected) return

        val paint = PaintOptions(
            color.value,
            strokeWidth.value,
            opacity.value,
            canvasTool.value
        ).createPaint()

        val start = _currentAction.value?.getFinalPoint() ?: from

        when (canvasTool.value){
            CanvasTool.EYEDROPPER -> setColorFromPixel(to)
            else -> _currentAction.value = DrawActionFactory.extend(_currentAction.value, internalBitmap, canvasTool.value, paint, start, to)
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