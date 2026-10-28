package uk.codecymru.drawbox.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint

internal object DrawActionFactory {
    internal fun create(bitmap: ImageBitmap, drawnPath: DrawnPath, tool: CanvasTool, paint: Paint, haloPaint: Paint? = null): DrawAction? {
        return when (tool){
            CanvasTool.PENCIL, CanvasTool.BRUSH, CanvasTool.ERASER -> DrawAction.Path(
                points = drawnPath.toList(),
                paint = paint,
                haloPaint = haloPaint
            )
            CanvasTool.SPRAY_CAN -> DrawAction.Spray(drawnPath, paint)
            CanvasTool.SHAPE_LINE, CanvasTool.SHAPE_RECT, CanvasTool.SHAPE_ROUNDED_RECT, CanvasTool.SHAPE_CIRCLE -> {
                DrawAction.Shape(
                    start = drawnPath.first().first,
                    end = drawnPath.first().second,
                    shapeType = tool,
                    paint = paint
                )
            }
            CanvasTool.FILL -> DrawAction.Fill.create(bitmap, drawnPath.first().first, paint.color)
            else -> null
        }
    }

    internal fun extend(action: DrawAction?, bitmap: ImageBitmap, tool: CanvasTool, paint: Paint, haloPaint: Paint?, from: Offset, to: Offset): DrawAction? {
        return when (action){
            is DrawAction.Path -> DrawAction.Path(action.points + (from to to), paint, haloPaint)
            is DrawAction.Fill -> action
            is DrawAction.Shape -> DrawAction.Shape(action.start, to, tool, paint)
            is DrawAction.Spray -> DrawAction.Spray(action.path + (from to to), paint, action.seed)

            is DrawAction.Curve -> action
            is DrawAction.Polygon -> action
            is DrawAction.Text -> action
            null -> create(bitmap, listOf(from to to), tool, paint, haloPaint)
        }
    }
}