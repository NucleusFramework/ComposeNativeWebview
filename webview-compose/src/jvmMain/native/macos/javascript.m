#include "compose_webview_internal.h"

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeEvaluateJavaScript(
    JNIEnv *env, jclass clazz, jlong handle, jstring script_str)
{
    (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil || script_str == NULL) {
        compose_webview_deliver_js_result(handle, @"");
        return;
    }
    NSString *script = compose_webview_jstring_to_ns(env, script_str);
    if (script == nil) {
        compose_webview_deliver_js_result(handle, @"");
        return;
    }

    jlong h = handle;
    [state.webView evaluateJavaScript:script
                    completionHandler:^(id result, NSError *error) {
        if (error != nil || result == nil || result == [NSNull null]) {
            if (error != nil) {
                compose_webview_deliver_js_result(h, @"");
            } else {
                compose_webview_deliver_js_result(h, @"null");
            }
            return;
        }
        if ([result isKindOfClass:[NSString class]]) {
            NSString *escaped = compose_webview_json_escape((NSString *)result);
            compose_webview_deliver_js_result(
                h, [NSString stringWithFormat:@"\"%@\"", escaped]);
            return;
        }
        if ([result isKindOfClass:[NSNumber class]]) {
            NSNumber *n = (NSNumber *)result;
            const char *t = [n objCType];
            if (t != NULL && (strcmp(t, @encode(BOOL)) == 0 ||
                              strcmp(t, @encode(bool)) == 0 ||
                              strcmp(t, @encode(char)) == 0)) {
                compose_webview_deliver_js_result(h, [n boolValue] ? @"true" : @"false");
            } else {
                compose_webview_deliver_js_result(h, [n stringValue]);
            }
            return;
        }
        if ([NSJSONSerialization isValidJSONObject:result]) {
            NSError *jsonErr = nil;
            NSData *data =
                [NSJSONSerialization dataWithJSONObject:result options:0 error:&jsonErr];
            if (data != nil && jsonErr == nil) {
                NSString *json =
                    [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
                compose_webview_deliver_js_result(h, json ?: @"");
                return;
            }
        }
        compose_webview_deliver_js_result(
            h,
            [NSString stringWithFormat:@"\"%@\"",
             compose_webview_json_escape([result description])]);
    }];
}
