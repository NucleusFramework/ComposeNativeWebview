# Keep the Linux WebKit JNI bridge — native-image / ProGuard would otherwise
# strip callbacks reached only from libcompose_webview_linux.so.
-keep class dev.nucleusframework.webview.web.linux.WebKitLinuxBridge {
    public static *;
    public *;
}
-keep class dev.nucleusframework.webview.web.linux.LinuxWebKitNativeWebView {
    <init>(...);
    public *;
}
-keep class dev.nucleusframework.webview.web.NativeWebView {
    public *;
}
-keep class dev.nucleusframework.webview.web.DesktopWebView {
    <init>(...);
    public *;
}
-keep class dev.nucleusframework.webview.cookie.DesktopCookieManager {
    <init>(...);
    public *;
}
-keep class dev.nucleusframework.webview.cookie.NativeCookieDto {
    <init>(...);
    *;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
