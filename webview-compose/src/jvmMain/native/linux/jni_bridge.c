#include "compose_webview_internal.h"

static JavaVM *g_jvm = NULL;
static jclass g_bridge_class = NULL;
static jmethodID g_on_navigate = NULL;
static jmethodID g_on_ipc = NULL;
static jmethodID g_on_js_result = NULL;
static jmethodID g_on_cookies = NULL;
static jmethodID g_on_screenshot = NULL;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    g_jvm = vm;
    return JNI_VERSION_1_8;
}

JNIEnv *compose_webview_get_env(void) {
    JNIEnv *env = NULL;
    if (g_jvm == NULL) return NULL;
    jint status = (*g_jvm)->GetEnv(g_jvm, (void **) &env, JNI_VERSION_1_8);
    if (status == JNI_EDETACHED) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, (void **) &env, NULL) != 0) {
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
        "dev/nucleusframework/webview/web/linux/WebKitLinuxBridge");
    if (local == NULL) return;
    g_bridge_class = (jclass) (*env)->NewGlobalRef(env, local);
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
