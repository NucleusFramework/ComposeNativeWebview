package dev.nucleusframework.webview.setting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Shared multiplatform suite — must pass on JVM, Android host, iOS simulator, Wasm.
 */
class WebSettingsTest {
    @Test
    fun defaults() {
        val settings = WebSettings()
        assertTrue(settings.isJavaScriptEnabled)
        assertTrue(settings.supportZoom)
        assertEquals(1.0, settings.zoomLevel)
        assertNull(settings.customUserAgentString)
        assertFalse(settings.allowFileAccessFromFileURLs)
        assertFalse(settings.allowUniversalAccessFromFileURLs)
    }

    @Test
    fun platformBucketsExist() {
        val settings = WebSettings()
        // Touch platform settings objects so they stay wired on every target.
        settings.androidWebSettings.domStorageEnabled = true
        settings.desktopWebSettings.transparent = true
        settings.iOSWebSettings.opaque = false
        settings.wasmJSWebSettings.showBorder = true

        assertTrue(settings.androidWebSettings.domStorageEnabled)
        assertTrue(settings.desktopWebSettings.transparent)
        assertFalse(settings.iOSWebSettings.opaque)
        assertTrue(settings.wasmJSWebSettings.showBorder)
    }

    @Test
    fun customUserAgentMutable() {
        val settings = WebSettings()
        settings.customUserAgentString = "ComposeWebView/e2e"
        assertEquals("ComposeWebView/e2e", settings.customUserAgentString)
    }
}
