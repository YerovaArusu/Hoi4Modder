package at.yerova.hoi4_modder.v2.ui.screens.views.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.yerova.hoi4_modder.v2.engine.EngineState
import at.yerova.hoi4_modder.v2.ui.*
import at.yerova.hoi4_modder.v2.ui.state.MapEditorSessionState
import hoi4_modder.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun CoastalModeView(
    session: MapEditorSessionState,
    engineState: EngineState
) {
    val primaryProvince = session.primaryProvince(engineState)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            stringResource(Res.string.coastal_editor_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        HMPanel(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentPadding = PaddingValues(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CoastalLegendRow(Color(0xFF06D6A0), "Coastal Land Province (coastal = true)")
                CoastalLegendRow(Color(0xFF4A7C59), "Inland Land Province (coastal = false)")
                CoastalLegendRow(Color(0xFF26547C), "Coastal Sea / Lake")
                CoastalLegendRow(Color(0xFF142438), "Deep Ocean")
            }
        }

        HMButton(
            onClick = { session.engine.autoFixCoastalFlagsAsync() },
            modifier = Modifier.fillMaxWidth()
        ) {
            HMIcon("build", size = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.autofix_coastal_btn), fontWeight = FontWeight.SemiBold)
        }

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
                    Text("Click on Map to Toggle Coastal", fontWeight = FontWeight.Bold)
                    Text(
                        "Directly flip coastal = true/false when clicking provinces on the map.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HMSwitch(
                    checked = session.toggleCoastalOnMapClick,
                    onCheckedChange = { session.toggleCoastalOnMapClick = it }
                )
            }
        }

        if (primaryProvince != null) {
            HMPanel(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentPadding = PaddingValues(12.dp)
            ) {
                Text(
                    stringResource(
                        Res.string.selected_province_count_format,
                        primaryProvince.id,
                        session.effectiveSelectedProvinceIds().size
                    ),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
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
        } else {
            Text(stringResource(Res.string.no_province_selected), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HMHorizontalDivider()

        // Adjacencies / Straits
        HMPanel(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentPadding = PaddingValues(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.adjacencies_section, engineState.adjacencies.size),
                    fontWeight = FontWeight.Bold
                )
                HMButton(onClick = { session.showAddAdjacencyDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    HMIcon("add_link", size = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.add_adjacency_btn))
                }
                engineState.adjacencies.takeLast(12).reversed().forEach { adj ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${adj.fromProvince} <-> #${adj.toProvince} (${adj.ruleName.ifBlank { adj.type }})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        HMIconButton(onClick = { session.engine.removeAdjacencyAsync(adj) }) {
                            HMIcon("delete", size = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoastalLegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(color, HMDefaults.ComponentShape)
                .border(1.dp, Color.White, HMDefaults.ComponentShape)
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}