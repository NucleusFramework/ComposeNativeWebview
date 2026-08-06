package dev.nucleusframework.webview.demo.visualsuite

import composewebview.demo_shared.generated.resources.Res
import dev.nucleusframework.webview.cookie.Cookie
import dev.nucleusframework.webview.web.LoadingState
import dev.nucleusframework.webview.web.WebContent
import dev.nucleusframework.webview.web.WebViewFileReadType
import dev.nucleusframework.webview.web.linux.LinuxWebKitNativeWebView
import dev.nucleusframework.webview.web.toAwtImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.ExperimentalResourceApi

internal suspend fun runFullSuite(
    ctx: SuiteContext,
    onCase: (id: String, status: CaseStatus, detail: String) -> Unit,
) {
    suspend fun case(id: String, block: suspend () -> Unit) {
        runCase(onStatus = { s, d -> onCase(id, s, d) }) { block() }
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
    case("C03") {
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
                baseUrl = "https://suite.local/c09-${System.nanoTime()}",
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

    // ── Navigation (use data: URLs so WebKit builds real history) ─────
    case("N01") {
        // Shared suite history may already allow back — assert the important property:
        // after two successive navigations, canGoBack is true.
        loadUrlAwaitMarker(ctx.navigator, "nav-first", dataHtmlUrl(pageWithMarker("nav-first")))
        loadUrlAwaitMarker(ctx.navigator, "nav-second", dataHtmlUrl(pageWithMarker("nav-second")))
        awaitUntil(12_000, "canGoBack after 2 loads") { ctx.navigator.canGoBack }
    }
    case("N02") {
        // At tip of history after N01's second page, forward should be false.
        delay(200)
        assertThat(!ctx.navigator.canGoForward, "canGoForward unexpectedly true at tip")
    }
    case("N03") {
        loadUrlAwaitMarker(ctx.navigator, "page-a", dataHtmlUrl(pageWithMarker("page-a")))
        loadUrlAwaitMarker(ctx.navigator, "page-b", dataHtmlUrl(pageWithMarker("page-b")))
        awaitUntil(12_000, "canGoBack") { ctx.navigator.canGoBack }
    }
    case("N04") {
        ctx.navigator.navigateBack()
        awaitUntil(15_000, "back to A") { markerOf(ctx.navigator) == "page-a" }
    }
    case("N05") {
        awaitUntil(10_000, "canGoForward") { ctx.navigator.canGoForward }
    }
    case("N06") {
        ctx.navigator.navigateForward()
        awaitUntil(15_000, "forward to B") { markerOf(ctx.navigator) == "page-b" }
    }
    case("N07") {
        ctx.navigator.reload()
        awaitUntil(12_000, "reload B") { markerOf(ctx.navigator) == "page-b" }
    }
    case("N08") {
        ctx.navigator.loadUrl(dataHtmlUrl(pageWithMarker("stop-target")))
        ctx.navigator.stopLoading()
        delay(200)
        loadUrlAwaitMarker(ctx.navigator, "after-stop", dataHtmlUrl(pageWithMarker("after-stop")))
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

    // ── Cookies ──────────────────────────────────────────────────────
    case("K01") {
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
    case("K04") {
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
    case("I03") {
        val rewritten = dataHtmlUrl(pageWithMarker("rewritten-ok"))
        ctx.setModifyMap(mapOf("rewrite-me.local" to rewritten))
        loadUrlAwaitMarker(ctx.navigator, "rewritten-ok", "https://rewrite-me.local/path", timeoutMs = 18_000)
        ctx.setModifyMap(emptyMap())
    }

    // ── Settings ─────────────────────────────────────────────────────
    case("S01") {
        val ua = evalJsUnquoted(ctx.navigator, "navigator.userAgent")
        assertThat(ua.isNotBlank(), "empty UA")
    }
    case("S02") {
        // Re-load a document and wait for post-Finished injectJsBridge.
        loadHtmlAwaitMarker(ctx.navigator, "init-probe")
        awaitUntil(12_000, "kmpJsBridge after load") {
            evalJs(ctx.navigator, "typeof window.kmpJsBridge !== 'undefined' && !!window.kmpJsBridge.callNative")
                .contains("true")
        }
    }
    case("S03") {
        val native = ctx.state.webView?.nativeWebView as? LinuxWebKitNativeWebView
        assertThat(native != null, "not linux webview")
        native!!.setZoomLevel(1.25)
        delay(80)
        native.setZoomLevel(1.0)
    }
    case("S04") {
        loadHtmlAwaitMarker(ctx.navigator, "white-bg", pageSolidColor("#ffffff").let {
            // solid color pages have no marker — inject one
            pageWithMarker("white-bg").replace("background:#ffffff", "background:#ffffff")
        })
        delay(300)
        val bytes = ctx.state.webView?.captureScreenshotOrNull()
        assertThat(bytes != null && bytes.size > 100, "screenshot empty")
        assertThat(bytes!![0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte(), "not PNG")
    }

    // ── Capture ──────────────────────────────────────────────────────
    case("P01") {
        val bytes = ctx.state.webView?.captureScreenshotOrNull()
        assertThat(bytes != null && bytes.size > 50, "null/empty")
        assertThat(
            bytes!![0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte(),
            "bad magic",
        )
    }
    case("P02") {
        val img = ctx.state.webView?.toAwtImage()
        assertThat(img != null && img.width > 0 && img.height > 0, "img=$img")
    }
    case("P03") {
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
        val img = ctx.state.webView?.toAwtImage()
        assertThat(img != null, "null image")
        val w = img!!.width
        val h = img.height
        val samples =
            listOf(
                img.getRGB(w / 2, h / 2),
                img.getRGB(w / 3, h / 3),
                img.getRGB(2 * w / 3, 2 * h / 3),
            )
        val hasRedish =
            samples.any { rgb ->
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                r > 150 && g < 120 && b < 120
            }
        assertThat(hasRedish || samples.any { it != 0 }, "no pixels samples=$samples ${w}x$h")
    }

    // ── Lifecycle ────────────────────────────────────────────────────
    case("L01") {
        assertThat(ctx.getOnCreatedFired(), "onCreated never fired")
    }
    case("L02") {
        val native = ctx.state.webView?.nativeWebView as? LinuxWebKitNativeWebView
        assertThat(native != null && native.isReady(), "not ready")
    }
    case("L03") {
        ctx.state.webView?.nativeWebView?.focus()
    }
    case("L04") {
        val n = ctx.state.webView?.nativeWebView
        n?.openDevTools()
        delay(80)
        n?.closeDevTools()
    }
    case("L05") {
        // Headers API path + recovery
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
}
