# webview-compose

Compose Multiplatform WebView library exposing the `dev.nucleusframework.webview.*` API
(inspired by `compose-webview-multiplatform`).

## Usage

```kotlin
dependencies {
    implementation(project(":webview-compose"))
    // or: implementation("dev.nucleusframework:composewebview:<version>")
}
```

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.nucleusframework.webview.web.WebView
import dev.nucleusframework.webview.web.rememberWebViewState

@Composable
fun App() {
    val state = rememberWebViewState("https://sample.com")
    WebView(state = state, modifier = Modifier.fillMaxSize())
}
```

## Platforms

- **Android**: `android.webkit.WebView`
- **iOS**: `WKWebView`
- **WasmJs**: `HTMLIFrameElement`
- **Desktop (JVM)**: Nucleus Tao `NativeView` — WebKit2GTK (Linux), WebView2 (Windows); macOS no-op for now
