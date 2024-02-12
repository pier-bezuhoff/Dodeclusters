/*
 * Copyright 2018-2024 KMath contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE-3RD-PARTY.txt file.
 */
// source: https://github.com/SciProgCentre/kmath/blob/83d9e1f0afb3024101a2f90a99b54f2a8b6e4212/kmath-complex/src/commonMain/kotlin/space/kscience/kmath/complex/Complex.kt
// original APACHE 2.0 license attached in the folder
// + cherry-picked by me

package utils

import kotlin.math.*

/**
 * This complex's conjugate.
 */
val Complex.conjugate: Complex
    get() = Complex(re, -im)

/**
 * This complex's reciprocal.
 */
val Complex.reciprocal: Complex
    get() {
        val scale = re * re + im * im
        return Complex(re / scale, -im / scale)
    }

val Complex.r2: Double
    get() = re * re + im * im

/**
 * Absolute value of complex number.
 */
val Complex.r: Double
    get() = sqrt(re * re + im * im)

/**
 * An angle between vector represented by complex number and X axis.
 */
val Complex.theta: Double
    get() = atan2(im, re)

private val PI_DIV_2 = Complex(PI / 2, 0)

/**
 * A field of [Complex].
 */
object ComplexField {
    val zero: Complex = 0.0.toComplex()
    val one: Complex = 1.0.toComplex()

    fun bindSymbolOrNull(value: String): Complex? = if (value == "i") i else null

    /**
     * The imaginary unit.
     */
    val i: Complex by lazy { Complex(0.0, 1.0) }

    operator fun Complex.unaryMinus(): Complex = Complex(-re, -im)

    fun number(value: Number): Complex = Complex(value.toDouble(), 0.0)

    fun scale(a: Complex, value: Double): Complex = Complex(a.re * value, a.im * value)

    fun add(left: Complex, right: Complex): Complex = Complex(left.re + right.re, left.im + right.im)
//    override fun multiply(a: Complex, k: Number): Complex = Complex(a.re * k.toDouble(), a.im * k.toDouble())

//    override fun Complex.minus(arg: Complex): Complex = Complex(re - arg.re, im - arg.im)

    fun multiply(left: Complex, right: Complex): Complex =
        Complex(left.re * right.re - left.im * right.im, left.re * right.im + left.im * right.re)

    fun divide(left: Complex, right: Complex): Complex = when {
        abs(right.im) < abs(right.re) -> {
            val wr = right.im / right.re
            val wd = right.re + wr * right.im

            if (wd.isNaN() || wd == 0.0)
                throw ArithmeticException("Division by zero or infinity")
            else
                Complex((left.re + left.im * wr) / wd, (left.im - left.re * wr) / wd)
        }

        right.im == 0.0 -> throw ArithmeticException("Division by zero")

        else -> {
            val wr = right.re / right.im
            val wd = right.im + wr * right.re

            if (wd.isNaN() || wd == 0.0)
                throw ArithmeticException("Division by zero or infinity")
            else
                Complex((left.re * wr + left.im) / wd, (left.im * wr - left.re) / wd)
        }
    }

    operator fun Complex.div(k: Number): Complex = Complex(re / k.toDouble(), im / k.toDouble())

    fun sin(arg: Complex): Complex = i * (exp(-i * arg) - exp(i * arg)) / 2.0
    fun cos(arg: Complex): Complex = (exp(-i * arg) + exp(i * arg)) / 2.0

    fun tan(arg: Complex): Complex {
        val e1 = exp(-i * arg)
        val e2 = exp(i * arg)
        return i * (e1 - e2) / (e1 + e2)
    }

    fun asin(arg: Complex): Complex = -i * ln(sqrt(1.0 - (arg * arg)) + i * arg)
    fun acos(arg: Complex): Complex = PI_DIV_2 + i * ln(sqrt(1.0 - (arg * arg)) + i * arg)

    fun atan(arg: Complex): Complex {
        val iArg = i * arg
        return i * (ln(1.0 - iArg) - ln(1.0 + iArg)) / 2
    }

    fun power(arg: Complex, pow: Number): Complex = if (arg.im == 0.0) {
        val powDouble = pow.toDouble()
        when {
            arg.re > 0 -> arg.re.pow(powDouble).toComplex()
            arg.re < 0 -> i * (-arg.re).pow(powDouble).toComplex()
            else -> if (powDouble == 0.0) {
                one
            } else {
                zero
            }
        }

    } else {
        exp(pow.toComplex() * ln(arg))
    }

    fun power(arg: Complex, pow: Complex): Complex = exp(pow * ln(arg))

    fun Complex.pow(power: Complex): Complex = power(this, power)

    fun sqrt(z: Complex): Complex = power(z, 0.5)


    fun exp(arg: Complex): Complex = exp(arg.re) * (cos(arg.im) + i * sin(arg.im).toComplex())

    fun ln(arg: Complex): Complex = ln(arg.r) + i * atan2(arg.im, arg.re).toComplex()

    /**
     * Adds complex number to real one.
     *
     * @receiver the augend.
     * @param c the addend.
     * @return the sum.
     */
    operator fun Double.plus(c: Complex): Complex = add(this.toComplex(), c)

    /**
     * Subtracts complex number from real one.
     *
     * @receiver the minuend.
     * @param c the subtrahend.
     * @return the difference.
     */
    operator fun Double.minus(c: Complex): Complex = add(this.toComplex(), -c)

    /**
     * Adds real number to complex one.
     *
     * @receiver the augend.
     * @param d the addend.
     * @return the sum.
     */
    operator fun Complex.plus(d: Double): Complex = d + this

    /**
     * Subtracts real number from complex one.
     *
     * @receiver the minuend.
     * @param d the subtrahend.
     * @return the difference.
     */
    operator fun Complex.minus(d: Double): Complex = add(this, -d.toComplex())

    /**
     * Multiplies real number by complex one.
     *
     * @receiver the multiplier.
     * @param c the multiplicand.
     * @receiver the product.
     */
    operator fun Double.times(c: Complex): Complex = Complex(c.re * this, c.im * this)

    operator fun Complex.times(c: Complex): Complex = multiply(this, c)
    operator fun Complex.div(c: Complex): Complex = divide(this, c)
    operator fun Complex.plus(c: Complex): Complex = add(this, c)
    operator fun Complex.minus(c: Complex): Complex = add(this, -c)

    fun norm(arg: Complex): Complex = sqrt((arg.conjugate * arg).re).toComplex()
}


/**
 * Represents `double`-based complex number.
 *
 * @property re The real part.
 * @property im The imaginary part.
 */
data class Complex(val re: Double, val im: Double) {
    constructor(re: Number, im: Number) : this(re.toDouble(), im.toDouble())
    constructor(re: Number) : this(re.toDouble(), 0.0)

    override fun toString(): String = "($re + i * $im)"
}

/**
 * Creates a complex number with real part equal to this real.
 *
 * @receiver the real part.
 * @return the new complex number.
 */
fun Number.toComplex(): Complex = Complex(this)

