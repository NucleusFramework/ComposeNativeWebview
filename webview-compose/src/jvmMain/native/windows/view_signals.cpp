#include "compose_webview_internal.h"

using Microsoft::WRL::Callback;

void compose_webview_hook_events(ComposeWebViewState *s) {
    auto *raw = s;

    s->webview->add_NavigationStarting(
        Callback<ICoreWebView2NavigationStartingEventHandler>(
            [raw](ICoreWebView2 *, ICoreWebView2NavigationStartingEventArgs *args) -> HRESULT {
                LPWSTR uri = nullptr;
                std::wstring url;
                if (SUCCEEDED(args->get_Uri(&uri)) && uri) {
                    url.assign(uri);
                    CoTaskMemFree(uri);
                    {
                        std::lock_guard<std::mutex> lock(raw->sourceMutex);
                        raw->lastSource = url;
                    }
                }
                /* Cancel before flipping isLoading so a rejected nav cannot
                 * leave the view stuck in Loading forever. */
                if (!url.empty() &&
                    url.rfind(L"about:", 0) != 0 &&
                    url.rfind(L"data:", 0) != 0 &&
                    url.rfind(L"blob:", 0) != 0) {
                    if (!compose_webview_call_on_navigate(raw->handle, url)) {
                        args->put_Cancel(TRUE);
                        raw->isLoading.store(false, std::memory_order_release);
                        return S_OK;
                    }
                }
                raw->isLoading.store(true, std::memory_order_release);
                return S_OK;
            }).Get(),
        &s->navigationStartingToken);

    s->webview->add_NavigationCompleted(
        Callback<ICoreWebView2NavigationCompletedEventHandler>(
            [raw](ICoreWebView2 *wv, ICoreWebView2NavigationCompletedEventArgs *) -> HRESULT {
                raw->isLoading.store(false, std::memory_order_release);
                LPWSTR src = nullptr;
                if (SUCCEEDED(wv->get_Source(&src)) && src) {
                    std::lock_guard<std::mutex> lock(raw->sourceMutex);
                    raw->lastSource.assign(src);
                    CoTaskMemFree(src);
                }
                LPWSTR title = nullptr;
                if (SUCCEEDED(wv->get_DocumentTitle(&title)) && title) {
                    std::lock_guard<std::mutex> lock(raw->sourceMutex);
                    raw->lastTitle.assign(title);
                    CoTaskMemFree(title);
                }
                BOOL b = FALSE;
                if (SUCCEEDED(wv->get_CanGoBack(&b))) raw->canGoBack.store(b == TRUE);
                if (SUCCEEDED(wv->get_CanGoForward(&b))) raw->canGoForward.store(b == TRUE);
                return S_OK;
            }).Get(),
        &s->navigationCompletedToken);

    s->webview->add_SourceChanged(
        Callback<ICoreWebView2SourceChangedEventHandler>(
            [raw](ICoreWebView2 *wv, ICoreWebView2SourceChangedEventArgs *) -> HRESULT {
                LPWSTR src = nullptr;
                if (SUCCEEDED(wv->get_Source(&src)) && src) {
                    std::lock_guard<std::mutex> lock(raw->sourceMutex);
                    raw->lastSource.assign(src);
                    CoTaskMemFree(src);
                }
                return S_OK;
            }).Get(),
        &s->sourceChangedToken);

    s->webview->add_HistoryChanged(
        Callback<ICoreWebView2HistoryChangedEventHandler>(
            [raw](ICoreWebView2 *wv, IUnknown *) -> HRESULT {
                BOOL b = FALSE;
                if (SUCCEEDED(wv->get_CanGoBack(&b))) raw->canGoBack.store(b == TRUE);
                if (SUCCEEDED(wv->get_CanGoForward(&b))) raw->canGoForward.store(b == TRUE);
                return S_OK;
            }).Get(),
        &s->historyChangedToken);

    s->webview->add_DocumentTitleChanged(
        Callback<ICoreWebView2DocumentTitleChangedEventHandler>(
            [raw](ICoreWebView2 *wv, IUnknown *) -> HRESULT {
                LPWSTR title = nullptr;
                if (SUCCEEDED(wv->get_DocumentTitle(&title)) && title) {
                    {
                        std::lock_guard<std::mutex> lock(raw->sourceMutex);
                        raw->lastTitle.assign(title);
                    }
                    CoTaskMemFree(title);
                    /* Fallback ready-signal: some Navigate(data:) paths paint
                     * and expose a title before NavigationCompleted is seen by
                     * the host message loop. Clear isLoading so Kotlin can
                     * transition to Finished and inject the JS bridge. */
                    if (!raw->lastTitle.empty()) {
                        raw->isLoading.store(false, std::memory_order_release);
                    }
                }
                return S_OK;
            }).Get(),
        &s->documentTitleChangedToken);

    s->compController->add_CursorChanged(
        Callback<ICoreWebView2CursorChangedEventHandler>(
            [raw](ICoreWebView2CompositionController *cc, IUnknown *) -> HRESULT {
                HCURSOR cursor = nullptr;
                if (SUCCEEDED(cc->get_Cursor(&cursor))) {
                    raw->currentCursor.store(cursor, std::memory_order_release);
                    POINT pt;
                    if (GetCursorPos(&pt) && IsWindow(raw->parent)) {
                        ScreenToClient(raw->parent, &pt);
                        if (compose_webview_inside(raw, pt.x, pt.y)) SetCursor(cursor);
                    }
                }
                return S_OK;
            }).Get(),
        &s->cursorChangedToken);

    s->webview->add_WebMessageReceived(
        Callback<ICoreWebView2WebMessageReceivedEventHandler>(
            [raw](ICoreWebView2 *, ICoreWebView2WebMessageReceivedEventArgs *args) -> HRESULT {
                LPWSTR msg = nullptr;
                if (SUCCEEDED(args->TryGetWebMessageAsString(&msg)) && msg) {
                    compose_webview_call_on_ipc(raw->handle, compose_webview_wide_to_utf8(msg));
                    CoTaskMemFree(msg);
                } else {
                    LPWSTR json = nullptr;
                    if (SUCCEEDED(args->get_WebMessageAsJson(&json)) && json) {
                        compose_webview_call_on_ipc(
                            raw->handle, compose_webview_wide_to_utf8(json));
                        CoTaskMemFree(json);
                    }
                }
                return S_OK;
            }).Get(),
        &s->webMessageToken);
}

void compose_webview_unhook_events(ComposeWebViewState *s) {
    if (!s) return;
    if (s->webview) {
        s->webview->remove_NavigationStarting(s->navigationStartingToken);
        s->webview->remove_NavigationCompleted(s->navigationCompletedToken);
        s->webview->remove_SourceChanged(s->sourceChangedToken);
        s->webview->remove_HistoryChanged(s->historyChangedToken);
        s->webview->remove_DocumentTitleChanged(s->documentTitleChangedToken);
        s->webview->remove_WebMessageReceived(s->webMessageToken);
    }
    if (s->compController) {
        s->compController->remove_CursorChanged(s->cursorChangedToken);
    }
}
