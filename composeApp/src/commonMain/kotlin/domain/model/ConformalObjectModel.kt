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

    // NOTE: handling of incident points on non-glued dependent objects is off
    override fun transform(
        expressions: Expressions<*, *, *, GCircle>,
        targets: List<Ix>,
        translation: Offset,
        focus: Offset,
        zoom: Float,
        rotationAngle: Float,
    ): Set<Ix> {
        if (targets.isEmpty())
            return emptySet()
        val targetsSet = targets.toSet()
        val requiresZoom = zoom != 1f
        val requiresRotation = rotationAngle != 0f
        if (requiresZoom || requiresRotation) {
            for (ix in targets) {
                val o = objects[ix]
                objects[ix] = o?.transformed(translation, focus, zoom, rotationAngle)
            }
        } else { // translation only
            // we assume the transformation is not Id
            for (ix in targets) {
                val o = objects[ix]
                objects[ix] = o?.translated(translation)
            }
        }
        val gluedIncidentPoints = expressions.getGluedIncidentPoints(targetsSet)
//        println("glued to $targets: $gluedIncidentPoints")
        for (j in gluedIncidentPoints) {
            val p0 = objects[j] as? Point
            val p = p0?.transformed(translation, focus, zoom, rotationAngle)
            downscaledObjects[j] = p?.downscale()
            // objects are synced later with syncObjects(updatedIndices)
        }
        syncDownscaledObjects(targets)
        val updatedIndices = gluedIncidentPoints + expressions.update(targetsSet, excluded = gluedIncidentPoints)
        expressions.adjustIncidentPointExpressions(gluedIncidentPoints)
        // MAYBE: it's better to recalc glued atp (we transformed them, then adjusted the order)
        syncObjects(updatedIndices)
        invalidatePositions()
        return targetsSet + updatedIndices
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