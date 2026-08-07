package dev.nucleusframework.webview.e2e

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.nucleusframework.webview.e2e.visualsuite.VisualSuiteApp
import kotlin.system.exitProcess

/**
 * Runs the shared multiplatform visual e2e suite against a real Android WebView.
 * Exit process when possible so instrumented/CI hosts can observe the result.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VisualSuiteApp { passed, reportPath ->
                Log.i(TAG, "SUITE_FINISHED passed=$passed report=$reportPath")
                // finish() only; process exit is for pure JVM hosts.
                finish()
                if (!passed) {
                    // Still mark the process as failed when running under instrumentation.
                    runCatching { exitProcess(1) }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ComposeWebViewE2E"
    }
}
