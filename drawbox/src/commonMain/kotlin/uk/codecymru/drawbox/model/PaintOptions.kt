package uk.codecymru.drawbox.model

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

internal data class PaintOptions(
    val color: Color,
    val strokeWidth: Float,
    val opacity: Float,
    val tool: CanvasTool
){
    fun createPaint(): Paint {
        return Paint().apply {
            strokeWidth = this@PaintOptions.strokeWidth
            style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
            strokeJoin = StrokeJoin.Round
            if (this@PaintOptions.tool == CanvasTool.ERASER) {
                blendMode = BlendMode.Clear
            } else {
                color = this@PaintOptions.color
                alpha = this@PaintOptions.opacity
                blendMode = BlendMode.SrcOver
            }
        }
    }
}