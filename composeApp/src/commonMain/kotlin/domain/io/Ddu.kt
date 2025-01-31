package domain.io

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import domain.average
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.reflect.KMutableProperty0

// NOTE: when restGlobals = listOf(4, 4) some magic occurs (rule-less circles are moving in DodecaLook)
class Ddu(
    var backgroundColor: Color = DEFAULT_BACKGROUND_COLOR,
    var restGlobals: List<Int> = emptyList(), // unused
    var drawTrace: Boolean? = null,
    var bestCenter: Offset? = null, // cross-(screen size)
    var shape: Shape = DEFAULT_SHAPE,
    var circles: List<Circle> = emptyList(),
    var name: String? = null
) {
    data class Circle(
        val x: Double,
        val y: Double,
        val radius: Double,
        val visible: Boolean = DEFAULT_CIRCLE_VISIBLE,
        val filled: Boolean = DEFAULT_CIRCLE_FILLED,
        val fillColor: Color = DEFAULT_CIRCLE_FILL_COLOR,
        val borderColor: Color? = DEFAULT_CIRCLE_BORDER_COLOR,
        val rule: List<Int> = DEFAULT_CIRCLE_RULE,
    ) {
        val offset: Offset
            get() = Offset(x.toFloat(), y.toFloat())
        fun toCircle(): data.geometry.Circle =
            data.geometry.Circle(x, y, radius)
    }

    val autoCenter: Offset
        get() = circles.filter { it.visible }.map { it.offset }.average()
    val complexity: Int
        get() = circles.sumOf { it.rule.size }
    private fun getNSmartUpdates(nPreviewUpdates: Int): Int =
        (MIN_PREVIEW_UPDATES + nPreviewUpdates * 20 / sqrt(1.0 + complexity)).roundToInt()
    private fun getNUpdates(nPreviewUpdates: Int, previewSmartUpdates: Boolean): Int =
        if (previewSmartUpdates) getNSmartUpdates(nPreviewUpdates) else nPreviewUpdates
    // ^^^ for buildPreview

    // NOTE: copy(newRule = null) resolves overload ambiguity
    fun copy() =
        Ddu(
            backgroundColor, restGlobals.toList(), drawTrace, bestCenter, shape,
            circles.map { it.copy(rule = emptyList()) }, name
        )

    override fun toString(): String = """Ddu(
        |  backgroundColor = ${backgroundColor}
        |  restGlobals = $restGlobals
        |  drawTrace = $drawTrace
        |  bestCenter = $bestCenter
        |  shape = $shape
        |  file = $name
        |  circles = $circles
        |)
    """.trimMargin()

    //    suspend fun saveToStream(outputStream: OutputStream) =
//        DduWriter(this).write(outputStream)
    suspend fun save(): String {
        val builder = StringBuilder()
        DduWriter(this).write(builder)
        return builder.toString()
    }

    //    suspend fun saveToStreamForDodecaLook(outputStream: OutputStream) =
//        DduWriter(this).writeForDodecaLook(outputStream)
    suspend fun saveForDodecaLook(): String {
        val builder = StringBuilder()
        DduWriter(this).writeForDodecaLook(builder)
        return builder.toString()
    }

    suspend fun buildPreview(width: Int, height: Int): ImageBitmap =
        withContext(Dispatchers.Default) {
            // used RGB_565 instead of ARGB_8888 for performance (visually indistinguishable)
            val bitmap = ImageBitmap(width, height, config = ImageBitmapConfig.Rgb565)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply {
                isAntiAlias = true
                filterQuality = FilterQuality.Low
            }
//            CanvasDrawScope().draw(TODO(), TODO(), canvas, Size(width.toFloat(), height.toFloat())) {}

//            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
//            val circleGroup = mkCircleGroup(circles, values, paint)
//            val center = Complex((width / 2).toDouble(), (height / 2).toDouble())
//            val bestCenter = bestCenter ?: if (values.autocenterPreview) autoCenter else center
//            val (dx, dy) = (center - bestCenter).asFF()
//            val (centerX, centerY) = center.asFF()
//            val scale: Float = PREVIEW_SCALE * width / NORMAL_PREVIEW_SIZE // or use options.preview_size.default
//            val matrix = Matrix().apply { postTranslate(dx, dy); postScale(scale, scale, centerX, centerY) }
//            canvas.withMatrix(matrix) {
//                drawColor(backgroundColor)
//                if (drawTrace ?: true) {
//                    circleGroup.drawTimes(getNUpdates(values.nPreviewUpdates, values.previewSmartUpdates), canvas = canvas, shape = shape)
//                } else {
//                    circleGroup.draw(canvas, shape = shape)
//                }
//            }
            return@withContext bitmap
        }

    companion object {
        val DEFAULT_BACKGROUND_COLOR: Color = Color.White
        val DEFAULT_SHAPE: Shape = Shape.CIRCLE
        const val DEFAULT_CIRCLE_VISIBLE = false
        const val DEFAULT_CIRCLE_FILLED = false
        val DEFAULT_CIRCLE_FILL_COLOR: Color = Color.Black
        val DEFAULT_CIRCLE_BORDER_COLOR: Color? = null
        val DEFAULT_CIRCLE_RULE = emptyList<Int>()

        private const val MIN_PREVIEW_UPDATES = 10
        private const val NORMAL_PREVIEW_SIZE = 300 // with this preview_size preview_scale was tuned
        private const val PREVIEW_SCALE = 0.5f

//        suspend fun fromStream(stream: InputStream): Ddu = withContext(Dispatchers.Default) {
//            DduReader(stream.reader()).read()
//        }

        fun createBlankPreview(previewSizePx: Int): ImageBitmap {
            val size = previewSizePx
            val bitmap = ImageBitmap(size, size, config = ImageBitmapConfig.Rgb565)
            val canvas = Canvas(bitmap)
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint().apply { color = Color.White })
            return bitmap
        }

        val BLANK_DDU: Ddu = Ddu()
//        val EXAMPLE_DDU: Ddu = run {
//            val circle = Ddu.Circle(0, 300.0, 400.0, 200.0, fillColor = Color.Blue, visible = true, rule = listOf(1, 2))
//            val circle1 = CircleFigure(450.0, 850.0, 300.0, Color.LTGRAY)
//            val circle2 = CircleFigure(460.0, 850.0, 300.0, Color.DKGRAY)
//            val circle0 = CircleFigure(0.0, 0.0, 100.0, Color.GREEN)
//            val circles: List<CircleFigure> = listOf(
//                circle,
//                circle1,
//                circle2,
//                circle0,
//                circle0.inverted(circle),
//                circle1.inverted(circle),
//                CircleFigure(600.0, 900.0, 10.0, Color.RED, fill = true)
//            )
//            Ddu(backgroundColor = Color.WHITE, circles = circles)
//        }
    }
}

private class CircleBuilder {
    var radius: Double? = null
    var x: Double? = null
    var y: Double? = null
    var color: Color? = null
    var fill: Boolean? = null
    var rule: String? = null
    var borderColor: Color? = null

    class NotEnoughBuildParametersException(message: String = DEFAULT_MESSAGE) : Exception(message) {
        companion object {
            private const val DEFAULT_MESSAGE: String =
                "CircleBuilder must have [radius], [x] and [y] in order to build CircleFigure"
        }
    }

    @Throws(NotEnoughBuildParametersException::class)
    fun build(): Ddu.Circle {
        if (x == null || y == null || radius == null)
            throw NotEnoughBuildParametersException()
        val symRule = rule?.let {
            it.trim('n').map { c -> c.digitToInt() }
        }
        return Ddu.Circle(
            x!!, y!!, radius!!,
            fillColor = color ?: Ddu.DEFAULT_CIRCLE_FILL_COLOR,
            filled = fill ?: Ddu.DEFAULT_CIRCLE_FILLED,
            visible = rule?.let { !it.startsWith('n') && it.isNotBlank() } ?: false,
            rule = symRule ?: emptyList(),
            borderColor = borderColor
        )
    }
}


private class DduBuilder {
    var backgroundColor: Color = Ddu.DEFAULT_BACKGROUND_COLOR
    val restGlobals: MutableList<Int> = mutableListOf()
    var drawTrace: Boolean? = null
    var bestCenter: Offset? = null
    var shape: Shape = Ddu.DEFAULT_SHAPE
    val circleFigures: MutableList<Ddu.Circle> = mutableListOf()

    fun build(): Ddu =
        Ddu(backgroundColor, restGlobals, drawTrace, bestCenter, shape, circleFigures)

    fun addCircleFigure(circleFigure: Ddu.Circle) {
        circleFigures.add(circleFigure)
    }
}


private class DduReader(private val reader: () -> Iterable<String>) {
    private enum class Mode { // for scanning .ddu, before <mode parameter>
        NO, GLOBAL, RADIUS, X, Y, COLOR, FILL, RULE, CIRCLE_AUX;
        fun next(): Mode = entries.toTypedArray().elementAtOrElse(ordinal + 1) { CIRCLE_AUX }
    }

    private val dduBuilder = DduBuilder()
    private var mode: Mode = Mode.NO // mode == FILL === before scanning [fill] parameter
    private var nGlobals: Int = 0
    private lateinit var circleBuilder: CircleBuilder
    private lateinit var trimmedLine: String
    private var nOfLine: Long = 0

    suspend fun read(): Ddu =
        withContext(Dispatchers.Default) {
            reader().map { line: String ->
                nOfLine++
                trimmedLine = line.trim()
                if (trimmedLine.isNotBlank())
                    when {
                        mode == Mode.GLOBAL -> readLegacyGlobalLine()
                        trimmedLine.startsWith("circle:") -> tryAddCircle()
                        mode == Mode.NO -> readModernGlobalLine()
                        mode >= Mode.RADIUS -> readCircleLine()
                    }
            }
            tryAddCircle()
            dduBuilder.build()
        }

    private fun readLegacyGlobalLine() {
        when (nGlobals) {
            0 -> dduBuilder::backgroundColor maybeSetGlobalTo maybeReadColor()
            // don't know, what these 2 mean ("howInvers" and "howAnim")
            1, 2 -> maybeReadInt()?.let {
                dduBuilder.restGlobals.add(it)
                nGlobals++
            }
            3 -> dduBuilder::drawTrace maybeSetGlobalTo maybeReadBoolean()
            4 -> dduBuilder::bestCenter maybeSetGlobalTo maybeReadComplex()?.let { Offset(it.first.toFloat(), it.second.toFloat()) }
        }
        mode = Mode.NO
    }

    private fun tryAddCircle() {
        if (this::circleBuilder.isInitialized) {
            try {
                val circleFigure = circleBuilder.build()
                dduBuilder.addCircleFigure(circleFigure)
            } catch (e: CircleBuilder.NotEnoughBuildParametersException) {
                e.printStackTrace()
                println("Error in DduReader.tryAddCircle occurred while reading line $nOfLine")
            }
        }
        circleBuilder = CircleBuilder()
        mode = Mode.RADIUS
    }

    private fun readModernGlobalLine() {
        when {
            trimmedLine.startsWith("global") ->
                mode = Mode.GLOBAL
            trimmedLine.startsWith("drawTrace:") ->
                dduBuilder::drawTrace maybeSetTo maybeReadBoolean(trimmedSubstringAfter("drawTrace:"))
            trimmedLine.startsWith("bestCenter:") ->
                dduBuilder::bestCenter maybeSetTo maybeReadComplex(trimmedSubstringAfter("bestCenter:"))?.let { Offset(it.first.toFloat(), it.second.toFloat()) }
            trimmedLine.startsWith("shape:") ->
                dduBuilder::shape maybeSetTo maybeReadShape(trimmedSubstringAfter("shape:"))
            trimmedLine.startsWith("showOutline:") ->
                println("Deprecated ddu parameter: showOutline")
        }
    }

    private fun readCircleLine() {
        when (mode) {
            Mode.RADIUS -> circleBuilder::radius maybeSetTo maybeReadDouble()
            Mode.X -> circleBuilder::x maybeSetTo maybeReadDouble()
            Mode.Y -> circleBuilder::y maybeSetTo maybeReadDouble()
            Mode.COLOR -> circleBuilder::color maybeSetTo maybeReadColor()
            Mode.FILL -> circleBuilder::fill maybeSetTo maybeReadBoolean()
            Mode.RULE -> circleBuilder::rule maybeSetTo maybeReadRule()
            else -> {}
        }
        if (mode >= Mode.RULE && trimmedLine.startsWith("borderColor:"))
            circleBuilder::borderColor maybeSetTo maybeReadColor(trimmedSubstringAfter("borderColor:"))
        mode = mode.next()
    }

    private infix fun <T : Any?> KMutableProperty0<T>.maybeSetGlobalTo(maybeValue: T?) {
        maybeValue?.let { value ->
            this.set(value)
            nGlobals++
        }
    }

    private infix fun <T : Any?> KMutableProperty0<T>.maybeSetTo(maybeValue: T?) {
        maybeValue?.let { value ->
            this.set(value)
        }
    }

    private fun maybeReadBoolean(s: String = trimmedLine): Boolean? {
        return when (s) {
            "0", "false" -> false
            "1", "true" -> true
            else -> null
        }
    }

    private fun maybeReadDouble(s: String = trimmedLine): Double? =
        s.replace(',', '.').toDoubleOrNull()

    private fun maybeReadInt(s: String = trimmedLine): Int? =
        s.toIntOrNull()

    private fun maybeReadColor(s: String = trimmedLine): Color? =
        s.toIntOrNull()?.toColor()

    private fun maybeReadComplex(s: String = trimmedLine): Pair<Double, Double>? {
        s.split(" ").let {
            if (it.size == 2) {
                val x = it[0].toDoubleOrNull()
                val y = it[1].toDoubleOrNull()
                if (x != null && y != null)
                    return Pair(x, y)
            }
        }
        return null
    }

    private fun maybeReadShape(s: String = trimmedLine): Shape? =
        Shape.entries.firstOrNull { it.toString() == s.uppercase() }

    private fun maybeReadRule(s: String = trimmedLine): String? =
        if (Regex("n?\\d+").matches(s)) s else null

    private fun trimmedSubstringAfter(prefix: String): String =
        trimmedLine.substringAfter(prefix).trim()
}

private class DduWriter(private val ddu: Ddu) {

    class IncompatibleFormatException(message: String) : Exception(message)

    private lateinit var stringBuilder: StringBuilder
    private val legacyGlobals: List<String> = listOf(
        ddu.backgroundColor.toColorInt(),
        *ddu.restGlobals.toTypedArray()
    ).map { it.toString() }

    @Throws(IncompatibleFormatException::class)
    suspend fun write(stringBuilder: StringBuilder) {
        withContext(Dispatchers.Default) {
            this@DduWriter.stringBuilder = stringBuilder
            writeLine(HEADER)
            legacyGlobals.forEach { writeLegacyGlobal(it) }
            writeModernGlobals()
            ddu.circles.forEach {
                try {
                    writeCircle(it)
                } catch (e: IncompatibleFormatException) {
                    println("skipping a circle because it caused: '${e.message}'")
                }
            }
        }
    }

    suspend fun writeForDodecaLook(stringBuilder: StringBuilder) {
        // MAYBE: abstract DduWriter + 2 impl-s
        withContext(Dispatchers.Default) {
            this@DduWriter.stringBuilder = stringBuilder
            writeLine(DODECA_LOOK_HEADER)
            legacyGlobals.forEach { writeLegacyGlobal(it) }
            ddu.circles.forEach {
                try {
                    writeCircleForDodecaLook(it)
                } catch (e: IncompatibleFormatException) {
                    println("skipping a circle because it caused: '${e.message}'")
                }
            }
        }
    }

    private fun writeLine(s: String) =
        stringBuilder.append("$s\n")

    private fun writeLegacyGlobal(global: String) {
        writeLine("global")
        writeLine(global)
    }

    private fun writeModernGlobals() {
        maybeWriteDrawTrace()
        maybeWriteBestCenter()
        writeShape()
    }

    @Throws(IncompatibleFormatException::class)
    private fun writeCircle(circleFigure: Ddu.Circle) {
        if (circleFigure.rule.any { it > 9 })
            throw IncompatibleFormatException("Rules with indices that are >9 are incompatible with .ddu format")
        writeLine("\ncircle:")
        with(circleFigure) {
            val fillInt = if (filled) 1 else 0
            listOf(radius, x, y, fillColor.toColorInt(), fillInt).forEach {
                writeLine(it.toString())
            }
            if (rule.isNotEmpty()) {
                val prefix = if (!visible) "n" else ""
                writeLine(prefix + rule.joinToString(separator = ""))
            }
            borderColor?.toColorInt()?.let {
                writeLine("borderColor: $it")
            }
        }
    }

    @Throws(IncompatibleFormatException::class)
    private fun writeCircleForDodecaLook(circleFigure: Ddu.Circle) {
        if (circleFigure.rule.any { it > 9 })
            throw IncompatibleFormatException("Rules with indices that are >9 are incompatible with .ddu format")
        writeLine("circle:")
        with(circleFigure) {
            val fillInt = if (filled) 1 else 0
            listOf(radius, x, y, fillColor.toColorInt(), fillInt).forEach {
                writeLine(it.toString())
            }
            if (rule.isNotEmpty()) {
                val prefix = if (!visible) "n" else ""
                writeLine(prefix + rule.joinToString(separator = ""))
            }
        }
    }

    private fun maybeWriteDrawTrace() {
        ddu.drawTrace?.let {
            writeLine("drawTrace: $it")
        }
    }

    private fun maybeWriteBestCenter() {
        ddu.bestCenter?.let {
            writeLine("bestCenter: ${it.x} ${it.y}")
        }
    }

    private fun writeShape() =
        writeLine("shape: ${ddu.shape}")

    companion object {
        private const val HEADER: String = "Dodeclusters"
        private const val DODECA_LOOK_HEADER: String = "DUDU C++v.1" // NOTE: important! without it DodecaLook fails
    }
}

/* In C++ (with which ddus were originally created) colors are represented as 0xRRGGBB, but in Android/Java -- AABBGGRR
* see Bitmap.Config.ARGB_8888 (https://developer.android.com/reference/android/graphics/Bitmap.Config.html#ARGB_8888) */

//@get:IntRange(from = 0, to = 255)
private val Int.red: Int get() = (this and 0xff0000) shr 16
//@get:IntRange(from = 0, to = 255)
private val Int.green: Int get() = (this and 0x00ff00) shr 8
//@get:IntRange(from = 0, to = 255)
private val Int.blue: Int get() = this and 0x0000ff

/** C++ color int 0xRRGGBB -> our color format */
internal fun Int.toColor(): Color =
    Color(red = red, green = green, blue = blue)
/** our color format -> C++ color int 0xRRGGBB */
internal fun Color.toColorInt(): Int =
    toArgb() and 0xffffff

