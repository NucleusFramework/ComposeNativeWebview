@file:OptIn(ExperimentalWasmJsInterop::class)
package io.github.kdroidfilter.webview.web

import org.w3c.dom.Element
import kotlin.js.Promise

/**
 * Evaluate JavaScript in the iframe context
 */
fun evaluateScriptJs(
    element: Element,
    script: String,
): String = js(
    //language=javascript
    """{
        return element.contentWindow && element.contentWindow.eval ? String(element.contentWindow.eval(script)) : '';
    }"""
)

/**
 * Add a content identifier to an iframe for history tracking
 */
fun addContentIdentifierJs(iframe: Element) {
    js(
        //language=javascript
        """{
            try {
                if (iframe.contentWindow) {
                    const uniqueId = Math.random().toString(36).substring(2, 15);
                    iframe.contentWindow.history.replaceState(
                      { id: uniqueId },
                      '',
                      iframe.contentWindow.location.href
                    );
                }
            } catch (e) {
                console.error("Error adding content identifier:", e);
            }
        }"""
    )
}

/**
 * Request focus on an element
 */
fun requestFocus(element: Element) {
    js("element.focus()")
}

/**
 * Register a DOM listener without relying on Kotlin event casting.
 */
fun registerDomListener(target: JsAny?, type: String, callback: () -> Unit) {
    js(
        //language=javascript
        """{
            if (target && typeof target.addEventListener === 'function') {
                target.addEventListener(type, function () {
                    callback();
                });
            }
        }"""
    )
}

/**
 * Capture screenshot of the iframe using html2canvas if available
 */
fun captureScreenshotJs(
    element: Element
): Promise<JsAny?> = js(
    //language=javascript
    """
    (async () => {
        try {
            if (!window.html2canvas) {
                console.warn("html2canvas not found. captureScreenshot requires html2canvas library.");
                return null;
            }
            const canvas = await window.html2canvas(element);
            return new Promise(resolve => {
                canvas.toBlob(async (blob) => {
                    if (!blob) {
                        resolve(null);
                        return;
                    }
                    const buffer = await blob.arrayBuffer();
                    resolve(new Uint8Array(buffer));
                }, 'image/png');
            });
        } catch (e) {
            console.error("Screenshot capture failed:", e);
            return null;
        }
    })()
    """
)
