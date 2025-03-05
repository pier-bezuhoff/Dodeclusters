package ui.edit_cluster.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import dodeclusters.composeapp.generated.resources.add_circle
import dodeclusters.composeapp.generated.resources.color_picker_title
import dodeclusters.composeapp.generated.resources.hex_name
import dodeclusters.composeapp.generated.resources.paint_splash
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.CancelButton
import ui.DialogTitle
import ui.OkButton
import ui.SimpleButton
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
        Color.White, Color.LightGray, Color.Gray, Color.DarkGray, Color.Black,
        // RGB & CMY[K] -> R Y G C B M
        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta,
        // random fun colors
        Color(0xFF_F08A5D), // orange
        Color(0xFF_6A2C70), // dark purple
        Color(0xFF_08D9D6), // aquamarine
        Color(0xFF_FFDE63), // pinkish red
        Color(0xFF_321E1E), // deep brown
        // UI colors (not sure it's a good idea... no contrast)
        DodeclustersColors.primaryDark, DodeclustersColors.primaryLight,
        DodeclustersColors.secondaryDark, DodeclustersColors.secondaryLight,
//        DodeclustersColors.tertiaryDark, DodeclustersColors.tertiaryLight,
        DodeclustersColors.highAccentDark, DodeclustersColors.highAccentLight,
        DodeclustersColors.skyBlue,
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog2(
    parameters: ColorPickerParameters,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onConfirm: (ColorPickerParameters) -> Unit,
) {
    val color = rememberSaveable(stateSaver = HsvColor.Saver) {
        mutableStateOf(HsvColor.from(parameters.currentColor))
    }
    val hex = mutableStateOf(computeHex(color)) // NOTE: need to be MANUALLY updated on every color change
    var savedColors by remember { mutableStateOf(parameters.savedColors) }
    val setColor = { newColor: Color ->
        color.value = HsvColor.from(newColor)
        hex.value = computeHex(color)
    }
    val lightDarkGradientBrush = remember { Brush.verticalGradient(
        listOf(Color.White, Color.Black),
        endY = 80f,
    ) }
    val lightDarkGradientSmallBrush = remember { Brush.verticalGradient(
        listOf(Color.White, Color.Black),
        endY = 40f,
    ) }
    val maxColorsPerRow = 10
    val paletteRowModifier = Modifier
        .padding(12.dp)
        .border(2.dp, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.shapes.medium)
//        .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
        .padding(8.dp)
    val swatchBgModifier = Modifier
        .padding(4.dp)
//        .background(
//            lightDarkGradientSmallBrush,
//            CircleShape,
////            alpha = 0.7f
//        )
    val splashIconModifier = Modifier
        .size(48.dp)
    val onConfirm0 = {
        onConfirm(
            parameters.copy(
                currentColor = color.value.toColor(),
                savedColors = savedColors,
            )
        ) // that is how it be, out-of-dialog tap
    }
    Dialog(
        onDismissRequest = onConfirm0, // that is how it be, out-of-dialog tap
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        hideSystemBars()
        Surface(
            modifier = modifier
                .padding(16.dp)
//                .fillMaxSize()
            ,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier
//                    .fillMaxSize()
                ,
//                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DialogTitle(Res.string.color_picker_title, modifier = Modifier.align(Alignment.CenterHorizontally))
                Row() {
                    ColorPickerDisplay(
                        color,
                        Modifier
                            .fillMaxHeight(0.8f)
                        ,
                        onColorChanged = { hex.value = computeHex(color) }
                    )
                    Column(
                        Modifier.padding(vertical = 12.dp)
                    ) {
                        Box(
                            Modifier
                                .background(lightDarkGradientBrush, MaterialTheme.shapes.medium)
                                .padding(12.dp)
                                .padding(end = 32.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(parameters.currentColor)
                                    .clickable { setColor(parameters.currentColor) }
                            ) {}
                            Box(
                                Modifier
                                    .offset(x = 32.dp)
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(color.value.toColor())
                                    .clickable(enabled = false, onClick = {}) // blocks thru-clicks
                            ) {}
                        }
                        // add icons/explanation for color-palette rows
                        FlowRow(
                            paletteRowModifier,
                            maxItemsInEachRow = maxColorsPerRow,
                        ) {
                            for (clr in parameters.usedColors) {
                                SimpleButton(
                                    painterResource(Res.drawable.paint_splash),
                                    "used color",
                                    swatchBgModifier,
                                    splashIconModifier,
//                                    containerColor = bgColorFor(clr),
                                    tint = clr,
                                ) { setColor(clr) }
                            }
                        }
                        FlowRow(
                            paletteRowModifier,
                            verticalArrangement = Arrangement.Center,
                            maxItemsInEachRow = maxColorsPerRow,
                        ) {
                            SimpleButton(
                                painterResource(Res.drawable.add_circle),
                                "save color",
                            ) {
                                val c = color.value.toColor()
                                if (c !in savedColors)
                                    savedColors += c
                            }
                            for (clr in savedColors.reversed()) {
                                SimpleButton(
                                    painterResource(Res.drawable.paint_splash),
                                    "saved color",
                                    swatchBgModifier,
                                    splashIconModifier,
//                                    containerColor = bgColorFor(clr),
                                    tint = clr,
                                ) { setColor(clr) }
                            }
                        }
                        FlowRow(
                            paletteRowModifier.verticalScroll(rememberScrollState()),
                            maxItemsInEachRow = maxColorsPerRow,
                        ) {
                            for (clr in parameters.predefinedColors) {
                                SimpleButton(
                                    painterResource(Res.drawable.paint_splash),
                                    "predefined color",
                                    swatchBgModifier,
                                    splashIconModifier,
//                                    containerColor = bgColorFor(clr),
                                    tint = clr,
                                ) { setColor(clr) }
                            }
                        }
                    }
                }
                Row(
                    Modifier
                        .weight(1f)
                        .requiredHeightIn(50.dp, 100.dp)
                    ,
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HexInput(color, hex, onConfirm = onConfirm0)
                    CancelButton(onDismissRequest = onCancel)
                    OkButton(onConfirm = onConfirm0)
                }
            }
        }
    }
}

private fun bgColorFor(color: Color): Color =
    if (color.luminance() > 0.2)
        DodeclustersColors.darkestGray
    else DodeclustersColors.lightestWhite


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
        shape = MaterialTheme.shapes.extraLarge,
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
        shape = MaterialTheme.shapes.extraLarge,
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
        shape = MaterialTheme.shapes.large,
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

/**
 * @param[onConfirm] shortcut confirm exit lambda
 */
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
            val hexText = new.text.let {
                if (it.isNotEmpty() && it[0] == '#')
                    it.drop(1) // drop leading '#'
                else it
            }
            if (hexText.length == 6) // primitive hex validation
                try {
                    val rgb = RGB(hexText)
                    color.value = HsvColor.from(Color(rgb.r, rgb.g, rgb.b))
                    isError = false
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                    println("cannot parse hex string \"$hexText\"")
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