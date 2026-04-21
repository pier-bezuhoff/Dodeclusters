@file:Suppress("NOTHING_TO_INLINE")

package core.geometry

import androidx.compose.ui.geometry.Offset
import core.geometry.Region.RegionLocation

sealed interface Region : CanMeasureDistanceFromPoint {

    /** A point is either [INSIDE] a region, [BORDERING] it or [OUTSIDE] of it */
    enum class PointLocation {
        INSIDE,
        BORDERING,
        OUTSIDE,
    }

    /** Region-Region location / containment state.
     * Region1 either fully [CONTAINS_INSIDE] region2,
     * [IS_CONTAINED_INSIDE] region2,
     * [OVERLAPS] it (but none contains the other)
     * or has [NO_INTERSECTION] with it */
    enum class RegionLocation {
        CONTAINS_INSIDE,
        IS_CONTAINED_INSIDE,
        OVERLAPS,
        NO_INTERSECTION,
    }

    fun getPointLocation(point: Point): PointLocation
    fun getPointLocation(point: Offset): PointLocation =
        getPointLocation(Point.fromOffset(point))

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

    /** tests whether `this` region contains another [region],
     * overlaps it, or they have no intersection */
    fun getRegionLocation(region: Region): RegionLocation
}

// `point in this` overload
inline operator fun Region.contains(point: Point): Boolean =
    hasInside(point)
inline operator fun Region.contains(point: Offset): Boolean =
    hasInside(point)

inline infix fun Point.liesInside(region: Region): Boolean =
    region.hasInside(this)
inline infix fun Offset.liesInside(region: Region): Boolean =
    region.hasInside(this)
inline infix fun Point.liesOutside(region: Region): Boolean =
    region.hasOutside(this)
inline infix fun Offset.liesOutside(region: Region): Boolean =
    region.hasOutside(this)

/** partial order ⊆ on regions (treated as either inside or outside regions) */
inline infix fun Region.isInside(region: Region): Boolean =
    region.getRegionLocation(this) == RegionLocation.CONTAINS_INSIDE
/** partial order ⊇ on regions (treated as either inside or outside regions)
 * `A isOutside B` == A ⊆ Bꟲ*/
inline infix fun Region.isOutside(region: Region): Boolean =
    region.getRegionLocation(this) == RegionLocation.NO_INTERSECTION
