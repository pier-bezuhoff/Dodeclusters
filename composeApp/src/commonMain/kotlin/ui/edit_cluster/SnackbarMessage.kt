package ui.edit_cluster

import androidx.compose.material3.SnackbarDuration
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.failed_open_notice
import dodeclusters.composeapp.generated.resources.failed_save_notice
import dodeclusters.composeapp.generated.resources.imaginary_circle_notice
import dodeclusters.composeapp.generated.resources.locked_object_notice
import dodeclusters.composeapp.generated.resources.locked_objects_notice
import dodeclusters.composeapp.generated.resources.successful_save_notice
import org.jetbrains.compose.resources.StringResource

enum class SnackbarMessage(
    /** Since `getString` breaks Windows/Chrome (ticket https://youtrack.jetbrains.com/issue/CMP-6930/Using-getString-method-causing-JsException),
     *  we resort to hardcoding... */
    val string: String,
    val stringResource: StringResource,
    val duration: SnackbarDuration = SnackbarDuration.Short,
) {
    LOCKED_OBJECTS_NOTICE("Locked", Res.string.locked_objects_notice),
    LOCKED_OBJECT_NOTICE("Locked", Res.string.locked_object_notice),
    IMAGINARY_CIRCLE_NOTICE("Imaginary circle created", Res.string.imaginary_circle_notice),
//    SUCCESSFUL_RESTORE(Res.string.successful_restore_notice),
//    SUCCESSFUL_OPEN(Res.string.stub),
    FAILED_OPEN("Failed to open", Res.string.failed_open_notice),
    SUCCESSFUL_SAVE("Saved as", Res.string.successful_save_notice),
    FAILED_SAVE("Failed to save", Res.string.failed_save_notice),
}