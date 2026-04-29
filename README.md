![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?&style=flat&logo=kotlin&logoColor=white)

![Platform Android](https://img.shields.io/badge/platform-Android-green?logo=android)
![Platform iOS](https://img.shields.io/badge/platform-iOS-lightgrey?logo=apple)
![Platform JVM](https://img.shields.io/badge/platform-JVM-blue?logo=openjdk)
![Platform WASM](https://img.shields.io/badge/platform-WASM%20%2F%20JS-yellow?logo=javascript)

# DrawBox Enhanced
Building on the work of Mark Yavorskyi's [DrawBox](https://github.com/MarkYav/DrawBox), DrawBox Enhanced is a Compose Multiplatform library to quickly and easily provide a canvas for your users to draw on.

## Features
- Compose Multiplatform, targeting: JVM, Android, WASM
- Customisable background, stroke size, colours and opacities
- Eraser tool
- Fill tool
- Eyedropper tool
- Spray can tool
- Shape tools (line, rectangle, oval)
- Undo, redo, or clear all brushstrokes
- An easy implementation

**Planned features:**
- Import/export bitmaps
- Maybe a select/translate tool
- Maybe a text tool
- Layers

[//]: # (## Demo)

[//]: # ()
[//]: # (https://user-images.githubusercontent.com/39382424/230722003-e9b91b28-706a-4048-a950-609f0b357151.mp4)

## Download

Using Gradle Kotlin DSL:
```kotlin
implementation("uk.codecymru.drawbox:drawbox:2.0.0")
```

## Usage

```kotlin
val controller = remember { BitmapDrawController() }
DrawBox(drawController = controller, modifier = Modifier.fillMaxSize())
```

**Enabling fill/eyedropper**
```kotlin
// To use the fill/eyedropper tools, the DrawController will need to be fed a coroutine scope
val fillScope = rememberCoroutineScope()
val controller = remember { BitmapDrawController(fillScope) }
```

**Enabling undo/redo**
```kotlin
val enableUndo by controller.canUndo.collectAsState()
val enableRedo by controller.canRedo.collectAsState()

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
TextButton(onClick = { controller.canvasTool.value = CanvasTool.BRUSH }) {
    Text("Brush")
}
TextButton(onClick = { controller.canvasTool.value = CanvasTool.ERASER }) {
    Text("Eraser")
}
// etc...
```

## Examples

Two useful samples of this library's basic usage can be found, written by Mark, in the sample package:
- [Android](sample/android/src/main/java/io/github/markyav/drawbox/android/MainActivity.kt)
- [Desktop](sample/desktop/src/jvmMain/kotlin/Main.kt)

For example projects, see the following:
- ~[Farsigraphy](https://github.com/CyrusCastle/Farsigraphy/blob/main/composeApp/src/commonMain/kotlin/uk/codecymru/abjad/view/navigation/pages/flows/letter/shared/LetterSharedDraw.kt) (where it is used to allow users to practise drawing letters from the Farsi alphabet)~ (REPO CURRENTLY PRIVATE)
- ~[PaintWindow](https://github.com/CyrusCastle/Cyrus-Website/blob/master/composeApp/src/webMain/kotlin/uk/cyruscastle/www/ui/system/window/windows/picture/PaintWindow.kt) (where it is used to emulate an old MS Paint on my personal website)~ (REPO CURRENTLY PRIVATE)

## Author
The original project was created by [Mark Yavorskyi](https://www.linkedin.com/in/mark-yavorskyi/), with the Enhanced edition being created by Cyrus Castle

## Mark's Message
>I love my work.
The idea of creating this open-source project appeared because I needed a multiplatform (Android + desktop) library for drawing.
I found several popular libs for Android but there was **ZERO** for using in KMM/KMP.
I still have some aspects to improve and I will be happy if you share your feedback or propose an idea!

>Hope you enjoy it! \
Mark

## License
Licensed under the Apache License, Version 2.0, [click here for the full license](LICENSE.txt).
