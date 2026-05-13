package uk.codecymru.drawbox.box

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import uk.codecymru.drawbox.controller.DrawController
import uk.codecymru.drawbox.controller.DrawBoxSubscription

@Composable
fun DrawBox(
    controller: DrawController,
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
    val canvasOpacity by controller.canvasOpacity.collectAsState()

    val currentAction by controller.currentAction.collectAsState()

    Box(modifier = modifier) {
        DrawBoxBackground(
            background = background,
            modifier = Modifier.fillMaxSize(),
        )
        DrawBoxCanvas(
            bitmap = bitmap,
            version = version,
            subscription = subscription,
            enableInteraction = enableInteraction,
            currentAction = currentAction,
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

@Composable
fun DrawBoxViewer(
    controller: DrawController,
    subscription: DrawBoxSubscription = DrawBoxSubscription.DynamicUpdate,
    modifier: Modifier = Modifier
) = DrawBox(controller, false, subscription, modifier)