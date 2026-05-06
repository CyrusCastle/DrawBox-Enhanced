package uk.codecymru.drawbox.android.drawing

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import uk.codecymru.drawbox.box.DrawBox
import uk.codecymru.drawbox.controller.BitmapDrawController
import uk.codecymru.drawbox.controller.DrawBoxSubscription
import uk.codecymru.drawbox.controller.DrawController
import uk.codecymru.drawbox.model.CanvasTool

@Composable
internal fun ExpandedDrawingScreen(
    drawController: BitmapDrawController,
) {
    val enableUndo by drawController.canUndo.collectAsState()
    val enableRedo by drawController.canRedo.collectAsState()

    Column {
        DrawBox(
            drawController,
            false,
            DrawBoxSubscription.FinishDrawingUpdate,
            Modifier
                .size(250.dp)
                .border(1.dp, Color.Red)
        )

        Column(modifier = Modifier.weight(4.5f, false)) {
            DrawBox(
                controller = drawController,
                modifier = Modifier
                    .aspectRatio(1f)
                    .padding(8.dp)
                    .border(width = 1.dp, color = Color.Blue)
                    .weight(1f, fill = false),
            )
            Row {
                IconButton(onClick = drawController::undo, enabled = enableUndo) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "undo")
                }
                IconButton(onClick = drawController::redo, enabled = enableRedo) {
                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "redo")
                }
                IconButton(onClick = drawController::reset, enabled = enableUndo || enableRedo) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = "reset")
                }
                TextButton(onClick = { drawController.canvasTool.value = CanvasTool.BRUSH }) {
                    Text("Brush")
                }
                TextButton(onClick = { drawController.canvasTool.value = CanvasTool.ERASER }) {
                    Text("Eraser")
                }
                TextButton(onClick = { drawController.canvasTool.value = CanvasTool.FILL }) {
                    Text("Fill")
                }
            }
        }
    }
}