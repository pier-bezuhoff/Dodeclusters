package domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import domain.ColorAsCss
import domain.ColorCssSerializer
import domain.SerializableOffset
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject
import kotlin.collections.contains

/**
 * Each object can have its own Styling object, alternative name: style.
 */
@OptIn(ExperimentalSerializationApi::class)
@Immutable
@KeepGeneratedSerializer
@Serializable(with = Styling.CompatSerializer::class)
data class Styling(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val borderColor: ColorAsCss? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val fillColor: ColorAsCss? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val isPhantom: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val label: Label? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val lineThickness: Int? = null,
    // line style: dotted etc
    // end style: arrows
) {
    @Serializable
    data class Label(
        val content: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val positionShift: SerializableOffset = Offset.Zero,
    )

    // docs: https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/json.md#manipulating-default-values
    object CompatSerializer : JsonTransformingSerializer<Styling>(generatedSerializer()) {
        override fun transformDeserialize(element: JsonElement): JsonElement {
            val jsonObject = element.jsonObject
            return when {
                "label" in jsonObject -> {
                    val value = jsonObject["label"]
                    when {
                        // label: String -> label: Label change
                        value is JsonPrimitive && value.isString -> {
                            val jsonMap = jsonObject.toMutableMap()
                            jsonMap["label"] = JsonObject(mapOf(
                                "content" to value
                            ))
                            JsonObject(jsonMap)
                        }
                        else -> jsonObject
                    }
                }
                else -> jsonObject
            }
        }
    }

    object CustomSafeSerializer : KSerializer<Styling> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Styling") {
            element("borderColor", ColorCssSerializer.nullable.descriptor, isOptional = true)
            element("fillColor", ColorCssSerializer.nullable.descriptor, isOptional = true)
            element("isPhantom", Boolean.serializer().descriptor, isOptional = true)
            element("label", Label.serializer().nullable.descriptor, isOptional = true)
            element("lineThickness", Int.serializer().nullable.descriptor, isOptional = true)
        }

        override fun serialize(encoder: Encoder, value: Styling) {
            encoder.encodeStructure(descriptor) {
                if (value.borderColor != null)
                    encodeNullableSerializableElement(descriptor, 0, ColorCssSerializer, value.borderColor)
                if (value.fillColor != null)
                    encodeNullableSerializableElement(descriptor, 1, ColorCssSerializer, value.fillColor)
                if (value.isPhantom != false)
                    encodeBooleanElement(descriptor, 2, value.isPhantom)
                if (value.label != null)
                    encodeNullableSerializableElement(descriptor, 3, Label.serializer(), value.label)
                if (value.lineThickness != null)
                    encodeNullableSerializableElement(descriptor, 4, Int.serializer().nullable, value.lineThickness)
            }
        }

        override fun deserialize(decoder: Decoder): Styling =
            try {
                decoder.decodeStructure(descriptor) {
                    var borderColor: Color? = null
                    var fillColor: Color? = null
                    var isPhantom: Boolean = false
                    var label: Label? = null
                    var lineThickness: Int? = null
                    if (decodeSequentially()) {
                        borderColor = decodeNullableSerializableElement(descriptor, 0, ColorCssSerializer)
                        fillColor = decodeNullableSerializableElement(descriptor, 1, ColorCssSerializer)
                        isPhantom = decodeBooleanElement(descriptor, 2)
                        label = decodeNullableSerializableElement(descriptor, 3, Label.serializer())
                        lineThickness = decodeNullableSerializableElement(descriptor, 4, Int.serializer())
                    } else {
                        loop@ while (true) {
                            try {
                                when (val index = decodeElementIndex(descriptor)) {
                                    0 -> borderColor = decodeNullableSerializableElement(descriptor, index, ColorCssSerializer)
                                    1 -> fillColor = decodeNullableSerializableElement(descriptor, index, ColorCssSerializer)
                                    2 -> isPhantom = decodeBooleanElement(descriptor, index)
                                    3 -> label = decodeNullableSerializableElement(descriptor, index, Label.serializer())
                                    4 -> lineThickness = decodeNullableSerializableElement(descriptor, index, Int.serializer())
                                    CompositeDecoder.DECODE_DONE -> break@loop
                                    CompositeDecoder.UNKNOWN_NAME -> {}
                                    else -> break@loop
                                }
                            } catch (e: Exception) {
//                                println("oopsie")
//                                e.printStackTrace()
                                break@loop // mb we should continue for several tries?
                            }
                        }
                    }
                    Styling(borderColor, fillColor, isPhantom, label, lineThickness)
                }
            } catch (e: Exception) {
//                println("oops")
//                e.printStackTrace()
                Styling()
            }
    }
}
