package uk.codecymru.drawbox.android.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import uk.codecymru.drawbox.android.drawing.DrawingScreen

@Composable
fun MainScreen() {
    Row {
        Column(modifier = Modifier.weight(2f, true)) {
            DrawingScreen()
        }
    }
}