package uk.codecymru.drawbox.controller

import androidx.compose.ui.unit.IntSize

sealed interface DrawBoxConnectionState {
    object Disconnected : DrawBoxConnectionState
    data class Connected(val size: IntSize, val alpha: Float = 1f) : DrawBoxConnectionState
}