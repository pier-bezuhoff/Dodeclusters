package core.geometry.projective

// wedge of two conics Q1 ^ Q2
/**
 * p=plus, m=minus, c=cross, 1=x, 2=y, d=dual/bar
 */
data class Quadruplet(
    val dpdm: Double,
    val dpdc: Double,
    val dpx: Double,
    val dpy: Double,
    val dpp: Double,
    val dmdc: Double,
    val dmx: Double,
    val dmy: Double,
    val dmp: Double,
    val dcx: Double,
    val dcy: Double,
    val dcp: Double,
    val xy: Double,
    val xp: Double,
    val yp: Double,
) {
}