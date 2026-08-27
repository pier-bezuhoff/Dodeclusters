package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.additional_points
import dodeclusters.composeapp.generated.resources.angle_snapping
import dodeclusters.composeapp.generated.resources.auto
import dodeclusters.composeapp.generated.resources.autosave
import dodeclusters.composeapp.generated.resources.autosave_period
import dodeclusters.composeapp.generated.resources.back
import dodeclusters.composeapp.generated.resources.color_theme
import dodeclusters.composeapp.generated.resources.create_additional_points_for_circle_by_3_points
import dodeclusters.composeapp.generated.resources.dark
import dodeclusters.composeapp.generated.resources.dashed_circle_around_point
import dodeclusters.composeapp.generated.resources.drag_pan
import dodeclusters.composeapp.generated.resources.enable_periodic_autosave
import dodeclusters.composeapp.generated.resources.inversion_of_control
import dodeclusters.composeapp.generated.resources.inversion_of_control_level_1
import dodeclusters.composeapp.generated.resources.inversion_of_control_level_infinity
import dodeclusters.composeapp.generated.resources.inversion_of_control_none
import dodeclusters.composeapp.generated.resources.light
import dodeclusters.composeapp.generated.resources.magnet
import dodeclusters.composeapp.generated.resources.motion
import dodeclusters.composeapp.generated.resources.reset_settings
import dodeclusters.composeapp.generated.resources.reset_to_defaults
import dodeclusters.composeapp.generated.resources.save
import dodeclusters.composeapp.generated.resources.settings
import dodeclusters.composeapp.generated.resources.snapping
import dodeclusters.composeapp.generated.resources.sun
import dodeclusters.composeapp.generated.resources.tangent_snapping
import domain.settings.InversionOfControl
import domain.settings.Settings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import ui.theme.ColorTheme
import ui.theme.DodeclustersTheme
import ui.theme.adaptiveTypography
import kotlin.math.ceil
import kotlin.math.roundToInt

@Suppress("ParamsComparedByRef")
@Composable
fun SettingsScreenRoot(
    close: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val settings: Settings by viewModel.settingsFlow.collectAsStateWithLifecycle()
    SettingsScreen(
        settings = settings,
        close = close,
        resetSettings = viewModel::resetSettings,
        setColorTheme = { colorTheme ->
            viewModel.updateSetting { it.copy(
                colorTheme = colorTheme
            ) }
        },
        setEnablePeriodicAutosave = { enable ->
            viewModel.updateSetting { it.copy(
                enablePeriodicAutosave = enable
            ) }
        },
        setAutosavePeriodInSeconds = { period ->
            viewModel.updateSetting { it.copy(
                autosavePeriodInSeconds = period
            ) }
        },
        setInversionOfControl = { inversionOfControl ->
            viewModel.updateSetting { it.copy(
                inversionOfControl = inversionOfControl
            ) }
        },
        setEnableTangentSnapping = { enable ->
            viewModel.updateSetting { it.copy(
                enableTangentSnapping = enable
            ) }
        },
        setEnableAngleSnapping = { enable ->
            viewModel.updateSetting { it.copy(
                enableAngleSnapping = enable
            ) }
        },
        setEnableCreatingAdditionPointsForCircleByCenterAndRadius = { enable ->
            viewModel.updateSetting { it.copy(
                enableCreatingAdditionPointsForCircleByCenterAndRadius = enable
            ) }
        },
        setEnableCreatingAdditionPointsForCircleBy3Points = { enable ->
            viewModel.updateSetting { it.copy(
                enableCreatingAdditionPointsForCircleBy3Points = enable
            ) }
        },
        setEnableCreatingAdditionPointsForLineBy2Points = { enable ->
            viewModel.updateSetting { it.copy(
                enableCreatingAdditionPointsForLineBy2Points = enable
            ) }
        },
    )
}

@Composable
private fun SettingsScreen(
    settings: Settings,
    close: () -> Unit = {},
    resetSettings: () -> Unit = {},
    setColorTheme: (ColorTheme) -> Unit = {},
    setEnablePeriodicAutosave: (Boolean) -> Unit = {},
    setAutosavePeriodInSeconds: (Int) -> Unit = {},
    setInversionOfControl: (InversionOfControl) -> Unit = {},
    setEnableTangentSnapping: (Boolean) -> Unit = {},
    setEnableAngleSnapping: (Boolean) -> Unit = {},
    setEnableCreatingAdditionPointsForCircleByCenterAndRadius: (Boolean) -> Unit = {},
    setEnableCreatingAdditionPointsForCircleBy3Points: (Boolean) -> Unit = {},
    setEnableCreatingAdditionPointsForLineBy2Points: (Boolean) -> Unit = {},
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
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                    ,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceAround,
                ) {
                    // chapters rail to the left?
                    DisplaySettings(
                        colorTheme = settings.colorTheme,
                        setColorTheme = setColorTheme,
                    )
                    CategoryDivider()
                    AutosaveSettings(
                        enablePeriodicAutosave = settings.enablePeriodicAutosave,
                        autosavePeriodInSeconds = settings.autosavePeriodInSeconds,
                        setEnablePeriodicAutosave = setEnablePeriodicAutosave,
                        setAutosavePeriodInSeconds = setAutosavePeriodInSeconds,
                    )
                    CategoryDivider()
                    MotionSettings(
                        inversionOfControl = settings.inversionOfControl,
                        setInversionOfControl = setInversionOfControl,
                    )
                    CategoryDivider()
                    AdditionalPointsSettings(
                        enableCreatingAdditionPointsForCircleByCenterAndRadius = settings.enableCreatingAdditionPointsForCircleByCenterAndRadius,
                        enableCreatingAdditionPointsForCircleBy3Points = settings.enableCreatingAdditionPointsForCircleBy3Points,
                        enableCreatingAdditionPointsForLineBy2Points = settings.enableCreatingAdditionPointsForLineBy2Points,
                        setEnableCreatingAdditionPointsForCircleByCenterAndRadius = setEnableCreatingAdditionPointsForCircleByCenterAndRadius,
                        setEnableCreatingAdditionPointsForCircleBy3Points = setEnableCreatingAdditionPointsForCircleBy3Points,
                        setEnableCreatingAdditionPointsForLineBy2Points = setEnableCreatingAdditionPointsForLineBy2Points,
                    )
                    CategoryDivider()
                    SnapSettings(
                        enableTangentSnapping = settings.enableTangentSnapping,
                        enableAngleSnapping = settings.enableAngleSnapping,
                        setEnableTangentSnapping = setEnableTangentSnapping,
                        setEnableAngleSnapping = setEnableAngleSnapping,
                    )
                }
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
private fun CategoryDivider() {
    HorizontalDivider(Modifier
        .fillMaxWidth(0.8f)
        .padding(24.dp)
    )
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                // default is 24, 8
                contentPadding = PaddingValues(16.dp, 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Icon(painterResource(Res.drawable.reset_settings), "reset settings")
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.reset_to_defaults),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
private fun ColumnScope.DisplaySettings(
    colorTheme: ColorTheme,
    setColorTheme: (ColorTheme) -> Unit,
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
    CategoryTitle(
        Res.drawable.sun,
        Res.string.color_theme
    )
    FlowRow(
        Modifier
            .fillMaxWidth()
            .selectableGroup()
        ,
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
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

private val sliderColorsPrimary: SliderColors
    @Composable /*@ReadOnlyComposable*/ get() =
        SliderDefaults.colors( // .colors is not marked with read-only...
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTickColor = MaterialTheme.colorScheme.primary,
            activeTickColor = MaterialTheme.colorScheme.primaryContainer,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            // default was [primary x3, secondaryContainer x2]
        )

private object AutosavePeriodSliderDefaults {
    const val MIN = 60f
    const val MAX = 60f*30
    const val DISCRETIZATION = 60f // 1 min
    val RANGE = MIN .. MAX
    val N_DISCRETIZATION_STEPS =
        ceil((MAX - MIN)/DISCRETIZATION).toInt()
}

@Composable
private fun ColumnScope.AutosaveSettings(
    enablePeriodicAutosave: Boolean,
    autosavePeriodInSeconds: Int,
    setEnablePeriodicAutosave: (Boolean) -> Unit,
    setAutosavePeriodInSeconds: (Int) -> Unit,
) {
    var autosavePeriod by remember { mutableStateOf(autosavePeriodInSeconds.toFloat()) }
    CategoryTitle(
        Res.drawable.save,
        Res.string.autosave
    )
    Spacer(Modifier.height(8.dp))
    TextSwitch(
        Res.string.enable_periodic_autosave,
        enablePeriodicAutosave,
        setEnablePeriodicAutosave
    )
    Spacer(Modifier.height(8.dp))
    val autosavePeriodInMinutes = autosavePeriodInSeconds.div(60)
    Text(
        pluralStringResource(
            Res.plurals.autosave_period,
            autosavePeriodInMinutes,
            autosavePeriodInMinutes,
        ),
        style = MaterialTheme.adaptiveTypography.body,
    )
    Spacer(Modifier.height(4.dp))
    Slider(
        value = autosavePeriod,
        onValueChange = {
            autosavePeriod = it
            setAutosavePeriodInSeconds(it.roundToInt())
        },
        modifier = Modifier.widthIn(max = 200.dp),
        enabled = enablePeriodicAutosave,
        valueRange = AutosavePeriodSliderDefaults.RANGE,
        steps = AutosavePeriodSliderDefaults.N_DISCRETIZATION_STEPS - 1,
        colors = sliderColorsPrimary,
    )
}

@Composable
private fun ColumnScope.MotionSettings(
    inversionOfControl: InversionOfControl,
    setInversionOfControl: (InversionOfControl) -> Unit,
) {
    CategoryTitle(
        Res.drawable.drag_pan,
        Res.string.motion
    )
    Text(stringResource(Res.string.inversion_of_control),
        style = MaterialTheme.adaptiveTypography.body,
    )
    Spacer(Modifier.height(8.dp))
    FlowRow(
        Modifier
            .fillMaxWidth()
            .selectableGroup()
        ,
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
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
private fun ColumnScope.AdditionalPointsSettings(
    enableCreatingAdditionPointsForCircleByCenterAndRadius: Boolean,
    enableCreatingAdditionPointsForCircleBy3Points: Boolean,
    enableCreatingAdditionPointsForLineBy2Points: Boolean,
    setEnableCreatingAdditionPointsForCircleByCenterAndRadius: (Boolean) -> Unit,
    setEnableCreatingAdditionPointsForCircleBy3Points: (Boolean) -> Unit,
    setEnableCreatingAdditionPointsForLineBy2Points: (Boolean) -> Unit,
) {
    CategoryTitle(
        Res.drawable.dashed_circle_around_point,
        Res.string.additional_points
    )
//    TextSwitch(
//        Res.string.create_additional_points_for_circle_by_center_and_radius,
//        enableCreatingAdditionPointsForCircleByCenterAndRadius,
//        setEnableCreatingAdditionPointsForCircleByCenterAndRadius
//    )
//    Spacer(Modifier.height(4.dp))
    TextSwitch(
        Res.string.create_additional_points_for_circle_by_3_points,
        enableCreatingAdditionPointsForCircleBy3Points,
        setEnableCreatingAdditionPointsForCircleBy3Points
    )
//    Spacer(Modifier.height(4.dp))
//    TextSwitch(
//        Res.string.create_additional_points_for_line_by_2_points,
//        enableCreatingAdditionPointsForLineBy2Points,
//        setEnableCreatingAdditionPointsForLineBy2Points
//    )
}

@Composable
private fun ColumnScope.SnapSettings(
    enableTangentSnapping: Boolean,
    enableAngleSnapping: Boolean,
    setEnableTangentSnapping: (Boolean) -> Unit,
    setEnableAngleSnapping: (Boolean) -> Unit,
) {
    CategoryTitle(Res.drawable.magnet, Res.string.snapping)
    // snapping toggles (magnet icon)
    TextSwitch(
        Res.string.tangent_snapping,
        enableTangentSnapping, setEnableTangentSnapping
    )
    Spacer(Modifier.height(4.dp))
    TextSwitch(
        Res.string.angle_snapping,
        enableAngleSnapping, setEnableAngleSnapping
    )
    // align snap (arc-path)
    // p2p snap
}

@Composable
private fun CategoryTitle(
    iconResource: DrawableResource,
    descriptionResource: StringResource,
) {
    Row(
        Modifier.padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(iconResource),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(descriptionResource),
            style = MaterialTheme.adaptiveTypography.title,
        )
    }
}

@Composable
private fun TextSwitch(
    descriptionResource: StringResource,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth(0.9f)
        ,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(descriptionResource),
            // doesnt looks great on wide screen but oh well
            Modifier
                .weight(1f, fill = true)
            ,
            style = MaterialTheme.adaptiveTypography.body,
        )
        Spacer(Modifier
            .width(8.dp)
            .weight(0.1f, fill = false)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            Modifier
                .weight(1f, fill = false)
            ,
        )
    }
}
