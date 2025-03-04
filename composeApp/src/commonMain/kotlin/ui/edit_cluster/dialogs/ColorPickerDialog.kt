package ui.edit_cluster.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.ajalt.colormath.RenderCondition
import com.github.ajalt.colormath.model.RGB
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.color_picker_title
import dodeclusters.composeapp.generated.resources.hex_name
import org.jetbrains.compose.resources.stringResource
import ui.CancelButton
import ui.DialogTitle
import ui.OkButton
import ui.colorpicker.ClassicColorPicker
import ui.colorpicker.HsvColor
import ui.hideSystemBars
import ui.isLandscape
import ui.theme.DodeclustersColors

/**
 * @param[currentColor] currently chosen color
 * @param[usedColors] colors used to fill regions, sorted from most common to rarest
 * @param[savedColors] manually saved custom colors chosen during color-picking, latest to oldest
 * @param[predefinedColors] default palette
 */
data class ColorPickerParameters(
    val currentColor: Color,
    val usedColors: List<Color>,
    val savedColors: List<Color> = emptyList(),
    val predefinedColors: List<Color> = listOf(
        Color.White, Color.Black,
        Color.Red, Color.Green, Color.Blue, // RGB
        Color.Cyan, Color.Magenta, Color.Yellow, // CMY[K]
        Color.LightGray, Color.Gray, Color.DarkGray,
        // UI colors
        DodeclustersColors.secondaryDark, DodeclustersColors.secondaryLight,
        DodeclustersColors.highAccentDark, DodeclustersColors.highAccentLight,
        DodeclustersColors.skyBlue,
        // nice pastel palette
        Color(0xFF_B5C0D0), // very pale blue
        Color(0xFF_CCD3CA), // very pale green
        Color(0xFF_F5E8DD), // very pale yellow
        Color(0xFF_EED3D9), // very pale pink/red
        // random fun colors
        Color(0xFF_F08A5D), // orange
        Color(0xFF_6A2C70), // dark purple
        Color(0xFF_08D9D6), // aquamarine
        Color(0xFF_FFDE63), // pinkish red
        Color(0xFF_321E1E), // deep brown
    ),
)

@Composable
fun ColorPickerDialog2(
    parameters: ColorPickerParameters,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val color = rememberSaveable(stateSaver = HsvColor.Saver) {
        mutableStateOf(HsvColor.from(parameters.currentColor))
    }
    val hex = mutableStateOf(computeHex(color)) // NOTE: need to be MANUALLY updated on every color change
    Dialog(
        onDismissRequest = {
            onConfirm(color.value.toColor()) // that is how it be, out-of-dialog tap
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        hideSystemBars()
        Surface(
            modifier = modifier
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(0.8f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                DialogTitle(Res.string.color_picker_title, modifier = Modifier.align(Alignment.CenterHorizontally))
                Row() {
                    ColorPickerDisplay(
                        color, Modifier.fillMaxHeight(0.7f),
                        onColorChanged = { hex.value = computeHex(color) }
                    )
                    Column() {
                        // old vs new (new color circle overlapping old color circle)
                        // palette (use 'splash' icons)
                        // custom colors
                        // used colors
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HexInput(color, hex) {
                        onConfirm(color.value.toColor())
                    }
                    CancelButton(onDismissRequest = onCancel)
                    OkButton { onConfirm(color.value.toColor()) }
                }
            }
        }
    }
}


// TODO: preview previous vs current color
// TODO: add predefined colors (e.g. a-la in inkscape or such)
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onDismissRequest: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val color = rememberSaveable(stateSaver = HsvColor.Saver) {
        mutableStateOf(HsvColor.from(initialColor))
    }
    val hex = mutableStateOf(computeHex(color)) // NOTE: need to be MANUALLY updated on every color change
    val windowSizeClass = calculateWindowSizeClass()
    Dialog(
        onDismissRequest = {
//        onDismissRequest()
            onConfirm(color.value.toColor()) // thats how it be
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        hideSystemBars()
        if (windowSizeClass.isLandscape) {
            if (windowSizeClass.heightSizeClass <= WindowHeightSizeClass.Compact) // for mobile phones
                ColorPickerHorizontalCompact(color, hex, onDismissRequest, onConfirm)
            else
                ColorPickerHorizontal(color, hex, onDismissRequest, onConfirm)
        } else { // portrait
            ColorPickerVertical(color, hex, onDismissRequest, onConfirm)
        }
    }
}

@Composable
private fun ColorPickerHorizontalCompact(
    color: MutableState<HsvColor>,
    hex: MutableState<TextFieldValue>,
    onDismissRequest: () -> Unit,
    onConfirm: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
//                    .fillMaxHeight()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(Modifier.fillMaxHeight(0.9f)) {
            ColorPickerDisplay(color, Modifier.fillMaxHeight()) {
                hex.value = computeHex(color)
            }
            Box(Modifier.fillMaxHeight()) {
                DialogTitle(Res.string.color_picker_title, modifier = Modifier.align(Alignment.TopCenter))
                HexInput(color, hex, Modifier.align(Alignment.CenterStart)) {
                    onConfirm(color.value.toColor())
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                    ,
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CancelButton { onDismissRequest() }
                    OkButton { onConfirm(color.value.toColor()) }
                }
            }
        }
    }
}

@Composable
private fun ColorPickerHorizontal(
    color: MutableState<HsvColor>,
    hex: MutableState<TextFieldValue>,
    onDismissRequest: () -> Unit,
    onConfirm: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
//                    .fillMaxHeight()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(0.8f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            DialogTitle(Res.string.color_picker_title, modifier = Modifier.align(Alignment.CenterHorizontally))
            ColorPickerDisplay(color, Modifier.fillMaxHeight(0.7f)) {
                hex.value = computeHex(color)
            }
            Row(
//                        modifier = Modifier.fillMaxWidth(),
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
}

@Composable
private fun ColorPickerVertical(
    color: MutableState<HsvColor>,
    hex: MutableState<TextFieldValue>,
    onDismissRequest: () -> Unit,
    onConfirm: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
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
            DialogTitle(Res.string.color_picker_title)
            ColorPickerDisplay(color, Modifier
                .align(Alignment.Start)
                .fillMaxHeight(0.7f)
            ) {
                hex.value = computeHex(color)
            }
            HexInput(color, hex, Modifier.align(Alignment.Start)) {
                onConfirm(color.value.toColor())
            }
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
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

private fun computeHex(clr: State<HsvColor>): TextFieldValue {
    val c = clr.value.toColor()
    val s = RGB(c.red, c.green, c.blue).toHex(withNumberSign = false, renderAlpha = RenderCondition.NEVER)
    return TextFieldValue(s, TextRange(s.length))
}

@Composable
private fun ColorPickerDisplay(
    color: MutableState<HsvColor>,
    modifier: Modifier = Modifier,
    onColorChanged: () -> Unit
) {
    ClassicColorPicker(
        modifier
            .aspectRatio(1.1f)
            .padding(16.dp)
        ,
        colorPickerValueState = color,
        showAlphaBar = false, // MAYBE: add alpha someday
        onColorChanged = { onColorChanged() }
        // $color is updated internally by the ColorPicker
    )
}

@Composable
private fun HexInput(
    color: MutableState<HsvColor>,
    hex: MutableState<TextFieldValue>,
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit
) {
    val windowInfo = LocalWindowInfo.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
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
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
            showKeyboardOnFocus = false, // this sadly does nothing...
        ),
        keyboardActions = KeyboardActions(
            onDone = { onConfirm() }
        ),
        singleLine = true,
        modifier = modifier
            .focusRequester(focusRequester)
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
    // NOTE: this fix only works 90% of time...
    // reference: https://stackoverflow.com/q/71412537/7143065
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.collect { isWindowFocused ->
            if (isWindowFocused) { // runs once every time the dialog is opened
                focusRequester.freeFocus()
                keyboard?.hide() // suppresses rare auto-showing keyboard bug
            }
        }
    }
}