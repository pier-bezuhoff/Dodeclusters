package data

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class Cluster(
    val circles: List<Circle>,
    /** union of parts comprised of circle intersections */
    val parts: List<Part> = emptyList(),
    /** fill regions inside / wireframe */
    val fill: Boolean = true,
) {
    /** intersection of insides and outside of circles of a cluster */
    @Serializable
    data class Part(
        /** indices of interior circles */
        val insides: Set<Int>,
        /** indices of bounding complementary circles */
        val outsides: Set<Int>,
        @Serializable(ColorSerializer::class)
        val fillColor: Color = Color.Cyan,
//        @Serializable(ColorSerializer::class)
//        val borderColor: Color,
    ) {
        override fun toString(): String =
            "Cluster.Part(in = [${insides.joinToString()}], out = [${outsides.joinToString()}], color = $fillColor)"

        /** ruff semiorder ⊆ on delimited regions; only goes off indices */
        infix fun isObviouslyInside(otherPart: Part): Boolean =
            // the more intersections the smaller the delimited region is
            insides.containsAll(otherPart.insides) &&
            outsides.containsAll(otherPart.outsides)
    }

    companion object {
        val SAMPLE = Cluster(
            circles = listOf(Circle(200.0, 100.0, 50.0)),
            parts = emptyList(),
            fill = true
        )
    }
}

// i have no idea why there was no default serializer
object ColorSerializer : KSerializer<Color> {
    override val descriptor = ULong.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Color) {
        encoder.encodeSerializableValue(ULong.serializer(), value.value)
    }

    override fun deserialize(decoder: Decoder): Color {
        return Color(value = decoder.decodeSerializableValue(ULong.serializer()))
    }
}