package dev.nucleusframework.webview.e2e

import androidx.compose.ui.window.ComposeUIViewController
import dev.nucleusframework.webview.e2e.visualsuite.VisualSuiteApp
import platform.Foundation.NSLog

/**
 * iOS entrypoint: runs the shared visual e2e suite against a real WKWebView.
 */
@Suppress("FunctionName") // iOS entrypoint for Xcode
fun MainViewController() =
    ComposeUIViewController {
        VisualSuiteApp { passed, reportPath ->
            NSLog("SUITE_FINISHED passed=%@ report=%@", passed.toString(), reportPath)
        }
    }
