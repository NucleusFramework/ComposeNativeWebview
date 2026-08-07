#include "compose_webview_internal.h"

#include <algorithm>
#include <vector>

/**
 * CompositionController has no HWND of its own — the host must forward
 * Win32 mouse messages via SendMouseInput. Multiple WebViews may share one
 * parent HWND; hit-testing picks the top-most view under the cursor.
 */

static const wchar_t *kParentProp = L"NucleusComposeWebViewList";

struct ParentWebViewList {
    std::vector<ComposeWebViewState *> views;
    WNDPROC originalProc = nullptr;
};

bool compose_webview_inside(ComposeWebViewState *s, int xClient, int yClient) {
    return xClient >= s->xPx && xClient < s->xPx + s->widthPx &&
           yClient >= s->yPx && yClient < s->yPx + s->heightPx;
}

static ComposeWebViewState *hitTest(ParentWebViewList *list, int x, int y) {
    if (!list) return nullptr;
    for (auto it = list->views.rbegin(); it != list->views.rend(); ++it) {
        if (compose_webview_inside(*it, x, y) && (*it)->compController) return *it;
    }
    return nullptr;
}

static LRESULT CALLBACK parentSubclassProc(HWND hwnd, UINT msg, WPARAM w, LPARAM l) {
    auto *list = static_cast<ParentWebViewList *>(GetPropW(hwnd, kParentProp));
    auto callPrev = [&]() -> LRESULT {
        return list && list->originalProc
            ? CallWindowProcW(list->originalProc, hwnd, msg, w, l)
            : DefWindowProcW(hwnd, msg, w, l);
    };
    if (!list) return callPrev();

    switch (msg) {
        case WM_MOUSEMOVE:
        case WM_LBUTTONDOWN: case WM_LBUTTONUP: case WM_LBUTTONDBLCLK:
        case WM_RBUTTONDOWN: case WM_RBUTTONUP: case WM_RBUTTONDBLCLK:
        case WM_MBUTTONDOWN: case WM_MBUTTONUP: case WM_MBUTTONDBLCLK:
        case WM_XBUTTONDOWN: case WM_XBUTTONUP: case WM_XBUTTONDBLCLK: {
            int x = GET_X_LPARAM(l);
            int y = GET_Y_LPARAM(l);
            ComposeWebViewState *s = hitTest(list, x, y);
            if (!s) break;
            POINT pt = {x - s->xPx, y - s->yPx};
            UINT32 mouseData = 0;
            if (msg == WM_XBUTTONDOWN || msg == WM_XBUTTONUP || msg == WM_XBUTTONDBLCLK) {
                mouseData = GET_XBUTTON_WPARAM(w);
            }
            s->compController->SendMouseInput(
                static_cast<COREWEBVIEW2_MOUSE_EVENT_KIND>(msg),
                static_cast<COREWEBVIEW2_MOUSE_EVENT_VIRTUAL_KEYS>(GET_KEYSTATE_WPARAM(w)),
                mouseData, pt);
            if (msg == WM_LBUTTONDOWN || msg == WM_RBUTTONDOWN ||
                msg == WM_MBUTTONDOWN || msg == WM_XBUTTONDOWN) {
                if (s->controller) {
                    s->controller->MoveFocus(COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC);
                }
            }
            return 0;
        }
        case WM_MOUSEWHEEL:
        case WM_MOUSEHWHEEL: {
            POINT pt = {GET_X_LPARAM(l), GET_Y_LPARAM(l)};
            ScreenToClient(hwnd, &pt);
            ComposeWebViewState *s = hitTest(list, pt.x, pt.y);
            if (!s) break;
            POINT local = {pt.x - s->xPx, pt.y - s->yPx};
            s->compController->SendMouseInput(
                static_cast<COREWEBVIEW2_MOUSE_EVENT_KIND>(msg),
                static_cast<COREWEBVIEW2_MOUSE_EVENT_VIRTUAL_KEYS>(GET_KEYSTATE_WPARAM(w)),
                static_cast<UINT32>(GET_WHEEL_DELTA_WPARAM(w)), local);
            return 0;
        }
        case WM_MOUSELEAVE: {
            for (ComposeWebViewState *s : list->views) {
                if (!s->compController) continue;
                POINT pt = {-1, -1};
                s->compController->SendMouseInput(
                    COREWEBVIEW2_MOUSE_EVENT_KIND_LEAVE,
                    static_cast<COREWEBVIEW2_MOUSE_EVENT_VIRTUAL_KEYS>(0), 0, pt);
            }
            break;
        }
        case WM_WINDOWPOSCHANGED: {
            for (ComposeWebViewState *s : list->views) {
                if (s->controller) s->controller->NotifyParentWindowPositionChanged();
            }
            break;
        }
        case WM_SETCURSOR: {
            if (LOWORD(l) != HTCLIENT) break;
            POINT pt;
            if (!GetCursorPos(&pt)) break;
            ScreenToClient(hwnd, &pt);
            ComposeWebViewState *s = hitTest(list, pt.x, pt.y);
            if (!s) break;
            HCURSOR cur = s->currentCursor.load(std::memory_order_acquire);
            if (!cur) break;
            SetCursor(cur);
            return TRUE;
        }
    }
    return callPrev();
}

void compose_webview_install_parent_subclass(ComposeWebViewState *s) {
    if (!s || !IsWindow(s->parent)) return;
    auto *list = static_cast<ParentWebViewList *>(GetPropW(s->parent, kParentProp));
    if (!list) {
        list = new ParentWebViewList();
        SetPropW(s->parent, kParentProp, static_cast<HANDLE>(list));
        LONG_PTR prev = SetWindowLongPtrW(
            s->parent, GWLP_WNDPROC,
            reinterpret_cast<LONG_PTR>(parentSubclassProc));
        list->originalProc = reinterpret_cast<WNDPROC>(prev);
    }
    list->views.push_back(s);
    s->originalParentProc = list->originalProc;
    s->subclassInstalled = true;
}

void compose_webview_uninstall_parent_subclass(ComposeWebViewState *s) {
    if (!s || !s->subclassInstalled || !IsWindow(s->parent)) {
        if (s) s->subclassInstalled = false;
        return;
    }
    auto *list = static_cast<ParentWebViewList *>(GetPropW(s->parent, kParentProp));
    if (!list) {
        s->subclassInstalled = false;
        return;
    }
    list->views.erase(
        std::remove(list->views.begin(), list->views.end(), s),
        list->views.end());
    if (list->views.empty()) {
        WNDPROC current = reinterpret_cast<WNDPROC>(
            GetWindowLongPtrW(s->parent, GWLP_WNDPROC));
        if (current == parentSubclassProc) {
            SetWindowLongPtrW(
                s->parent, GWLP_WNDPROC,
                reinterpret_cast<LONG_PTR>(list->originalProc));
        }
        RemovePropW(s->parent, kParentProp);
        delete list;
    }
    s->subclassInstalled = false;
}
