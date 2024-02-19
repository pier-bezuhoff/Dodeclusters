package utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// i have no idea why there was no default serializer
// nice reference: https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md#primitive-serializer
object ColorSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Color", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Color) {
        val rgb = value.toArgb() and 0xffffff
        val s = "#" + rgb.toString(16).padStart(6, '0')
        encoder.encodeString(s)
    }

    override fun deserialize(decoder: Decoder): Color {
        val s = decoder.decodeString()
        return Color(color = s.trimStart('#').toInt(16))
    }
}
