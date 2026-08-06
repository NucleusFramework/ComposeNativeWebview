package dev.nucleusframework.webview.demo.visualsuite

import java.io.File

internal const val DEFAULT_REPORT_PATH =
    "/tmp/composewebview-visual-suite-report.txt"

internal fun writeSuiteReport(
    report: SuiteReport,
    path: String = System.getenv("COMPOSEWEBVIEW_SUITE_REPORT") ?: DEFAULT_REPORT_PATH,
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
    File(path).writeText(body)
    // Also mirror to stdout for CI logs
    println(body)
    return path
}
