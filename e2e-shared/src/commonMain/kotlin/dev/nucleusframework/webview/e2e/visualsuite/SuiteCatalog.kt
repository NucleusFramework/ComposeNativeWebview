package dev.nucleusframework.webview.e2e.visualsuite

/**
 * Shared multiplatform visual e2e catalog.
 *
 * The **same** list runs on Desktop (Tao + WebKit/WebView2), Android WebView,
 * iOS WKWebView, and Wasm IFrame. Cases that need a [SuiteCapability] missing
 * on the host are Skipped with a reason — they are not dropped from the catalog.
 */
internal fun suiteCatalog(): List<SuiteCase> =
    listOf(
        // Content
        SuiteCase("C01", "Content", "loadHtml renders title"),
        SuiteCase("C02", "Content", "loadHtml body marker via JS"),
        SuiteCase("C03", "Content", "loadUrl(data:) loads document"),
        SuiteCase("C04", "Content", "loadHtmlFile(ASSET_RESOURCES)"),
        SuiteCase("C05", "Content", "lastLoadedUrl populated"),
        SuiteCase("C06", "Content", "pageTitle populated"),
        SuiteCase("C07", "Content", "loadingState Finished after load"),
        SuiteCase("C08", "Content", "isLoading false after Finished"),
        SuiteCase("C09", "Content", "state.content mutation loads new document"),
        SuiteCase("C10", "Content", "loadUrl normalizes trailing slash domain"),
        SuiteCase("C11", "Content", "loadHtml then loadUrl(data:) switches document"),
        // Navigation (Wry: go_back/forward/reload/stop/can_*)
        SuiteCase("N01", "Navigation", "second navigation enables canGoBack"),
        SuiteCase("N02", "Navigation", "canGoForward false at tip of history"),
        SuiteCase("N03", "Navigation", "history: A→B enables canGoBack"),
        SuiteCase("N04", "Navigation", "navigateBack restores A"),
        SuiteCase("N05", "Navigation", "canGoForward after back"),
        SuiteCase("N06", "Navigation", "navigateForward restores B"),
        SuiteCase("N07", "Navigation", "reload keeps document identity"),
        SuiteCase("N08", "Navigation", "stopLoading does not crash mid-load"),
        SuiteCase("N09", "Navigation", "A→B→A→B history depth still navigable"),
        // JavaScript (Wry: evaluate_javascript + callback)
        SuiteCase("J01", "JavaScript", "evaluateJavaScript number"),
        SuiteCase("J02", "JavaScript", "evaluateJavaScript string"),
        SuiteCase("J03", "JavaScript", "evaluateJavaScript sets window state"),
        SuiteCase("J04", "JavaScript", "printToStringOrNull contains markup"),
        SuiteCase("J05", "JavaScript", "DOM mutation visible to subsequent eval"),
        SuiteCase("J06", "JavaScript", "evaluateJavaScript boolean + nullish"),
        SuiteCase("J07", "JavaScript", "evaluateJavaScript object via JSON"),
        // Bridge (Wry: with_ipc_handler / drain_ipc_messages)
        SuiteCase("B01", "JS Bridge", "bridge object injected (kmpJsBridge)"),
        SuiteCase("B02", "JS Bridge", "JS→Kotlin ping handler fires"),
        SuiteCase("B03", "JS Bridge", "Kotlin callback reaches JS"),
        SuiteCase("B04", "JS Bridge", "JSON params round-trip"),
        SuiteCase("B05", "JS Bridge", "sequential bridge calls (×5)"),
        SuiteCase("B06", "JS Bridge", "Kotlin→JS evaluate + DOM + readback"),
        SuiteCase("B07", "JS Bridge", "second handler registration works"),
        SuiteCase("B08", "JS Bridge", "unregister stops dispatch"),
        SuiteCase("B09", "JS Bridge", "rapid IPC burst (×12) drains without drop"),
        SuiteCase("B10", "JS Bridge", "bridge callable from an inline script (document start)"),
        SuiteCase("B11", "JS Bridge", "bridge survives loads that keep the same URL (×3)"),
        // Cookies (Wry: set/get/clear_for_url/clear_all + attributes)
        SuiteCase("K01", "Cookies", "setCookie + getCookies finds cookie"),
        SuiteCase("K02", "Cookies", "removeCookies drops cookie"),
        SuiteCase("K03", "Cookies", "removeAllCookies clears jar"),
        SuiteCase("K04", "Cookies", "cookie value update (overwrite)"),
        SuiteCase("K05", "Cookies", "cookie path/domain attributes round-trip"),
        SuiteCase("K06", "Cookies", "multiple cookies coexist for same URL"),
        SuiteCase("K07", "Cookies", "incognito jar isolated from default jar"),
        // Interceptor (Wry: NavigationHandler)
        SuiteCase("I01", "Interceptor", "Reject blocks blocked host"),
        SuiteCase("I02", "Interceptor", "Allow permits navigation"),
        SuiteCase("I03", "Interceptor", "Modify rewrites destination"),
        SuiteCase("I04", "Interceptor", "Reject then Allow still works"),
        // Settings (Wry create_webview options)
        SuiteCase("S01", "Settings", "customUserAgentString applied at create"),
        SuiteCase("S02", "Settings", "initScript runs at document start"),
        SuiteCase("S03", "Settings", "zoomLevel applies (native)"),
        SuiteCase("S04", "Settings", "opaque white background (screenshot)"),
        SuiteCase("S05", "Settings", "default UA is non-empty browser string"),
        SuiteCase("S06", "Settings", "dataDirectory creates isolated profile dir"),
        // Capture (Wry: capture_screenshot)
        SuiteCase("P01", "Capture", "captureScreenshotOrNull PNG magic"),
        SuiteCase("P02", "Capture", "toAwtImage non-empty dimensions"),
        SuiteCase("P03", "Capture", "screenshot of solid color page has pixels"),
        SuiteCase("P04", "Capture", "screenshot dimensions stable across reloads"),
        // Lifecycle (Wry: focus/devtools/destroy/headers)
        SuiteCase("L01", "Lifecycle", "onCreated invoked"),
        SuiteCase("L02", "Lifecycle", "webView native isReady"),
        SuiteCase("L03", "Lifecycle", "focus() no crash"),
        SuiteCase("L04", "Lifecycle", "openDevTools/closeDevTools no crash"),
        SuiteCase("L05", "Lifecycle", "loadUrl with custom headers no crash"),
        SuiteCase("L06", "Lifecycle", "multiple evaluateJavaScript in parallel-ish"),
        SuiteCase("L07", "Lifecycle", "can recover after Rejected navigation"),
        SuiteCase("L08", "Lifecycle", "isolated destroy() tears down cleanly"),
        SuiteCase("L09", "Lifecycle", "headers load then HTML recovery keeps API live"),
        // Rendering — measurements, not thresholds: the backend embeds a real
        // native WebView and never throttles it, so these report what the host
        // actually achieves (see README "Rendering model & frame rate").
        SuiteCase("R01", "Rendering", "requestAnimationFrame rate"),
        SuiteCase("R02", "Rendering", "WebGL renderer"),
    )
