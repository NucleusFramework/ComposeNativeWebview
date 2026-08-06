package dev.nucleusframework.webview.demo

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
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import dev.nucleusframework.webview.jsbridge.rememberWebViewJsBridge
import dev.nucleusframework.webview.web.LoadingState
import dev.nucleusframework.webview.web.WebView
import dev.nucleusframework.webview.web.WebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewNavigator
import dev.nucleusframework.webview.web.rememberWebViewStateWithHTMLData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PanelBg = Color(0xFF0F1419)
private val CardBg = Color(0xFF1A2332)
private val Accent = Color(0xFF60A5FA)
private val Ok = Color(0xFF34D399)
private val Warn = Color(0xFFFBBF24)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextMuted = Color(0xFF8B9CB3)

/**
 * Visual lab: real Compose UI + real WebView + bidirectional JS bridge.
 * Left = WebView page, right = Compose log of every hop.
 */
@Composable
fun VisualBridgeLab() {
    val scope = rememberCoroutineScope()
    val logs = remember { mutableStateListOf<LogLine>() }
    val listState = rememberLazyListState()

    fun log(kind: String, message: String, color: Color = TextPrimary) {
        val ts = nowTimestamp()
        logs.add(0, LogLine(ts, kind, message, color))
        if (logs.size > 200) logs.removeRange(200, logs.size)
    }

    val navigator = rememberWebViewNavigator(coroutineScope = scope)
    val state =
        rememberWebViewStateWithHTMLData(
            data = VISUAL_BRIDGE_HTML,
            baseUrl = "https://bridge.local/",
        ).also {
            it.webSettings.desktopWebSettings.transparent = false
            it.webSettings.backgroundColor = Color.White
        }
    val jsBridge = rememberWebViewJsBridge(navigator)

    var lastNativeToJs by remember { mutableStateOf("—") }
    var lastJsToNative by remember { mutableStateOf("—") }
    var lastCallback by remember { mutableStateOf("—") }
    var roundTripCount by remember { mutableStateOf(0) }
    var autoRunning by remember { mutableStateOf(true) }

    DisposableEffect(jsBridge) {
        val ping =
            object : IJsMessageHandler {
                override fun methodName() = "ping"

                override fun handle(
                    message: JsMessage,
                    navigator: WebViewNavigator?,
                    callback: (String) -> Unit,
                ) {
                    lastJsToNative = message.params
                    log("JS → KT", "ping params=${message.params}", Accent)
                    val reply = """{"pong":true,"echo":${message.params},"from":"kotlin"}"""
                    callback(reply)
                    log("KT → JS", "callback → $reply", Ok)
                    lastCallback = reply
                    roundTripCount++
                }
            }
        val logFromJs =
            object : IJsMessageHandler {
                override fun methodName() = "log"

                override fun handle(
                    message: JsMessage,
                    navigator: WebViewNavigator?,
                    callback: (String) -> Unit,
                ) {
                    lastJsToNative = message.params
                    log("JS → KT", "log ${message.params}", Warn)
                    callback("""{"ok":true}""")
                }
            }
        val paint =
            object : IJsMessageHandler {
                override fun methodName() = "paintComposeColor"

                override fun handle(
                    message: JsMessage,
                    navigator: WebViewNavigator?,
                    callback: (String) -> Unit,
                ) {
                    lastJsToNative = message.params
                    log("JS → KT", "paintComposeColor ${message.params}", Accent)
                    callback("""{"applied":true}""")
                    lastCallback = """{"applied":true}"""
                    roundTripCount++
                }
            }
        jsBridge.register(ping)
        jsBridge.register(logFromJs)
        jsBridge.register(paint)
        onDispose {
            jsBridge.unregister(ping)
            jsBridge.unregister(logFromJs)
            jsBridge.unregister(paint)
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(0)
    }

    // Auto sequence once the page has finished loading.
    LaunchedEffect(state.loadingState, autoRunning) {
        if (!autoRunning) return@LaunchedEffect
        if (state.loadingState !is LoadingState.Finished) return@LaunchedEffect
        delay(600)
        log("SYS", "page finished — starting auto bridge sequence", Ok)

        // 1) Kotlin → JS: set a banner in the page
        val token = "KT-${System.currentTimeMillis() % 100000}"
        lastNativeToJs = token
        navigator.evaluateJavaScript(
            """
            (function(){
              window.__composeSetBanner && window.__composeSetBanner('Kotlin says: $token');
              return 'banner-set';
            })()
            """.trimIndent(),
        ) { result ->
            log("KT → JS", "evaluateJavaScript banner → $result", Ok)
        }
        delay(800)

        // 2) Kotlin → JS → Kotlin: ask page to callNative('ping')
        navigator.evaluateJavaScript(
            """
            (function(){
              if (!window.kmpJsBridge) return 'no-bridge';
              window.kmpJsBridge.callNative('ping', JSON.stringify({hello:'from-auto', n:1}), function(data){
                window.__composeOnCallback && window.__composeOnCallback(data);
              });
              return 'ping-sent';
            })()
            """.trimIndent(),
        ) { result ->
            log("KT → JS", "trigger callNative(ping) → $result", Accent)
        }
        delay(1200)

        // 3) Another hop with a different payload
        navigator.evaluateJavaScript(
            """
            (function(){
              if (!window.kmpJsBridge) return 'no-bridge';
              window.kmpJsBridge.callNative('ping', JSON.stringify({hello:'round-trip-2', n:2}), function(data){
                window.__composeOnCallback && window.__composeOnCallback(data);
              });
              return 'ping-2-sent';
            })()
            """.trimIndent(),
        ) { result ->
            log("KT → JS", "trigger callNative(ping#2) → $result", Accent)
        }
        delay(1000)
        log("SYS", "auto sequence done — use buttons for more", Warn)
        autoRunning = false
    }

    Surface(modifier = Modifier.fillMaxSize(), color = PanelBg) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                "Visual Bridge Lab — Compose ↔ WebView (real WebKit)",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                "Left: native WebView page · Right: Compose log of every hop",
                color = TextMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // WebView
                Column(
                    Modifier
                        .weight(1.25f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF2D3A4F), RoundedCornerShape(12.dp))
                        .background(Color.White),
                ) {
                    WebView(
                        state = state,
                        navigator = navigator,
                        webViewJsBridge = jsBridge,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Compose side panel
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .padding(12.dp),
                ) {
                    StatusRow("Loading", state.loadingState.toString())
                    StatusRow("URL", state.lastLoadedUrl ?: "—")
                    StatusRow("Title", state.pageTitle ?: "—")
                    StatusRow("Last KT → JS", lastNativeToJs, Ok)
                    StatusRow("Last JS → KT", lastJsToNative, Accent)
                    StatusRow("Last callback", lastCallback, Ok)
                    StatusRow("Round-trips", roundTripCount.toString(), Warn)
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val msg = "manual-${System.currentTimeMillis() % 10000}"
                                lastNativeToJs = msg
                                log("KT → JS", "manual evaluateJavaScript: $msg", Ok)
                                navigator.evaluateJavaScript(
                                    "window.__composeSetBanner && window.__composeSetBanner('Manual: $msg'); 'ok'",
                                )
                            },
                        ) { Text("KT → JS banner") }

                        FilledTonalButton(
                            onClick = {
                                log("KT → JS", "ask page to ping Kotlin", Accent)
                                navigator.evaluateJavaScript(
                                    """
                                    (function(){
                                      window.kmpJsBridge.callNative(
                                        'ping',
                                        JSON.stringify({hello:'manual-button', t:Date.now()}),
                                        function(data){ window.__composeOnCallback(data); }
                                      );
                                      return 'sent';
                                    })()
                                    """.trimIndent(),
                                )
                            },
                        ) { Text("JS → KT ping") }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Live log", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0B1016))
                                .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(logs) { line ->
                            Text(
                                "[${line.ts}] ${line.kind}: ${line.message}",
                                color = line.color,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp, modifier = Modifier.width(110.dp))
        Text(
            value.take(120),
            color = valueColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
        )
    }
}

private data class LogLine(
    val ts: String,
    val kind: String,
    val message: String,
    val color: Color,
)

private val VISUAL_BRIDGE_HTML =
    """
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="utf-8"/>
      <meta name="viewport" content="width=device-width, initial-scale=1"/>
      <title>Bridge Visual Page</title>
      <style>
        * { box-sizing: border-box; }
        body {
          margin: 0; font-family: system-ui, sans-serif;
          background: #ffffff; color: #0f172a;
          min-height: 100vh;
        }
        header {
          padding: 16px 20px; background: linear-gradient(135deg, #1e3a5f, #0f172a);
          color: #e2e8f0;
        }
        header h1 { margin: 0; font-size: 18px; }
        header p { margin: 6px 0 0; opacity: .85; font-size: 13px; }
        #banner {
          margin: 16px; padding: 14px 16px; border-radius: 12px;
          background: #eff6ff; border: 2px solid #3b82f6; color: #1e3a8a;
          font-weight: 600; min-height: 48px;
        }
        #callbackBox {
          margin: 16px; padding: 14px 16px; border-radius: 12px;
          background: #ecfdf5; border: 2px solid #10b981; color: #065f46;
          font-family: ui-monospace, monospace; font-size: 13px;
          white-space: pre-wrap; min-height: 64px;
        }
        .row { display: flex; flex-wrap: wrap; gap: 10px; margin: 16px; }
        button {
          border: none; border-radius: 10px; padding: 12px 16px;
          font-weight: 700; cursor: pointer; font-size: 14px;
        }
        #btnPing { background: #3b82f6; color: white; }
        #btnLog { background: #f59e0b; color: #1c1917; }
        #btnPaint { background: #8b5cf6; color: white; }
        #status {
          margin: 16px; font-size: 13px; color: #64748b;
        }
        .ok { color: #059669; font-weight: 700; }
        .wait { color: #d97706; font-weight: 700; }
      </style>
    </head>
    <body>
      <header>
        <h1>WebView side (WebKit)</h1>
        <p>Talks to Compose via <code>window.kmpJsBridge</code></p>
      </header>
      <div id="status">Bridge: <span id="bridgeState" class="wait">waiting…</span></div>
      <div id="banner">Waiting for Kotlin messages…</div>
      <div id="callbackBox">callback payloads will appear here</div>
      <div class="row">
        <button id="btnPing" type="button">JS → Kotlin: ping()</button>
        <button id="btnLog" type="button">JS → Kotlin: log()</button>
        <button id="btnPaint" type="button">JS → Kotlin: paintComposeColor()</button>
      </div>
      <script>
        function setBridgeState(ok) {
          var el = document.getElementById('bridgeState');
          el.textContent = ok ? 'READY' : 'waiting…';
          el.className = ok ? 'ok' : 'wait';
        }
        window.__composeSetBanner = function(text) {
          document.getElementById('banner').textContent = text;
        };
        window.__composeOnCallback = function(data) {
          var s = (typeof data === 'string') ? data : JSON.stringify(data);
          document.getElementById('callbackBox').textContent = 'callback: ' + s;
        };
        function callNative(method, params) {
          if (!window.kmpJsBridge) {
            document.getElementById('callbackBox').textContent = 'bridge not ready';
            return;
          }
          window.kmpJsBridge.callNative(method, params, function(data) {
            window.__composeOnCallback(data);
          });
        }
        document.getElementById('btnPing').onclick = function() {
          callNative('ping', JSON.stringify({hello: 'from-page-button', t: Date.now()}));
        };
        document.getElementById('btnLog').onclick = function() {
          callNative('log', JSON.stringify({msg: 'hello from JS button'}));
        };
        document.getElementById('btnPaint').onclick = function() {
          callNative('paintComposeColor', JSON.stringify({color: '#8b5cf6'}));
        };
        // Poll until Kotlin injects the bridge
        var tries = 0;
        var iv = setInterval(function() {
          tries++;
          var ok = !!(window.kmpJsBridge && window.kmpJsBridge.callNative);
          setBridgeState(ok);
          if (ok || tries > 80) clearInterval(iv);
        }, 100);
      </script>
    </body>
    </html>
    """.trimIndent()
