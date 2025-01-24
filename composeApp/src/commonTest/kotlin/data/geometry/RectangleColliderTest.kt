package data.geometry

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RectangleColliderTest {
    @Test
    fun testCollisions() {
        val rect = Rect(Offset(-100f, 100f), Offset(200f, 200f))
        // 300x100
        // ┏━┳━┳━┓
        // ┗━┻━┻━┛
        val smolIn = Circle(0.0, 150.0, 50.0)
        assertTrue(testObjectRectangleCollision(smolIn, rect))
        val smolOut = Circle(1000.0, 150.0, 50.0)
        assertFalse(testObjectRectangleCollision(smolOut, rect))
        val out = Circle(-150.0, 150.0, 60.0)
        assertTrue(testObjectRectangleCollision(out, rect))
        val bigCenterIn = Circle(0.0, 150.0, 500.0)
        assertFalse(testObjectRectangleCollision(bigCenterIn, rect))
        val midIn = Circle(100.0, 150.0, 60.0)
        assertTrue(testObjectRectangleCollision(midIn, rect))
    }
}