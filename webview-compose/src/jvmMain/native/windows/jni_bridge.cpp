#include "compose_webview_internal.h"

static JavaVM *g_jvm = nullptr;
static jclass g_bridge_class = nullptr;
static jmethodID g_on_navigate = nullptr;
static jmethodID g_on_ipc = nullptr;
static jmethodID g_on_js_result = nullptr;
static jmethodID g_on_cookies = nullptr;
static jmethodID g_on_screenshot = nullptr;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    g_jvm = vm;
    return JNI_VERSION_1_8;
}

JNIEnv *compose_webview_get_env(void) {
    if (!g_jvm) return nullptr;
    JNIEnv *env = nullptr;
    jint st = g_jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_8);
    if (st == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(reinterpret_cast<void **>(&env), nullptr) != 0) {
            return nullptr;
        }
    } else if (st != JNI_OK) {
        return nullptr;
    }
    return env;
}

void compose_webview_ensure_bridge_methods(JNIEnv *env) {
    if (g_bridge_class != nullptr || env == nullptr) return;
    jclass local = env->FindClass(
        "dev/nucleusframework/webview/web/windows/WebView2WindowsBridge");
    if (local == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return;
    }
    g_bridge_class = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    g_on_navigate = env->GetStaticMethodID(
        g_bridge_class, "nativeOnNavigate", "(JLjava/lang/String;)Z");
    g_on_ipc = env->GetStaticMethodID(
        g_bridge_class, "nativeOnIpcMessage", "(JLjava/lang/String;)V");
    g_on_js_result = env->GetStaticMethodID(
        g_bridge_class, "nativeOnJsResult", "(JLjava/lang/String;)V");
    g_on_cookies = env->GetStaticMethodID(
        g_bridge_class, "nativeOnCookiesResult", "(JLjava/lang/String;)V");
    g_on_screenshot = env->GetStaticMethodID(
        g_bridge_class, "nativeOnScreenshotResult", "(J[B)V");
}

void compose_webview_call_on_js_result(jlong handle, const std::string &utf8) {
    JNIEnv *env = compose_webview_get_env();
    if (!env) return;
    compose_webview_ensure_bridge_methods(env);
    if (!g_bridge_class || !g_on_js_result) return;
    jstring j = env->NewStringUTF(utf8.c_str());
    env->CallStaticVoidMethod(g_bridge_class, g_on_js_result, handle, j);
    env->DeleteLocalRef(j);
    if (env->ExceptionCheck()) env->ExceptionClear();
}

void compose_webview_call_on_ipc(jlong handle, const std::string &utf8) {
    JNIEnv *env = compose_webview_get_env();
    if (!env) return;
    compose_webview_ensure_bridge_methods(env);
    if (!g_bridge_class || !g_on_ipc) return;
    jstring j = env->NewStringUTF(utf8.c_str());
    env->CallStaticVoidMethod(g_bridge_class, g_on_ipc, handle, j);
    env->DeleteLocalRef(j);
    if (env->ExceptionCheck()) env->ExceptionClear();
}

bool compose_webview_call_on_navigate(jlong handle, const std::wstring &url) {
    JNIEnv *env = compose_webview_get_env();
    if (!env) return true;
    compose_webview_ensure_bridge_methods(env);
    if (!g_bridge_class || !g_on_navigate) return true;
    jstring j = env->NewString(
        reinterpret_cast<const jchar *>(url.c_str()),
        static_cast<jsize>(url.size()));
    jboolean allow = env->CallStaticBooleanMethod(
        g_bridge_class, g_on_navigate, handle, j);
    env->DeleteLocalRef(j);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return true;
    }
    return allow == JNI_TRUE;
}

void compose_webview_call_on_cookies(jlong handle, const std::string &json) {
    JNIEnv *env = compose_webview_get_env();
    if (!env) return;
    compose_webview_ensure_bridge_methods(env);
    if (!g_bridge_class || !g_on_cookies) return;
    jstring j = env->NewStringUTF(json.c_str());
    env->CallStaticVoidMethod(g_bridge_class, g_on_cookies, handle, j);
    env->DeleteLocalRef(j);
    if (env->ExceptionCheck()) env->ExceptionClear();
}

void compose_webview_call_on_screenshot(jlong handle, const std::vector<BYTE> *png) {
    JNIEnv *env = compose_webview_get_env();
    if (!env) return;
    compose_webview_ensure_bridge_methods(env);
    if (!g_bridge_class || !g_on_screenshot) return;
    jbyteArray arr = nullptr;
    if (png && !png->empty()) {
        arr = env->NewByteArray(static_cast<jsize>(png->size()));
        if (arr) {
            env->SetByteArrayRegion(
                arr, 0, static_cast<jsize>(png->size()),
                reinterpret_cast<const jbyte *>(png->data()));
        }
    }
    env->CallStaticVoidMethod(g_bridge_class, g_on_screenshot, handle, arr);
    if (arr) env->DeleteLocalRef(arr);
    if (env->ExceptionCheck()) env->ExceptionClear();
}
