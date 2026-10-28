package uk.codecymru.drawbox.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import uk.codecymru.drawbox.controller.getPixelArray
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

typealias DrawnPath = List<Pair<Offset, Offset>>

internal sealed interface DrawAction {
    val points: List<Any>

    fun getFinalPoint(): Offset?

    data class Path(
        override val points: DrawnPath,
        val paint: Paint,
        val haloPaint: Paint? = null
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
        val paint: Paint,
        val seed: Long = Random.nextLong()
    ) : DrawAction {
        override fun getFinalPoint(): Offset? {
            return path.lastOrNull()?.second
        }

        override val points: List<Offset>

        init {
            val random = Random(seed)
            val drawnPoints = path.map { it.first }

            val density = (paint.strokeWidth / 2).toInt()
            val sprayPoints = mutableListOf<Offset>()

            drawnPoints.forEach { (x, y) ->
                repeat(density) {
                    val angle = random.nextInt(0, 361).toDouble() * (PI / 180)
                    val distance = random.nextInt(0, 101).toFloat() / 100f * (paint.strokeWidth)// / 2)

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

    data class Curve(
        val start: Offset,
        val end: Offset,
        val control: Offset,
        val paint: Paint
    ) : DrawAction {
        override val points: List<Offset> = listOf(start, control, end)

        override fun getFinalPoint(): Offset = end
    }

    data class Polygon(
        override val points: List<Offset>,
        val paint: Paint,
        val closed: Boolean = false
    ) : DrawAction {
        override fun getFinalPoint(): Offset? {
            return points.lastOrNull()
        }
    }

    data class Text(
        val offset: Offset,
        val layout: TextLayoutResult
    ) : DrawAction {
        override val points: List<Offset> = listOf(offset)

        override fun getFinalPoint(): Offset = offset
    }
}

internal fun DrawAction.applyTo(canvas: Canvas){
    when (this) {
        is DrawAction.Path -> {
            this.points.forEach { (from, to) ->
                this.haloPaint?.let { canvas.drawLine(from, to, it) }
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

                CanvasTool.SHAPE_ROUNDED_RECT -> {
                    val left = minOf(this.start.x, this.end.x)
                    val top = minOf(this.start.y, this.end.y)
                    val right = maxOf(this.start.x, this.end.x)
                    val bottom = maxOf(this.start.y, this.end.y)
                    val corner = minOf(right - left, bottom - top).coerceAtMost(48f) / 2.5f

                    canvas.drawRoundRect(left, top, right, bottom, corner, corner, this.paint)
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
        is DrawAction.Curve -> {
            val start = this.start
            val control = this.control
            val end = this.end

            val curvePath = Path().apply {
                moveTo(start.x, start.y)
                quadraticTo(control.x, control.y, end.x, end.y)
            }

            canvas.drawPath(curvePath, this.paint)
        }
        is DrawAction.Polygon -> {
            val vertices = this.points
            if (vertices.isEmpty()) return

            if (vertices.size == 1) {
                canvas.drawLine(vertices.first(), vertices.first(), this.paint)
                return
            }

            val closed = this.closed

            val polygonPath = Path().apply {
                moveTo(vertices.first().x, vertices.first().y)
                vertices.drop(1).forEach { lineTo(it.x, it.y) }
                if (closed) close()
            }

            canvas.drawPath(polygonPath, this.paint)
        }
        is DrawAction.Text -> {
            val layout = this.layout
            val topLeft = this.offset

            CanvasDrawScope().draw(
                density = Density(1f),
                layoutDirection = LayoutDirection.Ltr,
                canvas = canvas,
                size = Size(layout.size.width.toFloat(), layout.size.height.toFloat())
            ) {
                drawText(textLayoutResult = layout, topLeft = topLeft)
            }
        }
    }
}