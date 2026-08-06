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
- `./gradlew :demo:run`: runs the desktop demo app (desktop WebView is currently a no-op).
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
