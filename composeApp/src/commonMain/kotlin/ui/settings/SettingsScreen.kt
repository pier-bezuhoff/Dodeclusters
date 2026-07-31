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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.angle_snapping
import dodeclusters.composeapp.generated.resources.auto
import dodeclusters.composeapp.generated.resources.back
import dodeclusters.composeapp.generated.resources.color_theme
import dodeclusters.composeapp.generated.resources.dark
import dodeclusters.composeapp.generated.resources.drag_pan
import dodeclusters.composeapp.generated.resources.inversion_of_control
import dodeclusters.composeapp.generated.resources.inversion_of_control_level_1
import dodeclusters.composeapp.generated.resources.inversion_of_control_level_infinity
import dodeclusters.composeapp.generated.resources.inversion_of_control_none
import dodeclusters.composeapp.generated.resources.light
import dodeclusters.composeapp.generated.resources.magnet
import dodeclusters.composeapp.generated.resources.motion
import dodeclusters.composeapp.generated.resources.reset_settings
import dodeclusters.composeapp.generated.resources.reset_to_defaults
import dodeclusters.composeapp.generated.resources.settings
import dodeclusters.composeapp.generated.resources.snapping
import dodeclusters.composeapp.generated.resources.sun
import dodeclusters.composeapp.generated.resources.tangent_snapping
import domain.settings.InversionOfControl
import domain.settings.Settings
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.theme.ColorTheme
import ui.theme.DodeclustersTheme
import ui.theme.adaptiveTypography

@Composable
fun SettingsScreenRoot(
    close: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val settings: Settings by viewModel.settingsFlow.collectAsStateWithLifecycle()
    SettingsScreen(
        close = close,
        resetSettings = viewModel::resetSettings,
        setColorTheme = { colorTheme ->
            viewModel.setSetting { it.copy(colorTheme = colorTheme) }
        },
        setInversionOfControl = { inversionOfControl ->
            viewModel.setSetting { it.copy(inversionOfControl = inversionOfControl) }
        },
        setEnableTangentSnapping = { enable ->
            viewModel.setSetting { it.copy(enableTangentSnapping = enable) }
        },
        setEnableAngleSnapping = { enable ->
            viewModel.setSetting { it.copy(enableAngleSnapping = enable) }
        },
        settings = settings,
    )
}

@Composable
private fun SettingsScreen(
    close: () -> Unit = {},
    resetSettings: () -> Unit = {},
    setColorTheme: (ColorTheme) -> Unit = {},
    setInversionOfControl: (InversionOfControl) -> Unit = {},
    setEnableTangentSnapping: (Boolean) -> Unit = {},
    setEnableAngleSnapping: (Boolean) -> Unit = {},
    settings: Settings,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                ,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                // chapters rail to the left?
                DisplaySettings(
                    setColorTheme = setColorTheme,
                    colorTheme = settings.colorTheme,
                )
                HorizontalDivider(Modifier.fillMaxWidth(0.8f).padding(16.dp))
                MotionSettings(
                    setInversionOfControl = setInversionOfControl,
                    inversionOfControl = settings.inversionOfControl,
                )
                HorizontalDivider(Modifier.fillMaxWidth(0.8f).padding(16.dp))
                SnapSettings(
                    setEnableTangentSnapping = setEnableTangentSnapping,
                    setEnableAngleSnapping = setEnableAngleSnapping,
                    enableTangentSnapping = settings.enableTangentSnapping,
                    enableAngleSnapping = settings.enableAngleSnapping,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    DodeclustersTheme(ColorTheme.DARK) {
        SettingsScreen(
            settings = Settings(),
        )
    }
}

@Composable
private fun TopBar(
    close: () -> Unit,
    resetSettings: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = {
            Text(stringResource(Res.string.settings),
                style = MaterialTheme.adaptiveTypography.title,
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
                    Text(stringResource(Res.string.reset_to_defaults),
                        style = MaterialTheme.adaptiveTypography.label,
                    )
                }
            }
        }
    )
}

@Composable
private fun ColumnScope.DisplaySettings(
    setColorTheme: (ColorTheme) -> Unit,
    colorTheme: ColorTheme,
) {
    ColorThemeSwitch(setColorTheme, colorTheme)
}

@Composable
private fun ColumnScope.ColorThemeSwitch(
    setColorTheme: (ColorTheme) -> Unit,
    colorTheme: ColorTheme,
) {
    val colorThemes = remember { listOf(
        ColorTheme.LIGHT to Res.string.light,
        ColorTheme.DARK to Res.string.dark,
        ColorTheme.AUTO to Res.string.auto,
    ) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(Res.drawable.sun), null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.color_theme),
            style = MaterialTheme.adaptiveTypography.title,
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup()
        ,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colorThemes.forEach { (colorTheme0, descriptionResource) ->
            val enabled = colorTheme == colorTheme0
            Row(
                Modifier
                    .wrapContentWidth()
                    .selectable(
                        selected = enabled,
                        onClick = { setColorTheme(colorTheme0) },
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
                Text(stringResource(descriptionResource),
                    style = MaterialTheme.adaptiveTypography.body,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.MotionSettings(
    setInversionOfControl: (InversionOfControl) -> Unit,
    inversionOfControl: InversionOfControl,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(Res.drawable.drag_pan), null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.motion),
            style = MaterialTheme.adaptiveTypography.title,
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(Res.string.inversion_of_control),
        style = MaterialTheme.adaptiveTypography.body,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup()
        ,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            InversionOfControl.NONE to Res.string.inversion_of_control_none,
            InversionOfControl.LEVEL_1 to Res.string.inversion_of_control_level_1,
            InversionOfControl.LEVEL_INFINITY to Res.string.inversion_of_control_level_infinity,
        ).forEach { (inversionOfControl0, descriptionResource) ->
            val enabled = inversionOfControl == inversionOfControl0
            Row(
                Modifier
                    .wrapContentWidth()
                    .selectable(
                        selected = enabled,
                        onClick = { setInversionOfControl(inversionOfControl0) },
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
                Text(stringResource(descriptionResource),
                    style = MaterialTheme.adaptiveTypography.body,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SnapSettings(
    setEnableTangentSnapping: (Boolean) -> Unit,
    setEnableAngleSnapping: (Boolean) -> Unit,
    enableTangentSnapping: Boolean,
    enableAngleSnapping: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(Res.drawable.magnet), null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.snapping),
            style = MaterialTheme.adaptiveTypography.title,
        )
    }
    Spacer(Modifier.height(12.dp))
    // snapping toggles (magnet icon)
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.tangent_snapping),
            style = MaterialTheme.adaptiveTypography.body,
        )
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = enableTangentSnapping,
            onCheckedChange = setEnableTangentSnapping,
        )
    }
    Spacer(Modifier.height(4.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.angle_snapping),
            style = MaterialTheme.adaptiveTypography.body,
        )
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = enableAngleSnapping,
            onCheckedChange = setEnableAngleSnapping,
        )
    }
    // align snap (arc-path)
    // p2p snap
}