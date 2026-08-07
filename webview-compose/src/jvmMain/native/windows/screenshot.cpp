#include "compose_webview_internal.h"

using Microsoft::WRL::Callback;

/** Minimal IStream that appends Write() payloads into a vector (PNG capture). */
class VectorStream : public IStream {
public:
    explicit VectorStream(std::vector<BYTE> *buf) : buf_(buf), ref_(1) {}

    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void **ppv) override {
        if (riid == IID_IUnknown || riid == IID_IStream || riid == IID_ISequentialStream) {
            *ppv = static_cast<IStream *>(this);
            AddRef();
            return S_OK;
        }
        *ppv = nullptr;
        return E_NOINTERFACE;
    }
    ULONG STDMETHODCALLTYPE AddRef() override { return ++ref_; }
    ULONG STDMETHODCALLTYPE Release() override {
        ULONG r = --ref_;
        if (r == 0) delete this;
        return r;
    }
    HRESULT STDMETHODCALLTYPE Read(void *, ULONG, ULONG *) override { return E_NOTIMPL; }
    HRESULT STDMETHODCALLTYPE Write(const void *pv, ULONG cb, ULONG *written) override {
        const BYTE *p = static_cast<const BYTE *>(pv);
        buf_->insert(buf_->end(), p, p + cb);
        if (written) *written = cb;
        return S_OK;
    }
    HRESULT STDMETHODCALLTYPE Seek(LARGE_INTEGER, DWORD, ULARGE_INTEGER *) override {
        return E_NOTIMPL;
    }
    HRESULT STDMETHODCALLTYPE SetSize(ULARGE_INTEGER) override { return E_NOTIMPL; }
    HRESULT STDMETHODCALLTYPE CopyTo(
        IStream *, ULARGE_INTEGER, ULARGE_INTEGER *, ULARGE_INTEGER *) override {
        return E_NOTIMPL;
    }
    HRESULT STDMETHODCALLTYPE Commit(DWORD) override { return S_OK; }
    HRESULT STDMETHODCALLTYPE Revert() override { return E_NOTIMPL; }
    HRESULT STDMETHODCALLTYPE LockRegion(ULARGE_INTEGER, ULARGE_INTEGER, DWORD) override {
        return E_NOTIMPL;
    }
    HRESULT STDMETHODCALLTYPE UnlockRegion(ULARGE_INTEGER, ULARGE_INTEGER, DWORD) override {
        return E_NOTIMPL;
    }
    HRESULT STDMETHODCALLTYPE Stat(STATSTG *, DWORD) override { return E_NOTIMPL; }
    HRESULT STDMETHODCALLTYPE Clone(IStream **) override { return E_NOTIMPL; }

private:
    std::vector<BYTE> *buf_;
    std::atomic<ULONG> ref_;
};

extern "C" {

JNIEXPORT void JNICALL
Java_dev_nucleusframework_webview_web_windows_WebView2WindowsBridge_nativeCaptureScreenshot(
    JNIEnv *, jclass, jlong handle) {
    ComposeWebViewState *s = compose_webview_state_from_handle(handle);
    if (!s || !s->webview) {
        compose_webview_call_on_screenshot(handle, nullptr);
        return;
    }
    auto *buf = new std::vector<BYTE>();
    auto *stream = new VectorStream(buf);
    jlong h = handle;
    s->webview->CapturePreview(
        COREWEBVIEW2_CAPTURE_PREVIEW_IMAGE_FORMAT_PNG,
        stream,
        Callback<ICoreWebView2CapturePreviewCompletedHandler>(
            [h, buf, stream](HRESULT result) -> HRESULT {
                stream->Release();
                if (FAILED(result) || buf->empty()) {
                    compose_webview_call_on_screenshot(h, nullptr);
                } else {
                    compose_webview_call_on_screenshot(h, buf);
                }
                delete buf;
                return S_OK;
            }).Get());
}

}  /* extern "C" */
