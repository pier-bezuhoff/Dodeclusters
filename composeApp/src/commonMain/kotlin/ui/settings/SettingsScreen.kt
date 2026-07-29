package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.back
import dodeclusters.composeapp.generated.resources.reset_settings
import org.jetbrains.compose.resources.painterResource
import ui.theme.ColorTheme
import ui.theme.DodeclustersTheme

@Composable
fun SettingsScreenRoot(
    close: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val colorThemesData: List<ColorThemeData> by viewModel.colorThemesDataFlow.collectAsStateWithLifecycle()
    SettingsScreen(
        close = close,
        resetSettings = viewModel::resetSettings,
        setColorTheme = viewModel::setColorTheme,
        colorThemesData = colorThemesData,
    )
}

@Composable
private fun SettingsScreen(
    close: () -> Unit,
    resetSettings: () -> Unit,
    setColorTheme: (ColorTheme) -> Unit,
    colorThemesData: List<ColorThemeData>,
) {
    Scaffold(
        topBar = {
            TopBar(
                close = close,
                resetSettings = resetSettings,
            )
        }
    ) { paddingValues ->
        Surface(Modifier.padding(paddingValues)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                // chapters rail to the left?
                // display
                ColorThemeSwitch(
                    setColorTheme = setColorTheme,
                    colorThemesData = colorThemesData,
                )
                // snapping toggles (magnet icon)
            }
        }
    }
}

@Composable
private fun TopBar(
    close: () -> Unit,
    resetSettings: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = {
            Text("Settings",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = close,
            ) {
                Icon(painterResource(Res.drawable.back), "close")
            }
        },
        actions = {
            Button(
                onClick = resetSettings,
                colors = ButtonDefaults.buttonColors().copy(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Icon(painterResource(Res.drawable.reset_settings), "reset settings")
                    Spacer(Modifier.width(8.dp))
                    Text("Reset to defaults",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    )
}

@Composable
private fun ColumnScope.ColorThemeSwitch(
    setColorTheme: (ColorTheme) -> Unit,
    colorThemesData: List<ColorThemeData>,
) {
    Text("Color theme",
        style = MaterialTheme.typography.titleSmall,
    )
    // add dark mode icon
    Spacer(Modifier.height(4.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup()
        ,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colorThemesData.forEach { (enabled, description, colorTheme) ->
            Row(
                Modifier
                    .wrapContentWidth()
                    .selectable(
                        selected = enabled,
                        onClick = {
                            setColorTheme(colorTheme)
                        },
                        role = Role.RadioButton,
                    )
                    .padding(horizontal = 8.dp)
                ,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = enabled,
                    enabled = true,
                    onClick = null,
                )
                Spacer(Modifier.width(4.dp))
                Text(description,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    DodeclustersTheme {
        SettingsScreen(
            close = {},
            resetSettings = {},
            setColorTheme = {},
            colorThemesData = SettingsViewModel.DEFAULT_COLOR_THEMES_DATA,
        )
    }
}