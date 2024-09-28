package ui

import androidx.compose.runtime.Composable

/** De-facto only needed for android since there dialogs re-show systemBars.
 * Usage: call *insides* the content of each Dialog */
@Composable
expect fun hideSystemBars()
