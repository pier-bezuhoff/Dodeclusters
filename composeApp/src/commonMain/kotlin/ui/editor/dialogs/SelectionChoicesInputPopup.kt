package ui.editor.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import core.geometry.Circle
import core.geometry.ImaginaryCircle
import core.geometry.Line
import core.geometry.Point
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.arc_path_number_label
import dodeclusters.composeapp.generated.resources.circle_number_label
import dodeclusters.composeapp.generated.resources.close
import dodeclusters.composeapp.generated.resources.imaginary_circle_number_label
import dodeclusters.composeapp.generated.resources.line_number_label
import dodeclusters.composeapp.generated.resources.point_number_label
import dodeclusters.composeapp.generated.resources.selection_choices_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.editor.Submode
import ui.theme.ColorTheme
import ui.theme.DodeclustersColors
import ui.theme.DodeclustersTheme
import ui.theme.adaptiveTypography
import ui.theme.extendedColorScheme

@Composable
fun SelectionChoicesInputPopup(
    choices: List<Submode.SelectionChoicesInput.Choice>,
    selectChoice: (indexAmongChoices: Int?) -> Unit,
    dismiss: () -> Unit,
) {
    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset = IntOffset(
                x = anchorBounds.center.x - popupContentSize.width/2,
                y = anchorBounds.center.y - popupContentSize.height/2,
            )
        },
        onDismissRequest = dismiss,
        properties = PopupProperties(
            focusable = true,
        ),
    ) {
        SelectionChoices(choices, selectChoice)
    }
}

@Composable
private fun SelectionChoices(
    choices: List<Submode.SelectionChoicesInput.Choice>,
    selectChoice: (indexAmongChoices: Int?) -> Unit = {},
) {
    Surface(
        modifier = Modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 12.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
            ,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    stringResource(Res.string.selection_choices_title),
                    Modifier
                        .padding(horizontal = 32.dp)
                        .align(Alignment.Center)
                    ,
                    style = MaterialTheme.adaptiveTypography.body
                )
                IconButton(
                    onClick = { selectChoice(null) },
                    // NOTE: upper 1/4th is OOB, which is obv on hover
                    Modifier
                        .size(32.dp)
                        .align(Alignment.TopEnd)
                        .offset(8.dp, -16.dp)
                ) {
                    Icon(painterResource(Res.drawable.close), "close", Modifier.size(18.dp))
                }
            }
            choices.forEachIndexed { i, choice ->
                val label = when (choice.objectOrArcPath) {
                    is Circle -> stringResource(Res.string.circle_number_label, choice.index)
                    is Line -> stringResource(Res.string.line_number_label, choice.index)
                    is ImaginaryCircle -> stringResource(
                        Res.string.imaginary_circle_number_label,
                        choice.index
                    )
                    is Point -> stringResource(Res.string.point_number_label, choice.index)
                    null -> stringResource(Res.string.arc_path_number_label, choice.index)
                }
                TextButton(
                    onClick = { selectChoice(i) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    border =
                        if (i == 0)
                        // salad green is the default selection color
                            BorderStroke(2.dp, DodeclustersColors.strongSalad)
//                                BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                        else null,
                ) {
                    Text(
                        text = label,
                        color = choice.borderColor ?: choice.fillColor
                        ?: MaterialTheme.extendedColorScheme.highAccentColor,
                        style = MaterialTheme.adaptiveTypography.label,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SelectionChoicesPreview() {
    DodeclustersTheme(ColorTheme.DARK) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.Center)) {
                SelectionChoices(
                    choices = listOf(
                        Submode.SelectionChoicesInput.Choice(
                            0, Circle(0.0, 0.0, 1.0),
                            Color.Red, null
                        ),
                        Submode.SelectionChoicesInput.Choice(
                            1, Line(1.0, 0.0, 1.0),
                            Color.Green, null
                        ),
                    ),
                )
            }
        }
    }
}

