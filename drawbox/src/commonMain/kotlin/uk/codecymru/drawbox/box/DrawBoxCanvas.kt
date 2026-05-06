package uk.codecymru.drawbox.box

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import uk.codecymru.drawbox.controller.DrawBoxSubscription
import uk.codecymru.drawbox.model.CanvasTool

@Composable
internal fun DrawBoxCanvas(
    bitmap: ImageBitmap,
    version: Int,
    subscription: DrawBoxSubscription,
    enableInteraction: Boolean,
    currentAction: List<Pair<Offset, Offset>>,
    color: Color,
    strokeWidth: Float,
    opacity: Float,
    canvasTool: CanvasTool,
    canvasOpacity: Float,
    onSizeChanged: (IntSize) -> Unit,
    onTap: (Offset) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
        .then(other =
            if (enableInteraction) Modifier
                .onSizeChanged(onSizeChanged)
                .pointerInput(Unit) { detectTapGestures(onTap = onTap) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = onDragStart,
                        onDrag = { change, _ -> onDrag(change.position) },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd
                    )
                }
            else Modifier
        )
        .clipToBounds()
        .alpha(canvasOpacity)
    ) {
        key(version) {
            DrawBoxDisplay(
                bitmap = bitmap,
                subscription = subscription,
                currentAction = currentAction,
                color = color,
                strokeWidth = strokeWidth,
                opacity = opacity,
                canvasTool = canvasTool,
                canvasOpacity = canvasOpacity
            )
        }
    }
}