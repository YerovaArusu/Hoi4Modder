package at.yerova.hoi4_modder.v2.ui.screens.views

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import at.yerova.hoi4_modder.v2.engine.EngineState
import at.yerova.hoi4_modder.v2.engine.model.BorderSegment
import at.yerova.hoi4_modder.v2.engine.model.MapViewMode
import at.yerova.hoi4_modder.v2.ui.HMHorizontalDivider
import at.yerova.hoi4_modder.v2.ui.HMIcon
import at.yerova.hoi4_modder.v2.ui.HMOutlinedButton
import at.yerova.hoi4_modder.v2.ui.HMVerticalDivider
import at.yerova.hoi4_modder.v2.ui.components.AddAdjacencyDialogV2
import at.yerova.hoi4_modder.v2.ui.components.MapCanvasViewport
import at.yerova.hoi4_modder.v2.ui.components.MapToolsToolbar
import at.yerova.hoi4_modder.v2.ui.components.NewProvinceDialogV2
import at.yerova.hoi4_modder.v2.ui.components.NewStateDialogV2
import at.yerova.hoi4_modder.v2.ui.components.NewStrategicRegionDialogV2
import at.yerova.hoi4_modder.v2.ui.model.EditorTool
import at.yerova.hoi4_modder.v2.ui.model.allowedToolsForRoute
import at.yerova.hoi4_modder.v2.ui.navigation.CoastalRoute
import at.yerova.hoi4_modder.v2.ui.navigation.ProvincesRoute
import at.yerova.hoi4_modder.v2.ui.navigation.StatesRoute
import at.yerova.hoi4_modder.v2.ui.navigation.StrategicRegionsRoute
import at.yerova.hoi4_modder.v2.ui.navigation.TerrainRoute
import at.yerova.hoi4_modder.v2.ui.screens.views.map.CoastalModeView
import at.yerova.hoi4_modder.v2.ui.screens.views.map.ProvincesModeView
import at.yerova.hoi4_modder.v2.ui.screens.views.map.StatesModeView
import at.yerova.hoi4_modder.v2.ui.screens.views.map.StrategicRegionsModeView
import at.yerova.hoi4_modder.v2.ui.screens.views.map.TerrainModeView
import at.yerova.hoi4_modder.v2.ui.state.MapEditorSessionState
import hoi4_modder.shared.generated.resources.Res
import hoi4_modder.shared.generated.resources.banner_paint_state
import hoi4_modder.shared.generated.resources.banner_pick_region
import hoi4_modder.shared.generated.resources.banner_pick_state
import hoi4_modder.shared.generated.resources.cancel_pick_mode_btn
import org.jetbrains.compose.resources.stringResource

@Composable
fun MapStudioTabContentView(
    session: MapEditorSessionState,
    engineState: EngineState
) {
    val engine = session.engine
    val focusRequester = remember { FocusRequester() }
    val currentRoute = session.currentRoute

    val targetMapMode = when (currentRoute) {
        is ProvincesRoute -> MapViewMode.PROVINCES
        is StatesRoute -> MapViewMode.STATES
        is StrategicRegionsRoute -> MapViewMode.STRATEGIC_REGIONS
        is TerrainRoute -> MapViewMode.TERRAIN
        is CoastalRoute -> MapViewMode.COASTAL
    }

    val targetHighlightId = when (targetMapMode) {
        MapViewMode.STATES -> session.selectedStateId
        MapViewMode.STRATEGIC_REGIONS -> session.selectedRegionId
        else -> null
    }

    LaunchedEffect(targetMapMode, targetHighlightId) {
        engine.setMapViewModeAsync(targetMapMode, targetHighlightId)
    }

    val selectionBorderSegments by produceState(
        initialValue = emptyList<BorderSegment>(),
        session.selectedProvinceIds.toList(),
        engineState.paintRevision,
        session.showSelectionOutline
    ) {
        value = if (!session.showSelectionOutline || session.selectedProvinceIds.isEmpty()) {
            emptyList()
        } else {
            engine.computeBorderSegments(session.selectedProvinceIds.toSet())
        }
    }

    val groupBorderSegments by produceState(
        initialValue = emptyList<BorderSegment>(),
        targetMapMode,
        targetHighlightId,
        engineState.paintRevision,
        engineState.stateDataRevision,
        engineState.regionDataRevision,
        session.showSelectionOutline
    ) {
        value = if (!session.showSelectionOutline || targetHighlightId == null) {
            emptyList()
        } else {
            val groupProvs = when (targetMapMode) {
                MapViewMode.STATES -> engineState.statesById[targetHighlightId]?.provinces ?: emptySet()
                MapViewMode.STRATEGIC_REGIONS -> engineState.strategicRegionsById[targetHighlightId]?.provinces ?: emptySet()
                else -> emptySet()
            }
            engine.computeBorderSegments(groupProvs)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                session.isCtrlHeld = event.isCtrlPressed || event.isMetaPressed
                if (event.key == Key.Spacebar) {
                    session.isSpacePressed = (event.type == KeyEventType.KeyDown)
                    return@onPreviewKeyEvent true
                }
                if (event.type == KeyEventType.KeyDown) {
                    val isCtrl = event.isCtrlPressed || event.isMetaPressed
                    val panStep = 60f
                    val allowed = allowedToolsForRoute(currentRoute)
                    when {
                        isCtrl && event.key == Key.C -> { session.copyActiveProvince(engineState); true }
                        isCtrl && event.key == Key.V -> { session.pasteToSelectedProvinces(); true }
                        isCtrl && event.key == Key.Z -> { session.undo(); true }
                        isCtrl && event.key == Key.Y -> { session.redo(); true }
                        isCtrl && event.key == Key.S -> { session.saveAll(); true }
                        event.key == Key.Enter && session.activeRailwayPath.isNotEmpty() -> {
                            session.finishActiveRailway(); true
                        }
                        event.key == Key.Escape -> {
                            when {
                                (currentRoute is StatesRoute && currentRoute.assignMode) ||
                                        (currentRoute is StrategicRegionsRoute && currentRoute.assignMode) ->
                                    session.popRouteIfPossible()
                                session.isPaintingStateProvinces -> { session.isPaintingStateProvinces = false; true }
                                session.paintTerrainOnMapClick -> { session.paintTerrainOnMapClick = false; true }
                                session.toggleCoastalOnMapClick -> { session.toggleCoastalOnMapClick = false; true }
                                session.activeRailwayPath.isNotEmpty() -> { session.activeRailwayPath.clear(); true }
                                session.selectedProvinceIds.isNotEmpty() -> { session.clearSelection(); true }
                                else -> false
                            }
                        }
                        !isCtrl && (event.key == Key.DirectionLeft || event.key == Key.A) -> { session.offset += Offset(panStep, 0f); true }
                        !isCtrl && (event.key == Key.DirectionRight || event.key == Key.D) -> { session.offset += Offset(-panStep, 0f); true }
                        !isCtrl && (event.key == Key.DirectionUp || event.key == Key.W) -> { session.offset += Offset(0f, panStep); true }
                        !isCtrl && (event.key == Key.DirectionDown || event.key == Key.S) -> { session.offset += Offset(0f, -panStep); true }
                        !isCtrl && event.key == Key.B && EditorTool.BRUSH in allowed -> { session.activeTool = EditorTool.BRUSH; true }
                        !isCtrl && event.key == Key.G && EditorTool.BUCKET in allowed -> { session.activeTool = EditorTool.BUCKET; true }
                        !isCtrl && event.key == Key.R && EditorTool.RAILWAY in allowed -> { session.activeTool = EditorTool.RAILWAY; session.showRailwaysOverlay = true; true }
                        !isCtrl && event.key == Key.H && EditorTool.SUPPLY_NODE in allowed -> { session.activeTool = EditorTool.SUPPLY_NODE; session.showSupplyOverlay = true; true }
                        !isCtrl && event.key == Key.P && EditorTool.BUILDING in allowed -> { session.activeTool = EditorTool.BUILDING; session.showBuildingsOverlay = true; true }
                        !isCtrl && (event.key == Key.I || event.key == Key.E) -> { session.activeTool = EditorTool.EYEDROPPER; true }
                        !isCtrl && event.key == Key.M -> { session.activeTool = EditorTool.NAVIGATE; true }
                        else -> false
                    }
                } else false
            }
    ) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // LEFT COLUMN: MAP TOOLBAR + CANVAS
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                MapToolsToolbar(session = session)
                HMHorizontalDivider()

                val isStateAssignRoute = currentRoute is StatesRoute && currentRoute.assignMode
                val isRegionAssignRoute = currentRoute is StrategicRegionsRoute && currentRoute.assignMode
                if (isStateAssignRoute || isRegionAssignRoute || session.isPaintingStateProvinces) {
                    val selectedState = session.selectedState(engineState)
                    val bannerText = when {
                        isStateAssignRoute -> stringResource(
                            Res.string.banner_pick_state,
                            (currentRoute as StatesRoute).allSourceProvinces.size
                        )
                        isRegionAssignRoute -> stringResource(
                            Res.string.banner_pick_region,
                            (currentRoute as StrategicRegionsRoute).sourceProvinceIds.size
                        )
                        else -> stringResource(
                            Res.string.banner_paint_state,
                            selectedState?.id ?: 0,
                            selectedState?.name.orEmpty()
                        )
                    }
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HMIcon("touch_app", size = 16.sp, color = MaterialTheme.colorScheme.primary)
                                Text(bannerText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                            HMOutlinedButton(
                                onClick = {
                                    if (session.isPaintingStateProvinces) session.isPaintingStateProvinces = false
                                    else session.popRouteIfPossible()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(stringResource(Res.string.cancel_pick_mode_btn), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    HMHorizontalDivider(color = MaterialTheme.colorScheme.primary)
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MapCanvasViewport(
                        session = session,
                        engineState = engineState,
                        targetMapMode = targetMapMode,
                        selectionBorderSegments = selectionBorderSegments,
                        groupBorderSegments = groupBorderSegments,
                        focusRequester = focusRequester
                    )
                }
            }

            HMVerticalDivider()

            // RIGHT COLUMN: INNER NAVIGATION 3 MAP SIDEBAR
            Surface(
                modifier = Modifier.width(440.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface
            ) {
                NavDisplay(
                    backStack = session.backStack,
                    onBack = { session.popRouteIfPossible() },
                    entryProvider = entryProvider {
                        entry<ProvincesRoute> { route ->
                            ProvincesModeView(route = route, session = session, engineState = engineState)
                        }
                        entry<StatesRoute> { route ->
                            StatesModeView(route = route, session = session, engineState = engineState)
                        }
                        entry<StrategicRegionsRoute> { route ->
                            StrategicRegionsModeView(route = route, session = session, engineState = engineState)
                        }
                        entry<TerrainRoute> {
                            TerrainModeView(session = session, engineState = engineState)
                        }
                        entry<CoastalRoute> {
                            CoastalModeView(session = session, engineState = engineState)
                        }
                    }
                )
            }
        }
    }

    // DIALOGS
    if (session.showNewProvinceDialog) {
        NewProvinceDialogV2(
            onDismiss = { session.showNewProvinceDialog = false },
            onCreate = { type, coastal, terrain, continent ->
                session.showNewProvinceDialog = false
                engine.createNewProvinceAsync(type, coastal, terrain, continent) { created ->
                    session.handleProvinceSelection(created, false, engineState)
                    session.activeTool = EditorTool.BRUSH
                }
            }
        )
    }

    if (session.showNewStateDialog) {
        NewStateDialogV2(
            onDismiss = { session.showNewStateDialog = false },
            onCreate = { name, manpower, category ->
                session.showNewStateDialog = false
                val initialIds = if (currentRoute is StatesRoute && currentRoute.assignMode) {
                    currentRoute.allSourceProvinces
                } else {
                    session.effectiveSelectedProvinceIds()
                }
                engine.createNewStateAsync(name, manpower, category, initialIds) { created ->
                    session.selectedStateId = created.id
                    if (currentRoute is StatesRoute && currentRoute.assignMode) {
                        session.popRouteIfPossible()
                    }
                }
            }
        )
    }

    if (session.showNewRegionDialog) {
        NewStrategicRegionDialogV2(
            onDismiss = { session.showNewRegionDialog = false },
            onCreate = { name ->
                session.showNewRegionDialog = false
                val initialIds = if (currentRoute is StrategicRegionsRoute && currentRoute.assignMode) {
                    currentRoute.sourceProvinceIds
                } else {
                    session.effectiveSelectedProvinceIds()
                }
                engine.createNewStrategicRegionAsync(name, initialIds) { created ->
                    session.selectedRegionId = created.id
                    if (currentRoute is StrategicRegionsRoute && currentRoute.assignMode) {
                        session.popRouteIfPossible()
                    }
                }
            }
        )
    }

    if (session.showAddAdjacencyDialog) {
        AddAdjacencyDialogV2(
            onDismiss = { session.showAddAdjacencyDialog = false },
            onAdd = { fromId, toId, throughSeaId, ruleName, comment ->
                session.showAddAdjacencyDialog = false
                session.showAdjacenciesOverlay = true
                engine.addAdjacencyAsync(fromId, toId, throughSeaId, ruleName, comment)
            }
        )
    }
}