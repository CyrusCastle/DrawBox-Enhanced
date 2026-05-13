package uk.codecymru.drawbox.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import uk.codecymru.drawbox.controller.getPixelArray
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

typealias DrawnPath = List<Pair<Offset, Offset>>

internal sealed interface DrawAction {
    val points: List<Any>

    fun getFinalPoint(): Offset?

    data class Path(
        override val points: DrawnPath,
        val paint: Paint
    ) : DrawAction {
        override fun getFinalPoint(): Offset? {
            return points.lastOrNull()?.second
        }
    }

    data class Fill(
        override val points: List<Offset>,
        val origin: Offset,
        val color: Color,
    ) : DrawAction {
        override fun getFinalPoint(): Offset {
            return origin
        }

        companion object {
            fun create(bitmap: ImageBitmap, startOffset: Offset, targetColor: Color): Fill? {
                val x = startOffset.x.toInt().coerceIn(0, bitmap.width - 1)
                val y = startOffset.y.toInt().coerceIn(0, bitmap.height - 1)

                val pixels = bitmap.getPixelArray()

                val startColor = pixels[y * bitmap.width + x]
                if (startColor == targetColor.toArgb()) return null

                val fillPoints = mutableListOf<Offset>()

                // Breadth-First Search
                val queue = ArrayDeque<IntOffset>()
                queue.add(IntOffset(x, y))

                val visited = BooleanArray(bitmap.width * bitmap.height)

                while (queue.isNotEmpty()) { // TODO following BFS has quite a lot of boxing
                    val curr = queue.removeFirst()
                    val cx = curr.x
                    val cy = curr.y

                    if (cx !in 0 until bitmap.width || cy !in 0 until bitmap.height) continue
                    val index = cy * bitmap.width + cx

                    if (!visited[index] && pixels[index] == startColor) {
                        visited[index] = true
                        fillPoints.add(Offset(cx.toFloat(), cy.toFloat()))

                        queue.add(IntOffset(cx + 1, cy))
                        queue.add(IntOffset(cx - 1, cy))
                        queue.add(IntOffset(cx, cy + 1))
                        queue.add(IntOffset(cx, cy - 1))
                    }
                }

                return Fill(fillPoints, startOffset, targetColor)
            }
        }
    }

    data class Spray(
        val path: DrawnPath,
        val paint: Paint
    ) : DrawAction {
        override fun getFinalPoint(): Offset? {
            return path.lastOrNull()?.second
        }

        override val points: List<Offset>

        init {
            val drawnPoints = path.map { it.first }

            val density = (paint.strokeWidth / 2).toInt()
            val sprayPoints = mutableListOf<Offset>()

            drawnPoints.forEach { (x, y) ->
                repeat(density) {
                    val angle = (0..360).random().toDouble() * (PI / 180)
                    val distance = (0..100).random().toFloat() / 100f * (paint.strokeWidth)// / 2)

                    val dX = x + (cos(angle) * distance).toFloat()
                    val dY = y + (sin(angle) * distance).toFloat()

                    sprayPoints.add(Offset(dX, dY))
                }
            }

            points = sprayPoints
        }
    }

    data class Shape(
        val start: Offset,
        val end: Offset,
        val shapeType: CanvasTool,
        val paint: Paint
    ) : DrawAction {
        override val points: List<Offset> = listOf(start, end)

        override fun getFinalPoint(): Offset {
            return start
        }
    }
}

internal fun DrawAction.applyTo(canvas: Canvas){
    when (this) {
        is DrawAction.Path -> {
            this.points.forEach { (from, to) ->
                canvas.drawLine(from, to, this.paint)
            }
        }
        is DrawAction.Fill -> {
            canvas.drawPoints(
                pointMode = PointMode.Points,
                points = this.points,
                paint = Paint().apply {
                    color = this@applyTo.color
                    strokeWidth = 1f
                    blendMode = BlendMode.SrcOver
                    isAntiAlias = false
                }
            )
        }
        is DrawAction.Spray -> {
            canvas.drawPoints(
                pointMode = PointMode.Points,
                points = this.points,
                paint = this.paint.apply {
                    strokeWidth = 1f
                }
            )
        }
        is DrawAction.Shape -> {
            when (this.shapeType){
                CanvasTool.SHAPE_LINE -> {
                    canvas.drawLine(this.start, this.end, this.paint)
                }

                CanvasTool.SHAPE_RECT -> {
                    canvas.drawRect(
                        left = minOf(this.start.x, this.end.x),
                        top = minOf(this.start.y, this.end.y),
                        right = maxOf(this.start.x, this.end.x),
                        bottom = maxOf(this.start.y, this.end.y),
                        paint = this.paint
                    )
                }

                CanvasTool.SHAPE_CIRCLE -> {
                    canvas.drawOval(
                        left = minOf(this.start.x, this.end.x),
                        top = minOf(this.start.y, this.end.y),
                        right = maxOf(this.start.x, this.end.x),
                        bottom = maxOf(this.start.y, this.end.y),
                        paint = this.paint
                    )

                }

                else -> {}
            }
        }
    }
}