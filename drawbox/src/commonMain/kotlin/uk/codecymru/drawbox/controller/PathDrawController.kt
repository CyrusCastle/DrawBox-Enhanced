package uk.codecymru.drawbox.controller

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.IntSize
import uk.codecymru.drawbox.model.CanvasAction
import uk.codecymru.drawbox.model.PathWrapper
import uk.codecymru.drawbox.model.CanvasTool
import uk.codecymru.drawbox.util.addNotNull
import uk.codecymru.drawbox.util.combineStates
import uk.codecymru.drawbox.util.createPath
import uk.codecymru.drawbox.util.mapState
import kotlinx.coroutines.flow.*

/**
 * DrawController interacts with [uk.codecymru.drawbox.box.DrawBox] and it allows you to control the canvas and all the components with it.
 */
class PathDrawController: DrawController {
    ////////////////
    // MAIN STATE //
    ////////////////
    private var state: MutableStateFlow<DrawBoxConnectionState> = MutableStateFlow(DrawBoxConnectionState.Disconnected)
    private val activeDrawingPath: MutableStateFlow<List<Offset>?> = MutableStateFlow(null)

    private val actions = MutableStateFlow<List<CanvasAction>>(emptyList())
    private val drawnPaths = actions.mapState { getPathsFromActions(it) }
    private val undoneActions = MutableStateFlow<List<CanvasAction>>(emptyList())

    ///////////////////
    // DERIVED STATE //
    ///////////////////
    override val canUndo = actions.mapState { it.isNotEmpty() }
    override val canRedo = undoneActions.mapState { it.isNotEmpty() }

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

    override fun open(image: ImageBitmap) {
        reset()
        openedImage.value = image
    }

    override fun connectToDrawBox(size: IntSize) {
        if (
            size.width > 0 &&
            size.height > 0 &&
            size.width == size.height
        ) {
            state.value = DrawBoxConnectionState.Connected(size)
        }
    }

    override fun reset() {
        actions.value = emptyList()
        undoneActions.value = emptyList()
    }

    ///////////////////////////
    // HANDLING INTERACTIONS //
    ///////////////////////////

    override fun onDragStart(offset: Offset) {
        if (!enabled.value) return

        insertNewPath(offset)
    }

    override fun onDrag(offset: Offset){
        if (!enabled.value) return

        updateLatestPath(offset)
    }

    override fun onDragEnd(){
        if (!enabled.value) return

        when (canvasTool.value){
            CanvasTool.BRUSH -> finalizePath()
            CanvasTool.ERASER -> finalizeEraserPath()
        }
    }

    override fun onTap(offset: Offset) {
        if (!enabled.value) return

        insertNewPath(offset)

        when (canvasTool.value){
            CanvasTool.BRUSH -> finalizePath()
            CanvasTool.ERASER -> finalizeEraserPath()
        }
    }

    /////////////////////////////
    // PRIVATE DRAWING METHODS //
    /////////////////////////////

    /** Call this function when user starts drawing a path. */
    internal fun updateLatestPath(offset: Offset) {
        (state.value as? DrawBoxConnectionState.Connected)?.let {
            require(activeDrawingPath.value != null)
            val list = activeDrawingPath.value!!.toMutableList()
            list.add(offset.div(it.size.width.toFloat()))
            activeDrawingPath.value = list
        }
    }

    /** When dragging call this function to update the last path. */
    internal fun insertNewPath(offset: Offset) {
        (state.value as? DrawBoxConnectionState.Connected)?.let {
            require(activeDrawingPath.value == null)
            activeDrawingPath.value = listOf(offset.div(it.size.width.toFloat()))
            undoneActions.value = emptyList()
        }
    }

    private fun finalizePath() {
        (state.value as? DrawBoxConnectionState.Connected)?.let {
            require(activeDrawingPath.value != null)
            val _actions = actions.value.toMutableList()

            // We need more than one point to draw a proper path, but we can point to the same place twice
            if (activeDrawingPath.value!!.size == 1){
                updateLatestPath(activeDrawingPath.value!![0].times(it.size.width.toFloat()))
            }

            val pathWrapper = PathWrapper(
                points = activeDrawingPath.value!!,
                strokeColor = color.value,
                alpha = opacity.value,
                strokeWidth = strokeWidth.value.div(it.size.width.toFloat()),
            )
            _actions.add(CanvasAction.Draw(pathWrapper))

            actions.value = _actions
            activeDrawingPath.value = null
        }
    }

    private fun finalizeEraserPath(){
        // TODO is sometimes unreliable, I think it's looking for each point, but it should check to see if our [p1] - [p2] intersects their [p1] - [p2]

        (state.value as? DrawBoxConnectionState.Connected)?.let {
            require(activeDrawingPath.value != null)

            val toRemove = mutableListOf<PathWrapper>()
            val _erasedPath = activeDrawingPath.value!!

            for (pw in actions.value) {
                if (pw !is CanvasAction.Draw) continue

                if (pw.path.points.any { p -> _erasedPath.any { e -> e.minus(p).getDistance() < strokeWidth.value.div(it.size.width.toFloat()) } }) {
                    toRemove.add(pw.path)
                }
            }

            if (toRemove.isNotEmpty()) {
                actions.value += CanvasAction.Erase(toRemove)
                undoneActions.value = emptyList()
            }

            activeDrawingPath.value = null
        }
    }

    private fun List<PathWrapper>.scale(size: Float): List<PathWrapper> {
        return this.map { pw ->
            val t = pw.points.map { it.times(size) }
            pw.copy(
                points = mutableListOf<Offset>().also { it.addAll(t) },
                strokeWidth = pw.strokeWidth * size
            )
        }
    }

    private fun getPathsFromActions(actions: List<CanvasAction>): List<PathWrapper> {
        val result = mutableListOf<PathWrapper>()

        actions.forEach { action ->
            when(action) {
                is CanvasAction.Draw -> result.add(action.path)
                is CanvasAction.Erase -> result.removeAll(action.erased.toSet())
            }
        }

        return result
    }

    /////////////////
    // UNDO & REDO //
    /////////////////
    override fun undo() {
        if (actions.value.isNotEmpty()) {
            val _actions = actions.value.toMutableList()
            val _undoneActions = undoneActions.value.toMutableList()

            val last = _actions.removeAt(_actions.lastIndex)
            _undoneActions.add(last)

            actions.value = _actions
            undoneActions.value = _undoneActions
        }
    }

    override fun redo() {
        if (undoneActions.value.isNotEmpty()) {
            val _actions = actions.value.toMutableList()
            val _undoneActions = undoneActions.value.toMutableList()

            val last = _undoneActions.removeAt(_undoneActions.lastIndex)
            _actions.add(last)

            actions.value = _actions
            undoneActions.value = _undoneActions
        }
    }

    ///////////////////////////
    // SUBSCRIPTION & BITMAP //
    ///////////////////////////

    private fun getDrawPath(subscription: DrawBoxSubscription): StateFlow<List<PathWrapper>> {
        return when (subscription) {
            is DrawBoxSubscription.DynamicUpdate -> getDynamicUpdateDrawnPath()
            is DrawBoxSubscription.FinishDrawingUpdate -> drawnPaths
        }
    }

    private fun getDynamicUpdateDrawnPath(): StateFlow<List<PathWrapper>> {
        return combineStates(drawnPaths, activeDrawingPath) { a, b ->
            val _a = a.toMutableList()
            (state.value as? DrawBoxConnectionState.Connected)?.let {
                val pathWrapper = PathWrapper(
                    points = activeDrawingPath.value ?: emptyList(),
                    strokeColor = if (canvasTool.value == CanvasTool.BRUSH) color.value else Color.Red,
                    alpha = if (canvasTool.value == CanvasTool.BRUSH) opacity.value else 0.8f,
                    strokeWidth = strokeWidth.value.div(it.size.width.toFloat()),
                )
                _a.addNotNull(pathWrapper)
            }
            _a
        }
    }

    private fun getOpenImageForDrawbox(size: Int?): StateFlow<OpenedImage> {
        return combineStates(openedImage, state) { image, connectionState ->
            val bitmap = image ?: return@combineStates OpenedImage.None
            val width = size ?: (connectionState as? DrawBoxConnectionState.Connected)?.size?.width ?: 1

            OpenedImage.Image(
                image = bitmap,
                dstSize = IntSize(width, width)
            )
        }
    }

    override fun getBitmap(size: Int?, subscription: DrawBoxSubscription): StateFlow<ImageBitmap> {
        val path = getDrawPath(subscription)
        val width = size ?: (state.value as? DrawBoxConnectionState.Connected)?.size?.width ?: 1

        return combineStates(getOpenImageForDrawbox(width), path) { openImage, p ->
            val bitmap = ImageBitmap(width, width, ImageBitmapConfig.Argb8888)
            val canvas = Canvas(bitmap)
            (openImage as? OpenedImage.Image)?.let {
                canvas.drawImageRect(
                    image = it.image,
                    srcOffset = it.srcOffset,
                    srcSize = it.srcSize,
                    dstSize = it.dstSize,
                    paint = Paint()
                )
            }
            p.scale(width.toFloat()).forEach { pw ->
                canvas.drawPath(
                    createPath(pw.points),
                    paint = Paint().apply {
                        color = pw.strokeColor
                        alpha = pw.alpha
                        style = PaintingStyle.Stroke
                        strokeCap = StrokeCap.Round
                        strokeJoin = StrokeJoin.Round
                        strokeWidth = pw.strokeWidth
                    }
                )
            }
            bitmap
        }
    }
}