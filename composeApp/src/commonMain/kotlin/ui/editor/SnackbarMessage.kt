package ui.editor

import androidx.compose.material3.SnackbarDuration
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.failed_open_notice
import dodeclusters.composeapp.generated.resources.failed_save_notice
import dodeclusters.composeapp.generated.resources.imaginary_circle_notice
import dodeclusters.composeapp.generated.resources.locked_object_notice
import dodeclusters.composeapp.generated.resources.locked_objects_notice
import dodeclusters.composeapp.generated.resources.overwrite_shared_done
import dodeclusters.composeapp.generated.resources.overwrite_shared_name
import dodeclusters.composeapp.generated.resources.phantom_object_explanation
import dodeclusters.composeapp.generated.resources.placeholder
import dodeclusters.composeapp.generated.resources.share_new_done
import dodeclusters.composeapp.generated.resources.share_new_name
import dodeclusters.composeapp.generated.resources.stub
import dodeclusters.composeapp.generated.resources.successful_save_notice
import org.jetbrains.compose.resources.StringResource

enum class SnackbarMessage(
    val stringResource: StringResource,
    val duration: SnackbarDuration = SnackbarDuration.Short,
) {
    STUB(Res.string.stub, duration = SnackbarDuration.Long),
    /** `$string` */
    PLACEHOLDER(Res.string.placeholder, duration = SnackbarDuration.Long),
    LOCKED_OBJECTS_NOTICE(Res.string.locked_objects_notice),
    LOCKED_OBJECT_NOTICE(Res.string.locked_object_notice),
    IMAGINARY_CIRCLE_NOTICE(Res.string.imaginary_circle_notice),
//    SUCCESSFUL_RESTORE(Res.string.successful_restore_notice),
//    SUCCESSFUL_OPEN(Res.string.stub),
    /** `... "$filename"` */
    FAILED_OPEN(Res.string.failed_open_notice),
    /** `... "$filename"` */
    SUCCESSFUL_SAVE(Res.string.successful_save_notice),
    /** `... "$filename"$error` */
    FAILED_SAVE(Res.string.failed_save_notice),
    PHANTOM_OBJECT_EXPLANATION(Res.string.phantom_object_explanation, duration = SnackbarDuration.Long),
    SUCCESSFUL_SHARE(Res.string.share_new_done),
    SUCCESSFUL_SHARE_OVERWRITE(Res.string.overwrite_shared_done),
}