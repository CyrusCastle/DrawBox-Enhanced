package uk.codecymru.drawbox.model

sealed class CanvasAction {
    data class Draw(val path: PathWrapper) : CanvasAction()
    data class Erase(val erased: List<PathWrapper>) : CanvasAction()
}