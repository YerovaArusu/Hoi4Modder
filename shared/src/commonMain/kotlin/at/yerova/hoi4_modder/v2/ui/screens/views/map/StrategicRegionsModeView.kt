package at.yerova.hoi4_modder.v2.ui.screens.views.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.yerova.hoi4_modder.v2.engine.EngineState
import at.yerova.hoi4_modder.v2.ui.*
import at.yerova.hoi4_modder.v2.ui.navigation.StrategicRegionsRoute
import at.yerova.hoi4_modder.v2.ui.state.MapEditorSessionState
import hoi4_modder.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun StrategicRegionsModeView(
    route: StrategicRegionsRoute,
    session: MapEditorSessionState,
    engineState: EngineState
) {
    var searchQuery by remember { mutableStateOf("") }
    val selectedRegion = session.selectedRegion(engineState)

    val allRegions = remember(engineState.regionDataRevision, engineState.strategicRegionsById.size) {
        engineState.strategicRegionsById.values.sortedByDescending { it.id }
    }

    val filteredRegions = remember(searchQuery, engineState.regionDataRevision) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) allRegions
        else allRegions.filter { reg ->
            reg.id.toString().contains(q) ||
                    reg.name.lowercase().contains(q) ||
                    reg.provinces.any { it.toString() == q }
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
                    text = stringResource(Res.string.assigning_region_provinces_format, route.sourceProvinceIds.size),
                    fontWeight = FontWeight.Bold,
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

        HMButton(onClick = { session.showNewRegionDialog = true }, modifier = Modifier.fillMaxWidth()) {
            HMIcon("add_circle", size = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.move_to_new_region_btn))
        }

        HMTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = stringResource(Res.string.search_regions_label),
            leadingIcon = { HMIcon("search", size = 18.sp) },
            modifier = Modifier.fillMaxWidth()
        )

        if (selectedRegion != null && !route.assignMode) {
            HMPanel(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentPadding = PaddingValues(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.region_title_format, selectedRegion.id, selectedRegion.name),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(Res.string.region_province_count, selectedRegion.provinces.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selectedRegion.provinces.sorted().take(24).forEach { pid ->
                        HMFilterChip(
                            selected = true,
                            onClick = {
                                engineState.provincesById[pid]?.let { prov ->
                                    session.handleProvinceSelection(prov, false, engineState)
                                    session.centerOnProvince(prov)
                                }
                            },
                            label = { Text("#$pid", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filteredRegions, key = { it.id }) { reg ->
                val isSelected = selectedRegion?.id == reg.id
                HMPanel(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (route.assignMode) {
                            session.engine.assignProvincesToStrategicRegionAsync(route.sourceProvinceIds, reg.id)
                            session.selectedRegionId = reg.id
                            session.popRouteIfPossible()
                        } else {
                            session.selectedRegionId = reg.id
                        }
                    },
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentPadding = PaddingValues(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(Res.string.region_title_format, reg.id, reg.name),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(Res.string.region_province_count, reg.provinces.size),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (route.assignMode || session.selectedProvinceIds.isNotEmpty()) {
                            HMOutlinedButton(
                                onClick = {
                                    val ids =
                                        if (route.assignMode) route.sourceProvinceIds else session.selectedProvinceIds.toList()
                                    session.engine.assignProvincesToStrategicRegionAsync(ids, reg.id)
                                    session.selectedRegionId = reg.id
                                    if (route.assignMode) session.popRouteIfPossible()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    stringResource(Res.string.assign_here_btn),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}