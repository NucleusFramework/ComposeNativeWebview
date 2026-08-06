#include "compose_webview_internal.h"

static JavaVM *g_jvm = NULL;
static jclass g_bridge_class = NULL;
static jmethodID g_on_navigate = NULL;
static jmethodID g_on_ipc = NULL;
static jmethodID g_on_js_result = NULL;
static jmethodID g_on_cookies = NULL;
static jmethodID g_on_screenshot = NULL;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    g_jvm = vm;
    return JNI_VERSION_1_8;
}

JNIEnv *compose_webview_get_env(void) {
    if (g_jvm == NULL) return NULL;
    JNIEnv *env = NULL;
    jint status = (*g_jvm)->GetEnv(g_jvm, (void **)&env, JNI_VERSION_1_8);
    if (status == JNI_EDETACHED) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, (void **)&env, NULL) != 0) {
            return NULL;
        }
    } else if (status != JNI_OK) {
        return NULL;
    }
    return env;
}

void compose_webview_ensure_bridge_methods(JNIEnv *env) {
    if (g_bridge_class != NULL) return;
    jclass local = (*env)->FindClass(
        env,
        "dev/nucleusframework/webview/web/macos/WebKitMacOsBridge");
    if (local == NULL) return;
    g_bridge_class = (jclass)(*env)->NewGlobalRef(env, local);
    (*env)->DeleteLocalRef(env, local);
    g_on_navigate = (*env)->GetStaticMethodID(
        env, g_bridge_class, "nativeOnNavigate", "(JLjava/lang/String;)Z");
    g_on_ipc = (*env)->GetStaticMethodID(
        env, g_bridge_class, "nativeOnIpcMessage", "(JLjava/lang/String;)V");
    g_on_js_result = (*env)->GetStaticMethodID(
        env, g_bridge_class, "nativeOnJsResult", "(JLjava/lang/String;)V");
    g_on_cookies = (*env)->GetStaticMethodID(
        env, g_bridge_class, "nativeOnCookiesResult", "(JLjava/lang/String;)V");
    g_on_screenshot = (*env)->GetStaticMethodID(
        env, g_bridge_class, "nativeOnScreenshotResult", "(J[B)V");
}

jclass compose_webview_bridge_class(void) { return g_bridge_class; }
jmethodID compose_webview_on_navigate(void) { return g_on_navigate; }
jmethodID compose_webview_on_ipc(void) { return g_on_ipc; }
jmethodID compose_webview_on_js_result(void) { return g_on_js_result; }
jmethodID compose_webview_on_cookies(void) { return g_on_cookies; }
jmethodID compose_webview_on_screenshot(void) { return g_on_screenshot; }

NSString *compose_webview_jstring_to_ns(JNIEnv *env, jstring js) {
    if (js == NULL) return nil;
    const char *utf = (*env)->GetStringUTFChars(env, js, NULL);
    if (utf == NULL) return nil;
    NSString *out = [NSString stringWithUTF8String:utf];
    (*env)->ReleaseStringUTFChars(env, js, utf);
    return out;
}

jstring compose_webview_ns_to_jstring(JNIEnv *env, NSString *s) {
    if (s == nil) return NULL;
    const char *utf = [s UTF8String];
    if (utf == NULL) return NULL;
    return (*env)->NewStringUTF(env, utf);
}

NSString *compose_webview_json_escape(NSString *raw) {
    if (raw == nil) return @"";
    NSMutableString *out = [NSMutableString stringWithCapacity:raw.length + 8];
    for (NSUInteger i = 0; i < raw.length; i++) {
        unichar c = [raw characterAtIndex:i];
        switch (c) {
            case '"':  [out appendString:@"\\\""]; break;
            case '\\': [out appendString:@"\\\\"]; break;
            case '\b': [out appendString:@"\\b"]; break;
            case '\f': [out appendString:@"\\f"]; break;
            case '\n': [out appendString:@"\\n"]; break;
            case '\r': [out appendString:@"\\r"]; break;
            case '\t': [out appendString:@"\\t"]; break;
            default:
                if (c < 0x20) {
                    [out appendFormat:@"\\u%04x", c];
                } else {
                    [out appendFormat:@"%C", c];
                }
                break;
        }
    }
    return out;
}

void compose_webview_deliver_js_result(jlong handle, NSString *payload) {
    JNIEnv *env = compose_webview_get_env();
    if (env == NULL) return;
    compose_webview_ensure_bridge_methods(env);
    if (g_bridge_class == NULL || g_on_js_result == NULL) return;
    jstring jpayload = compose_webview_ns_to_jstring(env, payload ?: @"");
    (*env)->CallStaticVoidMethod(env, g_bridge_class, g_on_js_result, handle, jpayload);
    if (jpayload != NULL) (*env)->DeleteLocalRef(env, jpayload);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }
}

void compose_webview_deliver_cookies_result(jlong handle, NSString *json) {
    JNIEnv *env = compose_webview_get_env();
    if (env == NULL) return;
    compose_webview_ensure_bridge_methods(env);
    if (g_bridge_class == NULL || g_on_cookies == NULL) return;
    jstring jjson = compose_webview_ns_to_jstring(env, json ?: @"[]");
    (*env)->CallStaticVoidMethod(env, g_bridge_class, g_on_cookies, handle, jjson);
    if (jjson != NULL) (*env)->DeleteLocalRef(env, jjson);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }
}

void compose_webview_deliver_screenshot_result(jlong handle, NSData *png) {
    JNIEnv *env = compose_webview_get_env();
    if (env == NULL) return;
    compose_webview_ensure_bridge_methods(env);
    if (g_bridge_class == NULL || g_on_screenshot == NULL) return;

    jbyteArray jbytes = NULL;
    if (png != nil && png.length > 0) {
        jbytes = (*env)->NewByteArray(env, (jsize)png.length);
        if (jbytes != NULL) {
            (*env)->SetByteArrayRegion(
                env, jbytes, 0, (jsize)png.length, (const jbyte *)png.bytes);
        }
    }
    (*env)->CallStaticVoidMethod(env, g_bridge_class, g_on_screenshot, handle, jbytes);
    if (jbytes != NULL) (*env)->DeleteLocalRef(env, jbytes);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }
}
