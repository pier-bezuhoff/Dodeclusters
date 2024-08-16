package domain

import kotlin.math.pow
import kotlin.math.roundToInt

// we unironically need Prelude for kotlin...

/** [decimalDigits] = digits after the decimal point */
fun Number.round(decimalDigits: Int): Double {
    val x = this.toDouble()
    val factor = 10.0.pow(decimalDigits)
    val rounded = (x * factor).roundToInt()/factor
    return rounded
}

/** [x] >= 0 => +1, otherwise => -1 */
fun signNonZero(x: Double): Int =
    if (x >= 0)
        +1
    else -1
