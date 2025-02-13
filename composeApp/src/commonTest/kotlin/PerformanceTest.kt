import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

private sealed interface Thing {
    val value: Double
    data class A(override val value: Double): Thing
    data class B(override val value: Double): Thing
    data class C(override val value: Double): Thing
    data class D(override val value: Double): Thing
    data class E(override val value: Double): Thing
}

private fun workWithThing(thing: Thing): Double =
    when (thing) {
        is Thing.A -> thing.value.pow(2)
        is Thing.B -> thing.value.pow(0.5)
        is Thing.C -> thing.value + 100
        is Thing.D -> thing.value/200.0
        is Thing.E -> exp(thing.value)
    }

private sealed interface Something {
    val value: Double
    fun calculate(): Double

    data class A(override val value: Double): Something {
        override fun calculate(): Double =
            value.pow(2)
    }
    data class B(override val value: Double): Something {
        override fun calculate(): Double =
            value.pow(0.5)
    }
    data class C(override val value: Double): Something {
        override fun calculate(): Double =
            value + 100
    }
    data class D(override val value: Double): Something {
        override fun calculate(): Double =
            value/200.0
    }
    data class E(override val value: Double): Something {
        override fun calculate(): Double =
            exp(value)
    }
}

class PerformanceTest {

    // when pattern-matching is 5%-10% faster than OOP-style method overloading polymorphism on desktop & wasm
    @Ignore
    @Test
    fun comparePolymorphism() {
        val things = mutableListOf<Thing>()
        val somethings = mutableListOf<Something>()
        repeat(10_000) {
            val value = Random.nextDouble(-10.0, 10.0)
            when (Random.nextInt(1..5)) {
                1 -> { things.add(Thing.A(value)); somethings.add(Something.A(value)) }
                2 -> { things.add(Thing.B(value)); somethings.add(Something.B(value)) }
                3 -> { things.add(Thing.C(value)); somethings.add(Something.C(value)) }
                4 -> { things.add(Thing.D(value)); somethings.add(Something.D(value)) }
                5 -> { things.add(Thing.E(value)); somethings.add(Something.E(value)) }
                else -> {}
            }
        }
        val results = mutableListOf<Double>()
        var time1 = 0.seconds
        var time2 = 0.seconds
        var count1 = 0
        var count2 = 0
        repeat(1000) {
            if (Random.nextBoolean()) {
                time1 += measureTime {
                    things.forEach {
                        results += workWithThing(it)
                    }
                }
                count1 += 1
            } else {
                time2 += measureTime {
                    somethings.forEach {
                        results += it.calculate()
                    }
                }
                count2 += 1
            }
            results.clear()
        }
        println("pattern-matching: ${time1/count1} ($count1 times)")
        println("oop polymorphism: ${time2/count2} ($count2 times)")
    }

    @Test
    fun compareLoops() {
        val things: List<Thing> = (0 until 1_000).map {
            val value = Random.nextDouble(-100.0, 100.0)
            when (Random.nextInt(1..3)) {
                1 -> Thing.A(value)
                2 -> Thing.B(value)
                3 -> Thing.C(value)
                else -> Thing.A(value)
            }
        }
        compareScenarios(
            "1 loop", {
                var accumulator = 0.0
                for (thing in things) {
                    when (thing) {
                        is Thing.A -> accumulator += thing.value
                        is Thing.B -> accumulator += thing.value - 1
                        is Thing.C -> accumulator += thing.value + 1
                        else -> {}
                    }
                }
            },
            "3 loops", {
                var accumulator = 0.0
                for (thing in things) {
//                    if (thing is Thing.A)
                        accumulator += thing.value
                }
                for (thing in things) {
//                    if (thing is Thing.B)
                        accumulator += thing.value - 1
                }
                for (thing in things) {
//                    if (thing is Thing.C)
                        accumulator += thing.value + 1
                }
            }
        )
    }
}

inline fun compareScenarios(
    name1: String, crossinline scenario1: () -> Unit,
    name2: String, crossinline scenario2: () -> Unit,
    nRuns: Int = 1000,
) {
    var time1 = 0.seconds
    var time2 = 0.seconds
    var count1 = 0
    var count2 = 0
    repeat(nRuns) {
        when (Random.nextInt(1..2)) {
            1 -> {
                time1 += measureTime { scenario1() }
                count1 += 1
            }
            2 -> {
                time2 += measureTime { scenario2() }
                count2 += 1
            }
            else -> {}
        }
    }
    println("$name1: average time = ${time1/min(count1, 1)} of $count1 tries")
    println("$name2: average time = ${time2/min(count2, 1)} of $count2 tries")
}