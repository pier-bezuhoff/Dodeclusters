interface Platform {
    val name: String
    /** min tap/grab distance to select an object in dp */
    val tapRadius: Float
    /** Circles with radius larger than [maxCircleRadius] will be approximated by lines */
    val maxCircleRadius: Float
    fun scrollToZoom(yDelta: Float): Float
}

expect fun getPlatform(): Platform
