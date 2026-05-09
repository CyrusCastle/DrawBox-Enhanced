package uk.codecymru.drawbox.android.drawing

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import uk.codecymru.drawbox.controller.DrawController
import uk.codecymru.drawbox.controller.DrawBoxBackground
import uk.codecymru.drawbox.controller.DrawBoxSubscription
import kotlinx.coroutines.flow.collectLatest

@SuppressLint("CoroutineCreationDuringComposition")
@Composable
fun DrawingScreen() {
    val fillScope = rememberCoroutineScope()
    val drawController = remember {
        DrawController(
            fillScope = fillScope,
            startingColor = Color.Blue,
            startingBackground = DrawBoxBackground.ColourBackground(color = Color.Red, alpha = 0.15f),
            startingCanvasOpacity = 0.5f
        )
    }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            drawController.open(bitmap.asImageBitmap())
        }
    }

    Column {
        IconButton(onClick = {
            takePictureLauncher.launch()
        }) {
            Icon(Icons.Default.Add, contentDescription = null)
        }
        ExpandedDrawingScreen(drawController)
    }
}