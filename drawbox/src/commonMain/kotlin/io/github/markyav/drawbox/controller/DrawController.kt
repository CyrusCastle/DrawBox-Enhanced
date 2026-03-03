package io.github.markyav.drawbox.controller

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.IntSize
import io.github.markyav.drawbox.model.CanvasAction
import io.github.markyav.drawbox.model.PathWrapper
import io.github.markyav.drawbox.model.CanvasTool
import io.github.markyav.drawbox.util.addNotNull
import io.github.markyav.drawbox.util.combineStates
import io.github.markyav.drawbox.util.createPath
import io.github.markyav.drawbox.util.mapState
import kotlinx.coroutines.flow.*

/**
 * DrawController interacts with [DrawBox] and it allows you to control the canvas and all the components with it.
 */
class DrawController {
    private var state: MutableStateFlow<DrawBoxConnectionState> = MutableStateFlow(DrawBoxConnectionState.Disconnected)

    /** What tool are we using on the [Canvas] at the minute? */
    var canvasTool: MutableStateFlow<CanvasTool> = MutableStateFlow(CanvasTool.BRUSH)

    private val activeDrawingPath: MutableStateFlow<List<Offset>?> = MutableStateFlow(null)

    private val actions = MutableStateFlow<List<CanvasAction>>(emptyList())
    private val drawnPaths = actions.mapState { getPathsFromActions(it) }
    private val undoneActions = MutableStateFlow<List<CanvasAction>>(emptyList())

    val openedImage: MutableStateFlow<ImageBitmap?> = MutableStateFlow(null)

    /** An [canvasOpacity] of the [Canvas] in the [DrawBox] */
    var canvasOpacity: MutableStateFlow<Float> = MutableStateFlow(1f)

    /** An [opacity] of the stroke */
    var opacity: MutableStateFlow<Float> = MutableStateFlow(1f)

    /** A [strokeWidth] of the stroke */
    var strokeWidth: MutableStateFlow<Float> = MutableStateFlow(10f)

    /** A [color] of the stroke */
    var color: MutableStateFlow<Color> = MutableStateFlow(Color.Red)

    /** Whether the controller should register any strokes */
    var enabled: MutableStateFlow<Boolean> = MutableStateFlow(true)

    /** A [background] of the background of DrawBox */
    var background: MutableStateFlow<DrawBoxBackground> = MutableStateFlow(DrawBoxBackground.NoBackground)

    /** Indicate how many redos it is possible to do. */
    val undoCount = actions.mapState { it.size }

    /** Indicate how many undos it is possible to do. */
    val redoCount = undoneActions.mapState { it.size }

    /** Executes undo the drawn path if possible. */
    fun undo() {
        if (actions.value.isNotEmpty()) {
            val _actions = actions.value.toMutableList()
            val _undoneActions = undoneActions.value.toMutableList()

//            _undoneActions.add(_actions.removeLast())
            val last = _actions.removeAt(_actions.lastIndex)
            _undoneActions.add(last)

            actions.value = _actions
            undoneActions.value = _undoneActions
        }
    }

    /** Executes redo the drawn path if possible. */
    fun redo() {
        if (undoneActions.value.isNotEmpty()) {
            val _actions = actions.value.toMutableList()
            val _undoneActions = undoneActions.value.toMutableList()

//            _actions.add(_undoneActions.removeLast())
            val last = _undoneActions.removeAt(_undoneActions.lastIndex)
            _actions.add(last)

            actions.value = _actions
            undoneActions.value = _undoneActions
        }
    }

    /** Clear drawn paths and the bitmap image. */
    fun reset() {
        actions.value = emptyList()
        undoneActions.value = emptyList()
    }

    fun open(image: ImageBitmap) {
        reset()
        openedImage.value = image
    }

    internal fun onDragStart(newPoint: Offset) {
        if (!enabled.value) return

        insertNewPath(newPoint)
    }

    internal fun onDrag(newPoint: Offset){
        if (!enabled.value) return

        updateLatestPath(newPoint)
    }

    internal fun onDragEnd(){
        if (!enabled.value) return

        when (canvasTool.value){
            CanvasTool.BRUSH -> finalizePath()
            CanvasTool.ERASER -> finalizeEraserPath()
        }
    }

    /** Call this function when user starts drawing a path. */
    internal fun updateLatestPath(newPoint: Offset) {
        (state.value as? DrawBoxConnectionState.Connected)?.let {
            require(activeDrawingPath.value != null)
            val list = activeDrawingPath.value!!.toMutableList()
            list.add(newPoint.div(it.size.toFloat()))
            activeDrawingPath.value = list
        }
    }

    /** When dragging call this function to update the last path. */
    internal fun insertNewPath(newPoint: Offset) {
        (state.value as? DrawBoxConnectionState.Connected)?.let {
            require(activeDrawingPath.value == null)
            activeDrawingPath.value = listOf(newPoint.div(it.size.toFloat()))
            undoneActions.value = emptyList()
        }
    }

    internal fun finalizePath() {
        (state.value as? DrawBoxConnectionState.Connected)?.let {
            require(activeDrawingPath.value != null)
            val _actions = actions.value.toMutableList()

            // We need more than one point to draw a proper path, but we can point to the same place twice
            if (activeDrawingPath.value!!.size == 1){
                updateLatestPath(activeDrawingPath.value!![0].times(it.size.toFloat()))
            }

            val pathWrapper = PathWrapper(
                points = activeDrawingPath.value!!,
                strokeColor = color.value,
                alpha = opacity.value,
                strokeWidth = strokeWidth.value.div(it.size.toFloat()),
            )
            _actions.add(CanvasAction.Draw(pathWrapper))

            actions.value = _actions
            activeDrawingPath.value = null
        }
    }

    internal fun finalizeEraserPath(){
        // TODO is sometimes unreliable, I think it's looking for each point, but it should check to see if our [p1] - [p2] intersects their [p1] - [p2]

        (state.value as? DrawBoxConnectionState.Connected)?.let {
            require(activeDrawingPath.value != null)

            val toRemove = mutableListOf<PathWrapper>()
            val _erasedPath = activeDrawingPath.value!!

            for (pw in actions.value) {
                if (pw !is CanvasAction.Draw) continue

                if (pw.path.points.any { p -> _erasedPath.any { e -> e.minus(p).getDistance() < strokeWidth.value.div(it.size.toFloat()) } }) {
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

    /** Call this function to connect to the [DrawBox]. */
    internal fun connectToDrawBox(size: IntSize) {
        if (
            size.width > 0 &&
            size.height > 0 &&
            size.width == size.height
        ) {
            state.value = DrawBoxConnectionState.Connected(size = size.width)
        }
    }

    internal fun onTap(newPoint: Offset) {
        if (!enabled.value) return

        insertNewPath(newPoint)

        when (canvasTool.value){
            CanvasTool.BRUSH -> finalizePath()
            CanvasTool.ERASER -> finalizeEraserPath()
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

    fun getDrawPath(subscription: DrawBoxSubscription): StateFlow<List<PathWrapper>> {
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
                    strokeWidth = strokeWidth.value.div(it.size.toFloat()),
                )
                _a.addNotNull(pathWrapper)
            }
            _a
        }
    }

    internal fun getPathWrappersForDrawbox(subscription: DrawBoxSubscription): StateFlow<List<PathWrapper>> {
        return combineStates(getDrawPath(subscription), state) { paths, st ->
            val size = (st as? DrawBoxConnectionState.Connected)?.size ?: 1
            paths.scale(size.toFloat())
        }
    }

    internal fun getOpenImageForDrawbox(size: Int?): StateFlow<OpenedImage> {
        return combineStates(openedImage, state) { image, st ->
            if (image !=  null) {
                OpenedImage.Image(
                    image,
                    dstSize = IntSize(
                        width = size ?: (st as? DrawBoxConnectionState.Connected)?.size ?: 1,
                        height = size ?: (st as? DrawBoxConnectionState.Connected)?.size ?: 1,
                    ),
                )
            } else {
                OpenedImage.None
            }
        }
    }

    fun getBitmap(size: Int, subscription: DrawBoxSubscription): StateFlow<ImageBitmap> {
        val path = getDrawPath(subscription)
        return combineStates(getOpenImageForDrawbox(size), path) { openImage, p ->
            val bitmap = ImageBitmap(size, size, ImageBitmapConfig.Argb8888)
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
            p.scale(size.toFloat()).forEach { pw ->
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