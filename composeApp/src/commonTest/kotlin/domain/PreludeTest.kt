package domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PreludeTest {

    @Test
    fun formatDecimals() {
        assertEquals("12.35", (12.3456).formatDecimals(2))
        assertEquals("12.3450", (12.345).formatDecimals(4))
        assertEquals("12.345", (12.345).formatDecimals(4, showTrailingZeroes = false))
        assertEquals("-100.6667", (-100.666666).formatDecimals(4))
        assertEquals("-100.6700", (-100.67).formatDecimals(4))
        assertEquals("-100.67", (-100.67).formatDecimals(4, showTrailingZeroes = false))
        assertEquals("-0.667", (-0.666666).formatDecimals(3))
        assertEquals("-1.67", (-1.666666).formatDecimals(2))
        assertEquals("0.0067", (0.0066666).formatDecimals(4, showTrailingZeroes = false))
        assertEquals("-0.0067", (-0.0066666).formatDecimals(4, showTrailingZeroes = false))
        assertEquals("0", (0.00006667).formatDecimals(2, showTrailingZeroes = false))
        assertEquals("0.00", (0.00006667).formatDecimals(2, showTrailingZeroes = true))
        // w/ trailing zeroes
        assertEquals("123.00", 123.formatDecimals(2, true))
        assertEquals("123.46", 123.456.formatDecimals(2, true))
        assertEquals("123.40", 123.4.formatDecimals(2, true))
        assertEquals("-123.46", (-123.456).formatDecimals(2, true))
        assertEquals("0.00", 0.formatDecimals(2, true))
        assertEquals("10.00", 9.999.formatDecimals(2, true))
        assertEquals("123", 123.formatDecimals(0, true))
        // w/o trailing zeroes
        assertEquals("123", 123.formatDecimals(2, false))
        assertEquals("123.46", 123.456.formatDecimals(2, false))
        assertEquals("123.4", 123.4.formatDecimals(2, false))
        assertEquals("0", 0.formatDecimals(2, false))
        assertEquals("10", 9.999.formatDecimals(2, false))
        assertEquals("123.45", 123.450.formatDecimals(3, false))
        assertEquals("123", 123.000.formatDecimals(3, false))
        // rounding
        assertEquals("0.67", 0.666666.formatDecimals(2, true))
        assertEquals("1.00", 0.9999.formatDecimals(2, true))
        assertEquals("-0.67", (-0.666666).formatDecimals(2, true))
        assertEquals("1.6", 1.555.formatDecimals(1, true))
        // 0 <= x <= 1
        assertEquals("0.10", 0.1.formatDecimals(2, true))
        assertEquals("0.1", 0.1.formatDecimals(2, false))
        assertEquals("0.01", 0.01.formatDecimals(2, true))
        assertEquals("0.00", 0.001.formatDecimals(2, true))
        assertEquals("1.00", 0.999.formatDecimals(2, true))
        assertEquals("1", 0.999.formatDecimals(2, false))
        assertEquals("0.000", 0.0001.formatDecimals(3, true))
        assertEquals("0.001", 0.0005.formatDecimals(3, true))
        // -1 <= x < 0
        assertEquals("-0.10", (-0.1).formatDecimals(2, true))
        assertEquals("-0.1", (-0.1).formatDecimals(2, false))
        assertEquals("-0.01", (-0.01).formatDecimals(2, true))
        assertEquals("-0.00", (-0.001).formatDecimals(2, true))
        assertEquals("-1.00", (-0.999).formatDecimals(2, true))
        // ~0
        assertEquals("0.00", 0.formatDecimals(2, true))
        assertEquals("0", 0.formatDecimals(2, false))
    }
}