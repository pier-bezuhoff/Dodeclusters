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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.github.ajalt.colormath.RenderCondition
import com.github.ajalt.colormath.model.RGB
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.cancel
import dodeclusters.composeapp.generated.resources.cancel_name
import dodeclusters.composeapp.generated.resources.color_picker_title
import dodeclusters.composeapp.generated.resources.confirm
import dodeclusters.composeapp.generated.resources.hello
import dodeclusters.composeapp.generated.resources.hex_name
import dodeclusters.composeapp.generated.resources.ok_description
import dodeclusters.composeapp.generated.resources.ok_name
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.colorpicker.ClassicColorPicker
import ui.colorpicker.HsvColor

// BUG: cancel/ok buttons look bad in mobile/landscape
@OptIn(ExperimentalResourceApi::class)
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
    var hex by mutableStateOf(computeHex(color)) // need to be manually updated on every color's change
    Dialog(onDismissRequest = {
//        onDismissRequest()
        onConfirm(color.value.toColor()) // thats how it be
    }) {
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
                Text(
                    text = stringResource(Res.string.color_picker_title),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                ClassicColorPicker(
                    Modifier
                        .fillMaxHeight(0.7f)
                        .padding(16.dp),
                    colorPickerValueState = color,
                    showAlphaBar = false, // MAYBE: add alpha someday
                ) {
                    // $color is updated internally by the ColorPicker
                    hex = computeHex(color)
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { new ->
                        hex = new
                        if (new.text.length == 6) // primitive hex validation
                            try {
                                val rgb = RGB(new.text)
                                color.value = HsvColor.from(Color(rgb.r, rgb.g, rgb.b))
                            } catch (e: IllegalArgumentException) {
                                e.printStackTrace()
                                println("cannot parse hex string \"${new.text}\"")
                            }
                    },
//                    textStyle = TextStyle(fontSize = 16.sp),
                    label = { Text(stringResource(Res.string.hex_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions( // smart ass enter capturing
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onConfirm(color.value.toColor()) }
                    ),
                    modifier = Modifier.onKeyEvent {
                        if (it.key == Key.Enter) {
                            onConfirm(color.value.toColor())
                            true
                        } else false
                    }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .align(Alignment.Start)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    OutlinedButton(
                        onClick = { onDismissRequest() },
                        modifier = Modifier.padding(8.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(50), // = 50% percent or shape = CircleShape
                    ) {
                        Icon(painterResource(Res.drawable.cancel), stringResource(Res.string.cancel_name))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(Res.string.cancel_name), fontSize = 24.sp)
                    }
                    Button(
                        onClick = { onConfirm(color.value.toColor()) },
                        modifier = Modifier.padding(8.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(50), // = 50% percent or shape = CircleShape
                    ) {
                        Icon(painterResource(Res.drawable.confirm), stringResource(Res.string.ok_description))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(Res.string.ok_name), fontSize = 24.sp)
                    }
                }
            }
        }
    }
}
