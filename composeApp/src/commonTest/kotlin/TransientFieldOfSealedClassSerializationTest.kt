import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

@Serializable
sealed class A(
    @Transient
    open val x: Int = 0
) {
    @Serializable
    data class B(
        val y: Int
    ) : A(y)
}

/**
 * There is a bug in kotlinx.serialization:
 * [tracking issue](https://github.com/Kotlin/kotlinx.serialization/issues/2785)
 * Still present as of version 1.7.3
 * */
class TransientFieldOfSealedClassSerializationTest {

    @Test
    fun test() {
        val b0 = A.B(10)
        val b = Json.decodeFromString<A.B>(Json.encodeToString(b0))
        assertTrue(b.x == 10 && b0.x == 10, "De-serialization bug")
        // de-serialization sets b.x to 0 as of now
    }
}

