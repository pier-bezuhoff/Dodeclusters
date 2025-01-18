package ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toSvg
import kotlin.test.Test

class PathsTest {

    @Test
    fun test() {
        val path = Path()
        path.addOval(Rect(Offset(0f, 0f), 10f))
        val pathData = path.toSvg(asDocument = false)
        println(pathData) // -> "M10.0 0.0Z"
    }
}