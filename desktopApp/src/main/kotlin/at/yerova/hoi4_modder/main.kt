package at.yerova.hoi4_modder

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import at.yerova.hoi4_modder.v2.ui.screens.Hoi4ModderV2App

fun main() = application {
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var unsavedSummary by remember { mutableStateOf("") }
    var isCloseRequested by remember { mutableStateOf(false) }

    val windowTitle = remember(hasUnsavedChanges, unsavedSummary) {
        if (hasUnsavedChanges) {
            val details = if (unsavedSummary.isNotBlank()) " [$unsavedSummary]" else ""
            "*Hoi4-Modder – Unsaved Changes$details"
        } else {
            "Hoi4-Modder"
        }
    }

    Window(
        onCloseRequest = {
            if (hasUnsavedChanges) {
                isCloseRequested = true
            } else {
                exitApplication()
            }
        },
        title = windowTitle,
        state = rememberWindowState(width = 1600.dp, height = 940.dp)
    ) {
        Hoi4ModderV2App(
            isCloseRequested = isCloseRequested,
            onDismissCloseRequest = { isCloseRequested = false },
            onExitApplication = ::exitApplication,
            onUnsavedChangesChanged = { dirty, summary ->
                hasUnsavedChanges = dirty
                unsavedSummary = summary
            }
        )
    }

}
