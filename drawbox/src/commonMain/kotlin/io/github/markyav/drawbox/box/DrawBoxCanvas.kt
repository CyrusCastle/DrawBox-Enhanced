package io.github.markyav.drawbox.box

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

@Composable
fun DrawBoxCanvas(
    bitmap: ImageBitmap,
    alpha: Float,
    onSizeChanged: (IntSize) -> Unit,
    onTap: (Offset) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier,
) {
    Canvas(modifier = modifier
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
        .clipToBounds()
        .alpha(alpha)
    ) {
        drawImage(image = bitmap) // TODO need to think about re-adding "opened image" above here, between the background and the drawn image
    }
}