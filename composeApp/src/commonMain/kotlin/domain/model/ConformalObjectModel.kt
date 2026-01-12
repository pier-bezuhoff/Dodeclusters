package domain.model

import androidx.compose.ui.geometry.Offset
import core.geometry.Circle
import core.geometry.GCircle
import core.geometry.ImaginaryCircle
import core.geometry.Line
import core.geometry.Point
import core.geometry.scaled00
import domain.Ix
import domain.expressions.Expressions

// MAYBE: additionally store GeneralizedCircle representations
/**
 * Purports to encapsulate & manage all objects ([GCircle]s) and object-related properties.
 * Very mutable, track [invalidationsState]/[invalidations] for changes and use with care.
 */
class ConformalObjectModel : ObjectModel<GCircle>() {

    fun getInfinityIndex(): Ix? {
        val infinityIndex = objects.indexOfFirst { it == Point.CONFORMAL_INFINITY }
        return if (infinityIndex == -1) {
            null
        } else infinityIndex
    }

    // NOTE: idk, handling of incident points is messy
    override fun transform(
        expressions: Expressions<*, *, *, GCircle>,
        targets: List<Ix>,
        translation: Offset,
        focus: Offset,
        zoom: Float,
        rotationAngle: Float,
    ): Set<Ix> {
        if (targets.isEmpty()) {
            return emptySet()
        }
        val targetsSet = targets.toSet()
        val requiresZoom = zoom != 1f
        val requiresRotation = rotationAngle != 0f
        val allIncidentPoints = mutableSetOf<Ix>()
        if (!requiresZoom && !requiresRotation) { // translation only
            // we assume the transformation is not Id
            for (ix in targets) {
                val o = objects[ix]
                objects[ix] = o?.translated(translation)
                if (o is Line) {
                    expressions.incidentChildren[ix]?.let { allIncidentPoints += it }
                }
            }
        } else {
            for (ix in targets) {
                when (val o = objects[ix]) {
                    is Circle -> {
                        objects[ix] = o.transformed(translation, focus, zoom, rotationAngle)
                        if (requiresRotation) {
                            expressions.incidentChildren[ix]?.let { allIncidentPoints += it }
                        }
                    }
                    is Line -> {
                        objects[ix] = o.transformed(translation, focus, zoom, rotationAngle)
                        expressions.incidentChildren[ix]?.let { allIncidentPoints += it }
                    }
                    is Point -> {
                        objects[ix] = o.transformed(translation, focus, zoom, rotationAngle)
                    }
                    is ImaginaryCircle -> {
                        objects[ix] = o.transformed(translation, focus, zoom, rotationAngle)
                    }
                    null -> {}
                }
            }
        }
        val allUnmovedIncidentPoints = allIncidentPoints - targetsSet
        for (j in allUnmovedIncidentPoints) {
            val p0 = objects[j] as? Point
            val p = p0?.transformed(translation, focus, zoom, rotationAngle)
            downscaledObjects[j] = p?.downscale() // objects[ix] will be recalculated & set during update phase
        }
        syncDownscaledObjects(targets)
        expressions.adjustIncidentPointExpressions(allUnmovedIncidentPoints)
        val updatedIndices = expressions.update(targets)
        syncObjects(updatedIndices)
        invalidatePositions()
        return targetsSet + updatedIndices + allUnmovedIncidentPoints
    }

    override fun GCircle.downscale(): GCircle =
        scaled00(DOWNSCALING_FACTOR)
    override fun GCircle.upscale(
//            screenCenter: Offset = Offset.Zero
    ): GCircle =
        when (this) {
            is Circle -> {
                // this introduces visual errors
//                    val upscaledCircle =
//                        copy(x = UPSCALING_FACTOR * x, y = UPSCALING_FACTOR * y, radius = UPSCALING_FACTOR * radius)
//                    if (upscaledCircle.radius >= MIN_CIRCLE_TO_LINE_APPROXIMATION_RADIUS)
//                        upscaledCircle.approximateToLine(Offset.Zero)
//                    else upscaledCircle
                copy(x = UPSCALING_FACTOR * x, y = UPSCALING_FACTOR * y, radius = UPSCALING_FACTOR * radius)
            }
            is Line -> copy(c = UPSCALING_FACTOR * c)
            is Point -> copy(x = UPSCALING_FACTOR * x, y = UPSCALING_FACTOR * y)
            is ImaginaryCircle ->
                copy(x = UPSCALING_FACTOR * x, y = UPSCALING_FACTOR * y, radius = UPSCALING_FACTOR * radius)
        }

    companion object {
        const val UPSCALING_FACTOR = 2_000.0
        const val DOWNSCALING_FACTOR = 1.0/UPSCALING_FACTOR
    }
}