#include "compose_webview_internal.h"

@implementation ComposeWebViewState

- (void)teardown {
    if (self.webView != nil) {
        self.webView.navigationDelegate = nil;
        [self.webView.configuration.userContentController removeScriptMessageHandlerForName:@"ipc"];
        [self.webView removeFromSuperview];
        self.webView = nil;
    }
    self.configuration = nil;
}

@end

ComposeWebViewState *compose_webview_state_from_handle(jlong handle) {
    if (handle == 0) return nil;
    return (__bridge ComposeWebViewState *)(void *)(uintptr_t)handle;
}

JNIEXPORT jlong JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeCreate(
    JNIEnv *env,
    jclass clazz,
    jstring user_agent,
    jstring data_directory,
    jstring init_script,
    jstring js_bridge_script,
    jboolean incognito,
    jboolean enable_devtools,
    jboolean javascript_enabled,
    jdouble zoom_level,
    jboolean transparent,
    jfloat bg_r,
    jfloat bg_g,
    jfloat bg_b,
    jfloat bg_a)
{
    (void)clazz;
    (void)data_directory; // WKWebsiteDataStore has no simple custom-path API.
    compose_webview_ensure_bridge_methods(env);

    ComposeWebViewState *state = [[ComposeWebViewState alloc] init];
    void *retained = (__bridge_retained void *)state;
    state.handle = (jlong)(uintptr_t)retained;

    WKWebViewConfiguration *config = [[WKWebViewConfiguration alloc] init];
    if (incognito) {
        config.websiteDataStore = [WKWebsiteDataStore nonPersistentDataStore];
    } else {
        config.websiteDataStore = [WKWebsiteDataStore defaultDataStore];
    }

    WKPreferences *prefs = [[WKPreferences alloc] init];
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
    prefs.javaScriptEnabled = javascript_enabled ? YES : NO;
#pragma clang diagnostic pop
    config.preferences = prefs;

    WKUserContentController *ucm = [[WKUserContentController alloc] init];

    // Match Linux ipc shim so Kotlin JS bridge can attach.
    NSString *ipcShim =
        @"if (typeof window.ipc === 'undefined') {"
         "  window.ipc = {"
         "    postMessage: function(message) {"
         "      if (window.webkit && window.webkit.messageHandlers &&"
         "          window.webkit.messageHandlers.ipc) {"
         "        window.webkit.messageHandlers.ipc.postMessage("
         "          (typeof message === 'string') ? message : JSON.stringify(message)"
         "        );"
         "      }"
         "    }"
         "  };"
         "}";
    WKUserScript *shim = [[WKUserScript alloc]
        initWithSource:ipcShim
         injectionTime:WKUserScriptInjectionTimeAtDocumentStart
      forMainFrameOnly:NO];
    [ucm addUserScript:shim];

    // JS bridge object, built once in Kotlin. Injected at document start so
    // page scripts can call it without waiting for a post-load injection.
    if (js_bridge_script != NULL) {
        NSString *bridgeSrc = compose_webview_jstring_to_ns(env, js_bridge_script);
        if (bridgeSrc != nil && bridgeSrc.length > 0) {
            WKUserScript *bridge = [[WKUserScript alloc]
                initWithSource:bridgeSrc
                 injectionTime:WKUserScriptInjectionTimeAtDocumentStart
              forMainFrameOnly:NO];
            [ucm addUserScript:bridge];
        }
    }

    if (!transparent) {
        NSString *css =
            @"(function(){var s=document.createElement('style');"
             "s.textContent='html, body { background-color: #ffffff !important; }';"
             "document.documentElement.appendChild(s);})();";
        WKUserScript *cssScript = [[WKUserScript alloc]
            initWithSource:css
             injectionTime:WKUserScriptInjectionTimeAtDocumentStart
          forMainFrameOnly:NO];
        [ucm addUserScript:cssScript];
    }

    if (init_script != NULL) {
        NSString *src = compose_webview_jstring_to_ns(env, init_script);
        if (src != nil && src.length > 0) {
            WKUserScript *userScript = [[WKUserScript alloc]
                initWithSource:src
                 injectionTime:WKUserScriptInjectionTimeAtDocumentStart
              forMainFrameOnly:YES];
            [ucm addUserScript:userScript];
        }
    }

    [ucm addScriptMessageHandler:state name:@"ipc"];
    config.userContentController = ucm;
    state.configuration = config;

    WKWebView *webview = [[WKWebView alloc] initWithFrame:NSZeroRect configuration:config];
    webview.navigationDelegate = state;
    webview.allowsBackForwardNavigationGestures = YES;

    if (user_agent != NULL) {
        NSString *ua = compose_webview_jstring_to_ns(env, user_agent);
        if (ua != nil && ua.length > 0) {
            webview.customUserAgent = ua;
        }
    }

    if (zoom_level > 0.0) {
        webview.pageZoom = zoom_level;
    }

    if (@available(macOS 13.3, *)) {
        webview.inspectable = enable_devtools ? YES : NO;
    }

    webview.wantsLayer = YES;
    if (@available(macOS 12.0, *)) {
        if (transparent) {
            webview.underPageBackgroundColor = [NSColor clearColor];
        } else {
            CGFloat r = (bg_a >= 1.0f) ? bg_r : 1.0;
            CGFloat g = (bg_a >= 1.0f) ? bg_g : 1.0;
            CGFloat b = (bg_a >= 1.0f) ? bg_b : 1.0;
            webview.underPageBackgroundColor =
                [NSColor colorWithCalibratedRed:r green:g blue:b alpha:1.0];
        }
    }

    state.webView = webview;
    return state.handle;
}

JNIEXPORT jlong JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeGetNsView(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil) return 0;
    return (jlong)(uintptr_t)(__bridge void *)state.webView;
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeRelease(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env;
    (void)clazz;
    if (handle == 0) return;
    ComposeWebViewState *state =
        (__bridge_transfer ComposeWebViewState *)(void *)(uintptr_t)handle;
    [state teardown];
}
