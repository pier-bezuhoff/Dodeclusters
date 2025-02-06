@file:Suppress("NOTHING_TO_INLINE")

package domain

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

// we unironically need Prelude for kotlin...

/** 2*[PI], 360°, 1 whole turn */
const val TAU: Double = 2*PI

/** Index of an array */
typealias Ix = Int

/** [fractionalDigits] = digits after the decimal point */
fun Number.round(fractionalDigits: Int): Double {
    val x = this.toDouble()
    val factor = 10.0.pow(fractionalDigits)
    val rounded = (x * factor).roundToInt()/factor
    return rounded
}

/** {integer digits}.{[fractionalDigits]} */
fun Number.formatDecimals(
    fractionalDigits: Int,
    showDotZero: Boolean = true
): String {
    val x = this.toDouble() // 12.345
    val factor = 10.0.pow(fractionalDigits).roundToInt() // 100 (fractionalDigits = 2)
    val x00 = (x * factor).roundToInt() // 1234.5
    val integerPart: Int = x00.floorDiv(factor) // 12
    val fractionalPart: Int = x00 - integerPart*factor // 34
    return if (showDotZero || fractionalPart != 0)
        "$integerPart.$fractionalPart" // 12.34
    else "$integerPart"
}

/** [x] >= 0 => +1, otherwise => -1 */
fun signNonZero(x: Double): Int =
    if (x >= 0)
        +1
    else -1

fun Double.divideWithRemainder(divisor: Double): Double =
    floor(this/divisor)

/** radian-to-degree conversion */
inline val Double.degrees: Float get() =
    (this*180/PI).toFloat()

/** degree-to-radian conversion */
inline val Float.radians: Double get() =
    this*PI/180.0

inline fun squareSum(dx: Float, dy: Float): Float =
    dx*dx + dy*dy

inline fun squareSum(dx: Double, dy: Double): Double =
    dx*dx + dy*dy

/** `this` => [result] */
inline infix fun Boolean.entails(result: Boolean): Boolean =
    !this or result

// sum types doko
inline fun <reified A, reified B> tryCatch2(
    crossinline tryBlock: () -> Unit,
    crossinline catchBlock: (Exception) -> Unit,
    crossinline finallyBlock: () -> Unit = {}
) where A : Exception, B : Exception {
    try {
        tryBlock()
    } catch (e: Exception) {
        when (e) {
            is A -> catchBlock(e)
            is B -> catchBlock(e)
            else -> throw e
        }
    } finally {
        finallyBlock()
    }
}

/** To be used in `when` pattern matching in cases that should NEVER be reached */
@Throws(IllegalStateException::class)
inline fun never(info: String = ""): Nothing =
    throw IllegalStateException(if (info.isBlank()) "Never" else "Never ($info)")
