package ui.edit_cluster

import kotlin.math.abs

/** Snaps when to 45 degree marks [snapMarkDeg] when closer than 5% [angleSnapPercent] */
fun snapAngle(
    angleDeg: Double,
    angleSnapPercent: Double = 5.0,
    snapMarkDeg: Double = 45.0
): Double {
    if (abs(angleDeg) <= snapMarkDeg/2)
        return angleDeg // no snapping to 0
    val mod = angleDeg.mod(snapMarkDeg) // mod is always >= 0
    val div = (angleDeg - mod)/snapMarkDeg
    val threshold = snapMarkDeg*angleSnapPercent/100
    return if (mod < threshold) {
        div * snapMarkDeg
    } else if ((snapMarkDeg - mod) < threshold) {
        (div + 1) * snapMarkDeg
    } else {
        angleDeg
    }
}