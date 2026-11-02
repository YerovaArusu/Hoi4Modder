package at.yerova.hoi4_modder.v2.engine

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import at.yerova.hoi4_modder.v2.engine.diagnostics.MapDiagnosticsScanner
import at.yerova.hoi4_modder.v2.engine.grid.MapGridEngine
import at.yerova.hoi4_modder.v2.engine.io.BmpCodec
import at.yerova.hoi4_modder.v2.engine.io.ClausewitzParser
import at.yerova.hoi4_modder.v2.engine.io.ModFileResolver
import at.yerova.hoi4_modder.v2.engine.model.*
import at.yerova.hoi4_modder.v2.engine.render.MapTileRenderer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Unveränderlicher UI-Snapshot des gesamten Projekts.
 * Kann von Jetpack Compose jederzeit sicher auf dem GUI-Thread gelesen werden.
 */
@Immutable
data class EngineState(
    val isLoading: Boolean = false,
    val loadingStep: String = "",
    val isSaving: Boolean = false,
    val isLoaded: Boolean = false,
    val mapWidth: Int = 0,
    val mapHeight: Int = 0,
    val mapViewMode: MapViewMode = MapViewMode.PROVINCES,
    val highlightedId: Int? = null,
    val baseMapBitmap: ImageBitmap? = null,
    val paintOverlayTiles: Map<Int, ImageBitmap> = emptyMap(),
    val provincesById: Map<Int, ProvinceDef> = emptyMap(),
    val provincesByRgb: Map<Int, ProvinceDef> = emptyMap(),
    val provinceCenters: Map<Int, Pair<Int, Int>> = emptyMap(),
    val statesById: Map<Int, StateDef> = emptyMap(),
    val provinceToStateId: Map<Int, Int> = emptyMap(),
    val strategicRegionsById: Map<Int, StrategicRegionDef> = emptyMap(),
    val provinceToStrategicRegionId: Map<Int, Int> = emptyMap(),
    val buildings: List<BuildingEntry> = emptyList(),
    val railways: List<RailwayEntry> = emptyList(),
    val supplyNodes: Set<Int> = emptySet(),
    val adjacencies: List<AdjacencyEntry> = emptyList(),
    val diagnosticsIssues: List<MapIssue>? = null,
    val hasUnsavedChanges: Boolean = false,
    val unsavedSummary: String = "",
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val statusMessage: String = "Ready",
    val statusMessageKey: String? = null,
    val statusMessageArgs: List<String> = emptyList(),
    val loadingStepKey: String? = null,
    // Revisions are split by responsibility so paint snapshots don't invalidate unrelated UI work.
    val paintRevision: Int = 0,
    val provinceDefinitionsRevision: Int = 0,
    val stateDataRevision: Int = 0,
    val regionDataRevision: Int = 0,
    val buildingsRevision: Int = 0,
    val provinceCentersRevision: Int = 0,
    val railwaysRevision: Int = 0,
    val supplyNodesRevision: Int = 0,
    val adjacenciesRevision: Int = 0,
    val editRevision: Int = 0
)

class Hoi4ModderEngine(
    val vanillaPath: File,
    val modPath: File
) {
    /**
     * Eigener Single-Thread-Worker für alle Engine- und Pixel-Operationen.
     * Hält den Compose GUI-Thread zu 100 % frei und verhindert Race-Conditions ohne Locks.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val engineDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + engineDispatcher)

    val fileResolver = ModFileResolver(vanillaPath, modPath)
    private val grid = MapGridEngine()
    private val tileRenderer = MapTileRenderer()

    // Interne, mutierbare Engine-Daten (Zugriff ausschließlich auf engineDispatcher!)
    private val provincesById = mutableMapOf<Int, ProvinceDef>()
    private val provincesByRgb = mutableMapOf<Int, ProvinceDef>()
    private val statesById = mutableMapOf<Int, StateDef>()
    private val provinceToStateId = mutableMapOf<Int, Int>()
    private val duplicateStateAssignments = mutableListOf<Pair<Int, List<Int>>>()
    private val strategicRegionsById = mutableMapOf<Int, StrategicRegionDef>()
    private val provinceToStrategicRegionId = mutableMapOf<Int, Int>()
    private val buildings = mutableListOf<BuildingEntry>()
    private val railways = mutableListOf<RailwayEntry>()
    private val supplyNodes = mutableSetOf<Int>()
    private val adjacencies = mutableListOf<AdjacencyEntry>()

    private val activeOverlayTiles = mutableMapOf<Int, ImageBitmap>()
    private var currentViewMode: MapViewMode = MapViewMode.PROVINCES
    private var currentHighlightedId: Int? = null

    // Owned exclusively by engineDispatcher. Prevents duplicate begin calls from erasing a stroke.
    private var paintStrokeOpen = false
    private var provinceDefinitionsRevision = 0
    private var stateDataRevision = 0
    private var regionDataRevision = 0
    private var buildingsRevision = 0
    private var provinceCentersRevision = 0
    private var railwaysRevision = 0
    private var supplyNodesRevision = 0
    private var adjacenciesRevision = 0

    // Dirty Flags
    private var dirtyBmp = false
    private var dirtyDefinitions = false
    private var dirtyDefaultMap = false
    private val dirtyStateIds = mutableSetOf<Int>()
    private val dirtyStrategicRegionIds = mutableSetOf<Int>()
    private var dirtyBuildings = false
    private var dirtyRailways = false
    private var dirtySupplyNodes = false
    private var dirtyAdjacencies = false

    private val _state = MutableStateFlow(EngineState(isLoading = true))
    val state: StateFlow<EngineState> = _state.asStateFlow()

    // =========================================================================
    // 1. INITIALIZATION & LOADING (OFF-THREAD)
    // =========================================================================

    fun initializeAndLoadAsync(onLoaded: (() -> Unit)? = null) {
        scope.launch {
            try {
                setLoadingStep("Preparing mod directories...", "loading_prepare_directories")
                withContext(Dispatchers.IO) {
                    fileResolver.ensureModDirectoriesAndDescriptor()
                }

                setLoadingStep(
                    "Loading definition.csv, States & Strategic Regions...",
                    "loading_definitions_states_regions"
                )
                val (loadedDefs, loadedStates, loadedRegions) = withContext(Dispatchers.IO) {
                    val defFile = fileResolver.resolveReadFile("map/definition.csv")
                    val defs = ClausewitzParser.loadDefinitions(defFile)
                    val states = ClausewitzParser.loadStates(fileResolver.vanillaStatesDir, fileResolver.modStatesDir)
                    val regions = ClausewitzParser.loadStrategicRegions(
                        fileResolver.vanillaStrategicRegionsDir,
                        fileResolver.modStrategicRegionsDir
                    )
                    Triple(defs, states, regions)
                }

                provincesById.clear()
                provincesById.putAll(loadedDefs)
                provinceDefinitionsRevision++
                provincesByRgb.clear()
                loadedDefs.values.filter { it.id > 0 }.forEach { provincesByRgb[it.rgbInt] = it }

                statesById.clear()
                statesById.putAll(loadedStates.statesById)
                stateDataRevision++
                provinceToStateId.clear()
                provinceToStateId.putAll(loadedStates.provinceToStateId)
                duplicateStateAssignments.clear()
                duplicateStateAssignments.addAll(loadedStates.duplicateStateAssignments)

                strategicRegionsById.clear()
                strategicRegionsById.putAll(loadedRegions.regionsById)
                regionDataRevision++
                provinceToStrategicRegionId.clear()
                provinceToStrategicRegionId.putAll(loadedRegions.provinceToRegionId)

                setLoadingStep("Decoding provinces.bmp...", "loading_decode_bitmap")
                val decodedBmp = withContext(Dispatchers.IO) {
                    val bmpFile = fileResolver.resolveReadFile("map/provinces.bmp")
                    BmpCodec.loadBitmap(bmpFile)
                }
                grid.attachBitmap(decodedBmp.image, decodedBmp.pixels)

                setLoadingStep("Computing province centroids & bounds...", "loading_centers_bounds")
                grid.recalculateAllProvinceCenters(provincesById, provincesByRgb)
                provinceCentersRevision++

                setLoadingStep("Loading infrastructure & buildings...", "loading_infrastructure")
                withContext(Dispatchers.IO) {
                    buildings.clear()
                    buildings.addAll(ClausewitzParser.loadBuildings(fileResolver.resolveReadFile("map/buildings.txt")))
                    buildingsRevision++

                    railways.clear()
                    railways.addAll(ClausewitzParser.loadRailways(fileResolver.resolveReadFile("map/railways.txt")))
                    railwaysRevision++

                    supplyNodes.clear()
                    supplyNodes.addAll(ClausewitzParser.loadSupplyNodes(fileResolver.resolveReadFile("map/supply_nodes.txt")))
                    supplyNodesRevision++

                    adjacencies.clear()
                    adjacencies.addAll(ClausewitzParser.loadAdjacencies(fileResolver.resolveReadFile("map/adjacencies.csv")))
                    adjacenciesRevision++
                }

                clearDirtyFlags()
                tileRenderer.invalidateColorLut()

                setLoadingStep("Rendering map...", "loading_render_map")
                val initialBitmap = renderFullMapInternal()

                publishSnapshot(
                    baseBitmap = initialBitmap,
                    status = "Ready. Project '${modPath.name}' loaded.",
                    statusKey = "engine_status_project_loaded",
                    statusArgs = listOf(modPath.name)
                )

                if (onLoaded != null) {
                    withContext(Dispatchers.Main) { onLoaded() }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        loadingStep = "",
                        loadingStepKey = null,
                        statusMessage = "Error loading project: ${e.message}",
                        statusMessageKey = "engine_status_load_error",
                        statusMessageArgs = listOf(e.message.orEmpty())
                    )
                }
            }
        }
    }

    // =========================================================================
    // 2. VIEW MODE & BORDER TRACING
    // =========================================================================

    fun setMapViewModeAsync(mode: MapViewMode, highlightedId: Int? = currentHighlightedId) {
        scope.launch {
            val finishedStroke = finishPaintStrokeInternal()
            if (currentViewMode == mode && currentHighlightedId == highlightedId && _state.value.baseMapBitmap != null) {
                if (finishedStroke) publishSnapshot(mapGeometryChanged = true)
                return@launch
            }
            currentViewMode = mode
            currentHighlightedId = highlightedId
            activeOverlayTiles.clear()
            val rendered = renderFullMapInternal()
            publishSnapshot(baseBitmap = rendered, mapGeometryChanged = finishedStroke)
        }
    }

    suspend fun computeBorderSegments(provinceIds: Set<Int>): List<BorderSegment> =
        withContext(engineDispatcher) {
            grid.computeBorderSegmentsForProvinces(provinceIds, provincesById)
        }

    /**
     * Schneller Lookup für Maus-Hover im Canvas (ohne Thread-Hop, da primitives Array-Lesen).
     */
    fun getRgbAtFast(x: Int, y: Int): Int? = grid.getRgbAt(x, y)

    suspend fun findProvinceCenter(prov: ProvinceDef): Pair<Int, Int>? = withContext(engineDispatcher) {
        val alreadyCached = grid.provinceCenters.containsKey(prov.id)
        val center = grid.findProvinceCenter(prov)
        if (!alreadyCached && center != null) {
            provinceCentersRevision++
            publishSnapshot()
        }
        center
    }

    suspend fun findStateCenter(state: StateDef): Pair<Int, Int>? =
        withContext(engineDispatcher) { grid.findStateCenter(state) }

    // =========================================================================
    // 3. PAINTING (BRUSH, FLOOD FILL, UNDO / REDO)
    // =========================================================================

    fun beginPaintStrokeAsync() {
        scope.launch {
            beginPaintStrokeInternal()
        }
    }

    private fun beginPaintStrokeInternal() {
        if (paintStrokeOpen) return
        grid.beginPaintStroke()
        paintStrokeOpen = true
    }

    /** Commits any pending brush changes before an operation that assumes a stable history. */
    private fun finishPaintStrokeInternal(): Boolean {
        if (!paintStrokeOpen) return false
        paintStrokeOpen = false
        return grid.endPaintStroke()
    }

    fun paintCircleAsync(mapX: Int, mapY: Int, radius: Int, targetRgb: Int) {
        scope.launch {
            // Defensive fallback: never allow a paint operation to run outside a history stroke.
            beginPaintStrokeInternal()
            val paintedProvinceId = provincesByRgb[targetRgb and 0xFFFFFF]?.id
            val hadProvinceCenter = paintedProvinceId?.let { grid.provinceCenters.containsKey(it) } ?: true
            val bounds = grid.paintCircleWithBounds(mapX, mapY, radius, targetRgb, provincesByRgb) ?: return@launch
            if (!hadProvinceCenter && paintedProvinceId != null && grid.provinceCenters.containsKey(paintedProvinceId)) {
                provinceCentersRevision++
            }
            dirtyBmp = true
            val dirtyTiles = tileRenderer.renderDirtyTiles(
                grid = grid,
                bounds = bounds,
                mode = currentViewMode,
                highlightedId = currentHighlightedId,
                provincesByRgb = provincesByRgb,
                provinceToStateId = provinceToStateId,
                provinceToStrategicRegionId = provinceToStrategicRegionId
            )
            activeOverlayTiles.putAll(dirtyTiles)
            publishSnapshot()
        }
    }

    fun endPaintStrokeAsync() {
        scope.launch {
            if (!paintStrokeOpen) return@launch
            val strokeCommitted = finishPaintStrokeInternal()
            val tilesPerRow = (grid.width + MapTileRenderer.MAP_TILE_SIZE - 1) / MapTileRenderer.MAP_TILE_SIZE
            val tilesPerCol = (grid.height + MapTileRenderer.MAP_TILE_SIZE - 1) / MapTileRenderer.MAP_TILE_SIZE
            val overlayLimit = minOf(64, maxOf(8, (tilesPerRow * tilesPerCol) / 4))

            if (activeOverlayTiles.size > overlayLimit) {
                activeOverlayTiles.clear()
                publishSnapshot(baseBitmap = renderFullMapInternal(), mapGeometryChanged = strokeCommitted)
            } else {
                publishSnapshot(mapGeometryChanged = strokeCommitted)
            }
        }
    }

    fun floodFillAsync(startX: Int, startY: Int, targetRgb: Int, provinceId: Int) {
        scope.launch {
            finishPaintStrokeInternal()
            val bounds = grid.floodFill(startX, startY, targetRgb, provincesByRgb)
            if (bounds != null) {
                dirtyBmp = true
                activeOverlayTiles.clear()
                publishSnapshot(
                    baseBitmap = renderFullMapInternal(),
                    status = "Flood-filled area with Province #$provinceId",
                    statusKey = "engine_status_flood_filled",
                    statusArgs = listOf(provinceId.toString()),
                    mapGeometryChanged = true
                )
            }
        }
    }

    fun undoAsync() {
        scope.launch {
            // End the stroke and undo it in this same serialized task. This avoids a race
            // between separate async end/undo jobs when Ctrl+Z is pressed just after painting.
            finishPaintStrokeInternal()
            val bounds = grid.undoLastStroke() ?: run {
                publishSnapshot(status = "Nothing to undo.", statusKey = "engine_status_nothing_to_undo")
                return@launch
            }
            dirtyBmp = true
            activeOverlayTiles.clear()
            publishSnapshot(
                baseBitmap = renderFullMapInternal(),
                status = "Undid last paint stroke (${bounds.width}x${bounds.height} px).",
                statusKey = "engine_status_undo",
                statusArgs = listOf(bounds.width.toString(), bounds.height.toString()),
                mapGeometryChanged = true
            )
        }
    }

    fun redoAsync() {
        scope.launch {
            finishPaintStrokeInternal()
            val bounds = grid.redoLastStroke() ?: run {
                publishSnapshot(status = "Nothing to redo.", statusKey = "engine_status_nothing_to_redo")
                return@launch
            }
            dirtyBmp = true
            activeOverlayTiles.clear()
            publishSnapshot(
                baseBitmap = renderFullMapInternal(),
                status = "Redid paint stroke (${bounds.width}x${bounds.height} px).",
                statusKey = "engine_status_redo",
                statusArgs = listOf(bounds.width.toString(), bounds.height.toString()),
                mapGeometryChanged = true
            )
        }
    }

    fun recalculateAllCentersAsync() {
        scope.launch {
            grid.recalculateAllProvinceCenters(provincesById, provincesByRgb)
            provinceCentersRevision++
            publishSnapshot(
                status = "Recalculated all province centers and bounds.",
                statusKey = "engine_status_centers_recalculated"
            )
        }
    }

    // =========================================================================
    // 4. PROVINCE, STATE & REGION MUTATIONS
    // =========================================================================

    fun createNewProvinceAsync(
        type: String,
        coastal: Boolean,
        terrain: String,
        continent: Int,
        onCreated: ((ProvinceDef) -> Unit)? = null
    ) {
        scope.launch {
            val nextId = (provincesById.keys.maxOrNull() ?: 0) + 1
            var r: Int
            var g: Int
            var b: Int
            var rgb: Int
            do {
                r = Random.nextInt(10, 255)
                g = Random.nextInt(10, 255)
                b = Random.nextInt(10, 255)
                rgb = (r shl 16) or (g shl 8) or b
            } while (provincesByRgb.containsKey(rgb))

            val newProv = ProvinceDef(nextId, r, g, b, type, coastal, terrain, continent)
            provincesById[nextId] = newProv
            provincesByRgb[rgb] = newProv
            provinceDefinitionsRevision++
            dirtyDefinitions = true
            dirtyDefaultMap = true
            tileRenderer.invalidateColorLut()

            publishSnapshot(
                status = "Staged new Province #${newProv.id} with RGB($r, $g, $b)",
                statusKey = "engine_status_new_province",
                statusArgs = listOf(newProv.id.toString(), r.toString(), g.toString(), b.toString())
            )
            if (onCreated != null) {
                withContext(Dispatchers.Main) { onCreated(newProv) }
            }
        }
    }

    fun batchEditProvinceDefinitionsAsync(targetIds: Collection<Int>, transform: (ProvinceDef) -> ProvinceDef) {
        scope.launch {
            var changed = false
            for (id in targetIds) {
                val current = provincesById[id] ?: continue
                val updated = transform(current)
                if (current != updated) {
                    if (current.rgbInt != updated.rgbInt) {
                        provincesByRgb.remove(current.rgbInt)
                    }
                    provincesById[id] = updated
                    if (updated.id > 0) {
                        provincesByRgb[updated.rgbInt] = updated
                    }
                    changed = true
                }
            }
            if (!changed) return@launch

            provinceDefinitionsRevision++
            dirtyDefinitions = true
            tileRenderer.invalidateColorLut()

            val needsReRender = currentViewMode == MapViewMode.TERRAIN || currentViewMode == MapViewMode.COASTAL
            if (needsReRender) {
                activeOverlayTiles.clear()
                publishSnapshot(baseBitmap = renderFullMapInternal())
            } else {
                publishSnapshot()
            }
        }
    }

    fun assignProvincesToStateAsync(provinceIds: Collection<Int>, targetStateId: Int) {
        scope.launch {
            if (provinceIds.isEmpty()) return@launch
            for (pid in provinceIds) {
                assignProvinceToStateInternal(pid, targetStateId)
            }
            stateDataRevision++
            currentHighlightedId = targetStateId
            tileRenderer.invalidateColorLut()
            activeOverlayTiles.clear()
            val targetName = statesById[targetStateId]?.name ?: ""
            publishSnapshot(
                baseBitmap = renderFullMapInternal(),
                status = "Assigned ${provinceIds.size} province(s) to State #$targetStateId ($targetName)",
                statusKey = "engine_status_assigned_state",
                statusArgs = listOf(provinceIds.size.toString(), targetStateId.toString(), targetName)
            )
        }
    }

    fun removeProvinceFromStateAsync(provinceId: Int, stateId: Int) {
        scope.launch {
            val state = statesById[stateId] ?: return@launch
            if (state.provinces.contains(provinceId)) {
                val updatedProvs = state.provinces - provinceId
                if (provinceToStateId[provinceId] == stateId) {
                    provinceToStateId.remove(provinceId)
                }
                statesById[stateId] = ClausewitzParser.formatUpdatedState(state, updatedProvs)
                stateDataRevision++
                dirtyStateIds.add(stateId)
                tileRenderer.invalidateColorLut()
                activeOverlayTiles.clear()
                publishSnapshot(
                    baseBitmap = renderFullMapInternal(),
                    status = "Removed Province #$provinceId from State #$stateId",
                    statusKey = "engine_status_removed_from_state",
                    statusArgs = listOf(provinceId.toString(), stateId.toString())
                )
            }
        }
    }

    fun createNewStateAsync(
        name: String,
        manpower: Int,
        category: String,
        initialProvinceIds: Collection<Int>,
        onCreated: ((StateDef) -> Unit)? = null
    ) {
        scope.launch {
            val nextId = (statesById.keys.maxOrNull() ?: 0) + 1
            val cleanName = name.ifBlank { "STATE_$nextId" }
            val fileName = "$nextId-${cleanName.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.txt"

            val rawContent = """
                state={
                    id=$nextId
                    name="$cleanName"
                    manpower=$manpower
                    state_category=$category
                    history={
                        owner = GER
                        buildings = {
                            infrastructure = 2
                        }
                    }
                    provinces={
                        
                    }
                }
            """.trimIndent()

            val stateDef = StateDef(
                id = nextId,
                name = cleanName,
                fileName = fileName,
                provinces = emptySet(),
                rawText = rawContent,
                isModifiedOrInMod = true
            )

            statesById[nextId] = stateDef
            dirtyStateIds.add(nextId)

            for (provId in initialProvinceIds) {
                assignProvinceToStateInternal(provId, nextId)
            }

            stateDataRevision++
            currentHighlightedId = nextId
            tileRenderer.invalidateColorLut()
            activeOverlayTiles.clear()
            val finalState = statesById[nextId]!!

            publishSnapshot(
                baseBitmap = renderFullMapInternal(),
                status = "Created State #${finalState.id} (${finalState.name}) with ${initialProvinceIds.size} province(s)!",
                statusKey = "engine_status_state_created",
                statusArgs = listOf(finalState.id.toString(), finalState.name, initialProvinceIds.size.toString())
            )
            if (onCreated != null) {
                withContext(Dispatchers.Main) { onCreated(finalState) }
            }
        }
    }

    fun assignProvincesToStrategicRegionAsync(provinceIds: Collection<Int>, targetRegionId: Int) {
        scope.launch {
            if (provinceIds.isEmpty()) return@launch
            for (pid in provinceIds) {
                assignProvinceToStrategicRegionInternal(pid, targetRegionId)
            }
            regionDataRevision++
            currentHighlightedId = targetRegionId
            tileRenderer.invalidateColorLut()
            activeOverlayTiles.clear()
            val regName = strategicRegionsById[targetRegionId]?.name ?: ""
            publishSnapshot(
                baseBitmap = renderFullMapInternal(),
                status = "Assigned ${provinceIds.size} province(s) to Strategic Region #$targetRegionId ($regName)",
                statusKey = "engine_status_assigned_region",
                statusArgs = listOf(provinceIds.size.toString(), targetRegionId.toString(), regName)
            )
        }
    }

    fun createNewStrategicRegionAsync(
        name: String,
        initialProvinceIds: Collection<Int>,
        onCreated: ((StrategicRegionDef) -> Unit)? = null
    ) {
        scope.launch {
            val nextId = (strategicRegionsById.keys.maxOrNull() ?: 0) + 1
            val cleanName = name.ifBlank { "STRATEGICREGION_$nextId" }
            val fileName = "$nextId-${cleanName.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.txt"

            val rawContent = """
                strategic_region={
                    id=$nextId
                    name="$cleanName"
                    provinces={
                        
                    }
                    weather={
                    }
                }
            """.trimIndent()

            val regDef = StrategicRegionDef(
                id = nextId,
                name = cleanName,
                fileName = fileName,
                provinces = emptySet(),
                rawText = rawContent,
                isModifiedOrInMod = true
            )

            strategicRegionsById[nextId] = regDef
            dirtyStrategicRegionIds.add(nextId)

            for (provId in initialProvinceIds) {
                assignProvinceToStrategicRegionInternal(provId, nextId)
            }

            regionDataRevision++
            currentHighlightedId = nextId
            tileRenderer.invalidateColorLut()
            activeOverlayTiles.clear()
            val finalReg = strategicRegionsById[nextId]!!

            publishSnapshot(
                baseBitmap = renderFullMapInternal(),
                status = "Created Strategic Region #${finalReg.id} (${finalReg.name})!",
                statusKey = "engine_status_region_created",
                statusArgs = listOf(finalReg.id.toString(), finalReg.name)
            )
            if (onCreated != null) {
                withContext(Dispatchers.Main) { onCreated(finalReg) }
            }
        }
    }

    private fun assignProvinceToStateInternal(provinceId: Int, targetStateId: Int) {
        val currentOwnerId = provinceToStateId[provinceId]
        if (currentOwnerId == targetStateId) return

        if (currentOwnerId != null) {
            statesById[currentOwnerId]?.let { oldState ->
                statesById[currentOwnerId] = ClausewitzParser.formatUpdatedState(
                    oldState,
                    oldState.provinces - provinceId
                )
                dirtyStateIds.add(currentOwnerId)
            }
        }

        val targetState = statesById[targetStateId] ?: return
        statesById[targetStateId] = ClausewitzParser.formatUpdatedState(
            targetState,
            targetState.provinces + provinceId
        )
        provinceToStateId[provinceId] = targetStateId
        dirtyStateIds.add(targetStateId)
    }

    private fun assignProvinceToStrategicRegionInternal(provinceId: Int, targetRegionId: Int) {
        val currentRegId = provinceToStrategicRegionId[provinceId]
        if (currentRegId == targetRegionId) return

        if (currentRegId != null) {
            strategicRegionsById[currentRegId]?.let { oldReg ->
                strategicRegionsById[currentRegId] = ClausewitzParser.formatUpdatedStrategicRegion(
                    oldReg,
                    oldReg.provinces - provinceId
                )
                dirtyStrategicRegionIds.add(currentRegId)
            }
        }

        val targetReg = strategicRegionsById[targetRegionId] ?: return
        strategicRegionsById[targetRegionId] = ClausewitzParser.formatUpdatedStrategicRegion(
            targetReg,
            targetReg.provinces + provinceId
        )
        provinceToStrategicRegionId[provinceId] = targetRegionId
        dirtyStrategicRegionIds.add(targetRegionId)
    }

    // =========================================================================
    // 5. INFRASTRUCTURE (RAILWAYS, SUPPLY, BUILDINGS, ADJACENCIES)
    // =========================================================================

    fun toggleSupplyNodeAsync(provinceId: Int) {
        scope.launch {
            val added = if (supplyNodes.contains(provinceId)) {
                supplyNodes.remove(provinceId)
                false
            } else {
                supplyNodes.add(provinceId)
                true
            }
            supplyNodesRevision++
            dirtySupplyNodes = true
            publishSnapshot(
                status = if (added) "Added Supply Hub at Province #$provinceId"
                else "Removed Supply Hub from Province #$provinceId",
                statusKey = if (added) "engine_status_supply_added" else "engine_status_supply_removed",
                statusArgs = listOf(provinceId.toString())
            )
        }
    }

    fun addRailwayAsync(level: Int, rawPath: List<Int>) {
        scope.launch {
            val cleaned = mutableListOf<Int>()
            for (p in rawPath) {
                if (cleaned.lastOrNull() != p) cleaned.add(p)
            }
            if (cleaned.size < 2) return@launch
            val entry = RailwayEntry(level.coerceIn(1, 5), cleaned)
            railways.add(entry)
            railwaysRevision++
            dirtyRailways = true
            publishSnapshot(
                status = "Staged new Level ${entry.level} Railway (${entry.provinces.size} provinces).",
                statusKey = "engine_status_railway_added",
                statusArgs = listOf(entry.level.toString(), entry.provinces.size.toString())
            )
        }
    }

    fun removeLastRailwayAsync() {
        scope.launch {
            if (railways.isNotEmpty()) {
                railways.removeLast()
                railwaysRevision++
                dirtyRailways = true
                publishSnapshot(status = "Deleted last railway track.", statusKey = "engine_status_railway_deleted")
            }
        }
    }

    fun addBuildingAtBmpCoordsAsync(
        bmpX: Int,
        bmpY: Int,
        buildingType: String,
        explicitSeaProvinceId: Int = 0
    ) {
        scope.launch {
            val rgb = grid.getRgbAt(bmpX, bmpY) ?: return@launch
            val prov = provincesByRgb[rgb] ?: return@launch
            val stateId = provinceToStateId[prov.id]
            if (stateId == null) {
                publishSnapshot(
                    status = "Cannot place building here: Province must belong to a State!",
                    statusKey = "engine_status_building_needs_state"
                )
                return@launch
            }

            val hoi4X = bmpX.toDouble()
            val hoi4Z = (grid.height - bmpY).toDouble()
            val requiresSeaProv = buildingType == "naval_base_spawn" || buildingType == "floating_harbor"
            val seaProv = if (requiresSeaProv) {
                if (explicitSeaProvinceId > 0) explicitSeaProvinceId
                else findNearestSeaProvinceIdInternal(bmpX, bmpY)
            } else 0

            val entry = BuildingEntry(
                stateId = stateId,
                type = buildingType.trim(),
                x = hoi4X,
                y = 9.80,
                z = hoi4Z,
                rotation = 0.0,
                adjacentSeaProvince = seaProv
            )
            buildings.add(entry)
            buildingsRevision++
            dirtyBuildings = true
            publishSnapshot(
                status = "Placed ${entry.type} in State #${entry.stateId} at HoI4 (${entry.x.toInt()}, ${entry.z.toInt()})",
                statusKey = "engine_status_building_placed",
                statusArgs = listOf(
                    entry.type,
                    entry.stateId.toString(),
                    entry.x.toInt().toString(),
                    entry.z.toInt().toString()
                )
            )
        }
    }

    fun removeBuildingAsync(entry: BuildingEntry) {
        scope.launch {
            if (buildings.remove(entry)) {
                buildingsRevision++
                dirtyBuildings = true
                publishSnapshot(
                    status = "Removed building ${entry.type} from State #${entry.stateId}.",
                    statusKey = "engine_status_building_removed",
                    statusArgs = listOf(entry.type, entry.stateId.toString())
                )
            }
        }
    }

    fun autoGenerateBuildingsForStateAsync(stateId: Int) {
        scope.launch {
            val state = statesById[stateId] ?: return@launch
            val provIds = state.provinces.toList()
            if (provIds.isEmpty()) return@launch

            val height = grid.height
            val typesToSpawn = listOf(
                "arms_factory", "arms_factory", "industrial_complex", "industrial_complex",
                "air_base", "bunker", "anti_air_building", "synthetic_refinery", "nuclear_reactor_spawn"
            )

            var addedCount = 0
            typesToSpawn.forEachIndexed { idx, type ->
                val targetProvId = provIds[idx % provIds.size]
                val center = grid.provinceCenters[targetProvId] ?: return@forEachIndexed
                val hoi4X = (center.first + (idx % 3) - 1).toDouble()
                val hoi4Z = (height - (center.second + (idx / 3) - 1)).toDouble()
                buildings.add(BuildingEntry(stateId, type, hoi4X, 9.80, hoi4Z, 0.0, 0))
                addedCount++
            }

            val coastalProvId = provIds.firstOrNull { provincesById[it]?.coastal == true }
            if (coastalProvId != null) {
                val c = grid.provinceCenters[coastalProvId]
                if (c != null) {
                    val seaProv = findNearestSeaProvinceIdInternal(c.first, c.second)
                    if (seaProv > 0) {
                        buildings.add(
                            BuildingEntry(
                                stateId = stateId,
                                type = "naval_base_spawn",
                                x = c.first.toDouble(),
                                y = 9.50,
                                z = (height - c.second).toDouble(),
                                rotation = 0.0,
                                adjacentSeaProvince = seaProv
                            )
                        )
                        addedCount++
                    }
                }
            }

            if (addedCount > 0) {
                buildingsRevision++
                dirtyBuildings = true
                publishSnapshot(
                    status = "Generated $addedCount buildings for State #$stateId!",
                    statusKey = "engine_status_buildings_generated",
                    statusArgs = listOf(addedCount.toString(), stateId.toString())
                )
            }
        }
    }

    private fun findNearestSeaProvinceIdInternal(bmpX: Int, bmpY: Int): Int {
        var bestId = 0
        var bestDist = Double.MAX_VALUE
        for (prov in provincesById.values) {
            if (prov.type == "sea") {
                val c = grid.provinceCenters[prov.id] ?: continue
                val d = hypot((c.first - bmpX).toDouble(), (c.second - bmpY).toDouble())
                if (d < bestDist) {
                    bestDist = d
                    bestId = prov.id
                }
            }
        }
        return bestId
    }

    fun addAdjacencyAsync(fromId: Int, toId: Int, throughSeaId: Int, ruleName: String, comment: String) {
        scope.launch {
            adjacencies.add(
                AdjacencyEntry(
                    fromProvince = fromId,
                    toProvince = toId,
                    type = "sea",
                    throughProvince = throughSeaId,
                    ruleName = ruleName.trim(),
                    comment = comment.trim()
                )
            )
            adjacenciesRevision++
            dirtyAdjacencies = true
            publishSnapshot(
                status = "Staged adjacency #$fromId <-> #$toId in RAM.",
                statusKey = "engine_status_adjacency_added",
                statusArgs = listOf(fromId.toString(), toId.toString())
            )
        }
    }

    fun removeAdjacencyAsync(entry: AdjacencyEntry) {
        scope.launch {
            if (adjacencies.remove(entry)) {
                adjacenciesRevision++
                dirtyAdjacencies = true
                publishSnapshot(
                    status = "Removed adjacency #${entry.fromProvince} <-> #${entry.toProvince}.",
                    statusKey = "engine_status_adjacency_removed",
                    statusArgs = listOf(entry.fromProvince.toString(), entry.toProvince.toString())
                )
            }
        }
    }

    // =========================================================================
    // 6. DIAGNOSTICS & AUTO-FIXES
    // =========================================================================

    fun runDiagnosticsAsync() {
        scope.launch {
            val configuredMax = withContext(Dispatchers.IO) {
                ClausewitzParser.readConfiguredMaxProvinces(fileResolver.resolveReadFile("map/default.map"))
            }
            val issues = MapDiagnosticsScanner.runDiagnostics(
                grid = grid,
                configuredMaxProvinces = configuredMax,
                dirtyDefaultMap = dirtyDefaultMap,
                provincesById = provincesById,
                provincesByRgb = provincesByRgb,
                statesById = statesById,
                provinceToStateId = provinceToStateId,
                duplicateStateAssignments = duplicateStateAssignments,
                provinceToStrategicRegionId = provinceToStrategicRegionId,
                railways = railways
            )
            _state.update {
                it.copy(
                    diagnosticsIssues = issues,
                    statusMessage = "Diagnostics finished: ${issues.size} issue(s) found.",
                    statusMessageKey = "engine_status_diagnostics_finished",
                    statusMessageArgs = listOf(issues.size.toString())
                )
            }
        }
    }

    fun autoFixCoastalFlagsAsync() {
        scope.launch {
            val updates = MapDiagnosticsScanner.computeCoastalFlagUpdates(grid, provincesById, provincesByRgb)
            if (updates.isNotEmpty()) {
                for (updated in updates) {
                    provincesById[updated.id] = updated
                    provincesByRgb[updated.rgbInt] = updated
                }
                provinceDefinitionsRevision++
                dirtyDefinitions = true
                tileRenderer.invalidateColorLut()
                val newBitmap = if (currentViewMode == MapViewMode.COASTAL) renderFullMapInternal() else null
                publishSnapshot(
                    baseBitmap = newBitmap,
                    status = "Auto-fixed coastal flags for ${updates.size} provinces!",
                    statusKey = "engine_status_coastal_fixed",
                    statusArgs = listOf(updates.size.toString())
                )
            } else {
                publishSnapshot(
                    status = "All coastal flags are already accurate.",
                    statusKey = "engine_status_coastal_already_correct"
                )
            }
        }
    }

    // =========================================================================
    // 7. SAVE ALL TO DISK (CTRL + S)
    // =========================================================================

    fun saveAllAsync() {
        scope.launch {
            val strokeCommitted = finishPaintStrokeInternal()
            _state.update {
                it.copy(
                    isSaving = true,
                    statusMessage = "Saving changes to Mod folder...",
                    statusMessageKey = "engine_status_saving",
                    statusMessageArgs = emptyList()
                )
            }
            try {
                // Keep this synchronous block on engineDispatcher. Switching to Dispatchers.IO here
                // would suspend the single-thread worker and let queued edits mutate the same data
                // while it is being written to disk.
                fileResolver.ensureModDirectoriesAndDescriptor()

                if (dirtyBmp) {
                    BmpCodec.saveProvincesBmp(
                        targetFile = fileResolver.modProvincesFile,
                        width = grid.width,
                        height = grid.height,
                        mapPixels = grid.mapPixels
                    )
                }
                if (dirtyDefinitions) {
                    ClausewitzParser.saveDefinitions(fileResolver.modDefinitionFile, provincesById)
                }
                if (dirtyDefaultMap) {
                    val maxId = provincesById.keys.maxOrNull() ?: 0
                    ClausewitzParser.saveDefaultMap(
                        sourceFile = fileResolver.resolveReadFile("map/default.map"),
                        targetFile = fileResolver.modDefaultMapFile,
                        maxProvinceId = maxId
                    )
                }
                if (dirtyStateIds.isNotEmpty()) {
                    ClausewitzParser.saveDirtyStates(fileResolver.modStatesDir, dirtyStateIds, statesById)
                }
                if (dirtyStrategicRegionIds.isNotEmpty()) {
                    ClausewitzParser.saveDirtyStrategicRegions(
                        fileResolver.modStrategicRegionsDir,
                        dirtyStrategicRegionIds,
                        strategicRegionsById
                    )
                }
                if (dirtyBuildings) {
                    ClausewitzParser.saveBuildings(fileResolver.modBuildingsFile, buildings)
                }
                if (dirtyRailways) {
                    ClausewitzParser.saveRailways(fileResolver.modRailwaysFile, railways)
                }
                if (dirtySupplyNodes) {
                    ClausewitzParser.saveSupplyNodes(fileResolver.modSupplyNodesFile, supplyNodes)
                }
                if (dirtyAdjacencies) {
                    ClausewitzParser.saveAdjacencies(fileResolver.modAdjacenciesFile, adjacencies)
                }

                clearDirtyFlags()
                publishSnapshot(
                    status = "Successfully saved all modified files to Mod folder!",
                    statusKey = "engine_status_saved",
                    mapGeometryChanged = strokeCommitted
                )
            } catch (e: Exception) {
                publishSnapshot(
                    status = "Save error: ${e.message}",
                    statusKey = "engine_status_save_error",
                    statusArgs = listOf(e.message.orEmpty()),
                    mapGeometryChanged = strokeCommitted
                )
            }
        }
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    private fun renderFullMapInternal(): ImageBitmap {
        return tileRenderer.renderMapModeImage(
            grid = grid,
            mode = currentViewMode,
            highlightedId = currentHighlightedId,
            provincesByRgb = provincesByRgb,
            provinceToStateId = provinceToStateId,
            provinceToStrategicRegionId = provinceToStrategicRegionId
        ).toComposeImageBitmap()
    }

    private fun hasUnsavedChangesInternal(): Boolean {
        return dirtyBmp || dirtyDefinitions || dirtyDefaultMap ||
                dirtyStateIds.isNotEmpty() || dirtyStrategicRegionIds.isNotEmpty() ||
                dirtyBuildings || dirtyRailways || dirtySupplyNodes || dirtyAdjacencies
    }

    private fun unsavedSummaryInternal(): String {
        val parts = mutableListOf<String>()
        if (dirtyBmp) parts.add("provinces.bmp")
        if (dirtyDefinitions) parts.add("definition.csv")
        if (dirtyDefaultMap) parts.add("default.map")
        if (dirtyStateIds.isNotEmpty()) parts.add("${dirtyStateIds.size} State(s)")
        if (dirtyStrategicRegionIds.isNotEmpty()) parts.add("${dirtyStrategicRegionIds.size} Region(s)")
        if (dirtyBuildings) parts.add("buildings.txt")
        if (dirtyRailways) parts.add("railways.txt")
        if (dirtySupplyNodes) parts.add("supply_nodes.txt")
        if (dirtyAdjacencies) parts.add("adjacencies.csv")
        return parts.joinToString(", ")
    }

    private fun clearDirtyFlags() {
        dirtyBmp = false
        dirtyDefinitions = false
        dirtyDefaultMap = false
        dirtyStateIds.clear()
        dirtyStrategicRegionIds.clear()
        dirtyBuildings = false
        dirtyRailways = false
        dirtySupplyNodes = false
        dirtyAdjacencies = false
    }

    private fun setLoadingStep(step: String, key: String) {
        _state.update {
            it.copy(
                isLoading = true,
                loadingStep = step,
                loadingStepKey = key,
                statusMessage = step,
                statusMessageKey = key,
                statusMessageArgs = emptyList()
            )
        }
    }

    private fun publishSnapshot(
        baseBitmap: ImageBitmap? = null,
        status: String? = null,
        statusKey: String? = null,
        statusArgs: List<String> = emptyList(),
        mapGeometryChanged: Boolean = false
    ) {
        val prev = _state.value
        val nextPaintRevision = prev.paintRevision + if (mapGeometryChanged) 1 else 0
        _state.value = prev.copy(
            isLoading = false,
            loadingStep = "",
            loadingStepKey = null,
            isSaving = false,
            isLoaded = grid.isInitialized,
            mapWidth = grid.width,
            mapHeight = grid.height,
            mapViewMode = currentViewMode,
            highlightedId = currentHighlightedId,
            baseMapBitmap = baseBitmap ?: prev.baseMapBitmap,
            paintOverlayTiles = activeOverlayTiles.toMap(),
            provincesById = if (prev.provinceDefinitionsRevision != provinceDefinitionsRevision) provincesById.toMap() else prev.provincesById,
            provincesByRgb = if (prev.provinceDefinitionsRevision != provinceDefinitionsRevision) provincesByRgb.toMap() else prev.provincesByRgb,
            provinceCenters = if (prev.provinceCentersRevision != provinceCentersRevision) grid.provinceCenters.toMap() else prev.provinceCenters,
            statesById = if (prev.stateDataRevision != stateDataRevision) statesById.toMap() else prev.statesById,
            provinceToStateId = if (prev.stateDataRevision != stateDataRevision) provinceToStateId.toMap() else prev.provinceToStateId,
            strategicRegionsById = if (prev.regionDataRevision != regionDataRevision) strategicRegionsById.toMap() else prev.strategicRegionsById,
            provinceToStrategicRegionId = if (prev.regionDataRevision != regionDataRevision) provinceToStrategicRegionId.toMap() else prev.provinceToStrategicRegionId,
            buildings = if (prev.buildingsRevision != buildingsRevision) buildings.toList() else prev.buildings,
            railways = if (prev.railwaysRevision != railwaysRevision) railways.toList() else prev.railways,
            supplyNodes = if (prev.supplyNodesRevision != supplyNodesRevision) supplyNodes.toSet() else prev.supplyNodes,
            adjacencies = if (prev.adjacenciesRevision != adjacenciesRevision) adjacencies.toList() else prev.adjacencies,
            hasUnsavedChanges = hasUnsavedChangesInternal(),
            unsavedSummary = unsavedSummaryInternal(),
            canUndo = grid.canUndo(),
            canRedo = grid.canRedo(),
            statusMessage = status ?: prev.statusMessage,
            statusMessageKey = if (status != null) statusKey else prev.statusMessageKey,
            statusMessageArgs = if (status != null) statusArgs.toList() else prev.statusMessageArgs,
            paintRevision = nextPaintRevision,
            provinceDefinitionsRevision = provinceDefinitionsRevision,
            stateDataRevision = stateDataRevision,
            regionDataRevision = regionDataRevision,
            buildingsRevision = buildingsRevision,
            provinceCentersRevision = provinceCentersRevision,
            railwaysRevision = railwaysRevision,
            supplyNodesRevision = supplyNodesRevision,
            adjacenciesRevision = adjacenciesRevision,
            editRevision = prev.editRevision + 1
        )
    }

    fun close() {
        scope.cancel()
    }
}
