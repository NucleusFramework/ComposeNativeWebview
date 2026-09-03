package dev.nucleusframework.webview.web.linux

import dev.nucleusframework.core.runtime.NativeLibraryLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred

/**
 * JNI bridge to `compose_webview.c` (WebKit2GTK).
 *
 * Loaded only on Linux. All native calls must run on the GTK main thread
 * (Tao application thread).
 */
internal object WebKitLinuxBridge {
    private const val LIBRARY_NAME = "compose_webview_linux"

    val isLoaded: Boolean =
        NativeLibraryLoader.load(
            LIBRARY_NAME,
            WebKitLinuxBridge::class.java,
        )

    private val navigateHandlers =
        ConcurrentHashMap<Long, MutableList<(String) -> Boolean>>()
    private val ipcQueues =
        ConcurrentHashMap<Long, ConcurrentLinkedQueue<String>>()
    private val jsCallbacks =
        ConcurrentHashMap<Long, ConcurrentLinkedQueue<(String) -> Unit>>()
    private val cookieDeferreds =
        ConcurrentHashMap<Long, ConcurrentLinkedQueue<CompletableDeferred<String>>>()
    private val screenshotDeferreds =
        ConcurrentHashMap<Long, ConcurrentLinkedQueue<CompletableDeferred<ByteArray?>>>()

    private val requestIds = AtomicLong(1)

    fun addNavigateListener(handle: Long, listener: (String) -> Boolean) {
        navigateHandlers.getOrPut(handle) { mutableListOf() }.add(listener)
    }

    fun removeNavigateListener(handle: Long, listener: (String) -> Boolean) {
        navigateHandlers[handle]?.remove(listener)
    }

    fun drainIpcMessages(handle: Long): List<String> {
        val queue = ipcQueues[handle] ?: return emptyList()
        val drained = ArrayList<String>()
        while (true) {
            val next = queue.poll() ?: break
            drained += next
        }
        return drained
    }

    fun registerJsCallback(handle: Long, callback: (String) -> Unit) {
        jsCallbacks.getOrPut(handle) { ConcurrentLinkedQueue() }.add(callback)
    }

    fun registerCookieDeferred(handle: Long, deferred: CompletableDeferred<String>) {
        cookieDeferreds.getOrPut(handle) { ConcurrentLinkedQueue() }.add(deferred)
    }

    fun registerScreenshotDeferred(handle: Long, deferred: CompletableDeferred<ByteArray?>) {
        screenshotDeferreds.getOrPut(handle) { ConcurrentLinkedQueue() }.add(deferred)
    }

    fun clearHandle(handle: Long) {
        navigateHandlers.remove(handle)
        ipcQueues.remove(handle)
        jsCallbacks.remove(handle)?.forEach { it.invoke("") }
        cookieDeferreds.remove(handle)?.forEach {
            it.complete("[]")
        }
        screenshotDeferreds.remove(handle)?.forEach {
            it.complete(null)
        }
    }

    // ── Callbacks from native (must be public for JNI) ────────────────

    @JvmStatic
    fun nativeOnNavigate(handle: Long, url: String): Boolean {
        val handlers = navigateHandlers[handle]
        if (handlers.isNullOrEmpty()) return true
        // Match previous Wry semantics: any listener returning true allows.
        return handlers.any { it(url) }
    }

    @JvmStatic
    fun nativeOnIpcMessage(handle: Long, message: String) {
        ipcQueues.getOrPut(handle) { ConcurrentLinkedQueue() }.add(message)
    }

    @JvmStatic
    fun nativeOnJsResult(handle: Long, result: String) {
        jsCallbacks[handle]?.poll()?.invoke(result)
    }

    @JvmStatic
    fun nativeOnCookiesResult(handle: Long, json: String) {
        cookieDeferreds[handle]?.poll()?.complete(json)
    }

    @JvmStatic
    fun nativeOnScreenshotResult(handle: Long, bytes: ByteArray?) {
        screenshotDeferreds[handle]?.poll()?.complete(bytes)
    }

    // ── Native methods ────────────────────────────────────────────────

    @JvmStatic
    external fun nativeCreate(
        userAgent: String?,
        dataDirectory: String?,
        initScript: String?,
        jsBridgeScript: String?,
        incognito: Boolean,
        enableDevtools: Boolean,
        javascriptEnabled: Boolean,
        zoomLevel: Double,
        transparent: Boolean,
        bgR: Float,
        bgG: Float,
        bgB: Float,
        bgA: Float,
    ): Long

    @JvmStatic
    external fun nativeGetGtkWidget(handle: Long): Long

    @JvmStatic
    external fun nativeRelease(handle: Long)

    @JvmStatic
    external fun nativeLoadUrl(handle: Long, url: String)

    @JvmStatic
    external fun nativeLoadUrlWithHeaders(
        handle: Long,
        url: String,
        headerNames: Array<String>,
        headerValues: Array<String>,
    )

    @JvmStatic
    external fun nativeLoadHtml(handle: Long, html: String, baseUri: String?)

    @JvmStatic
    external fun nativeGoBack(handle: Long)

    @JvmStatic
    external fun nativeGoForward(handle: Long)

    @JvmStatic
    external fun nativeReload(handle: Long)

    @JvmStatic
    external fun nativeStopLoading(handle: Long)

    @JvmStatic
    external fun nativeCanGoBack(handle: Long): Boolean

    @JvmStatic
    external fun nativeCanGoForward(handle: Long): Boolean

    @JvmStatic
    external fun nativeCurrentUrl(handle: Long): String?

    @JvmStatic
    external fun nativeGetTitle(handle: Long): String?

    @JvmStatic
    external fun nativeIsLoading(handle: Long): Boolean

    @JvmStatic
    external fun nativeSetZoomLevel(handle: Long, zoom: Double)

    @JvmStatic
    external fun nativeFocus(handle: Long)

    @JvmStatic
    external fun nativeOpenDevTools(handle: Long)

    @JvmStatic
    external fun nativeCloseDevTools(handle: Long)

    @JvmStatic
    external fun nativeEvaluateJavaScript(handle: Long, script: String)

    @JvmStatic
    external fun nativeGetCookies(handle: Long, url: String)

    @JvmStatic
    external fun nativeSetCookie(
        handle: Long,
        name: String,
        value: String,
        domain: String?,
        path: String?,
        secure: Boolean,
        httpOnly: Boolean,
        expiresMs: Long,
        sameSite: String?,
    )

    @JvmStatic
    external fun nativeRemoveAllCookies(handle: Long)

    @JvmStatic
    external fun nativeRemoveCookiesForUrl(handle: Long, url: String)

    @JvmStatic
    external fun nativeCaptureScreenshot(handle: Long)
}
