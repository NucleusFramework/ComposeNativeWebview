package dev.nucleusframework.webview.demo.visualsuite

/** Full catalog of visual e2e cases covering the desktop WebView API. */
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
        // Navigation
        SuiteCase("N01", "Navigation", "second navigation enables canGoBack"),
        SuiteCase("N02", "Navigation", "canGoForward false at tip of history"),
        SuiteCase("N03", "Navigation", "history: A→B enables canGoBack"),
        SuiteCase("N04", "Navigation", "navigateBack restores A"),
        SuiteCase("N05", "Navigation", "canGoForward after back"),
        SuiteCase("N06", "Navigation", "navigateForward restores B"),
        SuiteCase("N07", "Navigation", "reload keeps document identity"),
        SuiteCase("N08", "Navigation", "stopLoading does not crash mid-load"),
        // JavaScript
        SuiteCase("J01", "JavaScript", "evaluateJavaScript number"),
        SuiteCase("J02", "JavaScript", "evaluateJavaScript string"),
        SuiteCase("J03", "JavaScript", "evaluateJavaScript sets window state"),
        SuiteCase("J04", "JavaScript", "printToStringOrNull contains markup"),
        SuiteCase("J05", "JavaScript", "DOM mutation visible to subsequent eval"),
        // Bridge
        SuiteCase("B01", "JS Bridge", "bridge object injected (kmpJsBridge)"),
        SuiteCase("B02", "JS Bridge", "JS→Kotlin ping handler fires"),
        SuiteCase("B03", "JS Bridge", "Kotlin callback reaches JS"),
        SuiteCase("B04", "JS Bridge", "JSON params round-trip"),
        SuiteCase("B05", "JS Bridge", "sequential bridge calls (×5)"),
        SuiteCase("B06", "JS Bridge", "Kotlin→JS evaluate + DOM + readback"),
        SuiteCase("B07", "JS Bridge", "second handler registration works"),
        // Cookies
        SuiteCase("K01", "Cookies", "setCookie + getCookies finds cookie"),
        SuiteCase("K02", "Cookies", "removeCookies drops cookie"),
        SuiteCase("K03", "Cookies", "removeAllCookies clears jar"),
        SuiteCase("K04", "Cookies", "cookie value update (overwrite)"),
        // Interceptor
        SuiteCase("I01", "Interceptor", "Reject blocks blocked host"),
        SuiteCase("I02", "Interceptor", "Allow permits navigation"),
        SuiteCase("I03", "Interceptor", "Modify rewrites destination"),
        // Settings / desktop
        SuiteCase("S01", "Settings", "customUserAgentString applied"),
        SuiteCase("S02", "Settings", "initScript runs at document start"),
        SuiteCase("S03", "Settings", "zoomLevel applies (native)"),
        SuiteCase("S04", "Settings", "opaque white background (screenshot)"),
        // Capture
        SuiteCase("P01", "Capture", "captureScreenshotOrNull PNG magic"),
        SuiteCase("P02", "Capture", "toAwtImage non-empty dimensions"),
        SuiteCase("P03", "Capture", "screenshot of solid color page has pixels"),
        // Lifecycle / extras
        SuiteCase("L01", "Lifecycle", "onCreated invoked"),
        SuiteCase("L02", "Lifecycle", "webView native isReady"),
        SuiteCase("L03", "Lifecycle", "focus() no crash"),
        SuiteCase("L04", "Lifecycle", "openDevTools/closeDevTools no crash"),
        SuiteCase("L05", "Lifecycle", "loadUrl with custom headers no crash"),
        SuiteCase("L06", "Lifecycle", "multiple evaluateJavaScript in parallel-ish"),
        SuiteCase("L07", "Lifecycle", "can recover after Rejected navigation"),
    )
