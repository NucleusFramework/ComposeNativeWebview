package dev.nucleusframework.webview.demo

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.webview.demo.visualsuite.VisualSuiteApp
import dev.nucleusframework.window.TitleBar
import kotlin.system.exitProcess

/**
 * Full visual e2e suite entrypoint.
 *
 * Runs a real Tao window + WebKit WebView and exercises the full desktop API.
 * Writes `/tmp/composewebview-visual-suite-report.txt` and exits 0/1.
 *
 *   ./gradlew :demo:run
 */
fun main() {
    var exitCode = 1
    nucleusApplication(backend = NucleusBackend.Tao) {
        val windowState = rememberWindowState(size = DpSize(1400.dp, 900.dp))
        DecoratedWindow(
            onCloseRequest = {
                exitApplication()
                exitProcess(exitCode)
            },
            state = windowState,
            title = "ComposeNativeWebView — Visual E2E Suite",
        ) {
            TitleBar { }
            VisualSuiteApp { passed, reportPath ->
                println("SUITE_FINISHED passed=$passed report=$reportPath")
                exitCode = if (passed) 0 else 1
                exitApplication()
                // Ensure process exit for CI / agent validation
                exitProcess(exitCode)
            }
        }
    }
}
