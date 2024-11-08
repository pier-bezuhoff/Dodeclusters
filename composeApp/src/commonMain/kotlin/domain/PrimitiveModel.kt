package domain

// NOTE: premature optimization is the root of all problems...
class PrimitiveModel(
    val xs: DoubleArray,
    val ys: DoubleArray,
    val rs: DoubleArray,
    /** circle/point/line/imaginary circle */
    val types: IntArray,
    val edges: IntArray, // indices of circles & lines
) {
}

private const val CIRCLE_TYPE = 0 // x:y:r
private const val POINT_TYPE = 1 // x:y:0
private const val LINE_TYPE = 2 // a:b:c
private const val IMAGINARY_CIRCLE_TYPE = 3 // x:y:|r|
