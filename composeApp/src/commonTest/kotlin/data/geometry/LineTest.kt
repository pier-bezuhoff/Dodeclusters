package data.geometry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LineTest {

    @Test
    fun testIsInside() {
        // o--->           /             ^
        // | Ox           /          <= /
        // v y           /          <= /
        //              /             /   < line
        //    line2 >  /             /
        //            / =>          o  < circle
        //           v             /
        val line = Line(-1.0, -1.0, 10.0) // x + y = 10, normal pointing top-left
        val ox = Line(0.0, -1.0, 0.0) // x-axis with normal pointing upwards
        val line2 = Line(0.5, 0.5, 0.0) // same as line but thru origin and directed bottom-left
        assertTrue(line.isInside(line))
        assertFalse(line.isInside(line2))
        assertFalse(line2.isInside(line))
        assertFalse(line.isInside(ox))
        assertFalse(ox.isInside(line))
        val unLine = line.reversed()
        val unOx = ox.reversed()
        val unLine2 = line2.reversed()
        assertFalse(line.isInside(unLine))
        assertTrue(unLine.isInside(line2))
        assertTrue(unLine2.isInside(line))
        val circle = Circle(5.0, 4.9, 1.0)
        assertFalse(line.isInside(circle))
        assertFalse(line2.isInside(circle))
        val unCircle = circle.reversed()
        assertFalse(line.isInside(unCircle))
        assertFalse(unLine.isInside(unCircle))
        assertTrue(ox.isInside(unCircle))
        assertFalse(unOx.isInside(unCircle))
        assertTrue(unLine2.isInside(unCircle))
        assertFalse(line2.isInside(unCircle))
    }

    @Test
    fun testIsOutside() {
        // o--->           /             ^
        // | Ox           /          <= /
        // v y           /          <= /
        //              /             /   < line
        //    line2 >  /             /
        //            / =>          o  < circle
        //           v             /
        val line = Line(-1.0, -1.0, 10.0) // x + y = 10, normal pointing top-left
        val ox = Line(0.0, -1.0, 0.0) // x-axis with normal pointing upwards
        val line2 = Line(0.5, 0.5, 0.0) // same as line but thru origin and directed bottom-left
        assertFalse(line.isOutside(line))
        assertFalse(line.isOutside(line2))
        assertFalse(line2.isOutside(line))
        assertFalse(line.isOutside(ox))
        assertFalse(ox.isOutside(line))
        val unLine = line.reversed()
        val unOx = ox.reversed()
        val unLine2 = line2.reversed()
        assertTrue(line.isOutside(unLine))
        assertFalse(unLine.isOutside(line2))
        assertTrue(unLine2.isOutside(unLine))
        val circle = Circle(5.0, 4.9, 1.0)
        assertFalse(line.isOutside(circle))
        assertFalse(line2.isOutside(circle))
        assertTrue(ox.isOutside(circle))
        assertTrue(unLine2.isOutside(circle))
        val unCircle = circle.reversed()
        assertFalse(line.isOutside(unCircle))
        assertFalse(unLine.isOutside(unCircle))
        assertFalse(ox.isOutside(unCircle))
        assertFalse(unOx.isOutside(unCircle))
        assertFalse(unLine2.isOutside(unCircle))
        assertFalse(line2.isOutside(unCircle))
    }

}