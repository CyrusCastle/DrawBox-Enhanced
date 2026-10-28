package uk.codecymru.drawbox.box

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uk.codecymru.drawbox.controller.DrawController
import uk.codecymru.drawbox.controller.DrawBoxSubscription
import uk.codecymru.drawbox.controller.PendingText
import uk.codecymru.drawbox.model.DrawAction

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

    val textMeasurer = rememberTextMeasurer()
    SideEffect { controller.textMeasurer = textMeasurer }

    val bitmap = controller.internalBitmap
    val background by controller.background.collectAsState()
    val canvasOpacity by controller.canvasOpacity.collectAsState()

    val currentAction by controller.currentAction.collectAsState()
    val pendingText by controller.pendingText.collectAsState()
    val color by controller.color.collectAsState()
    val strokeWidth by controller.strokeWidth.collectAsState()

    val canvasTool by controller.canvasTool.collectAsState()
    LaunchedEffect(canvasTool) {
        controller.resolvePendingText()
        controller.finishCurve()
    }

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
            hasPendingText = pendingText != null,
            onSizeChanged = controller::connectToDrawBox,
            onTap = controller::onTap,
            onDragStart = controller::onDragStart,
            onDrag = controller::onDrag,
            onDragEnd = controller::onDragEnd,
            onTextTyped = controller::typePendingText,
            onTextBackspace = controller::backspacePendingText,
            onTextConfirm = controller::resolvePendingText,
            onTextCancel = controller::cancelText,
            modifier = Modifier.fillMaxSize(),
        )
        if (enableInteraction && pendingText != null) {
            PendingTextCaret(
                pending = pendingText!!,
                preview = currentAction as? DrawAction.Text,
                color = color,
                strokeWidth = strokeWidth,
            )
        }
    }
}

@Composable
private fun PendingTextCaret(pending: PendingText, preview: DrawAction.Text?, color: Color, strokeWidth: Float) {
    var visible by remember(pending) { mutableStateOf(true) }

    LaunchedEffect(pending) {
        while (true) {
            visible = true
            delay(500)
            visible = false
            delay(500)
        }
    }

    if (!visible) return

    val density = LocalDensity.current
    val caretHeightPx = with(density) { (strokeWidth * 2.2f).sp.toPx() * 1.2f }
    val caretWidthPx = with(density) { 2.dp.toPx() }
    val caretX = pending.offset.x + (preview?.layout?.size?.width?.toFloat() ?: 0f)
    val caretY = pending.offset.y

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawLine(
            color = color,
            start = Offset(caretX, caretY),
            end = Offset(caretX, caretY + caretHeightPx),
            strokeWidth = caretWidthPx
        )
    }
}

@Composable
fun DrawBoxViewer(
    controller: DrawController,
    subscription: DrawBoxSubscription = DrawBoxSubscription.DynamicUpdate,
    modifier: Modifier = Modifier
) = DrawBox(controller, false, subscription, modifier)