![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?&style=flat&logo=kotlin&logoColor=white)

![Platform Android](https://img.shields.io/badge/platform-Android-green?logo=android)
![Platform iOS](https://img.shields.io/badge/platform-iOS-lightgrey?logo=apple)
![Platform JVM](https://img.shields.io/badge/platform-JVM-blue?logo=openjdk)
![Platform WASM](https://img.shields.io/badge/platform-WASM%20%2F%20JS-yellow?logo=javascript)

# DrawBox Enhanced
Building on the work of Mark Yavorskyi's [DrawBox](https://github.com/MarkYav/DrawBox), DrawBox Enhanced is a Compose Multiplatform library to quickly and easily provide a canvas for your users to draw on.

## Features
- Compose Multiplatform, targeting: JVM, Android, WASM, iOS
- Customisable background, stroke size, colours and opacities
- Eraser tool
- Fill tool
- Eyedropper tool
- Spray can tool
- Text tool
- Shape tools (line, curve, rectangle, oval)
- Undo, redo, or clear all brushstrokes
- An easy implementation

**Planned features:**
- Maybe a select/translate tool
- Maybe layers

[//]: # (## Demo)

[//]: # ()
[//]: # (https://user-images.githubusercontent.com/39382424/230722003-e9b91b28-706a-4048-a950-609f0b357151.mp4)

## Download

Using Gradle Kotlin DSL:
```kotlin
implementation("uk.codecymru.drawbox:DrawBox-Enhanced:2.3.0")
```

## Usage

```kotlin
val controller = remember { DrawController() }
DrawBox(drawController = controller, modifier = Modifier.fillMaxSize())
```

**Configuring settings**
```kotlin
// The following optional parameters can be set to 
val controller = remember {
    DrawController(
        startingTool = CanvasTool.PENCIL,
        startingOpacity = 1f,
        startingStrokeWidth = 10f,
        startingColor = Color.Red,
        startingBackground = DrawBoxBackground.NoBackground,
        startingCanvasOpacity = 1f
    )
}
```

**Enabling undo/redo**
```kotlin
val enableUndo by controller.canUndo.collectAsState(false)
val enableRedo by controller.canRedo.collectAsState(false)

// Then create a button for each:
IconButton(onClick = controller::undo, enabled = enableUndo) {
    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "undo")
}
IconButton(onClick = controller::redo, enabled = enableRedo) {
    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "redo")
}
```

**Switching tools**
```kotlin
// To see the current tool
val tool by controller.canvasTool.collectAsState()

// To set the current tool, as an example:
TextButton(onClick = { controller.canvasTool.value = CanvasTool.PENCIL }) {
    Text("Pencil")
}
TextButton(onClick = { controller.canvasTool.value = CanvasTool.ERASER }) {
    Text("Eraser")
}
// etc... (PENCIL, BRUSH, ERASER, FILL, EYEDROPPER, SPRAY_CAN, TEXT, SHAPE_LINE, SHAPE_CURVE,
// SHAPE_RECT, SHAPE_ROUNDED_RECT, SHAPE_CIRCLE, SHAPE_POLYGON)
```

**Importing a bitmap**
```kotlin
val bitmap: ImageBitmap = /* (...) */
drawController.open(bitmap)
```

**Exporting a bitmap**
```kotlin
// This provides the up-to-date drawn bitmap
controller.internalBitmap

// As an example, on the JVM target we could save this via:
TextButton(onClick = { saveImage(controller.internalBitmap) }) {
    Text("Save")
}

fun saveImage(bitmap: ImageBitmap) {
    val dialog = FileDialog(null as Frame?, "Save Image", FileDialog.SAVE)

    dialog.file = "image.png"
    dialog.isVisible = true

    val directory = dialog.directory
    val fileName = dialog.file

    if (directory != null && fileName != null) {
        val file = File(directory, fileName)

        ImageIO.write(
            bitmap.toAwtImage(),
            "png",
            file
        )
    }
}
```

**Creating another viewer**
```kotlin
// FinishDrawingUpdate means that this viewer won't show the user any in-progress strokes as they draw
DrawBoxViewer(
    controller = controller,
    subscription = DrawBoxSubscription.FinishDrawingUpdate,
    modifier = Modifier.size(200.dp).border(width = 1.dp, color = Color.Red)
)
```

## Examples

Two useful samples of this library's basic usage can be found, written by Mark, in the sample package:
- [Android](sample/android/src/main/java/uk/codecymru/drawbox/android/MainActivity.kt)
- [Desktop](sample/desktop/src/jvmMain/kotlin/Main.kt)

For example projects, see the following:
- [Farsigraphy](https://github.com/CyrusCastle/Farsigraphy/blob/main/composeApp/src/commonMain/kotlin/uk/codecymru/abjad/view/navigation/pages/flows/letter/shared/LetterSharedDraw.kt) (where it is used to allow users to practise drawing letters from the Farsi alphabet)
- [PaintWindow](https://github.com/CyrusCastle/Cyrus-Website/blob/master/composeApp/src/webMain/kotlin/uk/cyruscastle/www/ui/system/window/windows/picture/PaintWindow.kt) (where it is used to emulate an old MS Paint on my personal website)

## Author
The original project was created by [Mark Yavorskyi](https://www.linkedin.com/in/mark-yavorskyi/), with the Enhanced edition being created by Cyrus Castle

## Mark's Message
>I love my work.
The idea of creating this open-source project appeared because I needed a multiplatform (Android + desktop) library for drawing.
I found several popular libs for Android but there was **ZERO** for using in KMM/KMP.
I still have some aspects to improve and I will be happy if you share your feedback or propose an idea!

>Hope you enjoy it! \
Mark

## Alternatives
These were the alternatives I found while I was working on this, I can't promise this list is up-to-date or comprehensive but might be a useful place to start (along with klibs.io).

| Title | Author | Targets | Choose this if... |
|---|---|---|---|
| [DrawBox](https://klibs.io/project/MarkYav/DrawBox) | MarkYav | Android, JVM | Bare-bones version of this one, likely better performance all things considered |
| [DrawBox-Enhanced](https://klibs.io/project/CyrusCastle/DrawBox-Enhanced) | CyrusCastle | Android, iOS, JVM, Wasm, JS | I made this one so that I could have access to internal functions for very specific projects, plus I wanted more tools |
| [DrawBox](https://klibs.io/project/akshay2211/DrawBox) | akshay2211 | Android, iOS, JVM, Wasm, JS | You want a more professional diagramming/annotation editor |
| [ArtMaker](https://klibs.io/project/Fbada006/ArtMaker) | Fbada006 | Android, iOS | You want a more artistic sketching-style editor |
| [CanvasPainter](https://klibs.io/project/zeeshanali-k/CanvasPainter) | zeeshanali-k | Android, JVM, iOS, Wasm | You want the lightest possible painting canvas + color picker with minimal setup |

## License
Licensed under the Apache License, Version 2.0, [click here for the full license](LICENSE.txt).
