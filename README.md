# ComposeNativeWebView 🌐

**ComposeNativeWebView** is a **Compose Multiplatform WebView** whose **API design and mobile implementations (Android & iOS) are intentionally derived almost verbatim from
[KevinnZou/compose-webview-multiplatform](https://github.com/KevinnZou/compose-webview-multiplatform)**.

This project exists **first and foremost to bring that same API to Desktop**, backed by **native OS webviews instead of a bundled Chromium runtime**.

```text
io.github.kdroidfilter.webview.*
```

### What is reused vs what is new

🟢 **Reused on purpose**

* API surface (`WebViewState`, `WebViewNavigator`, settings, callbacks, mental model)
* Android implementation (`android.webkit.WebView`)
* iOS implementation (`WKWebView`)
* Overall behavior and semantics

👉 If you already know **compose-webview-multiplatform**, you already know how to use this.

🆕 **What ComposeNativeWebView adds**

* **Desktop support with native engines**
* A **Rust + UniFFI (Wry)** backend instead of KCEF / embedded Chromium
* A **tiny desktop footprint** with system-provided webviews

---

## Platform backends

✅ **Android**: `android.webkit.WebView`
✅ **iOS**: `WKWebView`
✅ **Desktop**: **Wry (Rust)** via **UniFFI**

Desktop engines:

* **Windows**: WebView2
* **macOS**: WKWebView
* **Linux**: WebKitGTK

---

## Quick start 🚀

```kotlin
@Composable
fun App() {
  val state = rememberWebViewState("https://example.com")
  WebView(state, Modifier.fillMaxSize())
}
```

That’s it.

---

## Installation 🧩

### Dependency (all platforms)

```kotlin
dependencies {
  implementation("io.github.kdroidfilter:composewebview:<version>")
}
```

Same artifact for **Android, iOS, Desktop**.

---

### Desktop only: enable native access ⚠️

Wry uses native access via JNA.

```kotlin
compose.desktop {
  application {
    jvmArgs += "--enable-native-access=ALL-UNNAMED"
  }
}
```

---

## Demo app 🎮

Run the feature showcase first:

* **Desktop**: `./gradlew :demo:run`
* **Android**: `./gradlew :demo-android:installDebug`
* **iOS**: open `iosApp/iosApp.xcodeproj` in Xcode and Run

Responsive UI:

* large screens → side **Tools** panel
* phones → **bottom sheet**

---

## Core features ✨

### Content loading

* `loadUrl(url, headers)`
* `loadHtml(html)`
* `loadHtmlFile(fileName, readType)`

---

### Navigation

* `navigateBack()`, `navigateForward()`
* `reload()`, `stopLoading()`
* `canGoBack`, `canGoForward`

---

### Observable state

* `isLoading`
* `loadingState`
* `lastLoadedUrl`
* `pageTitle`

---

### Cookies 🍪

Unified cookie API:

```kotlin
state.cookieManager.setCookie(...)
state.cookieManager.getCookies(url)
state.cookieManager.removeCookies(url)
state.cookieManager.removeAllCookies()
```

---

### JavaScript

```kotlin
navigator.evaluateJavaScript("document.title = 'Hello'")
```

---

### JS ↔ Kotlin bridge 🌉

* injected automatically after page load
* callback-based
* works on all platforms

```js
window.kmpJsBridge.callNative("echo", {...}, callback)
```

---

### RequestInterceptor 🚦

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

## WebViewState & Navigator 📘

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

---

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

## Settings ⚙️

### Custom User-Agent

```kotlin
state.webSettings.customUserAgentString = "MyApp/1.2.3"
```



### Proxy (desktop only)

```kotlin
// HTTP CONNECT Proxy
state.webSettings.desktopWebSettings.proxyConfig = ProxyConfig.Http("proxy.tld", 8888)
// SOCKS5 Proxy
state.webSettings.desktopWebSettings.proxyConfig = ProxyConfig.Socks5("proxy.tld", 1080)
```

Proxy is only supported on Windows, macOS 14.0+ and Linux.

Desktop note on both user-agent and proxy:

* applied at creation time
* changing it **recreates** the WebView (debounced)
* JS context/history may be lost

👉 Set them early.

---

### Logging

```kotlin
state.webSettings.logSeverity = KLogSeverity.Debug
```

---

## Desktop advanced 🖥️

### Access native WebView handle

```kotlin
WebView(
  state,
  navigator,
  onCreated = { native ->
    println(native.getCurrentUrl())
  }
)
```

Useful for debugging or platform-specific hooks.

---

## Project structure 🗂️

* `wrywebview/` → Rust core + UniFFI bindings
* `wrywebview-compose/` → Compose API
* `demo-shared/` → shared demo UI
* `demo/`, `demo-android/`, `iosApp/` → platform launchers

---

## Limitations ⚠️

* RequestInterceptor does **not** intercept sub-resources
* Desktop UA change recreates the WebView

---


## Credits 🙏

* API inspiration: KevinnZou/compose-webview-multiplatform
* Wry (Tauri ecosystem)
* UniFFI (Mozilla)

