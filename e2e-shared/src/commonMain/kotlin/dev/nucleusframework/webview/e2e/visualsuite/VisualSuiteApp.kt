package dev.nucleusframework.webview.e2e.visualsuite

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nucleusframework.webview.jsbridge.IJsMessageHandler
import dev.nucleusframework.webview.jsbridge.JsMessage
import dev.nucleusframework.webview.jsbridge.WebViewJsBridge
import dev.nucleusframework.webview.jsbridge.rememberWebViewJsBridge
import dev.nucleusframework.webview.request.RequestInterceptor
import dev.nucleusframework.webview.request.WebRequest
import dev.nucleusframework.webview.request.WebRequestInterceptResult
import dev.nucleusframework.webview.web.WebView
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.WebViewState
import dev.nucleusframework.webview.e2e.currentTimeMillis
import dev.nucleusframework.webview.e2e.hostFromUrl
import dev.nucleusframework.webview.web.rememberWebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewStateWithHTMLData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Bg = Color(0xFF0B1220)
private val Card = Color(0xFF121A2B)
private val TextMain = Color(0xFFE8EEF9)
private val TextDim = Color(0xFF93A0B8)

/**
 * Full multiplatform visual e2e suite against a **real** platform WebView.
 * Same catalog on desktop / Android / iOS / Wasm (cases skip only when a
 * [SuiteCapability] is unavailable on the host).
 *
 * Writes a machine-readable report and calls [onFinished] with success flag.
 */
@Composable
fun VisualSuiteApp(
    onFinished: (passed: Boolean, reportPath: String) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val cases = remember { mutableStateListOf(*suiteCatalog().toTypedArray()) }
    val listState = rememberLazyListState()
    var currentId by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf("Starting…") }
    var done by remember { mutableStateOf(false) }

    // Interceptor policy controlled by suite runner
    var rejectHosts by remember { mutableStateOf(setOf<String>()) }
    var modifyMap by remember { mutableStateOf(mapOf<String, String>()) }

    val interceptor =
        remember {
            object : RequestInterceptor {
                override fun onInterceptUrlRequest(
                    request: WebRequest,
                    navigator: WebViewNavigator,
                ): WebRequestInterceptResult {
                    val host = hostFromUrl(request.url).orEmpty()
                    if (rejectHosts.any { host.contains(it) || request.url.contains(it) }) {
                        return WebRequestInterceptResult.Reject
                    }
                    modifyMap.entries.firstOrNull { request.url.contains(it.key) }?.let { e ->
                        return WebRequestInterceptResult.Modify(
                            request.copy(url = e.value),
                        )
                    }
                    return WebRequestInterceptResult.Allow
                }
            }
        }

    val navigator = rememberWebViewNavigator(coroutineScope = scope, requestInterceptor = interceptor)
    val state =
        rememberWebViewStateWithHTMLData(
            data = pageWithMarker("boot"),
            baseUrl = "https://suite.local/boot",
        ).also {
            it.webSettings.desktopWebSettings.transparent = false
            it.webSettings.backgroundColor = Color.White
            it.webSettings.isJavaScriptEnabled = true
        }
    val jsBridge = rememberWebViewJsBridge(navigator)

    // Bridge hit counters / last payloads for assertions
    val bridgeHits = remember { mutableStateListOf<String>() }
    var lastPingPayload by remember { mutableStateOf<String?>(null) }
    var lastPingCallbackAck by remember { mutableStateOf<String?>(null) }
    var secondaryHits by remember { mutableStateOf(0) }
    var onCreatedFired by remember { mutableStateOf(false) }

    DisposableEffect(jsBridge) {
        val ping =
            object : IJsMessageHandler {
                override fun methodName() = "suitePing"

                override fun handle(
                    message: JsMessage,
                    navigator: WebViewNavigator?,
                    callback: (String) -> Unit,
                ) {
                    lastPingPayload = message.params
                    bridgeHits += "suitePing:${message.params}"
                    val reply = """{"ok":true,"echo":${message.params}}"""
                    callback(reply)
                    lastPingCallbackAck = reply
                }
            }
        val secondary =
            object : IJsMessageHandler {
                override fun methodName() = "suiteSecondary"

                override fun handle(
                    message: JsMessage,
                    navigator: WebViewNavigator?,
                    callback: (String) -> Unit,
                ) {
                    secondaryHits++
                    bridgeHits += "suiteSecondary:${message.params}"
                    callback("""{"secondary":true}""")
                }
            }
        jsBridge.register(ping)
        jsBridge.register(secondary)
        onDispose {
            jsBridge.unregister(ping)
            jsBridge.unregister(secondary)
        }
    }

    fun updateCase(id: String, status: CaseStatus, detail: String = "") {
        val idx = cases.indexOfFirst { it.id == id }
        if (idx >= 0) {
            cases[idx] = cases[idx].copy(status = status, detail = detail)
        }
    }

    val parentHandle = rememberSuiteParentHandle()

    LaunchedEffect(Unit) {
        // Give the window a moment to map + embed WebView
        delay(700)
        val ctx =
            SuiteContext(
                state = state,
                navigator = navigator,
                jsBridge = jsBridge,
                bridgeHits = bridgeHits,
                getLastPingPayload = { lastPingPayload },
                getLastPingCallbackAck = { lastPingCallbackAck },
                getSecondaryHits = { secondaryHits },
                clearBridgeHits = {
                    bridgeHits.clear()
                    lastPingPayload = null
                    lastPingCallbackAck = null
                },
                setRejectHosts = { rejectHosts = it },
                setModifyMap = { modifyMap = it },
                getOnCreatedFired = { onCreatedFired },
                parentHandle = parentHandle,
            )
        val started = currentTimeMillis()
        var path = ""
        var passed = false
        try {
            runFullSuite(ctx) { id, status, detail ->
                currentId = id
                updateCase(id, status, detail)
                summary =
                    when (status) {
                        CaseStatus.Running -> "Running $id…"
                        CaseStatus.Passed -> "$id PASS"
                        CaseStatus.Failed -> "$id FAIL: $detail"
                        CaseStatus.Skipped -> "$id SKIP: $detail"
                        else -> summary
                    }
            }
        } catch (t: Throwable) {
            // Real cancellation (window disposed) must propagate.
            if (t is kotlinx.coroutines.CancellationException) throw t
            // Other failures (incl. waitWebView error()) must still exit the suite.
            val msg = t.message ?: t::class.simpleName ?: "suite aborted"
            summary = "ABORTED: $msg"
            if (cases.none { it.status == CaseStatus.Failed }) {
                val firstPending = cases.indexOfFirst { it.status == CaseStatus.Pending }
                if (firstPending >= 0) {
                    updateCase(cases[firstPending].id, CaseStatus.Failed, msg)
                }
            }
        } finally {
            val finished = currentTimeMillis()
            val report =
                SuiteReport(
                    startedAtMs = started,
                    finishedAtMs = finished,
                    cases = cases.toList(),
                )
            path = writeSuiteReport(report)
            // Skipped-only is not a failure; require zero fails and at least one pass.
            passed = report.allGreen
            summary =
                if (passed) {
                    "ALL GREEN  ${report.passed}/${report.total}  (${finished - started}ms)"
                } else {
                    "FAILED  pass=${report.passed} fail=${report.failed} skip=${report.skipped}  report=$path"
                }
            done = true
            // Keep the window visible long enough to inspect, then notify host.
            // Use NonCancellable delay path isn't needed; host exitProcess cleans up.
            try {
                delay(if (passed) 1500 else 4000)
            } catch (_: Throwable) {
                // ignore cancellation during teardown
            }
            onFinished(passed, path)
        }
    }

    // Auto-scroll running case into view
    LaunchedEffect(currentId) {
        val idx = cases.indexOfFirst { it.id == currentId }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    Row(
        Modifier.fillMaxSize().background(Bg).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // WebView pane
        Column(
            Modifier
                .weight(1.15f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF243049), RoundedCornerShape(12.dp))
                .background(Color.White),
        ) {
            WebView(
                state = state,
                navigator = navigator,
                webViewJsBridge = jsBridge,
                modifier = Modifier.fillMaxSize(),
                onCreated = { onCreatedFired = true },
            )
        }

        // Checklist pane
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(Card)
                .padding(12.dp),
        ) {
            Text(
                "Visual E2E Suite — full API coverage",
                color = TextMain,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(summary, color = if (done) Color(0xFF34D399) else TextDim, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            val progress =
                cases.count { it.status == CaseStatus.Passed || it.status == CaseStatus.Failed || it.status == CaseStatus.Skipped }
                    .toFloat() / cases.size.coerceAtLeast(1)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "pass=${cases.count { it.status == CaseStatus.Passed }}  " +
                    "fail=${cases.count { it.status == CaseStatus.Failed }}  " +
                    "skip=${cases.count { it.status == CaseStatus.Skipped }}  " +
                    "pending=${cases.count { it.status == CaseStatus.Pending }}",
                color = TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(cases, key = { it.id }) { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (c.id == currentId) Color(0xFF1B2740) else Color.Transparent,
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            c.status.name.take(4).uppercase(),
                            color = statusColor(c.status),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(48.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${c.id} · ${c.group} · ${c.title}",
                                color = TextMain,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                            if (c.detail.isNotBlank() && c.status != CaseStatus.Passed) {
                                Text(
                                    c.detail.take(140),
                                    color = statusColor(c.status),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal data class SuiteContext(
    val state: WebViewState,
    val navigator: WebViewNavigator,
    val jsBridge: WebViewJsBridge,
    val bridgeHits: MutableList<String>,
    val getLastPingPayload: () -> String?,
    val getLastPingCallbackAck: () -> String?,
    val getSecondaryHits: () -> Int,
    val clearBridgeHits: () -> Unit,
    val setRejectHosts: (Set<String>) -> Unit,
    val setModifyMap: (Map<String, String>) -> Unit,
    val getOnCreatedFired: () -> Boolean,
    /** Tao HWND for isolated Windows WebView2 instances (0 elsewhere). */
    val parentHandle: Long = 0L,
)
