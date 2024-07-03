interface Platform {
    val name: String
    fun scrollToZoom(yDelta: Float): Float
}

expect fun getPlatform(): Platform
