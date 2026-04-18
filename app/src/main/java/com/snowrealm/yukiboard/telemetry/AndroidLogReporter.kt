package com.snowrealm.yukiboard.telemetry

import android.util.Log

object AndroidLogReporter : ErrorReporter {
    override fun report(event: TelemetryEvent) {
        val tag = "Telemetry/${event.category}"
        val extrasPart = if (event.extras.isEmpty()) {
            ""
        } else {
            " extras=" + event.extras.entries.joinToString(",") { (k, v) -> "$k=$v" }
        }
        val msg = event.code + extrasPart
        when (event.severity) {
            Severity.INFO -> Log.i(tag, msg)
            Severity.WARN -> Log.w(tag, msg)
            Severity.ERROR -> Log.e(tag, msg)
        }
    }
}
