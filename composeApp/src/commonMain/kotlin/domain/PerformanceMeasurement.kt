package domain

import kotlin.time.Duration
import kotlin.time.measureTime

data class PerformanceRecord(
    val nRuns: Int,
    val sumDuration: Duration,
)

// global state hurr durr
val PERFORMANCE_RECORDS = mutableMapOf<String, PerformanceRecord>()

inline fun measureAndPrintPerformance(
    message: String,
    crossinline block: () -> Unit,
) {
    val duration = measureTime(block)
    val pastPerformance = PERFORMANCE_RECORDS[message]
    val updatedPerformance = if (pastPerformance == null)
        PerformanceRecord(1, duration)
    else PerformanceRecord(
        pastPerformance.nRuns + 1,
        pastPerformance.sumDuration + duration
    )
    PERFORMANCE_RECORDS[message] = updatedPerformance
    val average = updatedPerformance.sumDuration / updatedPerformance.nRuns
    println("$message: $duration, average = $average")
}
