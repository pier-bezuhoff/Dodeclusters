package ui

import androidx.compose.material3.DismissibleDrawerSheet
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.open_file
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Preview
@Composable
fun MyDrawer(modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Open)
    ModalNavigationDrawer(
        drawerContent = {
//            DismissibleDrawerSheet {  }
            ModalDrawerSheet(drawerState) {
                NavigationDrawerItem(
                    label = { Text("item1") },
                    selected = false,
                    onClick = {
                        println("item1 clicked")
                        coroutineScope.launch {
                            drawerState.close()
                        }
                    }
                )
            }
        },
        drawerState = drawerState
    ) {
        // scaffold
        Text("Body")
        SimpleButton(
            painterResource(Res.drawable.open_file),
            "open"
        ) {
            println("open clicked")
            coroutineScope.launch {
                drawerState.open()
            }
        }
    }
}
