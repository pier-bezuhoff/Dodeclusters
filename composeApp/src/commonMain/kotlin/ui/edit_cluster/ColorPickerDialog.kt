package ui.edit_cluster

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.ajalt.colormath.RenderCondition
import com.github.ajalt.colormath.model.RGB
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.cancel
import dodeclusters.composeapp.generated.resources.cancel_name
import dodeclusters.composeapp.generated.resources.color_picker_title
import dodeclusters.composeapp.generated.resources.confirm
import dodeclusters.composeapp.generated.resources.hex_name
import dodeclusters.composeapp.generated.resources.ok_description
import dodeclusters.composeapp.generated.resources.ok_name
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.colorpicker.ClassicColorPicker
import ui.colorpicker.HsvColor

// TODO: preview previous vs current color
// TODO: add predefined colors (e.g. a-la in inkscape or such)
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onDismissRequest: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    fun computeHex(clr: State<HsvColor>): TextFieldValue {
        val c = clr.value.toColor()
        val s = RGB(c.red, c.green, c.blue).toHex(withNumberSign = false, renderAlpha = RenderCondition.NEVER)
        return TextFieldValue(s, TextRange(s.length))
    }
    val color = rememberSaveable(stateSaver = HsvColor.Saver) {
        mutableStateOf(HsvColor.from(initialColor))
    }
    val hex = mutableStateOf(computeHex(color)) // need to be MANUALLY updated on every color change
    val windowSizeClass = calculateWindowSizeClass()
    Dialog(
        onDismissRequest = {
//        onDismissRequest()
            onConfirm(color.value.toColor()) // thats how it be
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded &&
            windowSizeClass.heightSizeClass <= WindowHeightSizeClass.Medium ||
            windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium &&
            windowSizeClass.heightSizeClass <= WindowHeightSizeClass.Compact
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
//                    .wrapContentWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(0.95f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ColorPickerTitle()
                    ColorPickerDisplay(color) {
                        hex.value = computeHex(color)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HexInput(color, hex) {
                            onConfirm(color.value.toColor())
                        }
                        CancelButton { onDismissRequest() }
                        OkButton { onConfirm(color.value.toColor()) }
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(0.95f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ColorPickerTitle()
                    ColorPickerDisplay(color) {
                        hex.value = computeHex(color)
                    }
                    HexInput(color, hex, Modifier.align(Alignment.Start)) {
                        onConfirm(color.value.toColor())
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                        ,
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        CancelButton { onDismissRequest() }
                        OkButton { onConfirm(color.value.toColor()) }
                    }
                }
            }
        }
    }
}

@Composable
fun ColorPickerTitle(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.color_picker_title),
        modifier = modifier.padding(16.dp),
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
fun ColorPickerDisplay(
    color: MutableState<HsvColor>,
    modifier: Modifier = Modifier,
    onColorChanged: () -> Unit
) {
    ClassicColorPicker(
        modifier
            .fillMaxHeight(0.7f)
            .padding(16.dp),
        colorPickerValueState = color,
        showAlphaBar = false, // MAYBE: add alpha someday
        onColorChanged = { onColorChanged() }
        // $color is updated internally by the ColorPicker
    )
}

@Composable
fun HexInput(
    color: MutableState<HsvColor>,
    hex: MutableState<TextFieldValue>,
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit
) {
    var isError by remember(color.value) { mutableStateOf(false) }
    OutlinedTextField(
        value = hex.value,
        onValueChange = { new ->
            hex.value = new
            if (new.text.length == 6) // primitive hex validation
                try {
                    val rgb = RGB(new.text)
                    color.value = HsvColor.from(Color(rgb.r, rgb.g, rgb.b))
                    isError = false
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                    println("cannot parse hex string \"${new.text}\"")
                    isError = true
                }
            else
                isError = true
        },
//                    textStyle = TextStyle(fontSize = 16.sp),
        label = { Text(stringResource(Res.string.hex_name)) },
        placeholder = { Text("RRGGBB", color = LocalContentColor.current.copy(alpha = 0.5f)) },
        isError = isError,
        keyboardOptions = KeyboardOptions( // smart ass enter capturing
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { onConfirm() }
        ),
        singleLine = true,
        modifier = modifier
            .onKeyEvent {
                if (it.key == Key.Enter) {
                    onConfirm()
                    true
                } else false
            }.padding(horizontal = 16.dp, vertical = 8.dp)
        ,
//        colors = OutlinedTextFieldDefaults.colors()
//            .copy(unfocusedContainerColor = color.value.toColor())
    )
}

@Composable
fun OkButton(
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit,
) {
    Button(
        onClick = { onConfirm() },
        modifier = modifier.padding(8.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(50), // = 50% percent or shape = CircleShape
    ) {
        Icon(painterResource(Res.drawable.confirm), stringResource(Res.string.ok_description))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(Res.string.ok_name), fontSize = 24.sp)
    }
}

@Composable
fun CancelButton(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
) {
    OutlinedButton(
        onClick = { onDismissRequest() },
        modifier = modifier.padding(8.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(50), // = 50% percent or shape = CircleShape
    ) {
        Icon(painterResource(Res.drawable.cancel), stringResource(Res.string.cancel_name))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(Res.string.cancel_name), fontSize = 24.sp)
    }
}