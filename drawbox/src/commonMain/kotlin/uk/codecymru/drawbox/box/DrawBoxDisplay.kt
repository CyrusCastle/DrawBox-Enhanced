package uk.codecymru.drawbox.box

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import uk.codecymru.drawbox.controller.DrawBoxSubscription
import uk.codecymru.drawbox.model.DrawAction
import uk.codecymru.drawbox.model.applyTo

@Composable
internal fun DrawBoxDisplay(
    bitmap: ImageBitmap,
    subscription: DrawBoxSubscription,
    currentAction: DrawAction?,
    canvasOpacity: Float
){
    val emptyPaint = remember { Paint() }

    Canvas(Modifier.fillMaxSize().clipToBounds().alpha(canvasOpacity)) {
        drawIntoCanvas { canvas ->
            canvas.drawImage(bitmap, Offset.Zero, emptyPaint)

            if (subscription is DrawBoxSubscription.DynamicUpdate && currentAction != null){
                currentAction.applyTo(canvas)
            }
        }
    }
}