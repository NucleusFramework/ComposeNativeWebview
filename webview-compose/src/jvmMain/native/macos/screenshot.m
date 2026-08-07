#include "compose_webview_internal.h"

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeCaptureScreenshot(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env; (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil) {
        compose_webview_deliver_screenshot_result(handle, nil);
        return;
    }

    jlong h = handle;
    [state.webView takeSnapshotWithConfiguration:nil
                               completionHandler:^(NSImage *snapshotImage, NSError *error) {
        if (error != nil || snapshotImage == nil) {
            compose_webview_deliver_screenshot_result(h, nil);
            return;
        }
        NSData *tiff = [snapshotImage TIFFRepresentation];
        if (tiff == nil) {
            compose_webview_deliver_screenshot_result(h, nil);
            return;
        }
        NSBitmapImageRep *rep = [NSBitmapImageRep imageRepWithData:tiff];
        if (rep == nil) {
            compose_webview_deliver_screenshot_result(h, nil);
            return;
        }
        NSData *png = [rep representationUsingType:NSBitmapImageFileTypePNG properties:@{}];
        compose_webview_deliver_screenshot_result(h, png);
    }];
}
