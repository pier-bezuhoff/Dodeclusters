import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test

sealed interface Arg1 {
    @Serializable
    sealed interface Type
    data class CircleIndex(val index: Int) : Arg1 {
        @Serializable
        companion object : Type
    }
}

class Experiments {
    @Test
    fun test() {
        val t: Arg1.Type = Arg1.CircleIndex
        println(Json.encodeToString(t))
    }
}