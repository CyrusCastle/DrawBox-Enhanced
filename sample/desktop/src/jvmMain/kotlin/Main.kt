import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.markyav.drawbox.box.DrawBox
import io.github.markyav.drawbox.controller.DrawBoxBackground
import io.github.markyav.drawbox.controller.DrawBoxSubscription
import io.github.markyav.drawbox.controller.DrawController
import io.github.markyav.drawbox.model.CanvasTool

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        val controller = remember { DrawController() }
        val bitmap by remember { controller.getBitmap(250, DrawBoxSubscription.DynamicUpdate) }.collectAsState()
        val bitmapFinishDrawingUpdate by remember { controller.getBitmap(250, DrawBoxSubscription.FinishDrawingUpdate) }.collectAsState()

        val undoCount by controller.undoCount.collectAsState()
        val redoCount by controller.redoCount.collectAsState()
        val enableUndo by remember { derivedStateOf { undoCount > 0 } }
        val enableRedo by remember { derivedStateOf { redoCount > 0 } }

        val strokeWith by controller.strokeWidth.collectAsState()
        val canvasOpacity by controller.canvasOpacity.collectAsState()
        val background by controller.background.collectAsState()

        LaunchedEffect(Unit) {
            controller.background.value = DrawBoxBackground.ColourBackground(color = Color.Blue, alpha = 0.15f)
            controller.canvasOpacity.value = 0.5f
        }

        Row {
            Column(modifier = Modifier.weight(2f, false)) {
                Row {
                    IconButton(onClick = controller::undo, enabled = enableUndo) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "undo")
                    }
                    IconButton(onClick = controller::redo, enabled = enableRedo) {
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "redo")
                    }
                    IconButton(onClick = controller::reset, enabled = enableUndo || enableRedo) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "reset")
                    }
                }
                Row(modifier = Modifier.padding(end = 8.dp)){
                    Column(modifier = Modifier.weight(2f, false)){
                        Text("Tool")
                        Row {
                            TextButton(onClick = { controller.canvasTool.value = CanvasTool.BRUSH }) {
                                Text("Brush")
                            }
                            TextButton(onClick = { controller.canvasTool.value = CanvasTool.ERASER }) {
                                Text("Eraser")
                            }
                        }
                    }
                }
                Row(modifier = Modifier.padding(end = 8.dp)) {
                    Column(modifier = Modifier.weight(2f, false)) {
                        Text("Stroke width")
                        Slider(
                            value = strokeWith,
                            onValueChange = { controller.strokeWidth.value = it },
                            valueRange = 1f..100f
                        )
                    }
                    Column(modifier = Modifier.weight(2f, false)) {
                        Text("Canvas opacity")
                        Slider(
                            value = canvasOpacity,
                            onValueChange = { controller.canvasOpacity.value = it },
                            valueRange = 0f..1f
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.padding(end = 8.dp)) {
                    Column(modifier = Modifier.weight(2f, true)) {
                        Text("Color")
                        Row {
                            TextButton(onClick = { controller.color.value = Color.Red }) {
                                Text("Red")
                            }
                            TextButton(onClick = { controller.color.value = Color.Green }) {
                                Text("Green")
                            }
                            TextButton(onClick = { controller.color.value = Color.Yellow }) {
                                Text("Yellow")
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(2f, false)) {
                        Text("Background opacity")
                        Slider(
                            value = (background as? DrawBoxBackground.ColourBackground)?.alpha ?: 0f,
                            onValueChange = { controller.background.value = DrawBoxBackground.ColourBackground(color = Color.Blue, alpha = it) },
                            valueRange = 0f..1f
                        )
                    }
                }
                DrawBox(
                    controller = controller,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .padding(100.dp)
                        .border(width = 1.dp, color = Color.Blue),
                )
            }
            Column(modifier = Modifier.weight(1f, false)) {
                Text("DynamicUpdate:")
                Spacer(modifier = Modifier.height(10.dp))
                Image(
                    bitmap,
                    contentDescription = "drawn bitmap",
                    modifier = Modifier.size(200.dp).border(width = 1.dp, color = Color.Red),
                )

                Spacer(modifier = Modifier.height(50.dp))

                Text("FinishDrawingUpdate:")
                Spacer(modifier = Modifier.height(10.dp))
                Image(
                    bitmapFinishDrawingUpdate,
                    contentDescription = "drawn bitmap",
                    modifier = Modifier.size(200.dp).border(width = 1.dp, color = Color.Red),
                )
            }
        }
    }
}
