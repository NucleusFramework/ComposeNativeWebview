#include "compose_webview_internal.h"

static NSString *cookie_to_json(NSHTTPCookie *cookie) {
    if (cookie == nil) return @"null";
    NSString *name = compose_webview_json_escape(cookie.name);
    NSString *value = compose_webview_json_escape(cookie.value);
    NSString *domain = compose_webview_json_escape(cookie.domain ?: @"");
    NSString *path = compose_webview_json_escape(cookie.path ?: @"/");
    BOOL secure = cookie.isSecure;
    BOOL httpOnly = cookie.isHTTPOnly;
    BOOL sessionOnly = cookie.isSessionOnly;
    long long expiresMs = 0;
    if (cookie.expiresDate != nil) {
        expiresMs = (long long)([cookie.expiresDate timeIntervalSince1970] * 1000.0);
    }
    NSString *sameSite = @"Lax";
    if (@available(macOS 10.15, *)) {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Warc-performSelector-leaks"
        if ([cookie respondsToSelector:@selector(sameSitePolicy)]) {
            id policy = [cookie valueForKey:@"sameSitePolicy"];
            if ([policy isKindOfClass:[NSString class]]) {
                NSString *p = [(NSString *)policy lowercaseString];
                if ([p isEqualToString:@"none"]) sameSite = @"None";
                else if ([p isEqualToString:@"strict"]) sameSite = @"Strict";
                else sameSite = @"Lax";
            }
        }
#pragma clang diagnostic pop
    }
    return [NSString stringWithFormat:
        @"{\"name\":\"%@\",\"value\":\"%@\",\"domain\":\"%@\",\"path\":\"%@\","
         "\"secure\":%@,\"httpOnly\":%@,\"sessionOnly\":%@,\"expiresDate\":%lld,"
         "\"sameSite\":\"%@\"}",
        name, value, domain, path,
        secure ? @"true" : @"false",
        httpOnly ? @"true" : @"false",
        sessionOnly ? @"true" : @"false",
        expiresMs,
        sameSite];
}

static BOOL host_matches_cookie_domain(NSString *host, NSString *domain) {
    if (host == nil || domain == nil) return NO;
    NSString *d = domain;
    if ([d hasPrefix:@"."]) {
        d = [d substringFromIndex:1];
    }
    if ([host caseInsensitiveCompare:d] == NSOrderedSame) return YES;
    NSString *suffix = [@"." stringByAppendingString:d];
    return [host.lowercaseString hasSuffix:suffix.lowercaseString];
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeGetCookies(
    JNIEnv *env, jclass clazz, jlong handle, jstring url_str)
{
    (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil || url_str == NULL) {
        compose_webview_deliver_cookies_result(handle, @"[]");
        return;
    }
    NSString *url = compose_webview_jstring_to_ns(env, url_str);
    NSURL *nsurl = url != nil ? [NSURL URLWithString:url] : nil;
    NSString *host = nsurl.host;
    jlong h = handle;

    WKHTTPCookieStore *store =
        state.webView.configuration.websiteDataStore.httpCookieStore;
    [store getAllCookies:^(NSArray<NSHTTPCookie *> *cookies) {
        NSMutableString *json = [NSMutableString stringWithString:@"["];
        BOOL first = YES;
        for (NSHTTPCookie *cookie in cookies) {
            if (host != nil && !host_matches_cookie_domain(host, cookie.domain)) {
                continue;
            }
            if (!first) [json appendString:@","];
            [json appendString:cookie_to_json(cookie)];
            first = NO;
        }
        [json appendString:@"]"];
        compose_webview_deliver_cookies_result(h, json);
    }];
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeSetCookie(
    JNIEnv *env,
    jclass clazz,
    jlong handle,
    jstring name,
    jstring value,
    jstring domain,
    jstring path,
    jboolean secure,
    jboolean http_only,
    jlong expires_ms,
    jstring same_site)
{
    (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil || name == NULL || value == NULL) return;

    NSString *cName = compose_webview_jstring_to_ns(env, name);
    NSString *cValue = compose_webview_jstring_to_ns(env, value);
    NSString *cDomain = domain != NULL ? compose_webview_jstring_to_ns(env, domain) : @"";
    NSString *cPath = path != NULL ? compose_webview_jstring_to_ns(env, path) : @"/";
    if (cName == nil || cValue == nil) return;

    NSMutableDictionary *props = [NSMutableDictionary dictionary];
    props[NSHTTPCookieName] = cName;
    props[NSHTTPCookieValue] = cValue;
    props[NSHTTPCookieDomain] = cDomain ?: @"";
    props[NSHTTPCookiePath] = cPath ?: @"/";
    if (secure) props[NSHTTPCookieSecure] = @"TRUE";
    if (http_only) {
        props[@"HttpOnly"] = @YES;
    }
    if (expires_ms > 0) {
        props[NSHTTPCookieExpires] =
            [NSDate dateWithTimeIntervalSince1970:(NSTimeInterval)expires_ms / 1000.0];
    }
    if (same_site != NULL) {
        NSString *ss = compose_webview_jstring_to_ns(env, same_site);
        if (ss != nil) {
            if (@available(macOS 10.15, *)) {
                if ([ss caseInsensitiveCompare:@"None"] == NSOrderedSame) {
                    props[NSHTTPCookieSameSitePolicy] = @"None";
                } else if ([ss caseInsensitiveCompare:@"Strict"] == NSOrderedSame) {
                    props[NSHTTPCookieSameSitePolicy] = NSHTTPCookieSameSiteStrict;
                } else {
                    props[NSHTTPCookieSameSitePolicy] = NSHTTPCookieSameSiteLax;
                }
            }
        }
    }

    NSHTTPCookie *cookie = [NSHTTPCookie cookieWithProperties:props];
    if (cookie == nil) return;

    WKHTTPCookieStore *store =
        state.webView.configuration.websiteDataStore.httpCookieStore;
    [store setCookie:cookie completionHandler:^{}];
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeRemoveAllCookies(
    JNIEnv *env, jclass clazz, jlong handle)
{
    (void)env; (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil) return;

    WKHTTPCookieStore *store =
        state.webView.configuration.websiteDataStore.httpCookieStore;
    [store getAllCookies:^(NSArray<NSHTTPCookie *> *cookies) {
        for (NSHTTPCookie *cookie in cookies) {
            [store deleteCookie:cookie completionHandler:^{}];
        }
    }];
}

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_macos_WebKitMacOsBridge_nativeRemoveCookiesForUrl(
    JNIEnv *env, jclass clazz, jlong handle, jstring url_str)
{
    (void)clazz;
    ComposeWebViewState *state = compose_webview_state_from_handle(handle);
    if (state == nil || state.webView == nil || url_str == NULL) return;

    NSString *url = compose_webview_jstring_to_ns(env, url_str);
    NSURL *nsurl = url != nil ? [NSURL URLWithString:url] : nil;
    NSString *host = nsurl.host;
    if (host == nil) return;

    WKHTTPCookieStore *store =
        state.webView.configuration.websiteDataStore.httpCookieStore;
    [store getAllCookies:^(NSArray<NSHTTPCookie *> *cookies) {
        for (NSHTTPCookie *cookie in cookies) {
            if (host_matches_cookie_domain(host, cookie.domain)) {
                [store deleteCookie:cookie completionHandler:^{}];
            }
        }
    }];
}
