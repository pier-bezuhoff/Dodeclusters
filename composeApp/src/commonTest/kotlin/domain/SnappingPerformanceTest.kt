package domain

import compareScenarios
import core.geometry.randomCircleOrLine
import core.geometry.randomPoint
import kotlin.test.Test

class SnappingPerformanceTest {

    @Test
    fun compareTop2CalculationMethods() {
        val point = randomPoint()
        val circleOrLines = List(200) { i ->
            if (i % 40 == 0)
                null
            else
                randomCircleOrLine()
        }
        // both are around similar ~100 elements, for larger numbers 'for' is 2-3 times faster
        compareScenarios(
            "for", scenario1 = {
//                circleOrLines.top2IndicesBy(
//                    measurer = { it?.distanceFrom(point) ?: Double.POSITIVE_INFINITY },
//                    condition = { o, d -> d < 0.1 }
//                )
            },
            "seq", scenario2 = {
                circleOrLines.asSequence()
                    .mapIndexed { i, o ->
                        i to (o?.distanceFrom(point) ?: Double.POSITIVE_INFINITY)
                    }.filter { (_, d) -> d < 0.1 }
                    .sortedBy { (_, d) -> d }
                    .take(2)
                    .map { (i, _) -> i }
                    .toList()
            }
        )
    }
}