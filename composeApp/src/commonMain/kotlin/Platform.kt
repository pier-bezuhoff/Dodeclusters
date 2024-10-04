interface Platform {
    val name: String
    /** min tap/grab distance to select an object in dp */
    val tapRadius: Float
    fun scrollToZoom(yDelta: Float): Float
}

expect fun getPlatform(): Platform
