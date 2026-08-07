package dev.nucleusframework.webview.e2e.visualsuite

import androidx.compose.ui.graphics.Color

enum class CaseStatus {
    Pending,
    Running,
    Passed,
    Failed,
    Skipped,
}

data class SuiteCase(
    val id: String,
    val group: String,
    val title: String,
    val status: CaseStatus = CaseStatus.Pending,
    val detail: String = "",
)

data class SuiteReport(
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val cases: List<SuiteCase>,
) {
    val passed get() = cases.count { it.status == CaseStatus.Passed }
    val failed get() = cases.count { it.status == CaseStatus.Failed }
    val skipped get() = cases.count { it.status == CaseStatus.Skipped }
    val total get() = cases.size
    val allGreen get() = failed == 0 && passed > 0
}

internal fun statusColor(status: CaseStatus): Color =
    when (status) {
        CaseStatus.Pending -> Color(0xFF64748B)
        CaseStatus.Running -> Color(0xFFFBBF24)
        CaseStatus.Passed -> Color(0xFF34D399)
        CaseStatus.Failed -> Color(0xFFF87171)
        CaseStatus.Skipped -> Color(0xFF94A3B8)
    }
