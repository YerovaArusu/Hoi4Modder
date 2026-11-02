package at.yerova.hoi4_modder.v2.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import at.yerova.hoi4_modder.v2.engine.EngineState
import at.yerova.hoi4_modder.v2.ui.navigation.*
import hoi4_modder.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

enum class EditorTool {
    NAVIGATE,
    EYEDROPPER,
    BRUSH,
    BUCKET,
    RAILWAY,
    SUPPLY_NODE,
    BUILDING
}

enum class ProvinceSidebarSubTab {
    INSPECTOR,
    LIST,
    DIAGNOSTICS
}

enum class StateSidebarSubTab {
    STATES,
    INFRASTRUCTURE
}

fun allowedToolsForRoute(route: EditorRoute): List<EditorTool> = when (route) {
    is ProvincesRoute -> listOf(
        EditorTool.NAVIGATE,
        EditorTool.EYEDROPPER,
        EditorTool.BRUSH,
        EditorTool.BUCKET,
        EditorTool.RAILWAY,
        EditorTool.SUPPLY_NODE,
        EditorTool.BUILDING
    )

    is StatesRoute -> listOf(
        EditorTool.NAVIGATE,
        EditorTool.EYEDROPPER,
        EditorTool.BUILDING,
        EditorTool.RAILWAY,
        EditorTool.SUPPLY_NODE
    )

    is StrategicRegionsRoute, is TerrainRoute, is CoastalRoute -> listOf(
        EditorTool.NAVIGATE,
        EditorTool.EYEDROPPER
    )
}

@Immutable
data class BuildingVisualSpec(
    val iconName: String,
    val color: Color
)

fun visualSpecForBuilding(type: String): BuildingVisualSpec = when {
    type.contains("supply_hub") -> BuildingVisualSpec("hub", Color(0xFF00F5D4))
    type.contains("naval") || type.contains("harbor") || type.contains("dockyard") ->
        BuildingVisualSpec("anchor", Color(0xFF38BDF8))

    type.contains("air_base") -> BuildingVisualSpec("flight", Color(0xFF60A5FA))
    type.contains("anti_air") -> BuildingVisualSpec("radar", Color(0xFFFACC15))
    type.contains("bunker") -> BuildingVisualSpec("shield", Color(0xFFCBD5E1))
    type.contains("synthetic_refinery") -> BuildingVisualSpec("oil_barrel", Color(0xFF34D399))
    type.contains("nuclear_reactor") -> BuildingVisualSpec("bolt", Color(0xFFC084FC))
    type.contains("rocket") -> BuildingVisualSpec("rocket_launch", Color(0xFFFB7185))
    type == "arms_factory" -> BuildingVisualSpec("factory", Color(0xFF4ADE80))
    type == "industrial_complex" || type.contains("factory") ->
        BuildingVisualSpec("factory", Color(0xFFF59E0B))

    else -> BuildingVisualSpec("domain", Color(0xFFE2E8F0))
}

val SupplyNodeVisualSpec = BuildingVisualSpec("warehouse", Color(0xFF00F5D4))

val AllMapIconSpecs: List<BuildingVisualSpec> = listOf(
    SupplyNodeVisualSpec,
    visualSpecForBuilding("naval_supply_hub"),
    visualSpecForBuilding("dockyard"),
    visualSpecForBuilding("air_base"),
    visualSpecForBuilding("anti_air_building"),
    visualSpecForBuilding("bunker"),
    visualSpecForBuilding("synthetic_refinery"),
    visualSpecForBuilding("nuclear_reactor_spawn"),
    visualSpecForBuilding("rocket_site"),
    visualSpecForBuilding("arms_factory"),
    visualSpecForBuilding("industrial_complex"),
    visualSpecForBuilding("other")
).distinctBy { it.iconName to it.color }

@Immutable
data class TerrainVisualSpec(
    val id: String,
    val mapColor: Color,
    val defaultProvinceType: String = "land"
)

val KnownTerrainSpecs: List<TerrainVisualSpec> = listOf(
    TerrainVisualSpec("plains", Color(0xFF8CB369), "land"),
    TerrainVisualSpec("forest", Color(0xFF386641), "land"),
    TerrainVisualSpec("hills", Color(0xFFBC6C25), "land"),
    TerrainVisualSpec("mountain", Color(0xFF6C757D), "land"),
    TerrainVisualSpec("urban", Color(0xFFE63946), "land"),
    TerrainVisualSpec("jungle", Color(0xFF1B4332), "land"),
    TerrainVisualSpec("marsh", Color(0xFF457B9D), "land"),
    TerrainVisualSpec("desert", Color(0xFFF4A261), "land"),
    TerrainVisualSpec("ocean", Color(0xFF13293D), "sea"),
    TerrainVisualSpec("lakes", Color(0xFF2A628F), "lake")
)

@Composable
fun provinceTypeLabel(type: String): String = when (type) {
    "land" -> stringResource(Res.string.province_type_land)
    "sea" -> stringResource(Res.string.province_type_sea)
    "lake" -> stringResource(Res.string.province_type_lake)
    else -> type
}

@Composable
fun terrainLabel(terrain: String): String = when (terrain) {
    "plains" -> stringResource(Res.string.terrain_plains)
    "forest" -> stringResource(Res.string.terrain_forest)
    "hills" -> stringResource(Res.string.terrain_hills)
    "mountain" -> stringResource(Res.string.terrain_mountain)
    "urban" -> stringResource(Res.string.terrain_urban)
    "jungle" -> stringResource(Res.string.terrain_jungle)
    "marsh" -> stringResource(Res.string.terrain_marsh)
    "desert" -> stringResource(Res.string.terrain_desert)
    "ocean" -> stringResource(Res.string.terrain_ocean)
    "lakes" -> stringResource(Res.string.terrain_lakes)
    else -> terrain
}

@Composable
fun buildingTypeLabel(type: String): String = when (type) {
    "arms_factory" -> stringResource(Res.string.building_arms_factory)
    "industrial_complex" -> stringResource(Res.string.building_industrial_complex)
    "air_base" -> stringResource(Res.string.building_air_base)
    "bunker" -> stringResource(Res.string.building_bunker)
    "coastal_bunker" -> stringResource(Res.string.building_coastal_bunker)
    "dockyard" -> stringResource(Res.string.building_dockyard)
    "naval_base_spawn" -> stringResource(Res.string.building_naval_base_spawn)
    "floating_harbor" -> stringResource(Res.string.building_floating_harbor)
    "anti_air_building" -> stringResource(Res.string.building_anti_air_building)
    "synthetic_refinery" -> stringResource(Res.string.building_synthetic_refinery)
    "nuclear_reactor_spawn" -> stringResource(Res.string.building_nuclear_reactor_spawn)
    "naval_supply_hub" -> stringResource(Res.string.building_naval_supply_hub)
    else -> type
}

@Composable
fun localizedEngineStatus(state: EngineState): String {
    val args = state.statusMessageArgs
    return when (state.statusMessageKey) {
        "engine_status_project_loaded" -> stringResource(
            Res.string.engine_status_project_loaded,
            args.getOrElse(0) { "" })

        "engine_status_load_error" -> stringResource(Res.string.engine_status_load_error, args.getOrElse(0) { "" })
        "engine_status_flood_filled" -> stringResource(Res.string.engine_status_flood_filled, args.getOrElse(0) { "" })
        "engine_status_undo" -> stringResource(
            Res.string.engine_status_undo,
            args.getOrElse(0) { "" },
            args.getOrElse(1) { "" })

        "engine_status_redo" -> stringResource(
            Res.string.engine_status_redo,
            args.getOrElse(0) { "" },
            args.getOrElse(1) { "" })

        "engine_status_nothing_to_undo" -> stringResource(Res.string.engine_status_nothing_to_undo)
        "engine_status_nothing_to_redo" -> stringResource(Res.string.engine_status_nothing_to_redo)
        "engine_status_centers_recalculated" -> stringResource(Res.string.engine_status_centers_recalculated)
        "engine_status_new_province" -> stringResource(
            Res.string.engine_status_new_province,
            args.getOrElse(0) { "" },
            args.getOrElse(1) { "" },
            args.getOrElse(2) { "" },
            args.getOrElse(3) { "" })

        "engine_status_assigned_state" -> stringResource(
            Res.string.engine_status_assigned_state,
            args.getOrElse(0) { "" },
            args.getOrElse(1) { "" },
            args.getOrElse(2) { "" })

        "engine_status_removed_from_state" -> stringResource(
            Res.string.engine_status_removed_from_state,
            args.getOrElse(0) { "" },
            args.getOrElse(1) { "" })

        "engine_status_state_created" -> stringResource(
            Res.string.engine_status_state_created,
            args.getOrElse(0) { "" },
            args.getOrElse(1) { "" },
            args.getOrElse(2) { "" })

        "engine_status_assigned_region" -> stringResource(
            Res.string.engine_status_assigned_region,
            args.getOrElse(0) { "" },
            args.getOrElse(1) { "" },
            args.getOrElse(2) { "" })

        "engine_status_region_created" -> stringResource(
            Res.string.engine_status_region_created,
            args.getOrElse(0) { "" },
            args.getOrElse(1) { "" })

        "engine_status_railway_added" -> stringResource(
            Res.string.engine_status_railway_added,
            args.getOrElse(0) { "" },
            args.getOrElse(1) { "" })

        "engine_status_railway_deleted" -> stringResource(Res.string.engine_status_railway_deleted)
        "engine_status_building_needs_state" -> stringResource(Res.string.engine_status_building_needs_state)
        "engine_status_building_placed" -> stringResource(
            Res.string.engine_status_building_placed,
            args.getOrElse(0) { "" },
            args.getOrElse(1) { "" },
            args.getOrElse(2) { "" },
            args.getOrElse(3) { "" })

        "engine_status_building_removed" -> stringResource(
            Res.string.engine_status_building_removed,
            args.getOrElse(0) { "" },
            args.getOrElse(1) { "" })

        "engine_status_buildings_generated" -> stringResource(
            Res.string.engine_status_buildings_generated,
            args.getOrElse(0) { "" },
            args.getOrElse(1) { "" })

        "engine_status_adjacency_added" -> stringResource(
            Res.string.engine_status_adjacency_added,
            args.getOrElse(0) { "" },
            args.getOrElse(1) { "" })

        "engine_status_adjacency_removed" -> stringResource(
            Res.string.engine_status_adjacency_removed,
            args.getOrElse(0) { "" },
            args.getOrElse(1) { "" })

        "engine_status_diagnostics_finished" -> stringResource(
            Res.string.engine_status_diagnostics_finished,
            args.getOrElse(0) { "" })

        "engine_status_coastal_fixed" -> stringResource(
            Res.string.engine_status_coastal_fixed,
            args.getOrElse(0) { "" })

        "engine_status_coastal_already_correct" -> stringResource(Res.string.engine_status_coastal_already_correct)
        "engine_status_saving" -> stringResource(Res.string.engine_status_saving)
        "engine_status_saved" -> stringResource(Res.string.engine_status_saved)
        "engine_status_save_error" -> stringResource(Res.string.engine_status_save_error, args.getOrElse(0) { "" })
        "engine_status_supply_added" -> stringResource(Res.string.engine_status_supply_added, args.getOrElse(0) { "" })
        "engine_status_supply_removed" -> stringResource(
            Res.string.engine_status_supply_removed,
            args.getOrElse(0) { "" })

        else -> state.statusMessage
    }
}

@Composable
fun localizedLoadingStep(state: EngineState): String = when (state.loadingStepKey) {
    "loading_prepare_directories" -> stringResource(Res.string.loading_prepare_directories)
    "loading_definitions_states_regions" -> stringResource(Res.string.loading_definitions_states_regions)
    "loading_decode_bitmap" -> stringResource(Res.string.loading_decode_bitmap)
    "loading_centers_bounds" -> stringResource(Res.string.loading_centers_bounds)
    "loading_infrastructure" -> stringResource(Res.string.loading_infrastructure)
    "loading_render_map" -> stringResource(Res.string.loading_render_map)
    else -> state.loadingStep
}