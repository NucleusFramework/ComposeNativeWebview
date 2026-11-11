package dev.nucleusframework.webview.e2e.visualsuite

internal val PAGE_BASE =
    """
    <!DOCTYPE html><html><head><meta charset="utf-8"><title>SUITE</title>
    <style>html,body{margin:0;background:#ffffff;color:#111;font-family:system-ui}
    #marker{padding:12px} #log{font:12px monospace;white-space:pre-wrap;padding:8px}</style>
    </head><body>
    <div id="marker">suite-root</div>
    <div id="log"></div>
    <script>
      window.__suiteLog = function(m){
        var el=document.getElementById('log');
        if(el) el.textContent = (el.textContent||'') + m + '\n';
      };
      window.__suiteSetMarker = function(t){
        var el=document.getElementById('marker');
        if(el) el.textContent = t;
      };
      window.__suiteLastCallback = null;
      window.__suiteOnCallback = function(d){
        window.__suiteLastCallback = (typeof d==='string')?d:JSON.stringify(d);
        window.__suiteLog('callback:'+window.__suiteLastCallback);
      };
    </script>
    </body></html>
    """.trimIndent()

internal fun pageWithMarker(marker: String, title: String = "SUITE"): String =
    """
    <!DOCTYPE html><html><head><meta charset="utf-8"><title>$title</title>
    <style>html,body{margin:0;background:#ffffff;color:#111;font-family:system-ui}
    #marker{padding:16px;font-size:18px;font-weight:700}</style>
    </head><body><div id="marker">$marker</div>
    <script>
      window.__suiteLastCallback=null;
      window.__suiteOnCallback=function(d){
        window.__suiteLastCallback=(typeof d==='string')?d:JSON.stringify(d);
      };
    </script>
    </body></html>
    """.trimIndent()

/**
 * The suite view's init script. It runs ahead of everything else in each document and calls
 * the bridge at once, which makes its call the earliest message a page can send (case B17).
 */
internal const val SUITE_INIT_SCRIPT =
    "window.kmpJsBridge.callNative('suiteEarly', JSON.stringify({from: location.href}));"

/**
 * A page embedding a sandboxed frame that calls the bridge on its own. The frame's
 * origin is opaque, so the engine must report it as `"null"` and not as the embedder's.
 */
internal fun pageWithBridgeCallingFrame(tag: String): String {
    val frame =
        "<script>window.kmpJsBridge.callNative('suiteSender'," +
            "JSON.stringify({from:'frame'}),function(d){});</script>"
    return """
    <!DOCTYPE html><html><head><meta charset="utf-8"><title>FrameSender</title></head>
    <body><div id="marker">$tag</div>
    <iframe sandbox="allow-scripts" srcdoc="${frame.replace("\"", "&quot;")}"></iframe>
    </body></html>
    """.trimIndent()
}

/**
 * A page embedding a sandboxed frame that calls the bridge and then tells the page it did;
 * the page answers with a bridge call of its own. So the page's call arriving means the
 * frame's was made before it.
 */
internal fun pageAnsweringBridgeCallingFrame(tag: String): String {
    val frame =
        "<script>window.kmpJsBridge.callNative('suiteSender'," +
            "JSON.stringify({from:'frame'}),function(d){});" +
            "window.parent.postMessage('frame-called','*');</script>"
    return """
    <!DOCTYPE html><html><head><meta charset="utf-8"><title>AnsweredFrame</title></head>
    <body><div id="marker">$tag</div>
    <script>
      window.addEventListener('message', function (e) {
        if (e.data !== 'frame-called') { return; }
        window.kmpJsBridge.callNative('suiteSender', JSON.stringify({from: 'main'}), function (d) {});
      });
    </script>
    <iframe sandbox="allow-scripts" srcdoc="${frame.replace("\"", "&quot;")}"></iframe>
    </body></html>
    """.trimIndent()
}

/**
 * A page embedding a sandboxed frame that tries every way it has to send a bridge message
 * as the embedding page before sending one of its own: the handler and the event of the
 * Linux backend's relay, the embedder's own `ipc`, and a message that names its sender.
 */
internal fun pageWithSenderForgingFrame(tag: String): String {
    val frame =
        """
        <script>
        (function () {
          function message(from, extra) {
            var m = extra || {};
            m.methodName = 'suiteSender';
            m.params = JSON.stringify({from: from});
            m.callbackId = -1;
            return JSON.stringify(m);
          }
          try {
            window.webkit.messageHandlers.nucleusIpcRelay.postMessage(
              {origin: 'https://suite.local', isMainFrame: true, body: message('forged-handler')});
          } catch (e) {}
          try {
            window.top.dispatchEvent(new CustomEvent(
              '__nucleusIpcMessage', {cancelable: true, detail: message('forged-event')}));
          } catch (e) {}
          try { window.top.ipc.postMessage(message('forged-embedder')); } catch (e) {}
          window.ipc.postMessage(message('forged-fields', {
            origin: 'https://suite.local',
            isMainFrame: true,
            sender: {origin: 'https://suite.local', isMainFrame: true}
          }));
          window.kmpJsBridge.callNative('suiteSender', JSON.stringify({from: 'frame'}), function (d) {});
        })();
        </script>
        """.trimIndent()
    return """
    <!DOCTYPE html><html><head><meta charset="utf-8"><title>ForgingFrame</title></head>
    <body><div id="marker">$tag</div>
    <iframe sandbox="allow-scripts" srcdoc="${frame.replace("\"", "&quot;")}"></iframe>
    </body></html>
    """.trimIndent()
}

/**
 * Calls the JS bridge from an inline script, i.e. while the document is still
 * parsing, and records in `window.__earlyBridge` whether the bridge was there.
 *
 * Only a bridge installed at document start can serve that call — the
 * post-load injection driven by Compose runs far too late.
 */
internal fun pageEarlyBridgeCall(tag: String): String =
    """
    <!DOCTYPE html><html><head><meta charset="utf-8"><title>EarlyBridge</title>
    <style>html,body{margin:0;background:#ffffff;color:#111;font-family:system-ui}
    #marker{padding:16px;font-size:18px;font-weight:700}</style>
    </head><body><div id="marker">$tag</div>
    <script>
      window.__suiteLastCallback=null;
      window.__suiteOnCallback=function(d){
        window.__suiteLastCallback=(typeof d==='string')?d:JSON.stringify(d);
      };
      window.__earlyBridge =
        typeof window.kmpJsBridge !== 'undefined' &&
        typeof window.kmpJsBridge.callNative === 'function';
      if (window.__earlyBridge) {
        window.kmpJsBridge.callNative(
          'suitePing',
          JSON.stringify({early:'$tag'}),
          function(d){ window.__suiteOnCallback(d); }
        );
      }
    </script>
    </body></html>
    """.trimIndent()

internal fun pageSolidColor(hex: String): String =
    """
    <!DOCTYPE html><html><head><meta charset="utf-8"><title>Color</title>
    <style>html,body{margin:0;width:100%;height:100%;background:$hex !important;}</style>
    </head><body></body></html>
    """.trimIndent()

/**
 * Animates a box on every `requestAnimationFrame` and publishes the frame rate
 * of the last full second in `window.__fps` (0 until the first second elapsed).
 * The transform keeps real compositing work in the loop, so a throttled
 * compositor shows up in the number instead of a free-running empty callback.
 */
internal fun pageFrameRate(): String =
    """
    <!DOCTYPE html><html><head><meta charset="utf-8"><title>FrameRate</title>
    <style>html,body{margin:0;background:#0f172a;color:#e2e8f0;font-family:system-ui}
    #marker{padding:12px;font-weight:700}#box{width:72px;height:72px;background:#34d399}</style>
    </head><body><div id="marker">raf-probe</div><div id="box"></div>
    <script>
      window.__fps = 0;
      var frames = 0, last = performance.now(), box = document.getElementById('box');
      function loop(t) {
        frames++;
        box.style.transform = 'translateX(' + ((t / 6) % 240) + 'px)';
        if (t - last >= 1000) {
          window.__fps = Math.round(frames * 1000 / (t - last));
          frames = 0;
          last = t;
        }
        requestAnimationFrame(loop);
      }
      requestAnimationFrame(loop);
    </script></body></html>
    """.trimIndent()

/**
 * Creates a WebGL context and publishes its renderer in `window.__glRenderer`
 * ("unavailable" when the host has no WebGL). A software renderer here explains
 * slow WebGL content far better than any frame-rate number.
 */
internal fun pageWebGl(): String =
    """
    <!DOCTYPE html><html><head><meta charset="utf-8"><title>WebGL</title>
    <style>html,body{margin:0;background:#0f172a;color:#e2e8f0;font-family:system-ui}
    #marker{padding:12px;font-weight:700}</style>
    </head><body><div id="marker">webgl-probe</div><canvas id="gl" width="64" height="64"></canvas>
    <script>
      window.__glRenderer = 'unavailable';
      try {
        var c = document.getElementById('gl');
        var gl = c.getContext('webgl') || c.getContext('experimental-webgl');
        if (gl) {
          gl.clearColor(0.1, 0.6, 0.4, 1.0);
          gl.clear(gl.COLOR_BUFFER_BIT);
          var dbg = gl.getExtension('WEBGL_debug_renderer_info');
          window.__glRenderer =
            (dbg && gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL)) ||
            gl.getParameter(gl.RENDERER) || 'webgl';
        }
      } catch (e) {
        window.__glRenderer = 'error: ' + e;
      }
    </script></body></html>
    """.trimIndent()

internal fun pageWithInitProbe(): String =
    """
    <!DOCTYPE html><html><head><meta charset="utf-8"><title>InitProbe</title></head>
    <body><div id="marker">late</div>
    <script>
      // If initScript set window.__initEarly=true before this runs, flip marker.
      if (window.__initEarly === true) {
        document.getElementById('marker').textContent = 'init-ok';
      } else {
        document.getElementById('marker').textContent = 'init-missing';
      }
    </script>
    </body></html>
    """.trimIndent()
