package uk.codecymru.drawbox.box

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import uk.codecymru.drawbox.controller.DrawBoxSubscription
import uk.codecymru.drawbox.controller.DrawController

@Composable
fun DrawBox(
    controller: DrawController,
    modifier: Modifier = Modifier,
) {
    val bitmap by controller.getBitmap(null, DrawBoxSubscription.DynamicUpdate).collectAsState()
    val background by controller.background.collectAsState()
    val canvasOpacity by controller.canvasOpacity.collectAsState()

    Box(modifier = modifier) {
        DrawBoxBackground(
            background = background,
            modifier = Modifier.fillMaxSize(),
        )
        DrawBoxCanvas(
            bitmap = bitmap,
            alpha = canvasOpacity,
            onSizeChanged = controller::connectToDrawBox,
            onTap = controller::onTap,
            onDragStart = controller::onDragStart,
            onDrag = controller::onDrag,
            onDragEnd = controller::onDragEnd,
            modifier = Modifier.fillMaxSize(),
        )
    }
}