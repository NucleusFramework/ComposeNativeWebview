#!/bin/bash
# Compiles the compose WKWebView JNI backend into per-architecture dylibs.
#
# Outputs:
#   webview-compose/src/jvmMain/resources/nucleus/native/darwin-{x64,aarch64}/
#     libcompose_webview_macos.dylib
#
# Prerequisites: Xcode command-line tools (clang) + JDK (jni.h).
# Usage: ./build.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESOURCE_DIR="$SCRIPT_DIR/../../resources/nucleus/native"
OUT_DIR_ARM64="$RESOURCE_DIR/darwin-aarch64"
OUT_DIR_X64="$RESOURCE_DIR/darwin-x64"
LIB_NAME="libcompose_webview_macos.dylib"
SOURCES=(
    "$SCRIPT_DIR/jni_bridge.m"
    "$SCRIPT_DIR/view_lifecycle.m"
    "$SCRIPT_DIR/view_signals.m"
    "$SCRIPT_DIR/navigation.m"
    "$SCRIPT_DIR/javascript.m"
    "$SCRIPT_DIR/cookies.m"
    "$SCRIPT_DIR/screenshot.m"
)

mkdir -p "$OUT_DIR_ARM64" "$OUT_DIR_X64"

if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || true)
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -f "$JAVA_HOME/include/jni.h" ]; then
    echo "ERROR: JAVA_HOME unset or missing jni.h. Set JAVA_HOME to a JDK." >&2
    exit 1
fi

JNI_INCLUDE="$JAVA_HOME/include"
JNI_INCLUDE_DARWIN="$JAVA_HOME/include/darwin"

FLAGS=(
    -dynamiclib
    -I"$SCRIPT_DIR" -I"$JNI_INCLUDE" -I"$JNI_INCLUDE_DARWIN"
    -framework Cocoa
    -framework WebKit
    -mmacosx-version-min=11.0
    -fobjc-arc
    -O2
    -fvisibility=hidden
    -Wl,-dead_strip
    -Wl,-x
)

echo "Building $OUT_DIR_ARM64/$LIB_NAME (arm64)..."
clang -arch arm64 "${FLAGS[@]}" -o "$OUT_DIR_ARM64/$LIB_NAME" "${SOURCES[@]}"
strip -x "$OUT_DIR_ARM64/$LIB_NAME"

echo "Building $OUT_DIR_X64/$LIB_NAME (x86_64)..."
clang -arch x86_64 "${FLAGS[@]}" -o "$OUT_DIR_X64/$LIB_NAME" "${SOURCES[@]}"
strip -x "$OUT_DIR_X64/$LIB_NAME"

for CACHE_DIR in "$HOME/Library/Caches/nucleus/native" "$HOME/.cache/nucleus/native"; do
    if [ -d "$CACHE_DIR" ]; then
        rm -rf "$CACHE_DIR"
        echo "Cleared NativeLibraryLoader cache: $CACHE_DIR"
    fi
done

echo "Built compose WebView macOS native library:"
ls -lh "$OUT_DIR_ARM64/$LIB_NAME" "$OUT_DIR_X64/$LIB_NAME"
