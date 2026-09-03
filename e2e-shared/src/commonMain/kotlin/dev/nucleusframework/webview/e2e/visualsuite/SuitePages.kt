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
