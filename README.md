# ComposeNativeWebView

**ComposeNativeWebView** is a **Compose Multiplatform WebView** whose **API design and mobile implementations (Android & iOS) are intentionally derived almost verbatim from
[KevinnZou/compose-webview-multiplatform](https://github.com/KevinnZou/compose-webview-multiplatform)**.

Package namespace:

```text
dev.nucleusframework.webview.*
```

> **⚠️ Breaking change (v1.0.0+)** — migrating from pre-Nucleus / Wry builds
>
> 1. **Classpath / package** — rename imports and package references:
>    - `io.github.kdroidfilter.webview.*` → `dev.nucleusframework.webview.*`
>    - Maven coordinates: `io.github.kdroidfilter:…` → `dev.nucleusframework:composewebview`
> 2. **Desktop is Tao-only** — the old **Wry** desktop backend is **removed**. Desktop WebView requires the **Nucleus Tao** backend (`NativeView`):
>    - App entry: `nucleusApplication(backend = NucleusBackend.Tao) { … }`
>    - Dependencies: Nucleus application + `decorated-window-tao` (Swing/Compose Desktop without Tao will not host the WebView)
>
>    **Why Tao?** Native WebViews are opaque platform surfaces. Tao’s `NativeView` embeds them in the same window stack as Compose, so you can **draw Compose UI on top of the WebView** (toolbars, dialogs, loading overlays, chrome) instead of fighting a separate HWND/GTK child. You also get the rest of Tao’s desktop stack (decorated window, title bar, input routing, multiplatform windowing) in one path.
>
> Android, iOS and WasmJs keep the same API shape; only the package and Maven group change.

### What is reused vs what is new

**Reused on purpose**

* API surface (`WebViewState`, `WebViewNavigator`, settings, callbacks, mental model)
* Android implementation (`android.webkit.WebView`)
* iOS implementation (`WKWebView`)
* Overall behavior and semantics

If you already know **compose-webview-multiplatform**, you already know how to use this.

**What ComposeNativeWebView adds**

* Multiplatform packaging under NucleusFramework (`dev.nucleusframework`)
* **WasmJs** target via **IFrame**
* Desktop (JVM) via **Nucleus Tao + NativeView** (Linux WebKit2GTK; macOS WKWebView; Windows WebView2)

---

## Platform backends

- **Android**: `android.webkit.WebView`
- **iOS**: `WKWebView`
- **WasmJs**: `org.w3c.dom.HTMLIFrameElement`
- **Desktop**: Nucleus Tao `NativeView` (requires `nucleusApplication` / Tao backend).
  - **Linux**: WebKit2GTK (`libcompose_webview_linux.so`)
  - **Windows**: WebView2 CompositionController + DirectComposition (`compose_webview_windows.dll`; needs WebView2 Runtime / Edge)
  - **macOS**: WKWebView (`libcompose_webview_macos.dylib`)

---

## Rendering model & frame rate

The desktop backend embeds a **real native view** — it does **not** render the page
offscreen into a bitmap and blit it into the Compose scene:

- **macOS**: the `WKWebView` `NSView` is a subview of the Tao window, below the Compose
  Metal layer; Compose punches a transparent hole over the WebView rect.
- **Linux**: the WebKit2GTK widget is reparented into Tao's content widget.
- **Windows**: WebView2 runs as a DirectComposition visual composited by DWM.

Consequences:

- There is **no frame pacing, throttling or `max_fps` knob** in this library — none of
  the backends contain frame-rate logic. The page paints at whatever rate the platform
  compositor gives it, which is normally the **display refresh rate**.
- The WebView's own frames do not go through Compose. Compose renders its overlay in
  the same window, so a heavy Compose UI shares the GPU with the page, but it never
  gates the WebView's frames.

### Measure it on your hardware

`./gradlew :e2e-desktop:run` reports two rendering measurements (they publish numbers,
they do not enforce thresholds):

```text
Passed  R01  Rendering  requestAnimationFrame rate   90 fps
Passed  R02  Rendering  WebGL renderer               Apple GPU
```

A healthy `R01` is the refresh rate of the display the window is on, and `R02` should
name a GPU (a software renderer there is the usual reason WebGL content is slow).

`R01` is **Skipped** when the document reports `visibilityState = "hidden"`: every engine
suspends `requestAnimationFrame` for a window that is fully covered or backgrounded, so
the sample would read 0 fps and say nothing about the backend. A bare `WKWebView` in a
plain `NSWindow` behaves exactly the same — keep the window in front while measuring.

Reference measurement (macOS, M4, 90 Hz display, Nucleus Tao 2.5.5, `rAF` + WebGL page)
— embedded WebView vs. the same page in a bare `WKWebView` in a plain `NSWindow`:

| Workload | Embedded (Tao `NativeView`) | Bare `WKWebView` |
|----------|-----------------------------|------------------|
| Canvas 2D animation | 90 fps | 90 fps |
| WebGL, GPU-bound shader | 31–34 fps | 32–35 fps |

When reporting a frame-rate problem, include the `R01`/`R02` values, the display refresh
rate, and whether Compose content overlaps the WebView.

---

## Quick start

```kotlin
@Composable
fun App() {
  val state = rememberWebViewState("https://example.com")
  WebView(state, Modifier.fillMaxSize()) {
    // Optional Compose overlay on top of the native WebView
    // (NativeView content slot on desktop; Box overlay elsewhere).
  }
}
```

That’s it.

---

## Installation

### Dependency (all platforms)

```kotlin
dependencies {
  implementation("dev.nucleusframework:composewebview:<version>")
}
```

Same artifact for **Android, iOS, Desktop and WasmJs**.

---

## E2E harness & tests

### Visual e2e suite (same catalog everywhere)

`VisualSuiteApp` + `suiteCatalog()` live in **`e2e-shared` commonMain**.
Every platform host runs that same suite against a **real** WebView:

| Host | Command | Backend |
|------|---------|---------|
| Desktop | `./gradlew :e2e-desktop:run` | Tao + WebKit2GTK / WKWebView / WebView2 |
| Android | `./gradlew :e2e-android:installDebug` then launch app | `android.webkit.WebView` |
| Wasm | `./gradlew :e2e-wasmJs:wasmJsBrowserDevelopmentRun` | IFrame |
| iOS | open `iosApp` in Xcode and Run | WKWebView |

Cases that need a platform-only capability (history on Wasm, isolated native
profiles on desktop, pixel screenshots, …) are **Skipped** with a reason —
not Failed — so the catalog stays identical.

### Unit suite (`commonTest`)

Same pure-logic packages on JVM / Android host / iOS simulator / Wasm browser:

```bash
COMMON='--tests dev.nucleusframework.webview.jsbridge.* --tests dev.nucleusframework.webview.web.* --tests dev.nucleusframework.webview.request.* --tests dev.nucleusframework.webview.cookie.* --tests dev.nucleusframework.webview.setting.*'
./gradlew :webview-compose:jvmTest $COMMON
./gradlew :webview-compose:testDebugUnitTest $COMMON
./gradlew :webview-compose:iosSimulatorArm64Test $COMMON   # macOS
./gradlew :webview-compose:wasmJsBrowserTest $COMMON
```

---

## Core features

### Content loading

* `loadUrl(url, headers)`
* `loadHtml(html)`
* `loadHtmlFile(fileName, readType)`

### Navigation

* `navigateBack()`, `navigateForward()`
* `reload()`, `stopLoading()`
* `canGoBack`, `canGoForward`

### Observable state

* `isLoading`
* `loadingState`
* `lastLoadedUrl`
* `pageTitle`

### Cookies

Unified cookie API:

```kotlin
state.cookieManager.setCookie(...)
state.cookieManager.getCookies(url)
state.cookieManager.removeCookies(url)
state.cookieManager.removeAllCookies()
```

### JavaScript

```kotlin
navigator.evaluateJavaScript("document.title = 'Hello'")
```

### JS ↔ Kotlin bridge

* injected automatically after page load
* callback-based
* works on Android / iOS / WasmJs / Desktop (Linux WebKit)

```js
window.kmpJsBridge.callNative("echo", {...}, callback)
```

### RequestInterceptor

Intercept **navigator-initiated** navigations only:

```kotlin
override fun onInterceptUrlRequest(
  request: WebRequest,
  navigator: WebViewNavigator
): WebRequestInterceptResult
```

Useful for:

* blocking URLs
* app-driven routing
* security rules

---

## WebViewState & Navigator

### State creation

```kotlin
val state = rememberWebViewState(
  url = "https://example.com"
) {
  customUserAgentString = "MyApp/1.0"
}
```

Supports:

* URL
* inline HTML
* resource files

### Navigator

```kotlin
val navigator = rememberWebViewNavigator()
WebView(state, navigator)
```

Commands:

* `loadUrl`
* `loadHtml`
* `loadHtmlFile`
* `evaluateJavaScript`

---

## Settings

### Custom User-Agent

```kotlin
state.webSettings.customUserAgentString = "MyApp/1.2.3"
```

### Logging

```kotlin
state.webSettings.logSeverity = KLogSeverity.Debug
```

---

## Project structure

* `webview-compose/` → Compose Multiplatform API + platform actuals + commonTest
* `e2e-shared/` → shared multiplatform visual e2e suite (`VisualSuiteApp`)
* `e2e-desktop/`, `e2e-android/`, `e2e-wasmJs/`, `iosApp/` → platform hosts for that suite

---

## Limitations

* RequestInterceptor does **not** intercept sub-resources
* **Desktop**: requires Nucleus Tao (`nucleusApplication` + `decorated-window-tao`). Linux (WebKit2GTK), macOS (WKWebView) and Windows (WebView2) are fully wired.
* **WasmJs**:
  * Navigation back and forward is not available in the IFrame
  * The IFrame will work only if the target website has appropriately configured its CORS
  * JS can be executed only on the same origin
  * Cookies can be set only for the parent destination (when the destination of the iframe is the same as the parent destination)

---

## Credits

* API inspiration: KevinnZou/compose-webview-multiplatform
