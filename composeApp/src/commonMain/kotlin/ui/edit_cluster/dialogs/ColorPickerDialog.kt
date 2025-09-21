package ui.edit_cluster.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.ajalt.colormath.RenderCondition
import com.github.ajalt.colormath.model.RGB
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.add_circle
import dodeclusters.composeapp.generated.resources.color_picker_default_palette_label
import dodeclusters.composeapp.generated.resources.color_picker_forget_color_description
import dodeclusters.composeapp.generated.resources.color_picker_save_color_description
import dodeclusters.composeapp.generated.resources.color_picker_title
import dodeclusters.composeapp.generated.resources.color_picker_used_and_saved_colors_label
import dodeclusters.composeapp.generated.resources.delete_forever
import dodeclusters.composeapp.generated.resources.hex_hash
import dodeclusters.composeapp.generated.resources.paint_splash
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.CancelButton
import ui.DialogTitle
import ui.OkButton
import ui.SimpleButton
import ui.TwoIconButtonWithTooltip
import ui.colorpicker.ClassicColorPicker
import ui.colorpicker.HsvColor
import ui.hideSystemBars
import ui.isCompact
import ui.isExpanded
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
        // RGB & CMY[-K] -> R Y G C B M
        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta,
        // fun colors
        DodeclustersColors.peachyPink,
        DodeclustersColors.pinkishRed,
        DodeclustersColors.venousBloodRed,
        DodeclustersColors.deepBrown,
        DodeclustersColors.orange,
        DodeclustersColors.orangeOrange,
        DodeclustersColors.goldenBananaYellow,
        DodeclustersColors.veryDarkForestyGreen,
        DodeclustersColors.aquamarine,
        DodeclustersColors.teal,
        DodeclustersColors.darkPurple,
        // UI colors (not sure it's a good idea... no contrast)
        DodeclustersColors.skyBlue,
        DodeclustersColors.primaryDark, DodeclustersColors.primaryLight,
        DodeclustersColors.secondaryDark, DodeclustersColors.secondaryLight,
        // DodeclustersColors.tertiaryDark, DodeclustersColors.tertiaryLight,
        // tertiary and highAccent are the same, BUT highAccent is _actually_ used
        DodeclustersColors.highAccentDark, DodeclustersColors.highAccentLight,
    ),
)

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ColorPickerDialog(
    parameters: ColorPickerParameters,
    onCancel: () -> Unit,
    onConfirm: (ColorPickerParameters) -> Unit,
    dialogActions: SharedFlow<DialogAction>? = null,
) {
    val colorState = rememberSaveable(parameters, stateSaver = HsvColor.Saver) {
        mutableStateOf(HsvColor.from(parameters.currentColor))
    }
    val color = colorState.value.toColor()
    val savedColorsState = remember(parameters) { mutableStateOf(parameters.savedColors) }
    var savedColors by savedColorsState
    val setColor = remember(colorState) { { newColor: Color ->
        colorState.value = HsvColor.from(newColor)
    } }
    val newUnsavedColor = color !in savedColors
    val lightDarkVerticalGradientBrush = remember { Brush.verticalGradient(
        0.1f to Color.White,
        0.9f to Color.Black,
    ) } // to grasp how the color looks in different contexts
    val lightDarkHorizontalGradientBrush = remember { Brush.horizontalGradient(
        0.1f to Color.White,
        0.9f to Color.Black,
    ) }
    val windowSizeClass = calculateWindowSizeClass()
    val isCompact = windowSizeClass.isCompact
    val isMedium = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium && windowSizeClass.heightSizeClass == WindowHeightSizeClass.Medium
    val isExpanded = windowSizeClass.isExpanded
    val isLandscape = windowSizeClass.isLandscape
    val maxColorsPerRowLandscape = 11
    val maxColorsPerRowPortrait = if (isMedium) 8 else 6
    val fontSize =
        if (isCompact) 14.sp
        else 24.sp
    val paletteModifier = Modifier
        .padding(4.dp)
        .border(2.dp, MaterialTheme.colorScheme.secondary, MaterialTheme.shapes.medium)
//        .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
    val swatchBgModifier = Modifier
        .padding(4.dp)
        .size(
            if (isCompact) 30.dp
            else if (isExpanded) 60.dp
            else 45.dp
        )
    val splashIconModifier = Modifier
        .size(
            if (isCompact) 24.dp
            else if (isExpanded) 40.dp
            else 32.dp
        )
    val onConfirm0 = remember(parameters) { {
        onConfirm(
            parameters.copy(
                // important that we capture states in the closure
                // otherwise changing values would invalidate this lambda
                // and the lambda captured by LaunchedEffect would be outdated one
                // that uses outdated values (i think)
                currentColor = colorState.value.toColor(),
                savedColors = savedColorsState.value,
            )
        )
    } }
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        hideSystemBars()
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            if (isLandscape) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DialogTitle(
                        Res.string.color_picker_title,
                        smallerFont = isCompact,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Row() {
                        Column(horizontalAlignment = Alignment.End) {
                            ColorPickerDisplay(
                                colorState,
                                Modifier.fillMaxHeight(0.8f),
                            )
                            Row(
                                Modifier
                                    .requiredHeightIn(50.dp, 100.dp) // desperation constraint
                                ,
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                HexInput(
                                    color,
                                    setColor = { colorState.value = HsvColor.from(it) },
                                    onConfirm = onConfirm0
                                )
                                CancelButton(fontSize, onDismissRequest = onCancel)
                                OkButton(fontSize, onConfirm = onConfirm0)
                            }
                        }
                        Column(
                            Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(top = 12.dp, end = 8.dp),
                        ) {
                            Box(
                                Modifier
                                    .padding(start = 4.dp, bottom = 8.dp) // outer offset
                                    .background(
                                        lightDarkVerticalGradientBrush,
                                        MaterialTheme.shapes.medium
                                    )
                                    .padding(12.dp)
                                    .padding(end = 40.dp) // adjust for 2nd circle-box offset
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
                                        .offset(x = 40.dp)
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable(enabled = false, onClick = {}) // blocks thru-clicks
                                ) {}
                            }
                            Text(
                                text = stringResource(Res.string.color_picker_used_and_saved_colors_label),
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .offset(y = 4.dp)
                                ,
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            FlowRow(
                                paletteModifier,
                                verticalArrangement = Arrangement.Center,
                                maxItemsInEachRow = maxColorsPerRowLandscape,
                            ) {
                                for (clr in parameters.usedColors) {
                                    SimpleButton(
                                        painterResource(Res.drawable.paint_splash),
                                        "used color",
                                        swatchBgModifier.align(Alignment.CenterVertically),
                                        splashIconModifier,
                                        contentColor = clr,
                                    ) { setColor(clr) }
                                }
                                TwoIconButtonWithTooltip(
                                    iconPainter = painterResource(Res.drawable.add_circle),
                                    disabledIconPainter = painterResource(Res.drawable.delete_forever),
                                    description = stringResource(Res.string.color_picker_save_color_description),
                                    disabledDescription = stringResource(Res.string.color_picker_forget_color_description),
                                    enabled = newUnsavedColor,
                                    positionModifier = Modifier.align(Alignment.CenterVertically),
                                    contentColor = MaterialTheme.colorScheme.secondary
                                ) {
                                    if (newUnsavedColor)
                                        savedColors += color
                                    else
                                        savedColors -= color
                                }
                                for (clr in savedColors.reversed()) { // newest-to-oldest
                                    SimpleButton(
                                        painterResource(Res.drawable.paint_splash),
                                        "saved color",
                                        swatchBgModifier.align(Alignment.CenterVertically),
                                        splashIconModifier,
                                        contentColor = clr,
                                    ) { setColor(clr) }
                                }
                            }
                            Text(
                                text = stringResource(Res.string.color_picker_default_palette_label),
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .offset(y = 4.dp)
                                ,
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            FlowRow(
                                paletteModifier,
                                maxItemsInEachRow = maxColorsPerRowLandscape,
                            ) {
                                for (clr in parameters.predefinedColors) {
                                    SimpleButton(
                                        painterResource(Res.drawable.paint_splash),
                                        "predefined color",
                                        swatchBgModifier,
                                        splashIconModifier,
                                        contentColor = clr,
                                    ) { setColor(clr) }
                                }
                            }
                        }
                    }
                }
            } else { // portrait
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                    ,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DialogTitle(
                        Res.string.color_picker_title,
                        smallerFont = isCompact,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    ColorPickerDisplay(
                        colorState,
                        Modifier.fillMaxWidth(
                            if (isMedium) 0.6f else 0.8f
                        ),
                    )
                    Row() {
                        Box(
                            Modifier
                                .padding(top = 4.dp, start = 4.dp, end = 8.dp)
                                .background(lightDarkHorizontalGradientBrush, MaterialTheme.shapes.medium)
                                .padding(12.dp)
                                .padding(bottom = 40.dp) // adjust for 2nd circle-box offset
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
                                    .offset(y = 40.dp)
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable(enabled = false, onClick = {}) // blocks thru-clicks
                            ) {}
                        }
                        Column() {
                            Text(
                                text = stringResource(Res.string.color_picker_used_and_saved_colors_label),
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .offset(y = 4.dp)
                                ,
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            FlowRow(
                                paletteModifier,
                                verticalArrangement = Arrangement.Center,
                                maxItemsInEachRow = maxColorsPerRowPortrait,
                            ) {
                                for (clr in parameters.usedColors) {
                                    SimpleButton(
                                        painterResource(Res.drawable.paint_splash),
                                        "used color",
                                        swatchBgModifier.align(Alignment.CenterVertically),
                                        splashIconModifier,
                                        contentColor = clr,
                                    ) { setColor(clr) }
                                }
                                TwoIconButtonWithTooltip(
                                    iconPainter = painterResource(Res.drawable.add_circle),
                                    disabledIconPainter = painterResource(Res.drawable.delete_forever),
                                    description = stringResource(Res.string.color_picker_save_color_description),
                                    disabledDescription = stringResource(Res.string.color_picker_forget_color_description),
                                    enabled = newUnsavedColor,
                                    positionModifier = Modifier.align(Alignment.CenterVertically),
                                    contentColor = MaterialTheme.colorScheme.secondary
                                ) {
                                    if (newUnsavedColor)
                                        savedColors += color
                                    else
                                        savedColors -= color
                                }
                                for (clr in savedColors.reversed()) { // newest-to-oldest
                                    SimpleButton(
                                        painterResource(Res.drawable.paint_splash),
                                        "saved color",
                                        swatchBgModifier.align(Alignment.CenterVertically),
                                        splashIconModifier,
                                        contentColor = clr,
                                    ) { setColor(clr) }
                                }
                            }
                            Text(
                                text = stringResource(Res.string.color_picker_default_palette_label),
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .offset(y = 4.dp)
                                ,
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            FlowRow(
                                paletteModifier,
                                maxItemsInEachRow = maxColorsPerRowPortrait,
                            ) {
                                for (clr in parameters.predefinedColors) {
                                    SimpleButton(
                                        painterResource(Res.drawable.paint_splash),
                                        "predefined color",
                                        swatchBgModifier,
                                        splashIconModifier,
                                        contentColor = clr,
                                    ) { setColor(clr) }
                                }
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HexInput(
                            color,
                            setColor = { colorState.value = HsvColor.from(it) },
                            onConfirm = onConfirm0,
                        )
                        CancelButton(fontSize, noText = isCompact, onDismissRequest = onCancel)
                        OkButton(fontSize, onConfirm = onConfirm0)
                    }
                }
            }
        }
    }
    LaunchedEffect(dialogActions, parameters) {
        dialogActions?.collect { dialogAction ->
            when (dialogAction) {
                DialogAction.DISMISS -> onCancel()
                DialogAction.CONFIRM -> onConfirm0()
            }
        }
    }
}

// looks awkward
private fun bgColorFor(color: Color): Color =
    if (color.luminance() > 0.2)
        DodeclustersColors.darkestGray
    else DodeclustersColors.lightestWhite


private fun computeHexTFV(color: Color): TextFieldValue {
    val hexString = RGB(color.red, color.green, color.blue)
        .toHex(withNumberSign = false, renderAlpha = RenderCondition.NEVER)
    return TextFieldValue(hexString, TextRange(hexString.length))
}

/**
 * @param[hsvColorState] this state is updated internally by [ClassicColorPicker]
 */
@Composable
private fun ColorPickerDisplay(
    hsvColorState: MutableState<HsvColor>,
    modifier: Modifier = Modifier,
    onColorChanged: () -> Unit = {},
) {
    ClassicColorPicker(
        modifier
            .aspectRatio(1.1f)
            .padding(16.dp)
        ,
        colorPickerValueState = hsvColorState,
        showAlphaBar = false, // MAYBE: add alpha someday
        onColorChanged = { onColorChanged() }
    )
}

/**
 * @param[onConfirm] shortcut confirm exit lambda
 */
@Composable
private fun HexInput(
    color: Color,
    modifier: Modifier = Modifier,
    setColor: (Color) -> Unit,
    onConfirm: () -> Unit,
) {
    var hexTFV by remember(color) {
        mutableStateOf(computeHexTFV(color))
    }
    val windowInfo = LocalWindowInfo.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var isError by remember(color) { mutableStateOf(false) }
    OutlinedTextField(
        value = hexTFV,
        onValueChange = { new ->
            hexTFV = new
            val hexString = new.text.let {
                if (it.isNotEmpty() && it[0] == '#')
                    it.drop(1) // drop leading '#'
                else it
            }
            if (hexString.length == 6) { // primitive hex validation
                try {
                    val rgb = RGB(hexString)
                    setColor(Color(rgb.r, rgb.g, rgb.b))
                    isError = false
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                    println("cannot parse hex string \"$hexString\"")
                    isError = true
                }
            } else {
                isError = true
            }
        },
//                    textStyle = TextStyle(fontSize = 16.sp),
        label = { Text(stringResource(Res.string.hex_hash)) },
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
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // overrides min width of 280.dp defined for TextField
            .widthIn(50.dp, 100.dp)
        ,
//        colors = OutlinedTextFieldDefaults.colors()
//            .copy(unfocusedContainerColor = color.value.toColor())
    )
    // NOTE: this (no focus by default on Android) fix only works 90% of time...
    // reference: https://stackoverflow.com/q/71412537/7143065
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.collect { isWindowFocused ->
            if (isWindowFocused) { // runs once every time the dialog is opened
                focusRequester.freeFocus()
//                focusRequester.requestFocus(FocusDirection.Exit)
                keyboard?.hide() // suppresses rare auto-showing keyboard bug
            }
        }
    }
}