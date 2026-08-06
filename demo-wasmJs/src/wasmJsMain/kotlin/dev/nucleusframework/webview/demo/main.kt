@file:OptIn(ExperimentalComposeUiApi::class)

package dev.nucleusframework.webview.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

fun main() {
    val body: HTMLElement = document.body ?: return
    ComposeViewport(body) {
        App()
    }
}
