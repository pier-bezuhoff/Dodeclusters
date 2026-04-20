@file:Suppress("NOTHING_TO_INLINE")

package core.geometry

import androidx.compose.ui.geometry.Offset

interface Region : CanMeasureDistanceFromPoint {

    /** A point is either [INSIDE] a region, [BORDERING] it or [OUTSIDE] of it */
    enum class PointLocation {
        INSIDE,
        BORDERING,
        OUTSIDE,
    }

    /** Region-Region location.
     * A region is either [INSIDE] another region, [INTERSECTING] it or [OUTSIDE] of it */
    enum class RegionLocation {
        INSIDE,
        INTERSECTING,
        OUTSIDE,
    }

    fun getPointLocation(point: Point): PointLocation
    fun getPointLocation(point: Offset): PointLocation

    fun hasInside(point: Point): Boolean =
        getPointLocation(point) == PointLocation.INSIDE
    fun hasInside(point: Offset): Boolean =
        getPointLocation(point) == PointLocation.INSIDE

    fun hasOutside(point: Offset): Boolean =
        getPointLocation(point) == PointLocation.OUTSIDE
    /** tests if [point] is strictly outside of `this`, within [EPSILON] of the border is
     * not considered outside */
    fun hasOutside(point: Point): Boolean =
        getPointLocation(point) == PointLocation.OUTSIDE
}

// `point in this` overload
inline operator fun Region.contains(point: Point): Boolean =
    hasInside(point)
inline operator fun Region.contains(point: Offset): Boolean =
    hasInside(point)

inline infix fun Point.isInside(region: Region): Boolean =
    region.hasInside(this)
inline infix fun Offset.isInside(region: Region): Boolean =
    region.hasInside(this)
inline infix fun Point.isOutside(region: Region): Boolean =
    region.hasOutside(this)
inline infix fun Offset.isOutside(region: Region): Boolean =
    region.hasOutside(this)