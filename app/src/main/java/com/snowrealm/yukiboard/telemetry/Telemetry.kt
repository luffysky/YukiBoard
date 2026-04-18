package com.snowrealm.yukiboard.telemetry

object Telemetry : ErrorReporter {

    @Volatile
    var reporter: ErrorReporter = AndroidLogReporter

    override fun report(event: TelemetryEvent) {
        reporter.report(event)
    }
}
