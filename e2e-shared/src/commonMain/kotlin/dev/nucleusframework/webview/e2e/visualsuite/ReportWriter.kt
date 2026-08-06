package dev.nucleusframework.webview.e2e.visualsuite

internal fun formatSuiteReport(report: SuiteReport): String {
    val duration = report.finishedAtMs - report.startedAtMs
    return buildString {
        appendLine("ComposeNativeWebView Visual E2E Suite Report")
        appendLine("startedMs=${report.startedAtMs}")
        appendLine("finishedMs=${report.finishedAtMs}")
        appendLine("durationMs=$duration")
        appendLine("total=${report.total}")
        appendLine("passed=${report.passed}")
        appendLine("failed=${report.failed}")
        appendLine("skipped=${report.skipped}")
        appendLine("allGreen=${report.allGreen}")
        appendLine("capabilities=${suiteCapabilities().joinToString(",")}")
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
}
