package at.yerova.hoi4_modder.v2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.yerova.hoi4_modder.v2.ui.HMButton
import at.yerova.hoi4_modder.v2.ui.HMDefaults
import at.yerova.hoi4_modder.v2.ui.HMFilterChip
import at.yerova.hoi4_modder.v2.ui.HMIcon
import at.yerova.hoi4_modder.v2.ui.model.*
import at.yerova.hoi4_modder.v2.ui.navigation.CoastalRoute
import at.yerova.hoi4_modder.v2.ui.navigation.TerrainRoute
import at.yerova.hoi4_modder.v2.ui.state.MapEditorSessionState
import hoi4_modder.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun MapToolsToolbar(
    session: MapEditorSessionState
) {
    val currentRoute = session.currentRoute
    val allowedTools = allowedToolsForRoute(currentRoute)

    LaunchedEffect(currentRoute) {
        if (session.activeTool !in allowedTools) {
            session.activeTool = EditorTool.EYEDROPPER
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (EditorTool.NAVIGATE in allowedTools) {
                    HMFilterChip(
                        selected = session.activeTool == EditorTool.NAVIGATE,
                        onClick = { session.activeTool = EditorTool.NAVIGATE },
                        label = {
                            Text(
                                stringResource(Res.string.tool_navigate),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = { HMIcon("open_with", size = 14.sp) }
                    )
                }
                if (EditorTool.EYEDROPPER in allowedTools) {
                    HMFilterChip(
                        selected = session.activeTool == EditorTool.EYEDROPPER,
                        onClick = { session.activeTool = EditorTool.EYEDROPPER },
                        label = {
                            Text(
                                stringResource(Res.string.tool_eyedropper),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = { HMIcon("colorize", size = 14.sp) }
                    )
                }
                if (EditorTool.BRUSH in allowedTools) {
                    HMFilterChip(
                        selected = session.activeTool == EditorTool.BRUSH,
                        onClick = { session.activeTool = EditorTool.BRUSH },
                        label = {
                            Text(
                                stringResource(Res.string.tool_brush),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = { HMIcon("brush", size = 14.sp) }
                    )
                }
                if (EditorTool.BUCKET in allowedTools) {
                    HMFilterChip(
                        selected = session.activeTool == EditorTool.BUCKET,
                        onClick = { session.activeTool = EditorTool.BUCKET },
                        label = {
                            Text(
                                stringResource(Res.string.tool_bucket),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = { HMIcon("format_color_fill", size = 14.sp) }
                    )
                }
                if (EditorTool.RAILWAY in allowedTools) {
                    HMFilterChip(
                        selected = session.activeTool == EditorTool.RAILWAY,
                        onClick = {
                            session.activeTool = EditorTool.RAILWAY
                            session.showRailwaysOverlay = true
                        },
                        label = {
                            Text(
                                stringResource(Res.string.tool_railway),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = { HMIcon("train", size = 14.sp, color = Color(0xFFFFB703)) }
                    )
                }
                if (EditorTool.SUPPLY_NODE in allowedTools) {
                    HMFilterChip(
                        selected = session.activeTool == EditorTool.SUPPLY_NODE,
                        onClick = {
                            session.activeTool = EditorTool.SUPPLY_NODE
                            session.showSupplyOverlay = true
                        },
                        label = {
                            Text(
                                stringResource(Res.string.tool_supply),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = { HMIcon("warehouse", size = 14.sp, color = SupplyNodeVisualSpec.color) }
                    )
                }
                if (EditorTool.BUILDING in allowedTools) {
                    val bSpec = visualSpecForBuilding(session.buildingTypeToPlace)
                    HMFilterChip(
                        selected = session.activeTool == EditorTool.BUILDING,
                        onClick = {
                            session.activeTool = EditorTool.BUILDING
                            session.showBuildingsOverlay = true
                        },
                        label = {
                            Text(
                                stringResource(Res.string.tool_building),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = { HMIcon(bSpec.iconName, size = 14.sp, color = bSpec.color) }
                    )
                }

                if (currentRoute is TerrainRoute) {
                    val spec =
                        KnownTerrainSpecs.find { it.id == session.activeTerrainBrush } ?: KnownTerrainSpecs.first()
                    HMFilterChip(
                        selected = session.paintTerrainOnMapClick,
                        onClick = { session.paintTerrainOnMapClick = !session.paintTerrainOnMapClick },
                        label = {
                            Text(
                                "Paint Terrain on Map: ${terrainLabel(spec.id)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(spec.mapColor, HMDefaults.ComponentShape)
                                    .border(1.dp, Color.White, HMDefaults.ComponentShape)
                            )
                        }
                    )
                }

                if (currentRoute is CoastalRoute) {
                    HMFilterChip(
                        selected = session.toggleCoastalOnMapClick,
                        onClick = { session.toggleCoastalOnMapClick = !session.toggleCoastalOnMapClick },
                        label = {
                            Text(
                                "Click Province to Toggle Coastal",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = { HMIcon("waves", size = 14.sp, color = Color(0xFF06D6A0)) }
                    )
                }
            }

            if (session.activeTool == EditorTool.BRUSH && EditorTool.BRUSH in allowedTools) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.width(220.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.brush_radius, session.brushRadius.roundToInt()),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Slider(
                        value = session.brushRadius,
                        onValueChange = { session.brushRadius = it },
                        valueRange = 0f..30f,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (session.activeTool == EditorTool.RAILWAY && session.activeRailwayPath.size >= 2) {
                HMButton(
                    onClick = { session.finishActiveRailway() },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        stringResource(Res.string.finish_railway_btn, session.activeRailwayPath.size),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}