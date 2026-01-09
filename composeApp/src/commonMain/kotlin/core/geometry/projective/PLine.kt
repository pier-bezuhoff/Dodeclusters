package core.geometry.projective

/** A line on a 2D projective plane. a=b=0 represents the line on infinity */
data class PLine(
    val a: Double,
    val b: Double,
    val c: Double,
) {
    init {
        require(
            a.isFinite() && b.isFinite() && c.isFinite() &&
            (a != 0.0 || b != 0.0 || c != 0.0)
        ) { "Invalid Line($a, $b, $c)" }
    }
}