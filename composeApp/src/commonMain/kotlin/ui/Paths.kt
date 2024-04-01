package ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import data.Circle
import data.Cluster

fun circle2path(circle: Circle): Path =
    Path().apply {
        addOval(
            Rect(
                center = circle.offset,
                radius = circle.radius.toFloat()
            )
        )
    }

// NOTE: to create proper reduce(xor):
// 2^(# circles) -> binary -> filter even number of 1 -> to parts
// MAYBE: move to Cluster methods
// MAYBE: add threading cuz it can be slow (esp. 100+ circles with xor)
fun part2path(circles: List<Circle>, part: Cluster.Part): Path {
    val circleInsides = part.insides.map { circles[it] }
    val insidePath: Path? = circleInsides
        .map { circle2path(it) }
        .reduceOrNull { acc: Path, anotherPath: Path ->
            Path.combine(PathOperation.Intersect, path1 = acc, path2 = anotherPath)
        }
    val circleOutsides = part.outsides.map { circles[it] }
    return if (insidePath == null) { // chessboard pattern case
        circles.map { circle2path(it) }
            // TODO: encapsulate as a separate tool
            // NOTE: reduce(xor) on outsides = makes binary interlacing pattern
            // cuz it makes even # of layers = transparent, odd = filled
            // NOTE: soft limit is 500-1k circles for this to have bearable performance
            .reduceOrNull { acc: Path, anotherPath: Path ->
                Path.combine(PathOperation.Xor, acc, anotherPath)
            } ?: Path()
    } else if (part.outsides.isEmpty())
        insidePath
    else
        circleOutsides.fold(insidePath) { acc: Path, circleOutside: Circle ->
            Path.combine(PathOperation.Difference, acc, circle2path(circleOutside))
        }
}
