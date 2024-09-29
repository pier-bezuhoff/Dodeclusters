package domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.util.packFloats
import androidx.compose.ui.util.unpackFloat1
import androidx.compose.ui.util.unpackFloat2
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** use as a type param `Json.encodeToString<ColorAsCss>(color)` */
typealias ColorAsCss = @Serializable(ColorCssSerializer::class) Color

// i have no idea why there was no default serializer
// nice reference: https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md#primitive-serializer
// NOTE: colormath also have similar (de-)serialization functions, maybe use theirs
object ColorCssSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Color", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Color) {
        val rgb = value.toArgb() and 0xffffff
        val s = "#" + rgb.toString(16).padStart(6, '0')
        encoder.encodeString(s)
    }

    override fun deserialize(decoder: Decoder): Color {
        val s = decoder.decodeString()
        val colorInt = (s.trimStart('#')).toInt(16)
        return Color(color = colorInt).copy(alpha = 1f)
    }
}

object ColorULongSerializer : KSerializer<Color> {
    override val descriptor = ULong.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Color) {
        encoder.encodeSerializableValue(ULong.serializer(), value.value)
    }

    override fun deserialize(decoder: Decoder): Color {
        return Color(value = decoder.decodeSerializableValue(ULong.serializer()))
    }
}

/** Serialize using [packFloats] into a [Long], idk why it's not by default */
object OffsetSerializer : KSerializer<Offset> {
    override val descriptor = Long.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Offset) {
        val (x, y) = value
        encoder.encodeSerializableValue(Long.serializer(), packFloats(x, y))
    }

    override fun deserialize(decoder: Decoder): Offset {
        val long = decoder.decodeSerializableValue(Long.serializer())
        return Offset(unpackFloat1(long), unpackFloat2(long))
    }
}

