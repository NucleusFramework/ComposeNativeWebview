/**
 * Shared state and JNI helpers for the macOS WKWebView backend.
 * Not a public API — only used by the compose_webview_*.m units.
 *
 * Mirrors the Linux/Windows layout (jni_bridge / lifecycle / signals /
 * navigation / javascript / cookies / screenshot).
 */
#ifndef COMPOSE_WEBVIEW_INTERNAL_H
#define COMPOSE_WEBVIEW_INTERNAL_H

#import <Cocoa/Cocoa.h>
#import <WebKit/WebKit.h>
#include <jni.h>
#include <stdint.h>
#include <string.h>

@interface ComposeWebViewState : NSObject
@property (nonatomic, strong) WKWebView *webView;
@property (nonatomic, strong) WKWebViewConfiguration *configuration;
@property (nonatomic, assign) jlong handle;
- (void)teardown;
@end

/* Delegate / script-message methods live in view_signals.m */
@interface ComposeWebViewState (Signals) <WKNavigationDelegate, WKScriptMessageHandler>
@end

/* jni_bridge.m */
JNIEnv *compose_webview_get_env(void);
void compose_webview_ensure_bridge_methods(JNIEnv *env);
jclass compose_webview_bridge_class(void);
jmethodID compose_webview_on_navigate(void);
jmethodID compose_webview_on_ipc(void);
jmethodID compose_webview_on_js_result(void);
jmethodID compose_webview_on_cookies(void);
jmethodID compose_webview_on_screenshot(void);

NSString *compose_webview_jstring_to_ns(JNIEnv *env, jstring js);
jstring compose_webview_ns_to_jstring(JNIEnv *env, NSString *s);
NSString *compose_webview_json_escape(NSString *raw);

void compose_webview_deliver_js_result(jlong handle, NSString *payload);
void compose_webview_deliver_cookies_result(jlong handle, NSString *json);
void compose_webview_deliver_screenshot_result(jlong handle, NSData *png);

/* view_lifecycle.m */
ComposeWebViewState *compose_webview_state_from_handle(jlong handle);

/* cookies.m helpers used only there — declared static in cookies.m */

#endif /* COMPOSE_WEBVIEW_INTERNAL_H */
