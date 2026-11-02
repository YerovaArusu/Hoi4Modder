package at.yerova.hoi4_modder.v2.ui.screens.views.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.yerova.hoi4_modder.v2.engine.EngineState
import at.yerova.hoi4_modder.v2.ui.*
import at.yerova.hoi4_modder.v2.ui.model.*
import at.yerova.hoi4_modder.v2.ui.navigation.StatesRoute
import at.yerova.hoi4_modder.v2.ui.state.MapEditorSessionState
import hoi4_modder.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun StatesModeView(
    route: StatesRoute,
    session: MapEditorSessionState,
    engineState: EngineState
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (!route.assignMode) {
            SecondaryScrollableTabRow(
                selectedTabIndex = session.stateSubTab.ordinal,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = { HMHorizontalDivider() }
            ) {
                Tab(
                    selected = session.stateSubTab == StateSidebarSubTab.STATES,
                    onClick = { session.stateSubTab = StateSidebarSubTab.STATES },
                    text = { Text(stringResource(Res.string.tab_states), style = MaterialTheme.typography.labelMedium) }
                )
                Tab(
                    selected = session.stateSubTab == StateSidebarSubTab.INFRASTRUCTURE,
                    onClick = { session.stateSubTab = StateSidebarSubTab.INFRASTRUCTURE },
                    text = { Text(stringResource(Res.string.tab_infra), style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (route.assignMode || session.stateSubTab == StateSidebarSubTab.STATES) {
                StatesListAndInspectorSection(route, session, engineState)
            } else {
                StateInfrastructureSection(session, engineState)
            }
        }
    }
}

@Composable
private fun StatesListAndInspectorSection(
    route: StatesRoute,
    session: MapEditorSessionState,
    engineState: EngineState
) {
    var searchQuery by remember { mutableStateOf("") }
    val selectedState = session.selectedState(engineState)

    val allStates = remember(engineState.stateDataRevision, engineState.statesById.size) {
        engineState.statesById.values.sortedByDescending { it.id }
    }

    val filteredStates = remember(searchQuery, engineState.stateDataRevision) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) allStates
        else allStates.filter { st ->
            st.id.toString().contains(q) ||
                    st.name.lowercase().contains(q) ||
                    st.provinces.any { it.toString() == q }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (route.assignMode) {
            HMPanel(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentPadding = PaddingValues(12.dp)
            ) {
                Text(
                    text = stringResource(
                        Res.string.assigning_state_provinces_format,
                        route.allSourceProvinces.size,
                        route.allSourceProvinces.joinToString(", ") { "#$it" }
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.assigning_state_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                HMOutlinedButton(
                    onClick = { session.popRouteIfPossible() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.dialog_cancel_btn))
                }
            }
        }

        HMButton(onClick = { session.showNewStateDialog = true }, modifier = Modifier.fillMaxWidth()) {
            HMIcon("add_circle", size = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.create_state_btn), fontWeight = FontWeight.SemiBold)
        }

        HMTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = stringResource(Res.string.search_states_label),
            leadingIcon = { HMIcon("search", size = 18.sp) },
            modifier = Modifier.fillMaxWidth()
        )

        if (selectedState != null && !route.assignMode) {
            HMPanel(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentPadding = PaddingValues(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.selected_state_provinces, selectedState.id, selectedState.name),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                HMButton(
                    onClick = { session.isPaintingStateProvinces = !session.isPaintingStateProvinces },
                    containerColor = if (session.isPaintingStateProvinces) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    contentColor = if (session.isPaintingStateProvinces) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HMIcon("touch_app", size = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.paint_state_provinces_btn, selectedState.id))
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedState.provinces.sorted().forEach { provId ->
                        HMFilterChip(
                            selected = true,
                            onClick = {
                                engineState.provincesById[provId]?.let { prov ->
                                    session.handleProvinceSelection(prov, false, engineState)
                                    session.centerOnProvince(prov)
                                }
                            },
                            label = { Text("#$provId", style = MaterialTheme.typography.labelSmall) },
                            trailingIcon = {
                                HMIcon(
                                    "close",
                                    size = 13.sp,
                                    modifier = Modifier.clickable {
                                        session.engine.removeProvinceFromStateAsync(provId, selectedState.id)
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filteredStates, key = { it.id }) { state ->
                val isSelected = selectedState?.id == state.id
                HMPanel(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (route.assignMode) {
                            session.engine.assignProvincesToStateAsync(route.allSourceProvinces, state.id)
                            session.selectedStateId = state.id
                            session.popRouteIfPossible()
                        } else {
                            session.selectedStateId = state.id
                        }
                    },
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentPadding = PaddingValues(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(Res.string.state_title_format, state.id, state.name),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (state.isModifiedOrInMod) {
                                    Surface(color = MaterialTheme.colorScheme.tertiary, shape = HMDefaults.ComponentShape) {
                                        Text(
                                            text = stringResource(Res.string.state_modified_badge),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiary
                                        )
                                    }
                                }
                            }
                            Text(
                                text = stringResource(Res.string.state_provinces_count, state.provinces.size),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (route.assignMode || session.selectedProvinceIds.isNotEmpty()) {
                                HMOutlinedButton(
                                    onClick = {
                                        val ids = if (route.assignMode) route.allSourceProvinces else session.selectedProvinceIds.toList()
                                        session.engine.assignProvincesToStateAsync(ids, state.id)
                                        session.selectedStateId = state.id
                                        if (route.assignMode) session.popRouteIfPossible()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    val count = if (route.assignMode) route.allSourceProvinces.size else session.selectedProvinceIds.size
                                    Text(
                                        text = stringResource(Res.string.assign_selected_to_this_state_btn, count),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            HMIconButton(
                                onClick = {
                                    session.selectedStateId = state.id
                                    session.centerOnState(state)
                                }
                            ) {
                                HMIcon("my_location", size = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StateInfrastructureSection(
    session: MapEditorSessionState,
    engineState: EngineState
) {
    val selectedState = session.selectedState(engineState)
    val commonBuildingTypes = listOf(
        "arms_factory", "industrial_complex", "air_base", "bunker",
        "coastal_bunker", "dockyard", "naval_base_spawn", "floating_harbor",
        "anti_air_building", "synthetic_refinery", "nuclear_reactor_spawn", "naval_supply_hub"
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(Res.string.infra_header), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        // 1. Buildings
        HMPanel(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant, contentPadding = PaddingValues(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(Res.string.building_tool_section), fontWeight = FontWeight.Bold)
                    val activeSpec = visualSpecForBuilding(session.buildingTypeToPlace)
                    HMFilterChip(
                        selected = session.activeTool == EditorTool.BUILDING,
                        onClick = {
                            session.activeTool = EditorTool.BUILDING
                            session.showBuildingsOverlay = true
                        },
                        label = { Text(stringResource(Res.string.tool_building)) },
                        leadingIcon = { HMIcon(activeSpec.iconName, size = 15.sp, color = activeSpec.color) }
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    commonBuildingTypes.forEach { bType ->
                        val spec = visualSpecForBuilding(bType)
                        HMFilterChip(
                            selected = session.buildingTypeToPlace == bType,
                            onClick = {
                                session.buildingTypeToPlace = bType
                                session.activeTool = EditorTool.BUILDING
                                session.showBuildingsOverlay = true
                            },
                            leadingIcon = { HMIcon(spec.iconName, size = 14.sp, color = spec.color) },
                            label = { Text(buildingTypeLabel(bType), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (session.buildingTypeToPlace == "naval_base_spawn" || session.buildingTypeToPlace == "floating_harbor") {
                    HMTextField(
                        value = session.buildingSeaProvinceInput,
                        onValueChange = { session.buildingSeaProvinceInput = it.filter { c -> c.isDigit() } },
                        label = stringResource(Res.string.building_sea_prov_label),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (selectedState != null) {
                    val stateBuildings = remember(engineState.buildingsRevision, selectedState.id) {
                        engineState.buildings.filter { it.stateId == selectedState.id }
                    }
                    HMHorizontalDivider()
                    Text(
                        text = stringResource(Res.string.state_buildings_list, selectedState.id, stateBuildings.size),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    HMButton(
                        onClick = {
                            session.showBuildingsOverlay = true
                            session.engine.autoGenerateBuildingsForStateAsync(selectedState.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(Res.string.auto_generate_state_buildings, selectedState.id), style = MaterialTheme.typography.labelSmall)
                    }

                    stateBuildings.take(20).forEach { b ->
                        val spec = visualSpecForBuilding(b.type)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f).clickable {
                                    session.centerCameraAt(b.x.toInt(), (engineState.mapHeight - b.z).toInt(), 8f)
                                }
                            ) {
                                HMIcon(spec.iconName, size = 15.sp, color = spec.color)
                                Text(
                                    text = "${buildingTypeLabel(b.type)} (${b.x.toInt()}, ${b.z.toInt()})${if (b.adjacentSeaProvince > 0) stringResource(Res.string.building_adjacent_sea_format, b.adjacentSeaProvince) else ""}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            HMIconButton(onClick = { session.engine.removeBuildingAsync(b) }) {
                                HMIcon("delete", size = 15.sp)
                            }
                        }
                    }
                }
            }
        }

        // 2. Railways & Supply Nodes
        HMPanel(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant, contentPadding = PaddingValues(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.railway_tool_section), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HMFilterChip(
                        selected = session.activeTool == EditorTool.RAILWAY,
                        onClick = {
                            session.activeTool = EditorTool.RAILWAY
                            session.showRailwaysOverlay = true
                        },
                        label = { Text(stringResource(Res.string.tool_railway)) },
                        leadingIcon = { HMIcon("train", size = 15.sp, color = Color(0xFFFFB703)) }
                    )
                    HMFilterChip(
                        selected = session.activeTool == EditorTool.SUPPLY_NODE,
                        onClick = {
                            session.activeTool = EditorTool.SUPPLY_NODE
                            session.showSupplyOverlay = true
                        },
                        label = { Text(stringResource(Res.string.tool_supply)) },
                        leadingIcon = { HMIcon("warehouse", size = 15.sp, color = SupplyNodeVisualSpec.color) }
                    )
                }
                Text(stringResource(Res.string.railway_level_label, session.railwayLevel.roundToInt()), style = MaterialTheme.typography.bodySmall)
                Slider(value = session.railwayLevel, onValueChange = { session.railwayLevel = it }, valueRange = 1f..5f, steps = 3)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    HMButton(
                        onClick = { session.finishActiveRailway() },
                        enabled = session.activeRailwayPath.size >= 2,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.finish_railway_btn, session.activeRailwayPath.size), style = MaterialTheme.typography.labelSmall)
                    }
                    HMOutlinedButton(
                        onClick = { session.engine.removeLastRailwayAsync() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.delete_last_railway_btn), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}