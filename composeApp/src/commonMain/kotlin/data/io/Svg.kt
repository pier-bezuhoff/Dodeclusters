package data.io

import androidx.compose.ui.graphics.Color
import data.Cluster
import data.ColorCssSerializer
import data.geometry.CircleOrLine
import kotlinx.serialization.json.Json

// NOTE: ksvg doesn't support wasm yet + my use case is relatively formulaic

// example:
// <svg width="400" height="400">
//   <defs>
//     <clipPath id="clip-part0-0">
//       <circle cx="100" cy="100" r="50"/>
//     </clipPath>
//     <clipPath id="clip-part0-1">
//       <circle cx="120" cy="100" r="50" clip-path="url(#clip-part0-0)"/>
//     </clipPath>
//     <mask id="mask-part0">
//       <circle cx="105" cy="80" r="50" clip-path="url(#clip-part0-1)" fill="white"/>
//       <circle cx="110" cy="130" r="20" fill="black"/>
//     </mask>
//   </defs>
//   <rect width="100%" height="100%" fill="black"/>
//   <rect width="100%" height="100%" mask="url(#mask-part0)" fill="deepskyblue"/>
// </svg>

// NOTE: "For reliable results cross-browser, use numbers with no more
//  than 2 digits after the decimal and four digits before it."
fun cluster2svg(
    cluster: Cluster,
    backgroundColor: Color?,
    startX: Float, startY: Float,
    width: Float, height: Float
): String {
    val partClipsAndMasks = cluster.parts.withIndex().map { (i, part) ->
        partMask(cluster.circles, i, part)
    }.joinToString("\n")
    val bgRect = backgroundColor?.let {
        val bg = Json.encodeToString(ColorCssSerializer, it)
        "<rect width=\"100%\" height=\"100%\" fill=\"$bg\">"
    } ?: ""
    // when only-border:
    // `stroke="color"`
    // when filled:
    // `fill="color"`
    val partRects = cluster.parts.withIndex().map { (i, part) ->
        val fill = Json.encodeToString(ColorCssSerializer, part.fillColor)
        "<rect width=\"100%\" height=\"100%\" mask=\"url(#mask-part$i)\" fill=\"$fill\">"
    }.joinToString("\n")
    return """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="$startX $startY $width $height">
          <defs>
            $partClipsAndMasks
          </defs>
          $bgRect
          $partRects
        </svg>
    """.trimIndent()
}

fun partMask(
    circles: List<CircleOrLine>,
    partIndex: Int,
    part: Cluster.Part
): String {
    return ""
}

