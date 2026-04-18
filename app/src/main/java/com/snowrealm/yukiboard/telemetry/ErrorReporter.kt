package com.snowrealm.yukiboard.telemetry

interface ErrorReporter {
    fun report(event: TelemetryEvent)
}

data class TelemetryEvent(
    val category: String,
    val severity: Severity,
    val code: String,
    val extras: Map<String, String> = emptyMap()
)

enum class Severity { INFO, WARN, ERROR }
