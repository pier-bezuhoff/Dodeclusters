package data.geometry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CircleTest {

    @Test
    fun testIsIn() {
        val smol = Circle(0.0, 0.0, 5.0)
        val big = Circle(0.0, 0.0, 6.0)
        val far = Circle(100.0, 0.0, 5.0)
        assertTrue(smol.isIn(smol))
        assertTrue(smol.isIn(big))
        assertFalse(big.isIn(smol))
        assertFalse(far.isIn(big))
    }

    @Test
    fun testIsOutBesides() {
        val smol = Circle(0.0, 0.0, 5.0)
        val big = Circle(0.0, 0.0, 6.0)
        val far = Circle(100.0, 0.0, 5.0)
        assertFalse(smol.isOutBeside(smol))
        assertFalse(smol.isOutBeside(big))
        assertFalse(big.isOutBeside(smol))
        assertTrue(far.isOutBeside(smol))
        assertTrue(smol.isOutBeside(far))
        assertTrue(far.isOutBeside(big))
        assertTrue(big.isOutBeside(far))
    }

    @Test
    fun testIsInside() {
        val smol = Circle(100.0, -200.0, 5.0)
        val unSmol = smol.copy(isCCW = false)
        assertTrue(smol.isInside(smol))
        assertFalse(smol.isInside(unSmol))
        assertFalse(unSmol.isInside(smol))
        val big = Circle(90.0, -210.0, 300.0)
        val unBig = big.copy(isCCW = false)
        assertTrue(smol.isInside(big))
        assertFalse(big.isInside(smol))
        assertFalse(big.isInside(unSmol))
        assertFalse(unSmol.isInside(big))
        assertFalse(unBig.isInside(smol))
        assertFalse(smol.isInside(unBig))
        assertTrue(unBig.isInside(unSmol))
        assertFalse(unSmol.isInside(unBig))
        val smol2 = Circle(-10.0, 10.0, 7.0)
        val unSmol2 = smol2.copy(isCCW = false)
        assertFalse(smol.isInside(smol2))
        assertFalse(smol2.isInside(smol))
        assertTrue(smol.isInside(unSmol2))
        assertTrue(smol2.isInside(unSmol))
        val line = Line(-1.0, 0.0, 50.0) // x = 50, directed upwards, region: x <= 50
        // o--->            ^
        // | Ox             |
        // v y            __|----___
        //               /  |       \
        //              /   |        \   < big
        //              |   |         |
        //               \  |  o     /   << smol
        //                \ |       /
        //     smol2       -|-___---
        //       v          |
        //       o          |
        //                  |
        assertFalse(smol.isInside(line))
        assertFalse(big.isInside(line))
        assertTrue(smol2.isInside(line))
        assertFalse(unSmol.isInside(line))
        assertFalse(unBig.isInside(line))
        assertFalse(unSmol2.isInside(line))
    }

    @Test
    fun testOutside() {
        val smol = Circle(100.0, -200.0, 5.0)
        val unSmol = smol.copy(isCCW = false)
        assertFalse(smol.isOutside(smol))
        assertTrue(smol.isOutside(unSmol))
        assertTrue(unSmol.isOutside(smol))
        val big = Circle(90.0, -210.0, 300.0)
        val unBig = big.copy(isCCW = false)
        assertFalse(smol.isOutside(big))
        assertFalse(big.isOutside(smol))
        assertFalse(big.isOutside(unSmol))
        assertFalse(unSmol.isOutside(big))
        assertTrue(unBig.isOutside(smol))
        assertTrue(smol.isOutside(unBig))
        assertFalse(unBig.isOutside(unSmol))
        assertFalse(unSmol.isOutside(unBig))
        val smol2 = Circle(-10.0, 10.0, 7.0)
        val unSmol2 = smol2.copy(isCCW = false)
        assertTrue(smol.isOutside(smol2))
        assertTrue(smol2.isOutside(smol))
        assertFalse(smol.isOutside(unSmol2))
        assertFalse(smol2.isOutside(unSmol))
    }
}