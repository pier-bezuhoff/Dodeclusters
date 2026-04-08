import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.test.Test

@Serializable
private data object Aa

@Serializable
private sealed interface Choice<T> {
    val t: T
    @Serializable
    data class One<T>(override val t: T) : Choice<T>
    @Serializable
    data class Two<T>(override val t: T, val n: Int) : Choice<T>
}

@Serializable
private data class MyState(
    @Serializable
    val choices: List<Choice<Aa>>,
//    val choices: Map<Int, Choice<A>>, // NG
//    val choice: Choice<A>, // good
) {
    companion object {
        val JSON_FORMAT = Json {
            serializersModule = SerializersModule {
                polymorphic(Choice::class) {
                    subclass(Choice.One.serializer(PolymorphicSerializer(Any::class)))
                    subclass(Choice.Two.serializer(PolymorphicSerializer(Any::class)))
                }
            }
        }
    }
}

class GenericSerializationBug {
    @Test
    fun go() {
        // fails on wasm
        MyState.JSON_FORMAT.encodeToString(
            MyState(listOf(Choice.One(Aa)))
//                    MyState(mapOf(0 to Choice.One(A))) // NG
//        mapOf(0 to Choice.One(A)) // good
        )
    }
}