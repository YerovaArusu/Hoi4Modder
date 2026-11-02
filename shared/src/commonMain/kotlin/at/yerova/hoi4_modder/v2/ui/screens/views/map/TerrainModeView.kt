package at.yerova.hoi4_modder.v2.ui.screens.views.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.yerova.hoi4_modder.v2.engine.EngineState
import at.yerova.hoi4_modder.v2.ui.*
import at.yerova.hoi4_modder.v2.ui.model.KnownTerrainSpecs
import at.yerova.hoi4_modder.v2.ui.model.provinceTypeLabel
import at.yerova.hoi4_modder.v2.ui.model.terrainLabel
import at.yerova.hoi4_modder.v2.ui.state.MapEditorSessionState
import hoi4_modder.shared.generated.resources.Res
import hoi4_modder.shared.generated.resources.terrain_editor_desc
import hoi4_modder.shared.generated.resources.terrain_editor_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun TerrainModeView(
    session: MapEditorSessionState,
    engineState: EngineState
) {
    val primaryProvince = session.primaryProvince(engineState)

    val terrainCounts = remember(engineState.provinceDefinitionsRevision, engineState.provincesById.size) {
        engineState.provincesById.values
            .filter { it.id > 0 }
            .groupingBy { it.terrain }
            .eachCount()
    }

    val matchingProvinces = remember(
        session.activeTerrainBrush,
        engineState.provinceDefinitionsRevision,
        engineState.provincesById.size
    ) {
        engineState.provincesById.values
            .filter { it.id > 0 && it.terrain == session.activeTerrainBrush }
            .sortedByDescending { it.id }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(Res.string.terrain_editor_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(Res.string.terrain_editor_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HMPanel(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentPadding = PaddingValues(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Interactive Terrain Brush", fontWeight = FontWeight.Bold)
                    Text(
                        "Click or drag across provinces on the map to paint '${terrainLabel(session.activeTerrainBrush)}'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HMSwitch(
                    checked = session.paintTerrainOnMapClick,
                    onCheckedChange = { session.paintTerrainOnMapClick = it }
                )
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KnownTerrainSpecs.forEach { spec ->
                val count = terrainCounts[spec.id] ?: 0
                val isSelectedBrush = session.activeTerrainBrush == spec.id
                HMFilterChip(
                    selected = isSelectedBrush,
                    onClick = {
                        session.activeTerrainBrush = spec.id
                        if (!session.paintTerrainOnMapClick && session.effectiveSelectedProvinceIds().isNotEmpty()) {
                            session.engine.batchEditProvinceDefinitionsAsync(session.effectiveSelectedProvinceIds()) { p ->
                                p.copy(terrain = spec.id, type = spec.defaultProvinceType)
                            }
                        }
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(spec.mapColor, HMDefaults.ComponentShape)
                                .border(1.dp, Color.White, HMDefaults.ComponentShape)
                        )
                    },
                    label = { Text("${terrainLabel(spec.id)} ($count)", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        if (session.effectiveSelectedProvinceIds().isNotEmpty()) {
            HMButton(
                onClick = {
                    val spec = KnownTerrainSpecs.find { it.id == session.activeTerrainBrush }
                    session.engine.batchEditProvinceDefinitionsAsync(session.effectiveSelectedProvinceIds()) { p ->
                        p.copy(
                            terrain = session.activeTerrainBrush,
                            type = spec?.defaultProvinceType ?: p.type
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                HMIcon("format_paint", size = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Apply '${terrainLabel(session.activeTerrainBrush)}' to ${session.effectiveSelectedProvinceIds().size} Selected",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        HMHorizontalDivider()

        Text(
            text = "Provinces with '${terrainLabel(session.activeTerrainBrush)}' (${matchingProvinces.size}):",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(matchingProvinces, key = { it.id }) { prov ->
                val isSelected = primaryProvince?.id == prov.id || session.selectedProvinceIds.contains(prov.id)
                HMPanel(
                    modifier = Modifier.fillMaxWidth().clickable {
                        session.handleProvinceSelection(prov, session.isCtrlHeld, engineState)
                    },
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#${prov.id} (${provinceTypeLabel(prov.type)})", fontWeight = FontWeight.SemiBold)
                        HMIconButton(onClick = { session.centerOnProvince(prov) }) {
                            HMIcon("my_location", size = 16.sp)
                        }
                    }
                }
            }
        }
    }
}