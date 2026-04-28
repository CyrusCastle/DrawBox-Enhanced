package io.github.markyav.drawbox.box

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import io.github.markyav.drawbox.controller.DrawBoxBackground

@Composable
fun DrawBoxBackground(
    background: DrawBoxBackground,
    modifier: Modifier,
) {
    when (background) {
        is DrawBoxBackground.ColourBackground -> Box(
            modifier = modifier
                .alpha(alpha = background.alpha)
                .background(color = background.color)
        )
        is DrawBoxBackground.ImageBackground -> Image(
            bitmap = background.bitmap,
            alpha = background.alpha,
            modifier = modifier,
            contentDescription = null
        )
        is DrawBoxBackground.ComposableBackground -> background.content
        is DrawBoxBackground.TransparentBackground -> Box(
            modifier.drawWithCache {
                val sizePx = 12.dp.toPx()

                val columns = (size.width / sizePx).toInt() + 1
                val rows = (size.height / sizePx).toInt() + 1

                onDrawBehind {
                    clipRect {
                        for (row in 0..rows) {
                            for (col in 0..columns) {

                                val isDark = (row + col) % 2 == 0

                                drawRect(
                                    color = if (isDark) Color(0xFFCCCCCC) else Color.White,
                                    topLeft = Offset(col * sizePx, row * sizePx),
                                    size = Size(sizePx, sizePx)
                                )
                            }
                        }
                    }
                }
            }
        )
        is DrawBoxBackground.NoBackground -> { }
    }
}