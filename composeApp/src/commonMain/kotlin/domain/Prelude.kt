package domain

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

// we unironically need Prelude for kotlin...

/** 2*[PI], 1 whole turn */
const val TAU: Double = 2*PI

/** Index */
typealias Ix = Int
/** indices */
typealias Indices = List<Int>

/** [fractionalDigits] = digits after the decimal point */
fun Number.round(fractionalDigits: Int): Double {
    val x = this.toDouble()
    val factor = 10.0.pow(fractionalDigits)
    val rounded = (x * factor).roundToInt()/factor
    return rounded
}

/** {integer digits}.{[fractionalDigits]} */
fun Number.formatDecimals(fractionalDigits: Int): String {
    val x = this.toDouble() // 12.345
    val factor = 10.0.pow(fractionalDigits).roundToInt() // 100
    val x00 = (x * factor).roundToInt() // 1234.5
    val integerPart: Int = x00.floorDiv(factor) // 12
    val fractionalPart: Int = x00 - integerPart*factor // 34
    return "$integerPart.$fractionalPart" // 12.34
    // MAYBE: don't show *.0
}

/** [x] >= 0 => +1, otherwise => -1 */
fun signNonZero(x: Double): Int =
    if (x >= 0)
        +1
    else -1

fun Double.divideWithRemainder(divisor: Double): Double =
    floor(this/divisor)