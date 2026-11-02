package at.yerova.hoi4_modder.v2.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.yerova.hoi4_modder.v2.ui.*
import at.yerova.hoi4_modder.v2.ui.navigation.*
import hoi4_modder.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun HMMapOverlayPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .padding(12.dp)
            .border(HMDefaults.BorderThickness, MaterialTheme.colorScheme.outline, HMDefaults.PanelShape),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = HMDefaults.PanelShape
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            content()
        }
    }
}

@Composable
fun MapRouteSwitcher(
    currentRoute: EditorRoute,
    onNavigate: (EditorRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    HMMapOverlayPanel(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            HMIcon("layers", size = 16.sp, color = MaterialTheme.colorScheme.primary)

            HMFilterChip(
                selected = currentRoute is ProvincesRoute,
                onClick = { onNavigate(ProvincesRoute()) },
                label = {
                    Text(
                        stringResource(Res.string.map_mode_provinces),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
            HMFilterChip(
                selected = currentRoute is StatesRoute,
                onClick = { onNavigate(StatesRoute()) },
                label = {
                    Text(
                        stringResource(Res.string.map_mode_states),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
            HMFilterChip(
                selected = currentRoute is StrategicRegionsRoute,
                onClick = { onNavigate(StrategicRegionsRoute()) },
                label = {
                    Text(
                        stringResource(Res.string.map_mode_regions),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
            HMFilterChip(
                selected = currentRoute is TerrainRoute,
                onClick = { onNavigate(TerrainRoute) },
                label = {
                    Text(
                        stringResource(Res.string.map_mode_terrain),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
            HMFilterChip(
                selected = currentRoute is CoastalRoute,
                onClick = { onNavigate(CoastalRoute) },
                label = {
                    Text(
                        stringResource(Res.string.map_mode_coastal),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}

@Composable
fun HMTopMenuBar(
    projectName: String?,
    hasUnsavedChanges: Boolean,
    unsavedSummary: String,
    canUndo: Boolean,
    canRedo: Boolean,
    currentLang: String,
    onLanguageChange: (String) -> Unit,
    useDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    openWorkspaceTabs: List<WorkspaceRoute> = emptyList(),
    activeWorkspaceTab: WorkspaceRoute? = null,
    onOpenWorkspaceTab: (WorkspaceRoute) -> Unit = {},
    onSaveAll: () -> Unit,
    onReloadProject: () -> Unit,
    onSwitchProject: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onClearSelection: () -> Unit,
    onAutoFixCoastal: () -> Unit,
    onRecalcCenters: () -> Unit,
    onFitView: () -> Unit,
    showSelectionOutline: Boolean,
    onToggleSelectionOutline: (Boolean) -> Unit,
    showRailwaysOverlay: Boolean,
    onToggleRailways: (Boolean) -> Unit,
    showSupplyOverlay: Boolean,
    onToggleSupply: (Boolean) -> Unit,
    showBuildingsOverlay: Boolean,
    onToggleBuildings: (Boolean) -> Unit,
    showAdjacenciesOverlay: Boolean,
    onToggleAdjacencies: (Boolean) -> Unit
) {
    var fileMenuExpanded by remember { mutableStateOf(false) }
    var editMenuExpanded by remember { mutableStateOf(false) }
    var viewMenuExpanded by remember { mutableStateOf(false) }
    var settingsMenuExpanded by remember { mutableStateOf(false) }
    var showViewMenuExpanded by remember { mutableStateOf(false) }

    val hasProject = projectName != null
    val isMapActive = activeWorkspaceTab == MapWorkspaceRoute

    Surface(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = HMDefaults.ComponentShape
                ) {
                    Text(
                        text = projectName ?: stringResource(Res.string.no_project_loaded),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                // --- FILE MENU ---
                Box {
                    HMTextButton(onClick = { fileMenuExpanded = true }) {
                        Text(stringResource(Res.string.menu_file))
                    }
                    DropdownMenu(expanded = fileMenuExpanded, onDismissRequest = { fileMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_file_save_all)) },
                            leadingIcon = { HMIcon("save", size = 18.sp) },
                            enabled = hasProject && hasUnsavedChanges,
                            onClick = { fileMenuExpanded = false; onSaveAll() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_file_reload)) },
                            leadingIcon = { HMIcon("refresh", size = 18.sp) },
                            enabled = hasProject,
                            onClick = { fileMenuExpanded = false; onReloadProject() }
                        )
                        HMHorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_file_switch_project)) },
                            leadingIcon = { HMIcon("folder_open", size = 18.sp) },
                            enabled = hasProject,
                            onClick = { fileMenuExpanded = false; onSwitchProject() }
                        )
                        HMHorizontalDivider()

                        Box {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { HMIcon("settings", size = 18.sp) },
                                trailingIcon = { HMIcon("chevron_right", size = 18.sp) },
                                onClick = { settingsMenuExpanded = true }
                            )

                            DropdownMenu(
                                expanded = settingsMenuExpanded,
                                onDismissRequest = { settingsMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Language: English") },
                                    trailingIcon = { if (currentLang == "en") HMIcon("check", size = 16.sp) },
                                    onClick = {
                                        onLanguageChange("en")
                                        settingsMenuExpanded = false
                                        fileMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Language: Deutsch") },
                                    trailingIcon = { if (currentLang == "de") HMIcon("check", size = 16.sp) },
                                    onClick = {
                                        onLanguageChange("de")
                                        settingsMenuExpanded = false
                                        fileMenuExpanded = false
                                    }
                                )
                                HMHorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (useDarkTheme) "Theme: Dark Mode" else "Theme: Light Mode") },
                                    leadingIcon = { HMIcon(if (useDarkTheme) "dark_mode" else "light_mode", size = 16.sp) },
                                    onClick = {
                                        onToggleDarkTheme(!useDarkTheme)
                                        settingsMenuExpanded = false
                                        fileMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // --- EDIT MENU ---
                Box {
                    HMTextButton(onClick = { editMenuExpanded = true }, enabled = hasProject && isMapActive) {
                        Text(stringResource(Res.string.menu_edit))
                    }
                    DropdownMenu(expanded = editMenuExpanded, onDismissRequest = { editMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_edit_undo)) },
                            leadingIcon = { HMIcon("undo", size = 18.sp) },
                            enabled = canUndo,
                            onClick = { editMenuExpanded = false; onUndo() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_edit_redo)) },
                            leadingIcon = { HMIcon("redo", size = 18.sp) },
                            enabled = canRedo,
                            onClick = { editMenuExpanded = false; onRedo() }
                        )
                        HMHorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_edit_copy)) },
                            leadingIcon = { HMIcon("content_copy", size = 18.sp) },
                            onClick = { editMenuExpanded = false; onCopy() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_edit_paste)) },
                            leadingIcon = { HMIcon("content_paste", size = 18.sp) },
                            onClick = { editMenuExpanded = false; onPaste() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_edit_clear_selection)) },
                            leadingIcon = { HMIcon("clear", size = 18.sp) },
                            onClick = { editMenuExpanded = false; onClearSelection() }
                        )
                        HMHorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_edit_autofix_coastal)) },
                            leadingIcon = { HMIcon("build", size = 18.sp) },
                            onClick = { editMenuExpanded = false; onAutoFixCoastal() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_edit_recalc_centers)) },
                            leadingIcon = { HMIcon("my_location", size = 18.sp) },
                            onClick = { editMenuExpanded = false; onRecalcCenters() }
                        )
                    }
                }

                Box {
                    HMTextButton(onClick = { viewMenuExpanded = true }, enabled = hasProject) {
                        Text("View")
                    }
                    DropdownMenu(expanded = viewMenuExpanded, onDismissRequest = { viewMenuExpanded = false }) {
                        Box {
                            DropdownMenuItem(
                                text = { Text("Show View") },
                                leadingIcon = { HMIcon("wysiwyg", size = 18.sp) },
                                trailingIcon = { HMIcon("chevron_right", size = 18.sp) },
                                onClick = { showViewMenuExpanded = true }
                            )

                            DropdownMenu(
                                expanded = showViewMenuExpanded,
                                onDismissRequest = { showViewMenuExpanded = false }
                            ) {
                                AvailableStudioViews.forEach { viewDesc ->
                                    val isOpen = viewDesc.route in openWorkspaceTabs
                                    DropdownMenuItem(
                                        text = { Text(viewDesc.defaultTitle) },
                                        leadingIcon = { HMIcon(viewDesc.iconName, size = 17.sp) },
                                        trailingIcon = {
                                            if (isOpen) HMIcon("check", size = 15.sp, color = MaterialTheme.colorScheme.primary)
                                        },
                                        onClick = {
                                            onOpenWorkspaceTab(viewDesc.route)
                                            showViewMenuExpanded = false
                                            viewMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        HMHorizontalDivider()

                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_view_fit)) },
                            leadingIcon = { HMIcon("fit_screen", size = 18.sp) },
                            enabled = isMapActive,
                            onClick = { viewMenuExpanded = false; onFitView() }
                        )
                        HMHorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.overlay_selection_outline)) },
                            enabled = isMapActive,
                            trailingIcon = { if (showSelectionOutline) HMIcon("check", size = 16.sp) },
                            onClick = { onToggleSelectionOutline(!showSelectionOutline) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.overlay_railways)) },
                            enabled = isMapActive,
                            trailingIcon = { if (showRailwaysOverlay) HMIcon("check", size = 16.sp) },
                            onClick = { onToggleRailways(!showRailwaysOverlay) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.overlay_supply)) },
                            enabled = isMapActive,
                            trailingIcon = { if (showSupplyOverlay) HMIcon("check", size = 16.sp) },
                            onClick = { onToggleSupply(!showSupplyOverlay) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.overlay_buildings)) },
                            enabled = isMapActive,
                            trailingIcon = { if (showBuildingsOverlay) HMIcon("check", size = 16.sp) },
                            onClick = { onToggleBuildings(!showBuildingsOverlay) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.overlay_adjacencies)) },
                            enabled = isMapActive,
                            trailingIcon = { if (showAdjacenciesOverlay) HMIcon("check", size = 16.sp) },
                            onClick = { onToggleAdjacencies(!showAdjacenciesOverlay) }
                        )
                    }
                }
            }

            if (hasProject && hasUnsavedChanges) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = HMDefaults.ComponentShape
                ) {
                    Text(
                        text = stringResource(Res.string.unsaved_indicator, unsavedSummary),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}