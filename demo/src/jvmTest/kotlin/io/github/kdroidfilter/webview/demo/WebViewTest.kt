@file:OptIn(ExperimentalTestApi::class)

package io.github.kdroidfilter.webview.demo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import io.github.kdroidfilter.webview.web.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

const val TEST_URL = "https://github.com/kdroidFilter/ComposeNativeWebview"

class WebViewTest {
    @Test
    fun testWebViewInitialization() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalWebViewFactory provides ::playwrightWebViewFactory) {
                val state = rememberWebViewState(TEST_URL)
                val navigator = rememberWebViewNavigator()
                WebView(
                    state = state,
                    navigator = navigator
                )
            }
        }
    }

    @Test
    fun testJavascriptInjection() = runComposeUiTest {
        var mockWebView: PlaywrightWebView? by mutableStateOf(null)
        var navigator: WebViewNavigator? = null
        val factory = { param: WebViewFactoryParam ->
            val webView = playwrightWebViewFactory(param)
            mockWebView = webView as PlaywrightWebView
            webView
        }

        setContent {
            val nav = rememberWebViewNavigator()
            navigator = nav
            CompositionLocalProvider(LocalWebViewFactory provides factory) {
                val state = rememberWebViewState(TEST_URL)
                WebView(
                    state = state,
                    navigator = nav
                )
            }
        }

        runOnIdle {
            navigator?.evaluateJavaScript("alert('hello')")
        }

        runOnIdle {
            assertEquals(
                expected = mockWebView?.evaluatedScripts?.contains("alert('hello')"),
                actual = true,
                message = "Script should have been evaluated"
            )
        }
    }

    @Test
    fun testInitScriptInjection() = runComposeUiTest {
        var capturedInitScript: String? = null
        val factory = { param: WebViewFactoryParam ->
            capturedInitScript = param.state.webSettings.desktopWebSettings.initScript
            playwrightWebViewFactory(param)
        }
        setContent {
            CompositionLocalProvider(LocalWebViewFactory provides factory) {
                val state = rememberWebViewState(TEST_URL) {
                    desktopWebSettings.initScript = "window.test = true;"
                }
                WebView(state)
            }
        }

        runOnIdle {
            assertEquals("window.test = true;", capturedInitScript)
        }
    }

    @Test
    fun testWebViewScreenshot() = runComposeUiTest {
        var state: WebViewState? = null
        setContent {
            CompositionLocalProvider(LocalWebViewFactory provides ::playwrightWebViewFactory) {
                val s = rememberWebViewState(TEST_URL)
                state = s
                WebView(
                    state = s
                )
            }
        }

        runOnIdle {
            val webView = state?.webView
            assertNotNull(webView, "WebView should not be null")
            val screenshot = runBlocking { webView.captureScreenshotOrNull() }
            assertNotNull(screenshot, "Screenshot should not be null")
            assertTrue(screenshot.isNotEmpty(), "Screenshot should not be empty")

            val awtImage = runBlocking { webView.toAwtImage() }
            assertNotNull(awtImage, "AWT Image should not be null")
            assertEquals(PLAYWRIGHT_PAGE_WIDTH, awtImage.width)
            assertEquals(PLAYWRIGHT_PAGE_HEIGHT, awtImage.height)
        }
    }

    @Test
    fun testWebViewPrintToString() = runComposeUiTest {
        var state: WebViewState? = null
        setContent {
            CompositionLocalProvider(LocalWebViewFactory provides ::playwrightWebViewFactory) {
                val s = rememberWebViewState(TEST_URL) 
                state = s
                WebView(
                    state = s
                )
            }
        }

        runOnIdle {
            val webView = state?.webView
            assertNotNull(webView, "WebView should not be null")
            val content = runBlocking { webView.printToStringOrNull() }
            assertNotNull(content, "Content should not be null")
            // GitHub page should contain some recognizable text
            assertTrue(
                actual = content.contains("github.com") ||
                        content.contains("ComposeNativeWebview"),
                message = "Content should contain recognizable text from the real page"
            )
        }
    }
}
