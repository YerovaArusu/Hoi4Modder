package at.yerova.hoi4_modder.v2.ui.screens.views.map

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import at.yerova.hoi4_modder.v2.ui.navigation.*
import at.yerova.hoi4_modder.v2.ui.state.MapEditorSessionState
import hoi4_modder.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProvincesModeView(
    route: ProvincesRoute,
    session: MapEditorSessionState,
    engineState: EngineState
) {
    LaunchedEffect(route.provinceId) {
        route.provinceId?.let { pid ->
            engineState.provincesById[pid]?.let { prov ->
                session.handleProvinceSelection(prov, false, engineState)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryScrollableTabRow(
            selectedTabIndex = session.provinceSubTab.ordinal,
            edgePadding = 8.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = { HMHorizontalDivider() }
        ) {
            Tab(
                selected = session.provinceSubTab == ProvinceSidebarSubTab.INSPECTOR,
                onClick = { session.provinceSubTab = ProvinceSidebarSubTab.INSPECTOR },
                text = { Text(stringResource(Res.string.tab_inspector), style = MaterialTheme.typography.labelMedium) }
            )
            Tab(
                selected = session.provinceSubTab == ProvinceSidebarSubTab.LIST,
                onClick = { session.provinceSubTab = ProvinceSidebarSubTab.LIST },
                text = { Text(stringResource(Res.string.tab_provinces), style = MaterialTheme.typography.labelMedium) }
            )
            Tab(
                selected = session.provinceSubTab == ProvinceSidebarSubTab.DIAGNOSTICS,
                onClick = { session.provinceSubTab = ProvinceSidebarSubTab.DIAGNOSTICS },
                text = { Text(stringResource(Res.string.tab_diagnostics), style = MaterialTheme.typography.labelMedium) }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (session.provinceSubTab) {
                ProvinceSidebarSubTab.INSPECTOR -> ProvinceInspectorSection(session, engineState)
                ProvinceSidebarSubTab.LIST -> ProvinceListSection(session, engineState)
                ProvinceSidebarSubTab.DIAGNOSTICS -> DiagnosticsSection(session, engineState)
            }
        }
    }
}

@Composable
private fun ProvinceInspectorSection(
    session: MapEditorSessionState,
    engineState: EngineState
) {
    val knownTypes = listOf("land", "sea", "lake")
    val primaryProvince = session.primaryProvince(engineState)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HMButton(onClick = { session.showNewProvinceDialog = true }, modifier = Modifier.fillMaxWidth()) {
            HMIcon("add_circle", size = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.create_province_btn), fontWeight = FontWeight.SemiBold)
        }

        Text(
            text = stringResource(Res.string.nav_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HMHorizontalDivider()

        Text(
            stringResource(Res.string.active_province_header),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        if (primaryProvince == null) {
            Text(
                text = stringResource(Res.string.no_province_selected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val currentOwnerStateId = engineState.provinceToStateId[primaryProvince.id]
            val currentOwnerState = currentOwnerStateId?.let { engineState.statesById[it] }
            val currentRegionId = engineState.provinceToStrategicRegionId[primaryProvince.id]
            val currentRegion = currentRegionId?.let { engineState.strategicRegionsById[it] }

            if (session.selectedProvinceIds.size > 1) {
                HMPanel(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    contentPadding = PaddingValues(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.multi_select_title, session.selectedProvinceIds.size),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HMOutlinedButton(
                            onClick = { session.clearSelection() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(stringResource(Res.string.clear_selection_btn), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        session.selectedProvinceIds.forEach { pid ->
                            HMFilterChip(
                                selected = true,
                                onClick = { engineState.provincesById[pid]?.let { session.centerOnProvince(it) } },
                                label = { Text("#$pid", style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = {
                                    HMIcon(
                                        "close",
                                        size = 13.sp,
                                        modifier = Modifier.clickable {
                                            session.selectedProvinceIds.remove(pid)
                                            if (session.primaryProvinceId == pid) {
                                                session.primaryProvinceId = session.selectedProvinceIds.lastOrNull()
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }

            HMPanel(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentPadding = PaddingValues(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color(primaryProvince.r, primaryProvince.g, primaryProvince.b), HMDefaults.ComponentShape)
                                    .border(1.dp, Color.White, HMDefaults.ComponentShape)
                            )
                            Column {
                                Text(stringResource(Res.string.province_title, primaryProvince.id), fontWeight = FontWeight.Bold)
                                Text(
                                    stringResource(Res.string.rgb_format, primaryProvince.r, primaryProvince.g, primaryProvince.b),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        HMIconButton(onClick = { session.centerOnProvince(primaryProvince) }, bordered = true) {
                            HMIcon("my_location", size = 18.sp)
                        }
                    }

                    Text(
                        text = if (currentOwnerState != null) {
                            stringResource(Res.string.state_owner_format, currentOwnerState.id, currentOwnerState.name)
                        } else {
                            stringResource(Res.string.state_owner_none)
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = if (currentRegion != null) {
                            stringResource(Res.string.region_owner_format, currentRegion.id, currentRegion.name)
                        } else {
                            stringResource(Res.string.region_owner_none)
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = if (currentRegion != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        HMOutlinedButton(onClick = { session.copyActiveProvince(engineState) }, modifier = Modifier.weight(1f)) {
                            HMIcon("content_copy", size = 15.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.copy_province), style = MaterialTheme.typography.labelSmall)
                        }
                        HMOutlinedButton(
                            onClick = { session.pasteToSelectedProvinces() },
                            enabled = session.copiedClipboard != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            HMIcon("content_paste", size = 15.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.paste_province), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Navigation 3 Assign Actions
            Text(
                stringResource(Res.string.state_assign_header),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HMButton(
                    onClick = {
                        val ids = session.effectiveSelectedProvinceIds()
                        if (ids.isNotEmpty()) {
                            session.navigateToRoute(
                                StatesRoute(
                                    assignMode = true,
                                    sourceProvinceId = ids.first(),
                                    sourceProvinceIds = ids
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    HMIcon("touch_app", size = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.pick_state_on_map_btn), style = MaterialTheme.typography.labelMedium)
                }
                HMOutlinedButton(onClick = { session.showNewStateDialog = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.move_to_new_state_btn), style = MaterialTheme.typography.labelMedium)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HMButton(
                    onClick = {
                        val ids = session.effectiveSelectedProvinceIds()
                        if (ids.isNotEmpty()) {
                            session.navigateToRoute(
                                StrategicRegionsRoute(
                                    assignMode = true,
                                    sourceProvinceIds = ids
                                )
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                ) {
                    HMIcon("touch_app", size = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.pick_region_on_map_btn), style = MaterialTheme.typography.labelMedium)
                }
                HMOutlinedButton(onClick = { session.showNewRegionDialog = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.move_to_new_region_btn), style = MaterialTheme.typography.labelMedium)
                }
            }

            HMHorizontalDivider()

            Text(
                stringResource(Res.string.edit_def_header),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                knownTypes.forEach { t ->
                    HMFilterChip(
                        selected = primaryProvince.type == t,
                        onClick = {
                            session.engine.batchEditProvinceDefinitionsAsync(session.effectiveSelectedProvinceIds()) { p ->
                                val newTerrain = if (t == "sea" && p.terrain != "ocean") "ocean" else p.terrain
                                p.copy(type = t, terrain = newTerrain)
                            }
                        },
                        label = { Text(provinceTypeLabel(t)) }
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KnownTerrainSpecs.forEach { spec ->
                    HMFilterChip(
                        selected = primaryProvince.terrain == spec.id,
                        onClick = {
                            session.engine.batchEditProvinceDefinitionsAsync(session.effectiveSelectedProvinceIds()) { p ->
                                p.copy(terrain = spec.id)
                            }
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(spec.mapColor, HMDefaults.ComponentShape)
                            )
                        },
                        label = { Text(terrainLabel(spec.id), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            HMTextField(
                value = primaryProvince.continent.toString(),
                onValueChange = { str ->
                    val c = str.filter { it.isDigit() }.toIntOrNull() ?: 0
                    session.engine.batchEditProvinceDefinitionsAsync(session.effectiveSelectedProvinceIds()) { p ->
                        p.copy(continent = c)
                    }
                },
                label = stringResource(Res.string.field_continent),
                modifier = Modifier.fillMaxWidth()
            )

            HMCheckbox(
                checked = primaryProvince.coastal,
                onCheckedChange = { isCoastal ->
                    session.engine.batchEditProvinceDefinitionsAsync(session.effectiveSelectedProvinceIds()) { p ->
                        p.copy(coastal = isCoastal)
                    }
                },
                label = stringResource(Res.string.field_coastal)
            )
        }
    }
}

@Composable
private fun ProvinceListSection(
    session: MapEditorSessionState,
    engineState: EngineState
) {
    var searchQuery by remember { mutableStateOf("") }

    val allProvinces = remember(engineState.provinceDefinitionsRevision, engineState.provincesById.size) {
        engineState.provincesById.values.filter { it.id > 0 }.sortedByDescending { it.id }
    }

    val filteredProvinces = remember(
        searchQuery,
        engineState.provinceDefinitionsRevision,
        engineState.stateDataRevision
    ) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) allProvinces
        else allProvinces.filter { p ->
            val stId = engineState.provinceToStateId[p.id]
            p.id.toString().contains(q) ||
                    p.terrain.lowercase().contains(q) ||
                    p.type.lowercase().contains(q) ||
                    (stId != null && "state $stId".contains(q))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HMTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = stringResource(Res.string.search_provinces_label),
            leadingIcon = { HMIcon("search", size = 18.sp) },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = stringResource(Res.string.provinces_count_format, filteredProvinces.size, allProvinces.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filteredProvinces, key = { it.id }) { prov ->
                val isSelected = session.selectedProvinceIds.contains(prov.id)
                val stateId = engineState.provinceToStateId[prov.id]

                HMPanel(
                    modifier = Modifier.fillMaxWidth().clickable {
                        session.handleProvinceSelection(prov, session.isCtrlHeld, engineState)
                    },
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentPadding = PaddingValues(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(Color(prov.r, prov.g, prov.b), HMDefaults.ComponentShape)
                                    .border(1.dp, Color.White, HMDefaults.ComponentShape)
                            )
                            Column {
                                Text(
                                    text = "#${prov.id} - ${terrainLabel(prov.terrain)} (${provinceTypeLabel(prov.type)})",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = stringResource(Res.string.province_rgb_state_format, prov.r, prov.g, prov.b, stateId?.toString() ?: "-"),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        HMIconButton(
                            onClick = {
                                session.handleProvinceSelection(prov, false, engineState)
                                session.centerOnProvince(prov)
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

@Composable
private fun DiagnosticsSection(
    session: MapEditorSessionState,
    engineState: EngineState
) {
    val issues = engineState.diagnosticsIssues

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.diag_header), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.diag_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HMButton(onClick = { session.engine.runDiagnosticsAsync() }, modifier = Modifier.fillMaxWidth()) {
            HMIcon("bug_report", size = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.run_scan_btn), fontWeight = FontWeight.SemiBold)
        }

        HMOutlinedButton(onClick = { session.engine.autoFixCoastalFlagsAsync() }, modifier = Modifier.fillMaxWidth()) {
            HMIcon("build", size = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.autofix_coastal_btn))
        }

        HMHorizontalDivider()

        if (issues != null) {
            if (issues.isEmpty()) {
                HMPanel(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer, contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HMIcon("check_circle", size = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.no_issues_found), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Text(stringResource(Res.string.issues_count_format, issues.size), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(issues) { issue ->
                        HMPanel(
                            modifier = Modifier.fillMaxWidth().clickable {
                                when {
                                    issue.mapCoordinates != null -> session.centerCameraAt(issue.mapCoordinates.first, issue.mapCoordinates.second, 12f)
                                    issue.provinceId != null -> engineState.provincesById[issue.provinceId]?.let { prov ->
                                        session.handleProvinceSelection(prov, false, engineState)
                                        session.centerOnProvince(prov)
                                    }
                                    issue.stateId != null -> engineState.statesById[issue.stateId]?.let { st ->
                                        session.selectedStateId = st.id
                                        session.navigateToRoute(StatesRoute())
                                        session.centerOnState(st)
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                HMIcon("warning", size = 16.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                Text(issue.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(issue.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }
    }
}