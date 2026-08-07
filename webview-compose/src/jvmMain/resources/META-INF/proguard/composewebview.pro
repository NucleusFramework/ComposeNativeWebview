# Keep desktop WebKit / WebView2 JNI bridges — native-image / ProGuard would
# otherwise strip callbacks reached only from the native libs.
-keep class dev.nucleusframework.webview.web.linux.WebKitLinuxBridge {
    public static *;
    public *;
}
-keep class dev.nucleusframework.webview.web.linux.LinuxWebKitNativeWebView {
    <init>(...);
    public *;
}
-keep class dev.nucleusframework.webview.web.macos.WebKitMacOsBridge {
    public static *;
    public *;
}
-keep class dev.nucleusframework.webview.web.macos.MacOsWebKitNativeWebView {
    <init>(...);
    public *;
}
-keep class dev.nucleusframework.webview.web.windows.WebView2WindowsBridge {
    public static *;
    public *;
}
-keep class dev.nucleusframework.webview.web.windows.WindowsWebView2NativeWebView {
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
