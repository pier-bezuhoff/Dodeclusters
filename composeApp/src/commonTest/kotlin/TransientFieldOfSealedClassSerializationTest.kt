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

// my workaround:
// we separate all parent classes into empty serializable interface + value holding unserialized interface
interface HasX {
    val x: Int
}

data class WithX(override val x: Int) : HasX

@Serializable
sealed interface AA : HasX

@Serializable
data class BB(
    val y: Int
): AA, HasX by WithX(y)

/**
 * There is a bug/unexpected behavior in kotlinx.serialization:
 * [tracking issue](https://github.com/Kotlin/kotlinx.serialization/issues/2785)
 * Still present as of version 1.7.3
 * UPDATE: #WONTFIX
 * */
class TransientFieldOfSealedClassSerializationTest {

    @Test
    fun test() {
        val b0 = A.B(10)
        val b = Json.decodeFromString<A.B>(Json.encodeToString(b0))
        assertTrue(b.x == 10 && b0.x == 10, "De-serialization bug [feature]")
        // de-serialization sets b.x to 0 as of now
    }

    @Test
    fun testWorkaround() {
        val bb = BB(2)
        val s = Json.encodeToString<AA>(bb)
        println(s)
        val bb1 = Json.decodeFromString<AA>(s)
        println(bb1)
        println(bb1.x)
        assertTrue(bb1.x == 2)
    }
}

