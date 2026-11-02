package at.yerova.hoi4_modder.v2.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntSize
import at.yerova.hoi4_modder.v2.engine.EngineState
import at.yerova.hoi4_modder.v2.engine.Hoi4ModderEngine
import at.yerova.hoi4_modder.v2.engine.model.CopiedProvinceClipboard
import at.yerova.hoi4_modder.v2.engine.model.ProvinceDef
import at.yerova.hoi4_modder.v2.engine.model.StateDef
import at.yerova.hoi4_modder.v2.engine.model.StrategicRegionDef
import at.yerova.hoi4_modder.v2.ui.model.EditorTool
import at.yerova.hoi4_modder.v2.ui.model.ProvinceSidebarSubTab
import at.yerova.hoi4_modder.v2.ui.model.StateSidebarSubTab
import at.yerova.hoi4_modder.v2.ui.navigation.EditorRoute
import at.yerova.hoi4_modder.v2.ui.navigation.ProvincesRoute
import at.yerova.hoi4_modder.v2.ui.navigation.StatesRoute
import at.yerova.hoi4_modder.v2.ui.navigation.StrategicRegionsRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection
import kotlin.math.roundToInt

class MapEditorSessionState(
    val engine: Hoi4ModderEngine,
    private val coroutineScope: CoroutineScope,
    private val clipboardManager: Clipboard
) {
    // Navigation 3 Backstack
    val backStack = mutableStateListOf<EditorRoute>(ProvincesRoute())
    val currentRoute: EditorRoute
        get() = backStack.lastOrNull() ?: ProvincesRoute()

    // Selection State
    val selectedProvinceIds = mutableStateListOf<Int>()
    var primaryProvinceId by mutableStateOf<Int?>(null)
    var selectedStateId by mutableStateOf<Int?>(null)
    var selectedRegionId by mutableStateOf<Int?>(null)
    var copiedClipboard by mutableStateOf<CopiedProvinceClipboard?>(null)

    // Mode-Specific Interactive Map Toggles
    var isPaintingStateProvinces by mutableStateOf(false)
    var paintTerrainOnMapClick by mutableStateOf(false)
    var activeTerrainBrush by mutableStateOf("plains")
    var toggleCoastalOnMapClick by mutableStateOf(false)

    // Overlays
    var showSelectionOutline by mutableStateOf(true)
    var showRailwaysOverlay by mutableStateOf(true)
    var showSupplyOverlay by mutableStateOf(true)
    var showBuildingsOverlay by mutableStateOf(false)
    var showAdjacenciesOverlay by mutableStateOf(false)

    // Viewport / Camera
    var canvasSize by mutableStateOf(IntSize(800, 600))
    var scale by mutableStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var hoverScreenPos by mutableStateOf<Offset?>(null)
    var hoverMapCoords by mutableStateOf<Pair<Int, Int>?>(null)
    var isSpacePressed by mutableStateOf(false)
    var isCtrlHeld by mutableStateOf(false)
    var isPanningDrag by mutableStateOf(false)

    // Active Tools & Parameters
    var activeTool by mutableStateOf(EditorTool.EYEDROPPER)
    var brushRadius by mutableStateOf(3f)
    var railwayLevel by mutableStateOf(2f)
    val activeRailwayPath = mutableStateListOf<Int>()
    var buildingTypeToPlace by mutableStateOf("arms_factory")
    var buildingSeaProvinceInput by mutableStateOf("0")
    val paintStrokeActive = mutableStateOf(false)

    // Sidebar Sub-Tabs
    var provinceSubTab by mutableStateOf(ProvinceSidebarSubTab.INSPECTOR)
    var stateSubTab by mutableStateOf(StateSidebarSubTab.STATES)

    // Dialog Flags
    var showNewProvinceDialog by mutableStateOf(false)
    var showNewStateDialog by mutableStateOf(false)
    var showNewRegionDialog by mutableStateOf(false)
    var showAddAdjacencyDialog by mutableStateOf(false)

    fun primaryProvince(engineState: EngineState): ProvinceDef? =
        primaryProvinceId?.let { engineState.provincesById[it] }

    fun selectedState(engineState: EngineState): StateDef? =
        selectedStateId?.let { engineState.statesById[it] }

    fun selectedRegion(engineState: EngineState): StrategicRegionDef? =
        selectedRegionId?.let { engineState.strategicRegionsById[it] }

    fun effectiveSelectedProvinceIds(): List<Int> =
        if (selectedProvinceIds.isNotEmpty()) selectedProvinceIds.toList()
        else listOfNotNull(primaryProvinceId)

    fun navigateToRoute(target: EditorRoute) {
        isPaintingStateProvinces = false
        val isAssignPush = (target is StatesRoute && target.assignMode) ||
                (target is StrategicRegionsRoute && target.assignMode)
        if (isAssignPush) {
            backStack.add(target)
        } else {
            if (backStack.isNotEmpty()) {
                backStack[backStack.lastIndex] = target
            } else {
                backStack.add(target)
            }
        }
    }

    fun popRouteIfPossible(): Boolean {
        return if (backStack.size > 1) {
            backStack.removeLastOrNull()
            true
        } else false
    }

    fun clearSelection() {
        selectedProvinceIds.clear()
        primaryProvinceId = null
    }

    fun handleProvinceSelection(
        prov: ProvinceDef?,
        toggleMultiSelect: Boolean,
        engineState: EngineState
    ) {
        if (prov == null) {
            if (!toggleMultiSelect) clearSelection()
            return
        }

        if (toggleMultiSelect) {
            if (selectedProvinceIds.contains(prov.id)) {
                selectedProvinceIds.remove(prov.id)
                primaryProvinceId = selectedProvinceIds.lastOrNull()
            } else {
                selectedProvinceIds.add(prov.id)
                primaryProvinceId = prov.id
            }
        } else {
            selectedProvinceIds.clear()
            selectedProvinceIds.add(prov.id)
            primaryProvinceId = prov.id
        }

        primaryProvinceId?.let { activeId ->
            selectedStateId = engineState.provinceToStateId[activeId]
            selectedRegionId = engineState.provinceToStrategicRegionId[activeId]
        }
    }

    fun centerCameraAt(mapX: Int, mapY: Int, minZoom: Float = 5f) {
        val targetScale = scale.coerceAtLeast(minZoom)
        scale = targetScale
        offset = Offset(
            x = (canvasSize.width / 2f) - (mapX * targetScale),
            y = (canvasSize.height / 2f) - (mapY * targetScale)
        )
    }

    fun centerOnProvince(prov: ProvinceDef) {
        coroutineScope.launch {
            engine.findProvinceCenter(prov)?.let { (x, y) ->
                centerCameraAt(x, y, 5f)
            }
        }
    }

    fun centerOnState(state: StateDef) {
        coroutineScope.launch {
            engine.findStateCenter(state)?.let { (x, y) ->
                centerCameraAt(x, y, 3.5f)
            }
        }
    }

    fun fitMapToViewport(mapWidth: Int) {
        if (mapWidth > 0) {
            scale = (canvasSize.width.toFloat() / mapWidth).coerceAtLeast(0.2f)
            offset = Offset.Zero
        }
    }

    fun copyActiveProvince(engineState: EngineState) {
        val prov = primaryProvince(engineState) ?: return

        val stId = engineState.provinceToStateId[prov.id]
        val regId = engineState.provinceToStrategicRegionId[prov.id]

        copiedClipboard = CopiedProvinceClipboard(
            sourceProvinceId = prov.id,
            type = prov.type,
            coastal = prov.coastal,
            terrain = prov.terrain,
            continent = prov.continent,
            stateId = stId,
            strategicRegionId = regId
        )

        // Externes Clipboard-Format: ID;R;G;B
        val clipboardText = "${prov.id};${prov.r};${prov.g};${prov.b}"

        coroutineScope.launch {
            clipboardManager.setClipEntry(
                clipboardText.toClipboardEntry()
            )
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun String.toClipboardEntry(): ClipEntry =
        ClipEntry(StringSelection(this))

    fun pasteToSelectedProvinces() {
        val clip = copiedClipboard ?: return
        val targets = effectiveSelectedProvinceIds()
        if (targets.isEmpty()) return

        engine.batchEditProvinceDefinitionsAsync(targets) { prov ->
            prov.copy(
                type = clip.type,
                coastal = clip.coastal,
                terrain = clip.terrain,
                continent = clip.continent
            )
        }
        if (clip.stateId != null) {
            engine.assignProvincesToStateAsync(targets, clip.stateId)
        }
        if (clip.strategicRegionId != null) {
            engine.assignProvincesToStrategicRegionAsync(targets, clip.strategicRegionId)
        }
    }

    fun finishActiveRailway() {
        if (activeRailwayPath.size >= 2) {
            engine.addRailwayAsync(railwayLevel.roundToInt(), activeRailwayPath.toList())
        }
        activeRailwayPath.clear()
    }

    fun finishPaintStrokeIfNeeded() {
        paintStrokeActive.value = false
    }

    fun undo() {
        finishPaintStrokeIfNeeded()
        engine.undoAsync()
    }

    fun redo() {
        finishPaintStrokeIfNeeded()
        engine.redoAsync()
    }

    fun saveAll() {
        finishPaintStrokeIfNeeded()
        if (activeRailwayPath.size >= 2) finishActiveRailway()
        engine.saveAllAsync()
    }
}