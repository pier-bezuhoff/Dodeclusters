package ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

object HighlightMarkdown {
    const val HIGHLIGHT_DELIMITER = "^"
    val HIGHLIGHT_FONT_WEIGHT = FontWeight.Bold

    fun parse(
        textWithMarkdown: String,
        highlightColor: Color,
    ): AnnotatedString = buildAnnotatedString {
        val style = SpanStyle(
            color = highlightColor,
            fontWeight = HIGHLIGHT_FONT_WEIGHT,
        )
        val parts = textWithMarkdown.splitToSequence(HIGHLIGHT_DELIMITER)
        var delimited = false
        for (part in parts) {
            if (delimited) {
                withStyle(style) {
                    append(part)
                }
            } else {
                append(part)
            }
            delimited = !delimited
        }
    }
}