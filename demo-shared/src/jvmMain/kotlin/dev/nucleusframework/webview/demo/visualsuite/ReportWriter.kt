package dev.nucleusframework.webview.demo.visualsuite

import java.io.File

internal fun defaultReportPath(): String {
    val dir = System.getProperty("java.io.tmpdir")?.trimEnd('/', '\\') ?: "."
    return "$dir${File.separator}composewebview-visual-suite-report.txt"
}

internal fun writeSuiteReport(
    report: SuiteReport,
    path: String = System.getenv("COMPOSEWEBVIEW_SUITE_REPORT") ?: defaultReportPath(),
): String {
    val duration = report.finishedAtMs - report.startedAtMs
    val body =
        buildString {
            appendLine("ComposeNativeWebView Visual E2E Suite Report")
            appendLine("startedMs=${report.startedAtMs}")
            appendLine("finishedMs=${report.finishedAtMs}")
            appendLine("durationMs=$duration")
            appendLine("total=${report.total}")
            appendLine("passed=${report.passed}")
            appendLine("failed=${report.failed}")
            appendLine("skipped=${report.skipped}")
            appendLine("allGreen=${report.allGreen}")
            appendLine("---")
            report.cases.forEach { c ->
                appendLine("${c.status.name}\t${c.id}\t${c.group}\t${c.title}\t${c.detail}")
            }
            appendLine("---")
            if (report.failed > 0) {
                appendLine("FAILURES:")
                report.cases.filter { it.status == CaseStatus.Failed }.forEach {
                    appendLine(" - ${it.id}: ${it.detail}")
                }
            }
        }
    File(path).apply {
        parentFile?.mkdirs()
        writeText(body)
    }
    // Also mirror to stdout for CI logs
    println(body)
    return path
}
