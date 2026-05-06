package uk.codecymru.drawbox.box

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.TextPainter.paint
import uk.codecymru.drawbox.controller.DrawBoxSubscription
import uk.codecymru.drawbox.model.CanvasTool
import uk.codecymru.drawbox.model.DrawnPath
import uk.codecymru.drawbox.model.PaintOptions

@Composable
internal fun DrawBoxDisplay(
    bitmap: ImageBitmap,
    subscription: DrawBoxSubscription,
    currentAction: DrawnPath,
    color: Color,
    strokeWidth: Float,
    opacity: Float,
    canvasTool: CanvasTool,
    canvasOpacity: Float
){
    val emptyPaint = remember { Paint() }

    Canvas(Modifier.fillMaxSize().clipToBounds().alpha(canvasOpacity)) {
        drawIntoCanvas { canvas ->
            canvas.drawImage(bitmap, Offset.Zero, emptyPaint)

            if (subscription is DrawBoxSubscription.DynamicUpdate){
                canvas.drawCurrentAction(currentAction, color, strokeWidth, opacity, canvasTool)
            }
        }
    }
}

private fun Canvas.drawCurrentAction(
    currentAction: DrawnPath,
    color: Color,
    strokeWidth: Float,
    opacity: Float,
    canvasTool: CanvasTool
) {
    val paint = PaintOptions(
        color,
        strokeWidth,
        opacity,
        canvasTool
    ).createPaint()

    when (canvasTool) {
        CanvasTool.SHAPE_RECT -> {
            currentAction.forEach { (from, to) ->
                this.drawRect(
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
                this.drawOval(
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
                this.drawLine(from, to, paint)
            }
        }
    }
}