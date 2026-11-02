package at.yerova.hoi4_modder.v2.ui.state

import androidx.compose.runtime.mutableStateListOf
import at.yerova.hoi4_modder.v2.ui.navigation.FocusTreeWorkspaceRoute
import at.yerova.hoi4_modder.v2.ui.navigation.MapWorkspaceRoute
import at.yerova.hoi4_modder.v2.ui.navigation.WorkspaceRoute

class WorkspaceTabManager(
    initialTabs: List<WorkspaceRoute> = listOf(MapWorkspaceRoute, FocusTreeWorkspaceRoute),
    initialActive: WorkspaceRoute = MapWorkspaceRoute
) {
    val openTabs = mutableStateListOf<WorkspaceRoute>().apply {
        addAll(initialTabs.distinct())
    }

    val navBackStack = mutableStateListOf<WorkspaceRoute>().apply {
        addAll(initialTabs.filter { it != initialActive })
        if (initialActive in initialTabs) {
            add(initialActive)
        }
    }

    val activeTab: WorkspaceRoute?
        get() = navBackStack.lastOrNull()

    fun isTabOpen(route: WorkspaceRoute): Boolean = route in openTabs

    fun openOrFocusTab(route: WorkspaceRoute) {
        if (route !in openTabs) {
            openTabs.add(route)
        }
        if (navBackStack.lastOrNull() != route) {
            navBackStack.remove(route)
            navBackStack.add(route)
        }
    }

    fun closeTab(route: WorkspaceRoute) {
        openTabs.remove(route)
        navBackStack.remove(route)
    }
}