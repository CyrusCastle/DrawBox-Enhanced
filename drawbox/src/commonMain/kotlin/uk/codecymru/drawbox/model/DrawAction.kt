package uk.codecymru.drawbox.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint

typealias DrawnPath = List<Pair<Offset, Offset>>

internal sealed interface DrawAction {
    val points: List<Any>

    data class Path(
        override val points: DrawnPath,
        val paint: Paint
    ) : DrawAction

    data class Fill(
        override val points: List<Offset>,
        val color: Color
    ) : DrawAction

    data class Spray(
        override val points: List<Offset>,
        val paint: Paint
    ) : DrawAction

    data class Shape(
        val start: Offset,
        val end: Offset,
        val shapeType: CanvasTool,
        val paint: Paint
    ) : DrawAction {
        override val points: List<Offset> = listOf(start, end)
    }
}