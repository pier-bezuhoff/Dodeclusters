package domain

import androidx.compose.ui.geometry.Offset
import data.geometry.Circle
import data.geometry.GCircle
import data.geometry.ImaginaryCircle
import data.geometry.Line
import data.geometry.Point
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// alternative setup: fuse x,y,r | a,b,c representation with GeneralizedCircle coordinates,
// resulting in
// type: Byte = null | circle | line | point | imaginary circle
// orientation: Boolean (to distinguish CCW vs CW circles)
// w = +1 | 0
// x: Double = normal x | a
// y: Double = normal y | b
// z: Double = (x^2 + y^2 - r^2)/2 | c

private const val NULL_TYPE: Byte = 0
private const val CIRCLE_CCW_TYPE: Byte = 1
private const val CIRCLE_CW_TYPE: Byte = 2
private const val LINE_TYPE: Byte = 3
private const val POINT_TYPE: Byte = 4
private const val IMAGINARY_CIRCLE_TYPE: Byte = 5

// This is definitely NOT premature optimization... right?
/**
 * @property[size] size of [objectTypes]
 * @property[objectTypes] types of 3-element batches in [objectParameters]
 * @property[objectParameters] every 3-element batch represents 3 parameters of an object.
 * For [CIRCLE_TYPE] it's (x,y,radius),
 * for [LINE_TYPE]: (a,b,c),
 * for [POINT_TYPE]: (x,y,?),
 * for [IMAGINARY_CIRCLE_TYPE]: (x,y,imaginary radius),
 * for [NULL_TYPE]: (?,?,?). `?` being unspecified.
 * [objectParameters]`.size == `3*[size]
 * @property[ids] flow of monotonically increasing ids, emitted for any object change (bulked)
 *
 * Not thread-safe, so beware
 * */
class ObjectsHost {
    // cmp: MutableDoubleList from kotlin collections
    // reference: https://developer.android.com/reference/kotlin/androidx/collection/MutableDoubleList
    var size = 0
    private var maxSize = 100
    var objectTypes: ByteArray = ByteArray(maxSize)
        private set
    var objectParameters: DoubleArray = DoubleArray(PARAMETERS_PER_OBJECT*maxSize)
        private set

    private val _ids = MutableStateFlow(0)
    val ids = _ids.asStateFlow()

    fun addObjects(objects: List<GCircle?>) {
        val sizeIncrease = objects.size
        val requiredSize = size + sizeIncrease
        val size3 = PARAMETERS_PER_OBJECT*size
        // i think doing this manually is faster than using MutableList
        // because of no unboxing
        if (requiredSize > maxSize) {
            val k = 1 + (requiredSize - maxSize) / SIZE_INCREMENT
            maxSize += SIZE_INCREMENT * k
            objectTypes = ByteArray(maxSize) { if (it < size) objectTypes[it] else NULL_TYPE }
            objectParameters = DoubleArray(PARAMETERS_PER_OBJECT*maxSize) { i ->
                if (i < size3)
                    objectParameters[i]
                else 0.0
            }
        }
        for (i in 0 until sizeIncrease) {
            val startOffset = size3 + PARAMETERS_PER_OBJECT*i
            when (val o = objects[i]) {
                is Circle -> {
                    objectParameters[startOffset] = o.x
                    objectParameters[startOffset + 1] = o.y
                    objectParameters[startOffset + 2] = o.radius
                    objectTypes[size + i] = if (o.isCCW) CIRCLE_CCW_TYPE else CIRCLE_CW_TYPE
                }
                is Line -> {
                    objectParameters[startOffset] = o.a
                    objectParameters[startOffset + 1] = o.b
                    objectParameters[startOffset + 2] = o.c
                    objectTypes[size + i] = LINE_TYPE
                }
                is Point -> {
                    objectParameters[startOffset] = o.x
                    objectParameters[startOffset + 1] = o.y
                    objectTypes[size + i] = POINT_TYPE
                }
                is ImaginaryCircle -> {
                    objectParameters[startOffset] = o.x
                    objectParameters[startOffset + 1] = o.y
                    objectParameters[startOffset + 2] = o.radius
                    objectTypes[size + i] = IMAGINARY_CIRCLE_TYPE
                }
                null -> objectTypes[size + i] = NULL_TYPE
            }
        }
        size = requiredSize
        _ids.update { it + 1 }
    }

    fun clearObjects(indices: List<Ix>) {
        for (ix in indices)
            objectTypes[ix] = NULL_TYPE
        _ids.update { it + 1 }
    }

    fun transformObjects(
        indices: List<Ix>,
        translation: Offset = Offset.Zero,
        focus: Offset = Offset.Unspecified,
        zoom: Float = 1f,
        rotationAngle: Float = 0f,
    ) {
        if (zoom == 1f && rotationAngle == 0f)
            return translateObjects(indices, vector = translation)
        val dx = translation.x.toDouble()
        val dy = translation.y.toDouble()
        // unpacking Unspecified should result in 2 NaN's
        val (focusX, focusY) = focus
        val zoomD = zoom.toDouble()
        val phi: Double = rotationAngle * PI/180.0
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        for (ix in indices) {
            when (objectTypes[ix]) { // inlined T;R;S for GCircle
                CIRCLE_CCW_TYPE, CIRCLE_CW_TYPE -> {
                    var x: Double = objectParameters[PARAMETERS_PER_OBJECT*ix] + dx
                    var y: Double = objectParameters[PARAMETERS_PER_OBJECT*ix + 1] + dy
                    if (focus != Offset.Unspecified) {
                        // cmp. Offset.rotateBy & zoom and rotation are commutative
                        x -= focusX
                        y -= focusY
                        x = (x * cosPhi - y * sinPhi) * zoom + focusX
                        y = (x * sinPhi + y * cosPhi) * zoom + focusY
                    } // tbf because of T;S;R order it is not completely accurate
                    objectParameters[PARAMETERS_PER_OBJECT*ix]  = x
                    objectParameters[PARAMETERS_PER_OBJECT*ix + 1] = y
                    objectParameters[PARAMETERS_PER_OBJECT*ix + 2] *= zoomD
                }
                LINE_TYPE -> {
                    val a = objectParameters[PARAMETERS_PER_OBJECT*ix]
                    val b = objectParameters[PARAMETERS_PER_OBJECT*ix + 1]
                    var c: Double = objectParameters[PARAMETERS_PER_OBJECT*ix + 2]
                    c -= a*dx + b*dy
                    c = zoom*(a*focusX + b*focusY + c) // - a*focusX - b*focusY // added back when rotating
                    val a1 = a * cosPhi - b * sinPhi
                    val b1 = a * sinPhi + b * cosPhi
                    c = (hypot(a1, b1)/hypot(a, b)) * c - a1*focusX - b1*focusY
                    objectParameters[PARAMETERS_PER_OBJECT*ix] = a1
                    objectParameters[PARAMETERS_PER_OBJECT*ix + 1] = b1
                    objectParameters[PARAMETERS_PER_OBJECT*ix + 2] = c
                }
                POINT_TYPE -> {
                    var x: Double = objectParameters[PARAMETERS_PER_OBJECT*ix] + dx
                    var y: Double = objectParameters[PARAMETERS_PER_OBJECT*ix + 1] + dy
                    if (focus != Offset.Unspecified) {
                        // cmp. Offset.rotateBy & zoom and rotation are commutative
                        x -= focusX
                        y -= focusY
                        x = (x * cosPhi - y * sinPhi) * zoom + focusX
                        y = (x * sinPhi + y * cosPhi) * zoom + focusY
                    } // tbf because of T;S;R order it is not completely accurate
                    objectParameters[PARAMETERS_PER_OBJECT*ix]  = x
                    objectParameters[PARAMETERS_PER_OBJECT*ix + 1] = y
                }
//                IMAGINARY_CIRCLE_TYPE -> {} // always dependent for now
                else -> {}
            }
        }
        _ids.update { it + 1 }
    }

    fun translateObjects(indices: List<Ix>, vector: Offset) {
        val dx = vector.x.toDouble()
        val dy = vector.y.toDouble()
        for (ix in indices) {
            when (objectTypes[ix]) { // inlined GCircle.translated
                CIRCLE_CCW_TYPE, CIRCLE_CW_TYPE, POINT_TYPE -> {
                    objectParameters[PARAMETERS_PER_OBJECT*ix] += dx
                    objectParameters[PARAMETERS_PER_OBJECT*ix + 1] += dy
                }
                LINE_TYPE -> {
//                    Line(a, b, c - (a*vector.x + b*vector.y))
                    objectParameters[PARAMETERS_PER_OBJECT*ix + 2] -=
                        objectParameters[PARAMETERS_PER_OBJECT*ix]*dx +
                        objectParameters[PARAMETERS_PER_OBJECT*ix + 1]*dy
                }
//                IMAGINARY_CIRCLE_TYPE -> {} // always dependent for now
                else -> {}
            }
        }
        _ids.update { it + 1 }
    }

    fun updateObjects(updatedObjects: Map<Ix, GCircle?>) {
        for ((ix, o) in updatedObjects) {
            val startOffset = PARAMETERS_PER_OBJECT*ix
            when (o) {
                is Circle -> {
                    objectParameters[startOffset] = o.x
                    objectParameters[startOffset + 1] = o.y
                    objectParameters[startOffset + 2] = o.radius
                    objectTypes[size + ix] = if (o.isCCW) CIRCLE_CCW_TYPE else CIRCLE_CW_TYPE
                }
                is Line -> {
                    objectParameters[startOffset] = o.a
                    objectParameters[startOffset + 1] = o.b
                    objectParameters[startOffset + 2] = o.c
                    objectTypes[size + ix] = LINE_TYPE
                }
                is Point -> {
                    objectParameters[startOffset] = o.x
                    objectParameters[startOffset + 1] = o.y
                    objectTypes[size + ix] = POINT_TYPE
                }
                is ImaginaryCircle -> {
                    objectParameters[startOffset] = o.x
                    objectParameters[startOffset + 1] = o.y
                    objectParameters[startOffset + 2] = o.radius
                    objectTypes[size + ix] = IMAGINARY_CIRCLE_TYPE
                }
                null -> objectTypes[ix] = NULL_TYPE
            }
        }
        _ids.update { it + 1 }
    }

    companion object {
        /** When new objects overflow [maxSize] it will increase by
         * [SIZE_INCREMENT] via copying existing [objectParameters] & [objectTypes] */
        private const val SIZE_INCREMENT = 100
        private const val PARAMETERS_PER_OBJECT = 3
    }
}