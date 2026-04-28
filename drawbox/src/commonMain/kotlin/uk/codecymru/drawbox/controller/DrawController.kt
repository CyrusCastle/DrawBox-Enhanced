package uk.codecymru.drawbox.controller

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import uk.codecymru.drawbox.model.CanvasTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface DrawController {
    ///////////////////
    // DERIVED STATE //
    ///////////////////
    /** Can we currently undo? */
    val canRedo: StateFlow<Boolean>

    /** Can we currently redo? */
    val canUndo: StateFlow<Boolean>

    ////////////////////
    // BRUSH SETTINGS //
    ////////////////////

    /** What tool is currently being used (Brush, Eraser, etc.) */
    val canvasTool: MutableStateFlow<CanvasTool>

    /** The current stroke width */
    val strokeWidth: MutableStateFlow<Float>

    /** The current stroke color */
    val color: MutableStateFlow<Color>

    /** The current stroke opacity */
    val opacity: MutableStateFlow<Float>

    ////////////////////
    // OTHER SETTINGS //
    ////////////////////
    var enabled: MutableStateFlow<Boolean>
    var canvasOpacity: MutableStateFlow<Float>

    /////////////////////////
    // BACKGROUND SETTINGS //
    /////////////////////////
    val openedImage: MutableStateFlow<ImageBitmap?>
    var background: MutableStateFlow<DrawBoxBackground>

    /////////////
    // METHODS //
    /////////////

    /** Initialize or update the controller with the canvas size */
    fun connectToDrawBox(size: IntSize)

    /** Open a new image as a backing for the canvas */
    fun open(image: ImageBitmap)

    /** Logic for when a touch/drag starts */
    fun onDragStart(offset: Offset)

    /** Logic for when a touch/drag moves */
    fun onDrag(offset: Offset)

    /** Logic for when a touch/drag ends */
    fun onDragEnd()

    /** Logic for when a quick tap happens */
    fun onTap(offset: Offset)

    /** Undo the last action */
    fun undo()

    /** Redo the last undone action */
    fun redo()

    /** Clear the canvas and history */
    fun reset()

    /** Get the canvas as a bitmap */
    fun getBitmap(size: Int? = null, subscription: DrawBoxSubscription): StateFlow<ImageBitmap>
}