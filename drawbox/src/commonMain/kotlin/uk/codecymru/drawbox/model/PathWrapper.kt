package uk.codecymru.drawbox.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class PathWrapper(
    var points: List<Offset>,
    val strokeWidth: Float = 5f,
    val strokeColor: Color,
    val alpha: Float = 1f
)