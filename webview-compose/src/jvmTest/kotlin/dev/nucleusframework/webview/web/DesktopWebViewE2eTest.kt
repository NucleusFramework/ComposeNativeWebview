package dev.nucleusframework.webview.web

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.webview.web.e2e.E2eHost
import dev.nucleusframework.webview.web.linux.WebKitLinuxBridge
import dev.nucleusframework.webview.web.macos.WebKitMacOsBridge
import dev.nucleusframework.webview.web.windows.WebView2WindowsBridge
import dev.nucleusframework.window.TitleBar
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test

/**
 * End-to-end tests against a real desktop WebView embedded via Nucleus
 * NativeView + Tao backend. No mocks.
 *
 *  - **Linux**: WebKit2GTK (`compose_webview_linux`)
 *  - **macOS**: WKWebView (`compose_webview_macos`)
 *  - **Windows**: WebView2 (`compose_webview_windows`)
 *
 * Drivers live in [dev.nucleusframework.webview.web.e2e].
 * Self-skips when the host OS/backend is unavailable.
 */
class DesktopWebViewE2eTest {
    @Test
    fun allDesktopWebViewFeaturesE2e() {
        when (Platform.Current) {
            Platform.Linux -> {
                if (!WebKitLinuxBridge.isLoaded) {
                    println("SKIPPED: compose_webview_linux native library not loaded")
                    return
                }
                if (System.getenv("DISPLAY").isNullOrBlank() &&
                    System.getenv("WAYLAND_DISPLAY").isNullOrBlank()
                ) {
                    println("SKIPPED: no DISPLAY / WAYLAND_DISPLAY")
                    return
                }
            }
            Platform.MacOS -> {
                if (!WebKitMacOsBridge.isLoaded) {
                    println("SKIPPED: compose_webview_macos native library not loaded")
                    return
                }
            }
            Platform.Windows -> {
                if (!WebView2WindowsBridge.isLoaded) {
                    println("SKIPPED: compose_webview_windows native library not loaded")
                    return
                }
            }
            else -> {
                println("SKIPPED: desktop e2e unsupported on ${Platform.Current}")
                return
            }
        }

        val failures = CopyOnWriteArrayList<String>()
        val completed = AtomicReference(false)

        thread(isDaemon = true, name = "webview-e2e-watchdog") {
            Thread.sleep(WATCHDOG_MS)
            if (!completed.get()) {
                System.err.println("WATCHDOG: e2e suite timed out")
                Runtime.getRuntime().halt(42)
            }
        }

        nucleusApplication(backend = NucleusBackend.Tao) {
            val windowState = rememberWindowState(size = DpSize(900.dp, 700.dp))
            DecoratedWindow(
                onCloseRequest = ::exitApplication,
                state = windowState,
                title = "webview-e2e",
            ) {
                TitleBar { }
                E2eHost(
                    onFailure = { failures += it },
                    onDone = {
                        completed.set(true)
                        exitApplication()
                    },
                )
            }
        }

        val failureSummary =
            if (failures.isEmpty()) {
                null
            } else {
                "E2E failures:\n" + failures.joinToString("\n") { " - $it" }
            }
        if (failureSummary != null) {
            println(failureSummary)
        }
        // Prefer a hard AssertionError so the suite cannot be recorded as "skipped"
        // when the Tao event loop exits before kotlin.test.fail propagates cleanly.
        check(completed.get()) { "suite did not complete" }
        check(failureSummary == null) { failureSummary!! }
    }

    private companion object {
        const val WATCHDOG_MS = 180_000L
    }
}
