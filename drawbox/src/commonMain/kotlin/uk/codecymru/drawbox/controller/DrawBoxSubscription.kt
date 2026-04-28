package uk.codecymru.drawbox.controller

sealed interface DrawBoxSubscription {
    object DynamicUpdate : DrawBoxSubscription
    object FinishDrawingUpdate : DrawBoxSubscription
}