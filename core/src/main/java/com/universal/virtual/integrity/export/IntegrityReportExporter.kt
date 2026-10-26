package com.universal.virtual.integrity.export

import com.universal.virtual.integrity.models.IntegrityReport

class IntegrityReportExporter {

    fun exportToJson(report: IntegrityReport): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"reportId\": \"${report.reportId}\",\n")
        sb.append("  \"startedAt\": ${report.startedAt},\n")
        sb.append("  \"completedAt\": ${report.completedAt},\n")
        sb.append("  \"durationMs\": ${report.durationMs},\n")
        sb.append("  \"environmentState\": \"${report.environmentState.name}\",\n")
        sb.append("  \"overallSeverity\": \"${report.overallSeverity.name}\",\n")
        sb.append("  \"healthScore\": ${report.healthScore},\n")
        sb.append("  \"checksExecuted\": ${report.checksExecuted},\n")
        sb.append("  \"passed\": ${report.passed},\n")
        sb.append("  \"warnings\": ${report.warnings},\n")
        sb.append("  \"errors\": ${report.errors},\n")
        sb.append("  \"critical\": ${report.critical},\n")
        sb.append("  \"results\": [\n")
        report.results.forEachIndexed { index, res ->
            sb.append("    {\n")
            sb.append("      \"checkId\": \"${res.checkId}\",\n")
            sb.append("      \"category\": \"${res.category.name}\",\n")
            sb.append("      \"severity\": \"${res.severity.name}\",\n")
            sb.append("      \"title\": \"${res.title.replace("\"", "\\\"")}\",\n")
            sb.append("      \"message\": \"${res.message.replace("\"", "\\\"")}\",\n")
            sb.append("      \"durationMs\": ${res.durationMs}\n")
            sb.append("    }${if (index < report.results.size - 1) "," else ""}\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    fun exportToText(report: IntegrityReport): String {
        val sb = StringBuilder()
        sb.append("====================================================\n")
        sb.append("       UNIVERSAL VIRTUAL CORE INTEGRITY REPORT      \n")
        sb.append("====================================================\n\n")
        sb.append("Report ID:        ${report.reportId}\n")
        sb.append("Environment State: ${report.environmentState.name}\n")
        sb.append("Overall Severity:  ${report.overallSeverity.name}\n")
        sb.append("Health Score:      ${report.healthScore}/100\n")
        sb.append("Duration:          ${report.durationMs} ms\n")
        sb.append("Checks Executed:   ${report.checksExecuted}\n")
        sb.append("Summary:           ${report.passed} Passed, ${report.warnings} Warnings, ${report.errors} Errors, ${report.critical} Critical\n\n")
        sb.append("----------------- CHECK RESULTS --------------------\n")
        report.results.forEach { res ->
            sb.append("[${res.severity.name}] ${res.title}\n")
            sb.append("Category: ${res.category.name} | Duration: ${res.durationMs}ms\n")
            sb.append("Message:  ${res.message}\n")
            if (res.evidence.isNotEmpty()) {
                sb.append("Evidence: ${res.evidence}\n")
            }
            sb.append("\n")
        }
        sb.append("====================================================\n")
        return sb.toString()
    }

    fun exportToCsv(report: IntegrityReport): String {
        val sb = StringBuilder()
        sb.append("Check ID,Category,Severity,Title,Duration Ms,Message\n")
        report.results.forEach { res ->
            val safeTitle = res.title.replace("\"", "\"\"")
            val safeMsg = res.message.replace("\"", "\"\"")
            sb.append("\"${res.checkId}\",\"${res.category.name}\",\"${res.severity.name}\",\"$safeTitle\",${res.durationMs},\"$safeMsg\"\n")
        }
        return sb.toString()
    }
}
