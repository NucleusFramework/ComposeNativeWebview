#!/bin/bash
# Builds libcompose_webview_linux.so for Linux x64 / aarch64.
#
# Prerequisites:
#   - libwebkit2gtk-4.1-dev (or 4.0) + libgtk-3-dev + libcairo2-dev
#   - JAVA_HOME with jni.h
# Usage: ./build.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESOURCE_DIR="$SCRIPT_DIR/../../resources/nucleus/native"
OUT_DIR_X64="$RESOURCE_DIR/linux-x64"
OUT_DIR_ARM64="$RESOURCE_DIR/linux-aarch64"

mkdir -p "$OUT_DIR_X64" "$OUT_DIR_ARM64"

if [ -z "${JAVA_HOME:-}" ]; then
    if command -v javac >/dev/null 2>&1; then
        JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
    fi
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -f "$JAVA_HOME/include/jni.h" ]; then
    echo "ERROR: JAVA_HOME unset or missing jni.h. Set JAVA_HOME to a JDK." >&2
    exit 1
fi

WEBKIT_PKG=""
for pkg in webkit2gtk-4.1 webkit2gtk-4.0; do
    if pkg-config --exists "$pkg"; then
        WEBKIT_PKG="$pkg"
        break
    fi
done
if [ -z "$WEBKIT_PKG" ]; then
    echo "ERROR: neither webkit2gtk-4.1 nor webkit2gtk-4.0 found via pkg-config." >&2
    exit 1
fi

CC="${CC:-cc}"
JNI_INCLUDE="$JAVA_HOME/include"
JNI_INCLUDE_LINUX="$JAVA_HOME/include/linux"
LIB_NAME="libcompose_webview_linux.so"
SOURCES=(
    "$SCRIPT_DIR/jni_bridge.c"
    "$SCRIPT_DIR/view_signals.c"
    "$SCRIPT_DIR/view_lifecycle.c"
    "$SCRIPT_DIR/navigation.c"
    "$SCRIPT_DIR/javascript.c"
    "$SCRIPT_DIR/cookies.c"
    "$SCRIPT_DIR/screenshot.c"
)

build_for() {
    local OUT_DIR="$1"
    local OUT="$OUT_DIR/$LIB_NAME"
    echo "Building $OUT (pkg=$WEBKIT_PKG)..."
    "$CC" -shared -fPIC -O2 -fvisibility=hidden \
        -I"$SCRIPT_DIR" -I"$JNI_INCLUDE" -I"$JNI_INCLUDE_LINUX" \
        $(pkg-config --cflags "$WEBKIT_PKG" gtk+-3.0 libsoup-3.0) \
        "${SOURCES[@]}" \
        $(pkg-config --libs "$WEBKIT_PKG" gtk+-3.0 libsoup-3.0) -lcairo -lpthread \
        -o "$OUT"
    strip --strip-unneeded "$OUT" || true
}

HOST_ARCH="$(uname -m)"
case "$HOST_ARCH" in
    x86_64)        build_for "$OUT_DIR_X64"   ;;
    aarch64|arm64) build_for "$OUT_DIR_ARM64" ;;
    *) echo "ERROR: unsupported host arch '$HOST_ARCH'" >&2; exit 1 ;;
esac

for CACHE_DIR in "$HOME/.cache/nucleus/native"; do
    if [ -d "$CACHE_DIR" ]; then
        rm -rf "$CACHE_DIR"
        echo "Cleared NativeLibraryLoader cache: $CACHE_DIR"
    fi
done

echo "Built compose WebView native library (using $WEBKIT_PKG)."
case "$HOST_ARCH" in
    x86_64)        ls -lh "$OUT_DIR_X64/$LIB_NAME"   ;;
    aarch64|arm64) ls -lh "$OUT_DIR_ARM64/$LIB_NAME" ;;
esac
