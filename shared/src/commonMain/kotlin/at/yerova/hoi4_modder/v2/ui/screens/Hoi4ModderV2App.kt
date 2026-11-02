package at.yerova.hoi4_modder.v2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import at.yerova.hoi4_modder.v2.engine.EngineState
import at.yerova.hoi4_modder.v2.engine.Hoi4ModderEngine
import at.yerova.hoi4_modder.v2.engine.model.AppPreferences
import at.yerova.hoi4_modder.v2.ui.*
import at.yerova.hoi4_modder.v2.ui.components.*
import at.yerova.hoi4_modder.v2.ui.model.localizedLoadingStep
import at.yerova.hoi4_modder.v2.ui.navigation.*
import at.yerova.hoi4_modder.v2.ui.screens.views.FocusTreeWorkspaceView
import at.yerova.hoi4_modder.v2.ui.screens.views.MapStudioTabContentView
import at.yerova.hoi4_modder.v2.ui.state.MapEditorSessionState
import at.yerova.hoi4_modder.v2.ui.state.WorkspaceTabManager
import java.io.File

@Composable
fun Hoi4ModderV2App(
    isCloseRequested: Boolean = false,
    onDismissCloseRequest: () -> Unit = {},
    onExitApplication: () -> Unit = {},
    onUnsavedChangesChanged: (Boolean, String) -> Unit = { _, _ -> }
) {
    LaunchedEffect(Unit) {
        AppPreferences.applySavedLocale()
    }

    var currentLang by remember { mutableStateOf(AppPreferences.language) }
    var useDarkTheme by remember { mutableStateOf(true) }
    var engine by remember { mutableStateOf<Hoi4ModderEngine?>(null) }
    var triedAutoOpen by remember { mutableStateOf(false) }
    var pendingExitAfterSave by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!triedAutoOpen && AppPreferences.autoOpenLastProject) {
            triedAutoOpen = true
            val vDir = File(AppPreferences.vanillaPath)
            val mDir = File(AppPreferences.lastModPath)
            if (File(vDir, "map/provinces.bmp").exists() && mDir.exists()) {
                val created = Hoi4ModderEngine(vDir, mDir)
                created.initializeAndLoadAsync()
                engine = created
            }
        }
    }

    val activeEngine = engine
    val engineState = activeEngine?.state?.collectAsState()?.value

    LaunchedEffect(engineState?.hasUnsavedChanges, engineState?.unsavedSummary) {
        onUnsavedChangesChanged(
            engineState?.hasUnsavedChanges == true,
            engineState?.unsavedSummary.orEmpty()
        )
    }

    LaunchedEffect(pendingExitAfterSave, engineState?.isSaving, engineState?.hasUnsavedChanges) {
        if (pendingExitAfterSave && engineState != null && !engineState.isSaving) {
            if (!engineState.hasUnsavedChanges) {
                onExitApplication()
            } else {
                pendingExitAfterSave = false
            }
        }
    }

    Hoi4ModderTheme(useDarkTheme = useDarkTheme) {
        key(currentLang) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    activeEngine == null -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            EmptyProjectTopBar(
                                projectName = null,
                                currentLang = currentLang,
                                onLanguageChange = { AppPreferences.language = it; currentLang = it },
                                useDarkTheme = useDarkTheme,
                                onToggleDarkTheme = { useDarkTheme = it }
                            )
                            HMHorizontalDivider()
                            ProjectSetupScreen(onEngineCreated = { engine = it })
                        }
                    }

                    engineState == null || engineState.isLoading -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            EmptyProjectTopBar(
                                projectName = activeEngine.modPath.name,
                                currentLang = currentLang,
                                onLanguageChange = { AppPreferences.language = it; currentLang = it },
                                useDarkTheme = useDarkTheme,
                                onToggleDarkTheme = { useDarkTheme = it }
                            )
                            HMHorizontalDivider()
                            EngineLoadingScreen(
                                modName = activeEngine.modPath.name,
                                loadingStep = engineState?.let { localizedLoadingStep(it) }.orEmpty()
                            )
                        }
                    }

                    else -> {
                        MapEditorV2Workspace(
                            engine = activeEngine,
                            engineState = engineState,
                            currentLang = currentLang,
                            onLanguageChange = { AppPreferences.language = it; currentLang = it },
                            useDarkTheme = useDarkTheme,
                            onToggleDarkTheme = { useDarkTheme = it },
                            onReloadProject = { activeEngine.initializeAndLoadAsync() },
                            onCloseProject = {
                                activeEngine.close()
                                AppPreferences.autoOpenLastProject = false
                                engine = null
                            }
                        )
                    }
                }

                // --- ECLIPSE-STYLE UNSAVED CHANGES DIALOG ---
                if (isCloseRequested && activeEngine != null && engineState != null) {
                    UnsavedChangesExitDialog(
                        modName = activeEngine.modPath.name,
                        unsavedSummary = engineState.unsavedSummary,
                        isSaving = engineState.isSaving || pendingExitAfterSave,
                        onSaveAndExit = {
                            pendingExitAfterSave = true
                            activeEngine.saveAllAsync()
                        },
                        onDiscardAndExit = onExitApplication,
                        onCancel = {
                            if (!pendingExitAfterSave) {
                                onDismissCloseRequest()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyProjectTopBar(
    projectName: String?,
    currentLang: String,
    onLanguageChange: (String) -> Unit,
    useDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit
) {
    HMTopMenuBar(
        projectName = projectName,
        hasUnsavedChanges = false,
        unsavedSummary = "",
        canUndo = false,
        canRedo = false,
        currentLang = currentLang,
        onLanguageChange = onLanguageChange,
        useDarkTheme = useDarkTheme,
        onToggleDarkTheme = onToggleDarkTheme,
        onSaveAll = {},
        onReloadProject = {},
        onSwitchProject = {},
        onUndo = {},
        onRedo = {},
        onCopy = {},
        onPaste = {},
        onClearSelection = {},
        onAutoFixCoastal = {},
        onRecalcCenters = {},
        onFitView = {},
        showSelectionOutline = true,
        onToggleSelectionOutline = {},
        showRailwaysOverlay = true,
        onToggleRailways = {},
        showSupplyOverlay = true,
        onToggleSupply = {},
        showBuildingsOverlay = false,
        onToggleBuildings = {},
        showAdjacenciesOverlay = false,
        onToggleAdjacencies = {}
    )
}
@Composable
fun MapEditorV2Workspace(
    engine: Hoi4ModderEngine,
    engineState: EngineState,
    currentLang: String,
    onLanguageChange: (String) -> Unit,
    useDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onReloadProject: () -> Unit,
    onCloseProject: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboard.current

    // 1. Top-Level Tab-Manager (Map Studio, Focus Tree Studio, ...)
    val tabManager = remember(engine) {
        WorkspaceTabManager(
            initialTabs = listOf(MapWorkspaceRoute, FocusTreeWorkspaceRoute),
            initialActive = MapWorkspaceRoute
        )
    }

    // 2. Map-Session bleibt im Speicher erhalten, auch wenn man kurz in den Focus-Tab wechselt!
    val mapSession = remember(engine) {
        MapEditorSessionState(engine, coroutineScope, clipboardManager)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HMTopMenuBar(
            projectName = engine.modPath.name,
            hasUnsavedChanges = engineState.hasUnsavedChanges,
            unsavedSummary = engineState.unsavedSummary,
            canUndo = engineState.canUndo,
            canRedo = engineState.canRedo,
            currentLang = currentLang,
            onLanguageChange = onLanguageChange,
            useDarkTheme = useDarkTheme,
            onToggleDarkTheme = onToggleDarkTheme,
            openWorkspaceTabs = tabManager.openTabs,
            activeWorkspaceTab = tabManager.activeTab,
            onOpenWorkspaceTab = { route -> tabManager.openOrFocusTab(route) },
            onSaveAll = { mapSession.saveAll() },
            onReloadProject = onReloadProject,
            onSwitchProject = onCloseProject,
            onUndo = { mapSession.undo() },
            onRedo = { mapSession.redo() },
            onCopy = { mapSession.copyActiveProvince(engineState) },
            onPaste = { mapSession.pasteToSelectedProvinces() },
            onClearSelection = { mapSession.clearSelection() },
            onAutoFixCoastal = { engine.autoFixCoastalFlagsAsync() },
            onRecalcCenters = { engine.recalculateAllCentersAsync() },
            onFitView = { mapSession.fitMapToViewport(engineState.mapWidth) },
            showSelectionOutline = mapSession.showSelectionOutline,
            onToggleSelectionOutline = { mapSession.showSelectionOutline = it },
            showRailwaysOverlay = mapSession.showRailwaysOverlay,
            onToggleRailways = { mapSession.showRailwaysOverlay = it },
            showSupplyOverlay = mapSession.showSupplyOverlay,
            onToggleSupply = { mapSession.showSupplyOverlay = it },
            showBuildingsOverlay = mapSession.showBuildingsOverlay,
            onToggleBuildings = { mapSession.showBuildingsOverlay = it },
            showAdjacenciesOverlay = mapSession.showAdjacenciesOverlay,
            onToggleAdjacencies = { mapSession.showAdjacenciesOverlay = it }
        )

        HMHorizontalDivider()

        // --- 2. CLOSABLE WORKSPACE TAB BAR ---
        if (tabManager.openTabs.isNotEmpty()) {
            HMWorkspaceTabBar(tabManager = tabManager)
            HMHorizontalDivider()
        }

        // --- 3. TOP-LEVEL NAVIGATION 3 WORKSPACE HOST ---
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (tabManager.navBackStack.isEmpty()) {
                EmptyWorkspaceViewsPlaceholder(
                    onOpenView = { route -> tabManager.openOrFocusTab(route) }
                )
            } else {
                NavDisplay(
                    backStack = tabManager.navBackStack,
                    onBack = {
                        if (tabManager.navBackStack.size > 1) {
                            val current = tabManager.navBackStack.removeLastOrNull()
                            if (current != null) tabManager.openTabs.remove(current)
                        }
                    },
                    entryProvider = entryProvider {
                        entry<MapWorkspaceRoute> {
                            MapStudioTabContentView(
                                session = mapSession,
                                engineState = engineState
                            )
                        }
                        entry<FocusTreeWorkspaceRoute> {
                            FocusTreeWorkspaceView(
                                modName = engine.modPath.name
                            )
                        }
                    }
                )
            }
        }
    }
}