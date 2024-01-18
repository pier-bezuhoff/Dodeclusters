package data

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

@Serializable
data class Cluster(
    val circles: List<Circle>,
    /** union of parts comprised of circle intersections */
    val parts: List<Part>,
    /** fill regions inside / wireframe */
    val fill: Boolean,
    @Serializable(ColorSerializer::class)
    val fillColor: Color,
    @Serializable(ColorSerializer::class)
    val borderColor: Color,
) {
    /** intersection of insides and outside of circles of a cluster */
    @Serializable
    data class Part(
        /** indices of interior circles */
        val insides: Set<Int>,
        /** indices of bounding complementary circles */
        val outsides: Set<Int>
    )

    companion object {
        val SAMPLE = Cluster(listOf(Circle(200.0, 100.0, 50.0)), emptyList(), true, Color.Black, Color.Black)
    }
}

// i have no idea why there was no default serializer
object ColorSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Color") {
        element<ULong>("value")
    }

    override fun serialize(encoder: Encoder, value: Color) {
//        encoder.encodeInline()
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, ULong.serializer(), value.value)
        }
    }

    override fun deserialize(decoder: Decoder): Color {
        return decoder.decodeStructure(descriptor) {
            var value: ULong? = null
            loop@ while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    DECODE_DONE -> break@loop
                    0 -> value = decodeSerializableElement(descriptor, 0, ULong.serializer())
                    else -> throw SerializationException("Unexpected index $index")
                }
            }
            Color(value = requireNotNull(value))
        }
    }
}