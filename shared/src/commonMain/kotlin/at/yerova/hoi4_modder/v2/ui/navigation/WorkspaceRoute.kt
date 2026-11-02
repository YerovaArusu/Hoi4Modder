package at.yerova.hoi4_modder.v2.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface WorkspaceRoute : NavKey

@Serializable
data object MapWorkspaceRoute : WorkspaceRoute

@Serializable
data object FocusTreeWorkspaceRoute : WorkspaceRoute

@Immutable
data class StudioViewDescriptor(
    val route: WorkspaceRoute,
    val defaultTitle: String,
    val iconName: String
)

val AvailableStudioViews: List<StudioViewDescriptor> = listOf(
    StudioViewDescriptor(
        route = MapWorkspaceRoute,
        defaultTitle = "Map Studio",
        iconName = "map"
    ),
    StudioViewDescriptor(
        route = FocusTreeWorkspaceRoute,
        defaultTitle = "Focus Tree Studio",
        iconName = "account_tree"
    )
)

fun WorkspaceRoute.descriptor(): StudioViewDescriptor =
    AvailableStudioViews.firstOrNull { it.route == this }
        ?: StudioViewDescriptor(this, "View", "tab")