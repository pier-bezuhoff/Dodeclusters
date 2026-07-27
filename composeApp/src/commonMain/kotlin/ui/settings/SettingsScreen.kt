package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SettingsScreen(
    close: () -> Unit,
) {
    Scaffold() { paddingValues ->
        Surface(Modifier.padding(paddingValues)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                // chapters rail to the left?
                Text("Settings")
                // light / dark / auto theme
                // about
                TextButton(
                    onClick = {
                        close()
                    }
                ) {
                    Text("back")
                }
            }
        }
    }
}