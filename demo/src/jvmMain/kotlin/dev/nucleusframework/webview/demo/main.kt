package dev.nucleusframework.webview.demo

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() {
//    System.setProperty("compose.swing.render.on.graphics", "true")
//    System.setProperty("compose.interop.blending", "true")

    application {

        Window(
            onCloseRequest = ::exitApplication,
            title = "composewebview",
        ) {
            App()
        }
    }
}