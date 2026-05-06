package uk.codecymru.drawbox.box

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import uk.codecymru.drawbox.controller.BitmapDrawController
import uk.codecymru.drawbox.controller.DrawBoxSubscription

@Composable
fun DrawBox(
    controller: BitmapDrawController,
    enableInteraction: Boolean = true,
    subscription: DrawBoxSubscription = DrawBoxSubscription.DynamicUpdate,
    modifier: Modifier = Modifier,
) {
    var version by remember { mutableStateOf(-1) }

    LaunchedEffect(Unit) {
        controller.trigger.collect {
            version = it
        }
    }

    val bitmap = controller.internalBitmap
    val background by controller.background.collectAsState()

    val currentAction by controller._currentAction.collectAsState()

    val color by controller.color.collectAsState()
    val strokeWidth by controller.strokeWidth.collectAsState()
    val opacity by controller.opacity.collectAsState()
    val canvasTool by controller.canvasTool.collectAsState()

    val canvasOpacity by controller.canvasOpacity.collectAsState()

    Box(modifier = modifier) {
        DrawBoxBackground(
            background = background,
            modifier = Modifier.fillMaxSize(),
        )
        DrawBoxCanvas(
            bitmap = bitmap ?: ImageBitmap(1, 1),
            version = version,
            subscription = subscription,
            enableInteraction = enableInteraction,
            currentAction = currentAction,
            color = color,
            strokeWidth = strokeWidth,
            opacity = opacity,
            canvasTool = canvasTool,
            canvasOpacity = canvasOpacity,
            onSizeChanged = controller::connectToDrawBox,
            onTap = controller::onTap,
            onDragStart = controller::onDragStart,
            onDrag = controller::onDrag,
            onDragEnd = controller::onDragEnd,
            modifier = Modifier.fillMaxSize(),
        )
    }
}