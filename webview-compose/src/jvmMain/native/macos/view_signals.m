#include "compose_webview_internal.h"

// Serializes a frame's origin the way `window.origin` does: "scheme://host[:port]", or
// "null" for an opaque origin, which WebKit reports with an empty scheme and host.
static NSString *compose_webview_serialize_origin(WKSecurityOrigin *origin) {
    if (origin == nil || origin.protocol.length == 0 || origin.host.length == 0) return @"null";
    NSString *host = origin.host;
    if ([host containsString:@":"]) host = [NSString stringWithFormat:@"[%@]", host];
    if (origin.port == 0) return [NSString stringWithFormat:@"%@://%@", origin.protocol, host];
    return [NSString stringWithFormat:@"%@://%@:%ld", origin.protocol, host, (long)origin.port];
}

@implementation ComposeWebViewState (Signals)

- (void)userContentController:(WKUserContentController *)userContentController
      didReceiveScriptMessage:(WKScriptMessage *)message
{
    (void)userContentController;
    if (![message.name isEqualToString:@"ipc"]) return;

    NSString *payload = nil;
    id body = message.body;
    if ([body isKindOfClass:[NSString class]]) {
        payload = (NSString *)body;
    } else if ([body isKindOfClass:[NSDictionary class]] ||
               [body isKindOfClass:[NSArray class]] ||
               [body isKindOfClass:[NSNumber class]]) {
        NSError *err = nil;
        NSData *data = [NSJSONSerialization dataWithJSONObject:body options:0 error:&err];
        if (data != nil && err == nil) {
            payload = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
        }
    }
    if (payload == nil) return;

    JNIEnv *env = compose_webview_get_env();
    if (env == NULL) return;
    compose_webview_ensure_bridge_methods(env);
    if (compose_webview_bridge_class() == NULL || compose_webview_on_ipc() == NULL) return;

    // The sender as WebKit knows it, not as the page describes itself.
    WKFrameInfo *frame = message.frameInfo;
    jstring jmsg = compose_webview_ns_to_jstring(env, payload);
    jstring jorigin = frame == nil
        ? NULL
        : compose_webview_ns_to_jstring(env, compose_webview_serialize_origin(frame.securityOrigin));
    (*env)->CallStaticVoidMethod(
        env, compose_webview_bridge_class(), compose_webview_on_ipc(), self.handle, jmsg,
        jorigin, (jboolean)(frame != nil && frame.isMainFrame));
    if (jmsg != NULL) (*env)->DeleteLocalRef(env, jmsg);
    if (jorigin != NULL) (*env)->DeleteLocalRef(env, jorigin);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }
}

- (void)webView:(WKWebView *)webView
decidePolicyForNavigationAction:(WKNavigationAction *)navigationAction
decisionHandler:(void (^)(WKNavigationActionPolicy))decisionHandler
{
    (void)webView;
    NSURL *url = navigationAction.request.URL;
    NSString *uri = url.absoluteString;
    if (uri == nil || uri.length == 0) {
        decisionHandler(WKNavigationActionPolicyAllow);
        return;
    }

    if ([uri hasPrefix:@"about:"] || [uri hasPrefix:@"data:"] || [uri hasPrefix:@"blob:"]) {
        decisionHandler(WKNavigationActionPolicyAllow);
        return;
    }

    JNIEnv *env = compose_webview_get_env();
    if (env == NULL) {
        decisionHandler(WKNavigationActionPolicyAllow);
        return;
    }
    compose_webview_ensure_bridge_methods(env);
    if (compose_webview_bridge_class() == NULL || compose_webview_on_navigate() == NULL) {
        decisionHandler(WKNavigationActionPolicyAllow);
        return;
    }

    jstring juri = compose_webview_ns_to_jstring(env, uri);
    jboolean allow = (*env)->CallStaticBooleanMethod(
        env, compose_webview_bridge_class(), compose_webview_on_navigate(), self.handle, juri);
    if (juri != NULL) (*env)->DeleteLocalRef(env, juri);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        decisionHandler(WKNavigationActionPolicyAllow);
        return;
    }
    decisionHandler(allow ? WKNavigationActionPolicyAllow : WKNavigationActionPolicyCancel);
}

@end
