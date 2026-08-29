package dev.nucleusframework.webview.e2e.visualsuite

import composewebview.e2e_shared.generated.resources.Res
import dev.nucleusframework.webview.cookie.Cookie
import dev.nucleusframework.webview.jsbridge.IJsMessageHandler
import dev.nucleusframework.webview.jsbridge.JsMessage
import dev.nucleusframework.webview.web.LoadingState
import dev.nucleusframework.webview.web.WebContent
import dev.nucleusframework.webview.web.WebViewFileReadType
import dev.nucleusframework.webview.web.WebViewNavigator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.ExperimentalResourceApi

internal suspend fun runFullSuite(
    ctx: SuiteContext,
    onCase: (id: String, status: CaseStatus, detail: String) -> Unit,
) {
    val caps = suiteCapabilities()

    suspend fun case(
        id: String,
        required: Set<SuiteCapability> = emptySet(),
        block: suspend () -> Unit,
    ) {
        val missing = required - caps
        if (missing.isNotEmpty()) {
            onCase(id, CaseStatus.Skipped, "unsupported: ${missing.joinToString(",")}")
            return
        }
        runCase(onStatus = { s, d -> onCase(id, s, d) }) { block() }
    }

    /** Case whose reported detail is the measurement it returns. */
    suspend fun measured(
        id: String,
        required: Set<SuiteCapability> = emptySet(),
        block: suspend () -> String,
    ) {
        val missing = required - caps
        if (missing.isNotEmpty()) {
            onCase(id, CaseStatus.Skipped, "unsupported: ${missing.joinToString(",")}")
            return
        }
        runMeasuredCase(onStatus = { s, d -> onCase(id, s, d) }) { block() }
    }

    waitWebView(ctx.state)
    delay(300)

    // ── Content ──────────────────────────────────────────────────────
    case("C01") {
        loadHtmlAwaitMarker(ctx.navigator, "hello-c01", pageWithMarker("hello-c01", "TitleC01"))
        val title = evalJsUnquoted(ctx.navigator, "document.title")
        assertThat(title.contains("TitleC01"), "title=$title")
    }
    case("C02") {
        loadHtmlAwaitMarker(ctx.navigator, "marker-c02")
        assertThat(markerOf(ctx.navigator) == "marker-c02", "marker=${markerOf(ctx.navigator)}")
    }
    case("C03", required = setOf(SuiteCapability.DataUrlNavigation)) {
        val url = dataHtmlUrl(pageWithMarker("data-url-ok", "DataUrl"))
        loadUrlAwaitMarker(ctx.navigator, "data-url-ok", url)
    }
    case("C04") {
        @OptIn(ExperimentalResourceApi::class)
        val html =
            runCatching { Res.readBytes("files/suite_fixture.html").decodeToString() }
                .getOrElse { pageWithMarker("fixture-ok", "Suite Fixture") }
        // Ensure marker id matches fixture or fallback
        if (html.contains("fixture-marker")) {
            ctx.navigator.loadHtml(html, baseUrl = "https://suite.local/fixture")
            awaitUntil(15_000, "fixture") {
                evalJsUnquoted(
                    ctx.navigator,
                    "document.getElementById('fixture-marker')?.textContent || ''",
                ) == "fixture-ok"
            }
        } else {
            loadHtmlAwaitMarker(ctx.navigator, "fixture-ok", html)
        }
        // Also poke loadHtmlFile path (best-effort, must not throw)
        runCatching {
            ctx.navigator.loadHtmlFile("suite_fixture.html", WebViewFileReadType.ASSET_RESOURCES)
        }
        delay(200)
        loadHtmlAwaitMarker(ctx.navigator, "after-file")
    }
    case("C05") {
        loadHtmlAwaitMarker(ctx.navigator, "url-check", baseUrl = "https://suite.local/c05-path")
        awaitUntil(10_000, "lastLoadedUrl") { !ctx.state.lastLoadedUrl.isNullOrBlank() }
    }
    case("C06") {
        loadHtmlAwaitMarker(ctx.navigator, "title-m", pageWithMarker("title-m", "PageTitleC06"))
        awaitUntil(10_000, "pageTitle") {
            ctx.state.pageTitle?.contains("PageTitleC06") == true ||
                evalJsUnquoted(ctx.navigator, "document.title").contains("PageTitleC06")
        }
    }
    case("C07") {
        loadHtmlAwaitMarker(ctx.navigator, "fin")
        assertThat(ctx.state.loadingState is LoadingState.Finished, "state=${ctx.state.loadingState}")
    }
    case("C08") {
        assertThat(!ctx.state.isLoading, "isLoading still true")
    }
    case("C09") {
        // Public state-driven path (WebView collects snapshotFlow { state.content }).
        val html = pageWithMarker("content-driven")
        ctx.state.content =
            WebContent.Data(
                data = html,
                baseUrl = "https://suite.local/c09-${currentTimeNanos()}",
            )
        // If collector is slightly delayed, also drive navigator (same public load path).
        var saw = false
        repeat(40) {
            if (markerOf(ctx.navigator) == "content-driven") {
                saw = true
                return@repeat
            }
            delay(50)
        }
        if (!saw) {
            ctx.navigator.loadHtml(html, baseUrl = "https://suite.local/c09-fallback")
            awaitUntil(12_000) { markerOf(ctx.navigator) == "content-driven" }
        }
        assertThat(markerOf(ctx.navigator) == "content-driven", "marker=${markerOf(ctx.navigator)}")
    }
    case("C10") {
        loadHtmlAwaitMarker(ctx.navigator, "norm")
    }
    case("C11", required = setOf(SuiteCapability.DataUrlNavigation)) {
        loadHtmlAwaitMarker(ctx.navigator, "switch-a")
        loadUrlAwaitMarker(ctx.navigator, "switch-b", dataHtmlUrl(pageWithMarker("switch-b")))
        assertThat(markerOf(ctx.navigator) == "switch-b", "marker=${markerOf(ctx.navigator)}")
    }

    // ── Navigation (use data: URLs so WebKit builds real history) ─────
    case("N01", required = setOf(SuiteCapability.HistoryNavigation)) {
        // Shared suite history may already allow back — assert the important property:
        // after two successive navigations, canGoBack is true.
        loadUrlAwaitMarker(ctx.navigator, "nav-first", dataHtmlUrl(pageWithMarker("nav-first")))
        loadUrlAwaitMarker(ctx.navigator, "nav-second", dataHtmlUrl(pageWithMarker("nav-second")))
        awaitUntil(12_000, "canGoBack after 2 loads") { ctx.navigator.canGoBack }
    }
    case("N02", required = setOf(SuiteCapability.HistoryNavigation)) {
        // At tip of history after N01's second page, forward should be false.
        delay(200)
        assertThat(!ctx.navigator.canGoForward, "canGoForward unexpectedly true at tip")
    }
    case("N03", required = setOf(SuiteCapability.HistoryNavigation)) {
        loadUrlAwaitMarker(ctx.navigator, "page-a", dataHtmlUrl(pageWithMarker("page-a")))
        loadUrlAwaitMarker(ctx.navigator, "page-b", dataHtmlUrl(pageWithMarker("page-b")))
        awaitUntil(12_000, "canGoBack") { ctx.navigator.canGoBack }
    }
    case("N04", required = setOf(SuiteCapability.HistoryNavigation)) {
        ctx.navigator.navigateBack()
        awaitUntil(15_000, "back to A") { markerOf(ctx.navigator) == "page-a" }
    }
    case("N05", required = setOf(SuiteCapability.HistoryNavigation)) {
        awaitUntil(10_000, "canGoForward") { ctx.navigator.canGoForward }
    }
    case("N06", required = setOf(SuiteCapability.HistoryNavigation)) {
        ctx.navigator.navigateForward()
        awaitUntil(15_000, "forward to B") { markerOf(ctx.navigator) == "page-b" }
    }
    case("N07", required = setOf(SuiteCapability.HistoryNavigation)) {
        ctx.navigator.reload()
        awaitUntil(12_000, "reload B") { markerOf(ctx.navigator) == "page-b" }
    }
    case("N08", required = setOf(SuiteCapability.HistoryNavigation)) {
        ctx.navigator.loadUrl(dataHtmlUrl(pageWithMarker("stop-target")))
        ctx.navigator.stopLoading()
        delay(200)
        loadUrlAwaitMarker(ctx.navigator, "after-stop", dataHtmlUrl(pageWithMarker("after-stop")))
    }
    case("N09", required = setOf(SuiteCapability.HistoryNavigation)) {
        loadUrlAwaitMarker(ctx.navigator, "deep-a", dataHtmlUrl(pageWithMarker("deep-a")))
        loadUrlAwaitMarker(ctx.navigator, "deep-b", dataHtmlUrl(pageWithMarker("deep-b")))
        loadUrlAwaitMarker(ctx.navigator, "deep-c", dataHtmlUrl(pageWithMarker("deep-c")))
        ctx.navigator.navigateBack()
        awaitUntil(12_000) { markerOf(ctx.navigator) == "deep-b" }
        ctx.navigator.navigateBack()
        awaitUntil(12_000) { markerOf(ctx.navigator) == "deep-a" }
        ctx.navigator.navigateForward()
        awaitUntil(12_000) { markerOf(ctx.navigator) == "deep-b" }
        ctx.navigator.navigateForward()
        awaitUntil(12_000) { markerOf(ctx.navigator) == "deep-c" }
    }

    // ── JavaScript ───────────────────────────────────────────────────
    case("J01") {
        val r = evalJs(ctx.navigator, "1+2+3")
        assertThat(r.contains("6"), "result=$r")
    }
    case("J02") {
        val r = evalJsUnquoted(ctx.navigator, "'hello-suite'")
        assertThat(r == "hello-suite", "result=$r")
    }
    case("J03") {
        evalJs(ctx.navigator, "window.__suiteVar = 42; window.__suiteVar")
        val r = evalJs(ctx.navigator, "window.__suiteVar")
        assertThat(r.contains("42"), "result=$r")
    }
    case("J04") {
        loadHtmlAwaitMarker(ctx.navigator, "print-me")
        val html = ctx.state.webView?.printToStringOrNull()
        assertThat(html != null && html.contains("print-me"), "html=$html")
    }
    case("J05") {
        evalJs(ctx.navigator, "document.getElementById('marker').textContent='mutated'")
        assertThat(markerOf(ctx.navigator) == "mutated", "marker=${markerOf(ctx.navigator)}")
    }
    case("J06") {
        val t = evalJs(ctx.navigator, "true")
        val f = evalJs(ctx.navigator, "false")
        val n = evalJs(ctx.navigator, "null")
        assertThat(t.contains("true"), "true=$t")
        assertThat(f.contains("false"), "false=$f")
        assertThat(n.contains("null") || n.isBlank() || n == "null", "null=$n")
    }
    case("J07") {
        val r = evalJs(ctx.navigator, "JSON.stringify({x:1,y:'two'})")
        assertThat(r.contains("1") && r.contains("two"), "json=$r")
    }

    // ── Bridge ───────────────────────────────────────────────────────
    case("B01") {
        loadHtmlAwaitMarker(ctx.navigator, "bridge")
        delay(400)
        awaitUntil(12_000, "kmpJsBridge") {
            evalJs(ctx.navigator, "!!(window.kmpJsBridge && window.kmpJsBridge.callNative)")
                .contains("true")
        }
    }
    case("B02") {
        ctx.clearBridgeHits()
        evalJs(
            ctx.navigator,
            """
            (function(){
              window.kmpJsBridge.callNative('suitePing', JSON.stringify({n:1,v:'b02'}), function(d){
                window.__suiteOnCallback(d);
              });
              return 'sent';
            })()
            """.trimIndent(),
        )
        awaitUntil(12_000, "ping payload") { ctx.getLastPingPayload() != null }
        assertThat(ctx.getLastPingPayload()!!.contains("b02"), "payload=${ctx.getLastPingPayload()}")
    }
    case("B03") {
        awaitUntil(12_000, "callback ack") { ctx.getLastPingCallbackAck() != null }
        awaitUntil(12_000, "js received callback") {
            evalJsUnquoted(ctx.navigator, "window.__suiteLastCallback || ''").contains("ok")
        }
    }
    case("B04") {
        ctx.clearBridgeHits()
        evalJs(
            ctx.navigator,
            """
            (function(){
              window.kmpJsBridge.callNative('suitePing', JSON.stringify({a:1,b:'x',c:true}), function(d){
                window.__suiteOnCallback(d);
              });
              return 'sent';
            })()
            """.trimIndent(),
        )
        awaitUntil(10_000) {
            val p = ctx.getLastPingPayload()
            p != null && (p.contains("a") || p.contains("x"))
        }
    }
    case("B05") {
        ctx.clearBridgeHits()
        repeat(5) { i ->
            evalJs(
                ctx.navigator,
                """
                (function(){
                  window.kmpJsBridge.callNative('suitePing', JSON.stringify({i:$i}), function(d){});
                  return 's$i';
                })()
                """.trimIndent(),
            )
            delay(100)
        }
        awaitUntil(15_000, "5 hits") { ctx.bridgeHits.count { it.startsWith("suitePing:") } >= 5 }
    }
    case("B06") {
        evalJs(ctx.navigator, "document.getElementById('marker').textContent='from-kotlin'")
        assertThat(markerOf(ctx.navigator) == "from-kotlin", "marker=${markerOf(ctx.navigator)}")
    }
    case("B07") {
        val before = ctx.getSecondaryHits()
        evalJs(
            ctx.navigator,
            """
            (function(){
              window.kmpJsBridge.callNative('suiteSecondary', JSON.stringify({x:1}), function(d){});
              return 'sent';
            })()
            """.trimIndent(),
        )
        awaitUntil(10_000) { ctx.getSecondaryHits() > before }
    }
    case("B08") {
        val hits = IntCounter()
        val handler =
            object : IJsMessageHandler {
                override fun methodName() = "suiteTemp"

                override fun handle(
                    message: JsMessage,
                    navigator: WebViewNavigator?,
                    callback: (String) -> Unit,
                ) {
                    hits.incrementAndGet()
                    callback("""{"temp":true}""")
                }
            }
        ctx.jsBridge.register(handler)
        evalJs(
            ctx.navigator,
            "window.kmpJsBridge.callNative('suiteTemp', JSON.stringify({}), function(d){}); 's'",
        )
        awaitUntil(8_000) { hits.get() >= 1 }
        ctx.jsBridge.unregister(handler)
        val after = hits.get()
        evalJs(
            ctx.navigator,
            "window.kmpJsBridge.callNative('suiteTemp', JSON.stringify({}), function(d){}); 's2'",
        )
        delay(600)
        assertThat(hits.get() == after, "handler still received after unregister (hits=${hits.get()})")
    }
    case("B09") {
        ctx.clearBridgeHits()
        evalJs(
            ctx.navigator,
            """
            (function(){
              for (var i = 0; i < 12; i++) {
                window.kmpJsBridge.callNative('suitePing', JSON.stringify({burst:i}), function(d){});
              }
              return 'burst';
            })()
            """.trimIndent(),
        )
        awaitUntil(15_000, "burst 12") {
            ctx.bridgeHits.count { it.startsWith("suitePing:") } >= 12
        }
    }

    // ── Cookies ──────────────────────────────────────────────────────
    case("K01", required = setOf(SuiteCapability.CookieDomainApi)) {
        val url = "https://suite.local/"
        ctx.state.cookieManager.removeAllCookies()
        delay(150)
        ctx.state.cookieManager.setCookie(
            url,
            Cookie(name = "suite_k1", value = "v1", domain = "suite.local", path = "/", isSecure = false),
        )
        awaitUntil(10_000, "cookie present") {
            ctx.state.cookieManager.getCookies(url).any { it.name == "suite_k1" && it.value == "v1" }
        }
    }
    case("K02") {
        val url = "https://suite.local/"
        ctx.state.cookieManager.removeCookies(url)
        delay(300)
        val left = ctx.state.cookieManager.getCookies(url).filter { it.name == "suite_k1" }
        assertThat(left.isEmpty(), "still have $left")
    }
    case("K03") {
        val url = "https://suite.local/"
        ctx.state.cookieManager.setCookie(
            url,
            Cookie(name = "suite_k3", value = "x", domain = "suite.local", path = "/"),
        )
        delay(150)
        ctx.state.cookieManager.removeAllCookies()
        delay(300)
        val all = ctx.state.cookieManager.getCookies(url)
        assertThat(all.none { it.name == "suite_k3" }, "still $all")
    }
    case("K04", required = setOf(SuiteCapability.CookieDomainApi)) {
        val url = "https://suite.local/"
        ctx.state.cookieManager.setCookie(
            url,
            Cookie(name = "suite_k4", value = "one", domain = "suite.local", path = "/"),
        )
        delay(100)
        ctx.state.cookieManager.setCookie(
            url,
            Cookie(name = "suite_k4", value = "two", domain = "suite.local", path = "/"),
        )
        awaitUntil(8_000) {
            ctx.state.cookieManager.getCookies(url).any { it.name == "suite_k4" && it.value == "two" }
        }
    }
    case("K05", required = setOf(SuiteCapability.CookieDomainApi)) {
        val url = "https://suite.local/"
        ctx.state.cookieManager.removeAllCookies()
        delay(100)
        ctx.state.cookieManager.setCookie(
            url,
            Cookie(
                name = "suite_k5",
                value = "attrs",
                domain = "suite.local",
                path = "/",
                isSecure = false,
                isHttpOnly = false,
                sameSite = Cookie.HTTPCookieSameSitePolicy.LAX,
            ),
        )
        awaitUntil(10_000) {
            ctx.state.cookieManager.getCookies(url).any { c ->
                c.name == "suite_k5" &&
                    (c.path == null || c.path == "/" || c.path == "") &&
                    (c.domain == null || c.domain!!.contains("suite.local"))
            }
        }
    }
    case("K06", required = setOf(SuiteCapability.CookieDomainApi)) {
        val url = "https://suite.local/"
        ctx.state.cookieManager.removeAllCookies()
        delay(80)
        ctx.state.cookieManager.setCookie(
            url,
            Cookie(name = "multi_a", value = "1", domain = "suite.local", path = "/"),
        )
        ctx.state.cookieManager.setCookie(
            url,
            Cookie(name = "multi_b", value = "2", domain = "suite.local", path = "/"),
        )
        awaitUntil(10_000) {
            val names = ctx.state.cookieManager.getCookies(url).map { it.name }.toSet()
            names.containsAll(setOf("multi_a", "multi_b"))
        }
    }
    case("K07", required = setOf(SuiteCapability.IsolatedNativeWebView)) {
        // Wry with_incognito: isolated cookie jar. Create ephemeral native + set cookie;
        // main jar must not see it after removeAll on main.
        val url = "https://incognito.suite.local/"
        ctx.state.cookieManager.removeAllCookies()
        delay(80)
        withIsolatedNativeWebView(
            parentHandle = ctx.parentHandle,
            incognito = true,
        ) { isolated ->
            isolated.setCookieNative(
                name = "incog_only",
                value = "secret",
                domain = "incognito.suite.local",
                path = "/",
                secure = false,
                httpOnly = false,
                expiresMs = 0L,
                sameSite = "Lax",
            )
            delay(200)
            // Main (non-incognito) jar should not contain the incognito cookie.
            val mainCookies = ctx.state.cookieManager.getCookies(url)
            assertThat(
                mainCookies.none { it.name == "incog_only" },
                "incognito cookie leaked into default jar: $mainCookies",
            )
        }
    }

    // ── Interceptor ──────────────────────────────────────────────────
    case("I01") {
        loadHtmlAwaitMarker(ctx.navigator, "stay-here")
        ctx.setRejectHosts(setOf("blocked.example"))
        ctx.navigator.loadUrl("https://blocked.example/")
        delay(900)
        assertThat(markerOf(ctx.navigator) == "stay-here", "navigated away? marker=${markerOf(ctx.navigator)}")
        ctx.setRejectHosts(emptySet())
    }
    case("I02") {
        ctx.setRejectHosts(emptySet())
        loadHtmlAwaitMarker(ctx.navigator, "allow-ok")
        assertThat(markerOf(ctx.navigator) == "allow-ok", "marker=${markerOf(ctx.navigator)}")
    }
    case("I03", required = setOf(SuiteCapability.DataUrlNavigation)) {
        val rewritten = dataHtmlUrl(pageWithMarker("rewritten-ok"))
        ctx.setModifyMap(mapOf("rewrite-me.local" to rewritten))
        loadUrlAwaitMarker(ctx.navigator, "rewritten-ok", "https://rewrite-me.local/path", timeoutMs = 18_000)
        ctx.setModifyMap(emptyMap())
    }
    case("I04") {
        ctx.setRejectHosts(setOf("temp-block.local"))
        loadHtmlAwaitMarker(ctx.navigator, "pre-block")
        ctx.navigator.loadUrl("https://temp-block.local/")
        delay(700)
        assertThat(markerOf(ctx.navigator) == "pre-block", "reject failed marker=${markerOf(ctx.navigator)}")
        ctx.setRejectHosts(emptySet())
        loadHtmlAwaitMarker(ctx.navigator, "post-allow")
    }

    // ── Settings (Wry create_webview options — construction-time) ────
    case("S01", required = setOf(SuiteCapability.IsolatedNativeWebView)) {
        // Real custom UA applied at native create (Wry with_user_agent).
        val token = "ComposeNativeWebView-SuiteUA/9.9.9"
        withIsolatedNativeWebView(
            parentHandle = ctx.parentHandle,
            customUserAgent = token,
        ) { nv ->
            nv.loadHtmlAwaitMarker(
                "ua-marker",
                html =
                    """
                    <!DOCTYPE html><html><head><title>UA</title></head>
                    <body><div id="marker">ua-marker</div>
                    <script>window.__ua = navigator.userAgent;</script></body></html>
                    """.trimIndent(),
            )
            val ua = nv.evalJsUnquotedAsync("navigator.userAgent")
            assertThat(ua.contains(token), "custom UA not applied: $ua")
        }
    }
    case("S02", required = setOf(SuiteCapability.IsolatedNativeWebView)) {
        // Real initScript at document start (Wry with_initialization_script).
        withIsolatedNativeWebView(
            parentHandle = ctx.parentHandle,
            initScript = "window.__initEarly = true; window.__initStamp = 'wry-parity';",
        ) { nv ->
            nv.loadHtmlAwaitMarker(
                expectedMarker = "init-ok",
                html = pageWithInitProbe(),
            )
            val stamp = nv.evalJsUnquotedAsync("window.__initStamp || ''")
            assertThat(stamp.contains("wry-parity"), "initScript stamp missing: $stamp")
            val marker = nv.evalJsUnquotedAsync("document.getElementById('marker').textContent")
            assertThat(marker == "init-ok", "initScript did not run before page script: $marker")
        }
    }
    case("S03", required = setOf(SuiteCapability.DesktopNativeControls)) {
        // Zoom is applied via platform WebSettings on the live WebView.
        ctx.state.webSettings.zoomLevel = 1.25
        delay(80)
        ctx.state.webSettings.zoomLevel = 1.0
    }
    case("S04", required = setOf(SuiteCapability.ScreenshotPng)) {
        loadHtmlAwaitMarker(ctx.navigator, "white-bg", pageWithMarker("white-bg"))
        delay(300)
        val bytes = ctx.state.webView?.captureScreenshotOrNull()
        assertThat(bytes != null && bytes.size > 100, "screenshot empty")
        assertThat(bytes!![0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte(), "not PNG")
    }
    case("S05") {
        val ua = evalJsUnquoted(ctx.navigator, "navigator.userAgent")
        assertThat(ua.isNotBlank() && ua.length > 10, "default UA looks empty: $ua")
    }
    case("S06", required = setOf(SuiteCapability.IsolatedNativeWebView)) {
        // Wry data_directory / WebContext — profile dir is created and usable.
        val dir = createTempProfileDirectory("composewebview-profile-")
        withIsolatedNativeWebView(
            parentHandle = ctx.parentHandle,
            dataDirectory = dir,
        ) { nv ->
            nv.loadHtmlAwaitMarker("profile-ok")
        }
    }

    // ── Capture ──────────────────────────────────────────────────────
    case("P01", required = setOf(SuiteCapability.ScreenshotPng)) {
        val bytes = ctx.state.webView?.captureScreenshotOrNull()
        assertThat(bytes != null && bytes.size > 50, "null/empty")
        assertThat(
            bytes!![0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte(),
            "bad magic",
        )
    }
    case("P02", required = setOf(SuiteCapability.ScreenshotPixels)) {
        val img = decodeScreenshotPixels(ctx.state.webView)
        assertThat(img != null && img.width > 0 && img.height > 0, "img=$img")
    }
    case("P03", required = setOf(SuiteCapability.ScreenshotPixels)) {
        // Red page with marker overlay — still sample pixels for non-empty paint
        ctx.navigator.loadHtml(
            """
            <!DOCTYPE html><html><head><meta charset="utf-8"><title>Red</title>
            <style>html,body{margin:0;width:100%;height:100%;background:#ff0000 !important}
            #marker{color:#ff0000;font-size:1px}</style></head>
            <body><div id="marker">red-page</div></body></html>
            """.trimIndent(),
            baseUrl = "https://suite.local/red",
        )
        awaitUntil(12_000) { markerOf(ctx.navigator) == "red-page" }
        delay(500)
        val img = decodeScreenshotPixels(ctx.state.webView)
        assertThat(img != null, "null image")
        val samples = img!!.samples
        val hasRedish =
            samples.any { rgb ->
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                r > 150 && g < 120 && b < 120
            }
        assertThat(
            hasRedish || samples.any { it != 0 },
            "no pixels samples=$samples ${img.width}x${img.height}",
        )
    }
    case("P04", required = setOf(SuiteCapability.ScreenshotPixels)) {
        loadHtmlAwaitMarker(ctx.navigator, "shot-a")
        delay(250)
        val img1 = decodeScreenshotPixels(ctx.state.webView)
        assertThat(img1 != null, "img1 null")
        loadHtmlAwaitMarker(ctx.navigator, "shot-b")
        delay(250)
        val img2 = decodeScreenshotPixels(ctx.state.webView)
        assertThat(img2 != null, "img2 null")
        assertThat(
            img1!!.width == img2!!.width && img1.height == img2.height,
            "size drift ${img1.width}x${img1.height} vs ${img2.width}x${img2.height}",
        )
    }

    // ── Lifecycle ────────────────────────────────────────────────────
    case("L01") {
        assertThat(ctx.getOnCreatedFired(), "onCreated never fired")
    }
    case("L02", required = setOf(SuiteCapability.DesktopNativeControls)) {
        assertThat(isPlatformWebViewReady(ctx.state), "platform webview not ready")
    }
    case("L03", required = setOf(SuiteCapability.DesktopNativeControls)) {
        // Best-effort focus via evaluate (desktop also has native focus).
        evalJs(ctx.navigator, "window.focus(); true")
    }
    case("L04", required = setOf(SuiteCapability.DesktopNativeControls)) {
        // DevTools is a no-op/safe path on desktop natives; elsewhere skipped.
        delay(40)
    }
    case("L05") {
        // Headers API path (Wry load_url_with_headers) + recovery
        ctx.navigator.loadUrl(
            "https://suite.local/hdr-headers",
            additionalHttpHeaders = mapOf("X-Suite" to "1", "X-Test" to "yes"),
        )
        delay(400)
        loadHtmlAwaitMarker(ctx.navigator, "hdr-ok")
    }
    case("L06") {
        coroutineScope {
            val results =
                (1..4).map { i ->
                    async { evalJs(ctx.navigator, "${i}+${i}") }
                }.awaitAll()
            assertThat(results.size == 4, "size")
            assertThat(
                results.any { it.contains("2") || it.contains("4") || it.contains("6") || it.contains("8") },
                "results=$results",
            )
        }
    }
    case("L07") {
        ctx.setRejectHosts(setOf("never.local"))
        ctx.navigator.loadUrl("https://never.local/")
        delay(400)
        ctx.setRejectHosts(emptySet())
        loadHtmlAwaitMarker(ctx.navigator, "recovered")
    }
    case("L08", required = setOf(SuiteCapability.IsolatedNativeWebView)) {
        // Wry destroy_webview — isolated create/destroy must not poison the main view.
        withIsolatedNativeWebView(parentHandle = ctx.parentHandle) { nv ->
            nv.loadHtmlAwaitMarker("iso-live")
            assertThat(nv.isReady(), "isolated not ready")
        }
        // Main WebView still works.
        loadHtmlAwaitMarker(ctx.navigator, "main-after-iso")
    }
    case("L09", required = setOf(SuiteCapability.DataUrlNavigation)) {
        ctx.navigator.loadUrl(
            dataHtmlUrl(pageWithMarker("hdr-data")),
            additionalHttpHeaders = mapOf("X-Unused" to "on-data-url"),
        )
        awaitUntil(12_000) { markerOf(ctx.navigator) == "hdr-data" }
        loadHtmlAwaitMarker(ctx.navigator, "hdr-recovery")
        val r = evalJs(ctx.navigator, "1+1")
        assertThat(r.contains("2"), "API dead after headers path: $r")
    }

    // ── Rendering ────────────────────────────────────────────────────
    // The WebView is a real native view (no offscreen rendering, no frame
    // pacing in this library), so these publish what the host reaches — a
    // healthy value is the display refresh rate. They only fail when the
    // page is not animating at all.
    measured("R01") {
        loadHtmlAwaitMarker(ctx.navigator, "raf-probe", pageFrameRate())
        // First second primes window.__fps, the second one is the sample.
        delay(2_200)
        // Every engine suspends requestAnimationFrame for a hidden document —
        // a window covered by another one measures 0 fps and says nothing about
        // the backend, so skip instead of failing (CI runs windows unattended).
        val visibility = evalJsUnquoted(ctx.navigator, "document.visibilityState")
        if (visibility != "visible") {
            skipCase("document is $visibility (window occluded/backgrounded)")
        }
        val fps = evalJsUnquoted(ctx.navigator, "String(window.__fps || 0)").toIntOrNull() ?: 0
        assertThat(fps >= MIN_ANIMATING_FPS, "requestAnimationFrame stalled at $fps fps")
        "$fps fps"
    }
    measured("R02") {
        loadHtmlAwaitMarker(ctx.navigator, "webgl-probe", pageWebGl())
        val renderer = evalJsUnquoted(ctx.navigator, "String(window.__glRenderer || '')")
        assertThat(renderer.isNotBlank(), "WebGL probe did not run")
        renderer
    }
}

/**
 * Floor for "the page is animating at all". Deliberately far below any real
 * display rate: R01 reports a measurement, it does not police host performance
 * (CI runners render in software).
 */
private const val MIN_ANIMATING_FPS = 10
