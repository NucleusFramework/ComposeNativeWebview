package dev.nucleusframework.webview.web.e2e

internal val E2E_PAGE_HTML =
    """
    <!DOCTYPE html>
    <html>
    <head><meta charset="utf-8"><title>E2E Page</title></head>
    <body>
      <h1>E2E</h1>
      <div id="marker">hello-e2e</div>
    </body>
    </html>
    """.trimIndent()

internal val E2E_PAGE_A =
    """
    <!DOCTYPE html><html><head><title>A</title></head>
    <body><div id="marker">page-a</div></body></html>
    """.trimIndent()

internal val E2E_PAGE_B =
    """
    <!DOCTYPE html><html><head><title>B</title></head>
    <body><div id="marker">page-b</div></body></html>
    """.trimIndent()
