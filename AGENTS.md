# Repository Guidelines

## Project Structure & Module Organization

- `webview-compose/`: Compose Multiplatform WebView library exposing `dev.nucleusframework.webview.*`
  (`WebView`, `WebViewState`, `WebViewNavigator`).
  - Shared API/types: `webview-compose/src/commonMain/kotlin/...`.
  - Platform actuals: `.../src/jvmMain/` (desktop: Linux WebKit2GTK + macOS WKWebView + Windows WebView2),
    `.../src/androidMain/` (Android WebView),
    `.../src/iosMain/` (WKWebView + cinterop in `.../src/nativeInterop/`), `.../src/wasmJsMain/` (IFrame).
  - Unit tests: `src/commonTest/` (JVM / Android host / iOS simulator / Wasm browser).
- `e2e-shared/`: **shared multiplatform visual e2e suite** (`visualsuite/*` in `commonMain`) —
  same catalog of cases on every platform; capabilities skip only what the host cannot do.
- `e2e-desktop/`: desktop host (`./gradlew :e2e-desktop:run`, Nucleus Tao + real WebView).
- `e2e-android/`, `e2e-wasmJs/`, `iosApp/`: same suite host apps (Android WebView / IFrame / WKWebView).
- Generated/build outputs live under `*/build/` (don’t edit or commit).

## Build, Test, and Development Commands

- `./gradlew build`: builds all modules.
- **Visual e2e (only real WebView suite)**:
  - Desktop: `./gradlew :e2e-desktop:run` (exit 0/1)
  - Android: `./gradlew :e2e-android:installDebug` then launch the app
  - Wasm: `./gradlew :e2e-wasmJs:wasmJsBrowserDevelopmentRun`
  - iOS: open `iosApp/iosApp.xcodeproj` in Xcode and Run
- **Unit tests** (`commonTest`, same packages on every target):
  ```bash
  COMMON='--tests dev.nucleusframework.webview.jsbridge.* --tests dev.nucleusframework.webview.web.* --tests dev.nucleusframework.webview.request.* --tests dev.nucleusframework.webview.cookie.* --tests dev.nucleusframework.webview.setting.*'
  ./gradlew :webview-compose:jvmTest $COMMON
  ./gradlew :webview-compose:testDebugUnitTest $COMMON
  ./gradlew :webview-compose:iosSimulatorArm64Test $COMMON   # macOS
  ./gradlew :webview-compose:wasmJsBrowserTest $COMMON
  ```
- `./gradlew :webview-compose:buildNativeLinux` / `buildNativeMacos` / `buildNativeWindows`:
  host native WebView backends into `webview-compose/src/jvmMain/resources/nucleus/native/…`
  (not committed; CI matrix builds them).
- CI: `.github/workflows/build-natives.yaml` + `.github/workflows/pr-build-check.yml`
  (unit commonTest on all targets + visual e2e on desktop matrix + Android emulator).
- GraalVM (e2e desktop): `nucleus.application { graalvm { isEnabled = true … } }`.
  Library reachability metadata under
  `webview-compose/src/jvmMain/resources/META-INF/native-image/dev.nucleusframework/composewebview/`.
- `./gradlew clean`: removes Gradle build outputs.

## Coding Style & Naming Conventions

- Kotlin/Compose: 4-space indentation, idiomatic Kotlin style, `camelCase` for values/functions, `PascalCase` for types and `@Composable` functions (e.g., `WebView`).
- Keep public API changes small and documented (README usage snippets should stay accurate).

## Testing Guidelines

- **Only** real WebView e2e lives in `e2e-shared` → `VisualSuiteApp` / `suiteCatalog()`.
  Do not reintroduce jvmTest driver suites, Playwright, or `LocalWebViewFactory` mocks for coverage.
- Unit logic that is platform-agnostic goes in `webview-compose/src/commonTest` and must run on
  JVM, Android host, iOS simulator, and Wasm with the same packages.
- Cases that need a missing [SuiteCapability] are **Skipped** (not Failed) so the catalog stays identical.

## Commit & Pull Request Guidelines

- Commit messages follow a simple imperative style (e.g., “Add …”, “Fix …”, “Refactor …”) and mention the affected module/API when helpful.
- PRs should include: a short rationale, steps to verify (`./gradlew :e2e-desktop:run`), OS tested (Linux/macOS/Windows), and screenshots/GIFs for UI changes.

## Security & Configuration Tips

- Platform builds may require system deps (Android SDK, Xcode for iOS, WebView2 Runtime on Windows); call out any new requirements in the PR description.
