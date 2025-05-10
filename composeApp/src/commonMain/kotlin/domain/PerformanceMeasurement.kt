package domain

import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.measureTime

data class PerformanceRecord(
    val nRuns: Int,
    val sumDuration: Duration,
)

data class FullPerformanceRecord(
    val attempts: List<Duration>,
)

// global state hurr durr
val PERFORMANCE_RECORDS = mutableMapOf<String, PerformanceRecord>()
val FULL_PERFORMANCE_RECORDS = mutableMapOf<String, FullPerformanceRecord>()

inline fun _measureAndPrintPerformance(
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

inline fun measureAndPrintPerformance(
    message: String,
    crossinline block: () -> Unit,
) {
    val duration = measureTime(block)
    val pastPerformance = FULL_PERFORMANCE_RECORDS[message]
    val updatedPerformance = FullPerformanceRecord(
        ((pastPerformance?.attempts ?: emptyList()) + duration)
            .sorted()
    )
    FULL_PERFORMANCE_RECORDS[message] = updatedPerformance
    val sumDuration = updatedPerformance.attempts.reduce { acc, d -> acc + d }
    val average = sumDuration / updatedPerformance.attempts.size
    val percentile90 = updatedPerformance.attempts[
        ceil(updatedPerformance.attempts.size * 0.90f).toInt() - 1
    ]
    val percentile95 = updatedPerformance.attempts[
        ceil(updatedPerformance.attempts.size * 0.95f).toInt() - 1
    ]
    val percentile99 = updatedPerformance.attempts[
        ceil(updatedPerformance.attempts.size * 0.99f).toInt() - 1
    ]
    println("$message: $duration, average = $average, 90%-percentile = $percentile90, 95%-percentile = $percentile95, 99%-percentile = $percentile99")
}
