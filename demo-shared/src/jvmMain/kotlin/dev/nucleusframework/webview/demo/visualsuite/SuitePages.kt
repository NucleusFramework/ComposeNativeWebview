package dev.nucleusframework.webview.demo.visualsuite

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

internal fun pageSolidColor(hex: String): String =
    """
    <!DOCTYPE html><html><head><meta charset="utf-8"><title>Color</title>
    <style>html,body{margin:0;width:100%;height:100%;background:$hex !important;}</style>
    </head><body></body></html>
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
