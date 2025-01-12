package ui.edit_cluster

import androidx.compose.material3.SnackbarDuration
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.locked_object_notice
import dodeclusters.composeapp.generated.resources.locked_objects_notice
import org.jetbrains.compose.resources.StringResource

enum class SnackbarMessage(
    val stringResource: StringResource,
    val duration: SnackbarDuration = SnackbarDuration.Short,
) {
    LOCKED_OBJECTS_NOTICE(Res.string.locked_objects_notice),
    LOCKED_OBJECT_NOTICE(Res.string.locked_object_notice),
}