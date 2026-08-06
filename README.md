# ComposeNativeWebView

**ComposeNativeWebView** is a **Compose Multiplatform WebView** whose **API design and mobile implementations (Android & iOS) are intentionally derived almost verbatim from
[KevinnZou/compose-webview-multiplatform](https://github.com/KevinnZou/compose-webview-multiplatform)**.

Package namespace:

```text
dev.nucleusframework.webview.*
```

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

## Quick start

```kotlin
@Composable
fun App() {
  val state = rememberWebViewState("https://example.com")
  WebView(state, Modifier.fillMaxSize())
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

## Demo app

Run the feature showcase first:

* **Desktop**: `./gradlew :demo:run` (Nucleus application plugin; visual e2e suite on Linux)
* **Android**: `./gradlew :demo-android:installDebug`
* **WasmJs**: `./gradlew :demo-wasmJs:wasmJsBrowserDevelopmentRun`
* **iOS**: open `iosApp/iosApp.xcodeproj` in Xcode and Run

Responsive UI:

* large screens → side **Tools** panel
* phones → **bottom sheet**

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

* `webview-compose/` → Compose Multiplatform API + platform actuals
* `webview-compose-test/` → Playwright-based test helpers (JVM)
* `demo-shared/` → shared demo UI
* `demo/`, `demo-android/`, `demo-wasmJs/`, `iosApp/` → platform launchers

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
