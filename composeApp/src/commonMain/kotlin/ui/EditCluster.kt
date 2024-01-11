package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.BottomAppBar
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.IconToggleButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

@Composable
fun EditCluster() {
    val scaffoldState = rememberScaffoldState()
    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TopBar({}, {})
        },
        bottomBar = {
            BottomBar({}, {}, {})
        },
    ) { inPaddings ->
        Text(
            "Hello Hi",
            modifier = Modifier.padding(inPaddings)
        )
    }
}

@Composable
private fun TopBar(
    onSaveAndGoBack: () -> Unit,
    onCancelAndGoBack: () -> Unit,
) {
    TopAppBar(
        title = { Text("Edit cluster") },
        navigationIcon = {
            IconButton(onClick = onSaveAndGoBack) {
                Icon(Icons.Default.Done, contentDescription = "Done")
            }
        },
        actions = {
            IconButton(onClick = onCancelAndGoBack) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
        }
    )
}

enum class SelectionMode {
    DRAG, MULTISELECT
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun BottomBar(
    onNewCircle: () -> Unit,
    onCopyCircles: () -> Unit,
    onDeleteCircles: () -> Unit,
) {
    var selectionMode by remember { mutableStateOf(SelectionMode.DRAG) }
    BottomAppBar {
        IconButton(onClick = onNewCircle) {
            Icon(Icons.Default.AddCircle, contentDescription = "new circle")
        }
        IconButton(onClick = onCopyCircles) {
            Icon(painterResource("icons/copy.xml"), contentDescription = "copy circle(s)")
        }
        IconButton(onClick = onDeleteCircles) {
            Icon(Icons.Default.Delete, contentDescription = "delete circle(s)")
        }
        Divider(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxHeight()
                .width(4.dp)
        )
        IconToggleButton(
            checked = selectionMode == SelectionMode.DRAG,
            onCheckedChange = {
                selectionMode = SelectionMode.DRAG
            },
            modifier = Modifier
                .background(
                    if (selectionMode == SelectionMode.DRAG)
                        MaterialTheme.colors.primaryVariant
                    else
                        MaterialTheme.colors.primary,
                )
        ) {
            Icon(
                painterResource("icons/drag_mode.xml"),
                contentDescription = "drag mode",
            )
        }
        IconToggleButton(
            checked = selectionMode == SelectionMode.MULTISELECT,
            onCheckedChange = {
                selectionMode = SelectionMode.MULTISELECT
            },
            modifier = Modifier
                .background(
                    if (selectionMode == SelectionMode.MULTISELECT)
                        MaterialTheme.colors.primaryVariant
                    else
                        MaterialTheme.colors.primary,
                )
        ) {
            Icon(
                painterResource("icons/multiselect_mode.xml"),
                contentDescription = "multiselect mode",
            )
        }
    }
}