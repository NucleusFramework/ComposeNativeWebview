@file:OptIn(ExperimentalComposeUiApi::class)

package dev.nucleusframework.webview.e2e

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.nucleusframework.webview.e2e.visualsuite.VisualSuiteApp
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Wasm entrypoint: runs the shared visual e2e suite against a real IFrame WebView.
 */
fun main() {
    val body: HTMLElement = document.body ?: return
    ComposeViewport(body) {
        VisualSuiteApp { passed, reportPath ->
            println("SUITE_FINISHED passed=$passed report=$reportPath")
        }
    }
}
