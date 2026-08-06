# Repository Guidelines

## Project Structure & Module Organization

- `demo/`: Compose Desktop sample app (`demo/src/jvmMain/kotlin/...`).
- `webview-compose/`: Compose Multiplatform WebView library exposing `dev.nucleusframework.webview.*`
  (`WebView`, `WebViewState`, `WebViewNavigator`).
  - Shared API/types: `webview-compose/src/commonMain/kotlin/...`.
  - Platform actuals: `.../src/jvmMain/` (desktop no-op), `.../src/androidMain/` (Android WebView),
    `.../src/iosMain/` (WKWebView + cinterop in `.../src/nativeInterop/`), `.../src/wasmJsMain/` (IFrame).
- `webview-compose-test/`: JVM testing helpers (Playwright-backed mock WebView).
- Generated/build outputs live under `*/build/` (don’t edit or commit).

## Build, Test, and Development Commands

- `./gradlew build`: builds all modules.
- `./gradlew :demo:run`: runs the desktop demo via the Nucleus application plugin
  (Tao backend + WebKit2GTK on Linux; visual e2e suite entrypoint).
- `./gradlew :webview-compose:buildNativeLinux`: builds
  `libcompose_webview_linux.so` for the host arch into
  `webview-compose/src/jvmMain/resources/nucleus/native/linux-{x64,aarch64}/`
  (requires `libwebkit2gtk-4.1-dev` + `libgtk-3-dev` + JDK).
  Or: `bash webview-compose/src/jvmMain/native/linux/build.sh`.
- CI: `.github/workflows/build-natives.yaml` matrix builds **linux-x64**
  (`ubuntu-latest`) and **linux-aarch64** (`ubuntu-24.04-arm`), uploads
  artifacts; PR/publish workflows download them before compile/publish.
  Natives are **not** committed (see `.gitignore`).
- GraalVM (demo): `nucleus.application { graalvm { isEnabled = true … } }`.
  Library reachability metadata lives under
  `webview-compose/src/jvmMain/resources/META-INF/native-image/dev.nucleusframework/composewebview/`
  (JNI bridge + `nucleus/native/**` resources). Demo app metadata under
  `demo/src/jvmMain/resources/META-INF/native-image/.../composewebview-demo/`.
  Build a native image with `:demo:package` / Nucleus native-image tasks once
  natives are present for the host OS.
- `./gradlew :webview-compose:compileDebugKotlinAndroid`: compiles the Android implementation (requires Android SDK).
- `./gradlew clean`: removes Gradle build outputs.

## Coding Style & Naming Conventions

- Kotlin/Compose: 4-space indentation, idiomatic Kotlin style, `camelCase` for values/functions, `PascalCase` for types and `@Composable` functions (e.g., `WebView`).
- Keep public API changes small and documented (README usage snippets should stay accurate).

## Testing Guidelines

- Kotlin tests (when added) should live in `*/src/jvmTest/kotlin` (or `commonTest`) and run with `./gradlew test`.

## Commit & Pull Request Guidelines

- Commit messages follow a simple imperative style (e.g., “Add …”, “Fix …”, “Refactor …”) and mention the affected module/API when helpful.
- PRs should include: a short rationale, steps to verify (`./gradlew :demo:run`), OS tested (Linux/macOS/Windows), and screenshots/GIFs for UI changes.

## Security & Configuration Tips

- Platform builds may require system deps (Android SDK, Xcode for iOS); call out any new requirements in the PR description.
