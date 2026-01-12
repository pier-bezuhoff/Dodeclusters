package domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PreludeTest {

    @Test
    fun formatDecimals() {
        assertEquals("12.35", (12.3456).formatDecimals(2))
        assertEquals("12.3450", (12.345).formatDecimals(4))
        assertEquals("12.345", (12.345).formatDecimals(4, showTrailingZeroes = false))
        assertEquals("-0.667", (-0.666666).formatDecimals(3))
        assertEquals("-1.67", (-1.666666).formatDecimals(2))
        assertEquals("-100.6667", (-100.666666).formatDecimals(4))
        assertEquals("-100.6700", (-100.67).formatDecimals(4))
        assertEquals("-100.67", (-100.67).formatDecimals(4, showTrailingZeroes = false))
    }
}