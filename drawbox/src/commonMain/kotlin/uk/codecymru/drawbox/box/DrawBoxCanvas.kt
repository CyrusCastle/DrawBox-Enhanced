package uk.codecymru.drawbox.box

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import uk.codecymru.drawbox.controller.DrawBoxSubscription
import uk.codecymru.drawbox.model.DrawAction

@Composable
internal fun DrawBoxCanvas(
    bitmap: ImageBitmap,
    version: Int,
    subscription: DrawBoxSubscription,
    enableInteraction: Boolean,
    currentAction: DrawAction?,
    canvasOpacity: Float,
    hasPendingText: Boolean,
    onSizeChanged: (IntSize) -> Unit,
    onTap: (Offset) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onTextTyped: (String) -> Unit,
    onTextBackspace: () -> Unit,
    onTextConfirm: () -> Unit,
    onTextCancel: () -> Unit,
    modifier: Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(hasPendingText, enableInteraction) {
        if (enableInteraction && hasPendingText) focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
        .then(other =
            if (enableInteraction) Modifier
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (!hasPendingText || event.type != KeyEventType.KeyDown) return@onKeyEvent false

                    when (event.key) {
                        Key.Backspace -> { onTextBackspace(); true }
                        Key.Enter, Key.NumPadEnter -> { onTextConfirm(); true }
                        Key.Escape -> { onTextCancel(); true }
                        else -> {
                            val codePoint = event.utf16CodePoint
                            val char = codePoint.toChar()
                            val isPrintable = codePoint > 0 && codePoint != 0xFFFF && !char.isISOControl() &&
                                    !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed

                            if (isPrintable) {
                                onTextTyped(char.toString())
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
                .onSizeChanged(onSizeChanged)
                .pointerInput(Unit) { detectTapGestures(onTap = onTap) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = onDragStart,
                        onDrag = { change, _ -> onDrag(change.position) },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd
                    )
                }
            else Modifier
        )
        .clipToBounds()
        .alpha(canvasOpacity)
    ) {
        key(version) {
            DrawBoxDisplay(
                bitmap = bitmap,
                subscription = subscription,
                currentAction = currentAction,
                canvasOpacity = canvasOpacity
            )
        }
    }
}