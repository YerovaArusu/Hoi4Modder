package at.yerova.hoi4_modder

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import hoi4_modder.shared.generated.resources.*
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.stringResource
import java.io.File
import kotlin.math.roundToInt

private const val MAP_TILE_SIZE = 256

val AppButtonShape = RoundedCornerShape(4.dp)
val AppCardShape = RoundedCornerShape(6.dp)

private val StudioColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF061621),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD6EFFF),
    secondary = Color(0xFF60A5FA),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFF22C55E),
    onTertiary = Color(0xFF052E16),
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF21262D),
    onSurface = Color(0xFFE6EDF3),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF30363D),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF451A1A),
    onErrorContainer = Color(0xFFFECACA)
)

private val StudioShapes = Shapes(
    extraSmall = AppButtonShape,
    small = AppButtonShape,
    medium = AppCardShape,
    large = AppCardShape,
    extraLarge = AppCardShape
)

enum class EditorTool {
    NAVIGATE,
    EYEDROPPER,
    BRUSH,
    BUCKET,
    RAILWAY,
    SUPPLY_NODE,
    BUILDING
}

/**
 * Interactive map-click assignment modes so the user never has to type State/Region IDs manually.
 */
enum class MapPickMode {
    NONE,
    PICK_STATE_FOR_SELECTED,
    PICK_REGION_FOR_SELECTED,
    CLICK_PROVINCES_INTO_ACTIVE_STATE
}

// --- UNIFIED RECTANGULAR UI COMPONENTS ---

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = AppButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = AppButtonShape,
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = AppButtonShape,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        content = content
    )
}

@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        leadingIcon = leadingIcon,
        shape = AppButtonShape,
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
@Preview
fun App() {
    LaunchedEffect(Unit) {
        AppPreferences.applySavedLocale()
    }
    var currentLang by remember { mutableStateOf(AppPreferences.language) }

    MaterialTheme(
        colorScheme = StudioColorScheme,
        shapes = StudioShapes
    ) {
        key(currentLang) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                MapStudioApp(
                    currentLang = currentLang,
                    onLanguageChange = { newLang ->
                        AppPreferences.language = newLang
                        currentLang = newLang
                    }
                )
            }
        }
    }
}

@Composable
fun MapStudioApp(
    currentLang: String,
    onLanguageChange: (String) -> Unit
) {
    var project by remember { mutableStateOf<Hoi4MapProject?>(null) }
    var triedAutoOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!triedAutoOpen && AppPreferences.autoOpenLastProject) {
            triedAutoOpen = true
            val vDir = File(AppPreferences.vanillaPath)
            val mDir = File(AppPreferences.lastModPath)
            if (File(vDir, "map/provinces.bmp").exists() && mDir.exists()) {
                try {
                    val proj = Hoi4MapProject(vDir, mDir)
                    proj.initializeAndLoad()
                    project = proj
                } catch (_: Exception) {
                }
            }
        }
    }

    if (project == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopMenuBar(
                project = null,
                editRevision = 0,
                currentLang = currentLang,
                onLanguageChange = onLanguageChange,
                onSaveAll = {},
                onReloadProject = {},
                onSwitchProject = {},
                onUndo = {},
                onRedo = {},
                onCopy = {},
                onPaste = {},
                onClearSelection = {},
                onAutoFixCoastal = {},
                onRecalcCenters = {},
                onFitView = {},
                showSelectionOutline = true,
                onToggleSelectionOutline = {},
                showRailwaysOverlay = true,
                onToggleRailways = {},
                showSupplyOverlay = true,
                onToggleSupply = {},
                showBuildingsOverlay = false,
                onToggleBuildings = {},
                showAdjacenciesOverlay = false,
                onToggleAdjacencies = {}
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            ProjectSetupScreen(
                onProjectLoaded = { loadedProject ->
                    AppPreferences.autoOpenLastProject = true
                    project = loadedProject
                }
            )
        }
    } else {
        MapEditorScreen(
            project = project!!,
            currentLang = currentLang,
            onLanguageChange = onLanguageChange,
            onReloadProject = {
                val reloaded = Hoi4MapProject(project!!.vanillaPath, project!!.modPath)
                reloaded.initializeAndLoad()
                project = reloaded
            },
            onCloseProject = {
                AppPreferences.autoOpenLastProject = false
                project = null
            }
        )
    }
}

@Composable
fun ProjectSetupScreen(
    onProjectLoaded: (Hoi4MapProject) -> Unit
) {
    var vanillaPath by remember { mutableStateOf(AppPreferences.vanillaPath) }
    var modPath by remember { mutableStateOf(AppPreferences.lastModPath) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val invalidVanillaMsg = stringResource(Res.string.invalid_vanilla_path)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .width(680.dp)
                .padding(16.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, AppCardShape),
            shape = AppCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(Res.string.setup_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(Res.string.setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = vanillaPath,
                        onValueChange = { vanillaPath = it },
                        label = { Text(stringResource(Res.string.vanilla_path_label)) },
                        modifier = Modifier.weight(1f),
                        shape = AppButtonShape,
                        singleLine = true
                    )
                    AppOutlinedButton(
                        onClick = { runBlocking { chooseDirectory(vanillaPath) }?.let { vanillaPath = it } },
                        modifier = Modifier.height(54.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.browse_button))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = modPath,
                        onValueChange = { modPath = it },
                        label = { Text(stringResource(Res.string.mod_path_label)) },
                        modifier = Modifier.weight(1f),
                        shape = AppButtonShape,
                        singleLine = true
                    )
                    AppOutlinedButton(
                        onClick = { runBlocking { chooseDirectory(modPath) }?.let { modPath = it } },
                        modifier = Modifier.height(54.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.browse_button))
                    }
                }

                if (errorMessage != null) {
                    Text(
                        errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                AppButton(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        try {
                            val vDir = File(vanillaPath)
                            val mDir = File(modPath)
                            require(File(vDir, "map/provinces.bmp").exists()) { invalidVanillaMsg }

                            AppPreferences.vanillaPath = vanillaPath
                            AppPreferences.lastModPath = modPath

                            val proj = Hoi4MapProject(vDir, mDir)
                            proj.initializeAndLoad()
                            onProjectLoaded(proj)
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Error"
                        } finally {
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    enabled = !isLoading
                ) {
                    Text(
                        text = if (isLoading) stringResource(Res.string.loading_map)
                        else stringResource(Res.string.open_create_mod),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun LanguageSwitcher(currentLang: String, onLanguageChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Language,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppFilterChip(
            selected = currentLang == "en",
            onClick = { onLanguageChange("en") },
            label = { Text("EN", style = MaterialTheme.typography.labelSmall) }
        )
        AppFilterChip(
            selected = currentLang == "de",
            onClick = { onLanguageChange("de") },
            label = { Text("DE", style = MaterialTheme.typography.labelSmall) }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MapEditorScreen(
    project: Hoi4MapProject,
    currentLang: String,
    onLanguageChange: (String) -> Unit,
    onReloadProject: () -> Unit,
    onCloseProject: () -> Unit
) {
    val clipboardManager = LocalClipboard.current
    val focusRequester = remember { FocusRequester() }

    // Map Rendering & View Mode
    var mapViewMode by remember { mutableStateOf(MapViewMode.PROVINCES) }
    var highlightedId by remember { mutableStateOf<Int?>(null) }
    var composeMapBitmap by remember(project) {
        mutableStateOf<ImageBitmap>(project.renderMapModeImage(mapViewMode, highlightedId).toComposeImageBitmap())
    }
    val paintOverlayTiles = remember(project) { mutableStateMapOf<Int, ImageBitmap>() }
    var canvasSize by remember { mutableStateOf(IntSize(800, 600)) }

    // Dirty State & Edit Counter
    var editRevision by remember { mutableStateOf(0) }

    // Map Overlays
    var showSelectionOutline by remember { mutableStateOf(true) }
    var showRailwaysOverlay by remember { mutableStateOf(true) }
    var showSupplyOverlay by remember { mutableStateOf(true) }
    var showBuildingsOverlay by remember { mutableStateOf(false) }
    var showAdjacenciesOverlay by remember { mutableStateOf(false) }

    // Camera / Viewport
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var hoverScreenPos by remember { mutableStateOf<Offset?>(null) }
    var hoverMapCoords by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var isSpacePressed by remember { mutableStateOf(false) }
    var isCtrlHeld by remember { mutableStateOf(false) }

    // Tools, Tabs & Interactive Map Pick Mode
    var selectedTab by remember { mutableStateOf(0) }
    var activeTool by remember { mutableStateOf(EditorTool.EYEDROPPER) }
    var mapPickMode by remember { mutableStateOf(MapPickMode.NONE) }
    var brushRadius by remember { mutableStateOf(3f) }

    // Infrastructure Builder State
    var railwayLevel by remember { mutableStateOf(2f) }
    val activeRailwayPath = remember { mutableStateListOf<Int>() }
    var buildingTypeToPlace by remember { mutableStateOf("arms_factory") }
    var buildingSeaProvinceInput by remember { mutableStateOf("0") }

    // Multi-Select State (Ctrl + Click)
    val selectedProvinceIds = remember { mutableStateListOf<Int>() }
    var primaryProvince by remember { mutableStateOf<ProvinceDef?>(null) }
    var selectedState by remember { mutableStateOf<StateDef?>(null) }
    var copiedClipboard by remember { mutableStateOf<CopiedProvinceClipboard?>(null) }

    // Cached Outline Border Segments for Selected Provinces & Selected State/Region
    val selectionBorderSegments = remember(selectedProvinceIds.toList(), editRevision, showSelectionOutline) {
        if (!showSelectionOutline || selectedProvinceIds.isEmpty()) emptyList()
        else project.computeBorderSegmentsForProvinces(selectedProvinceIds.toSet())
    }

    val groupBorderSegments = remember(mapViewMode, highlightedId, editRevision, showSelectionOutline) {
        if (!showSelectionOutline || highlightedId == null) emptyList()
        else when (mapViewMode) {
            MapViewMode.STATES -> {
                val stProvs = project.statesById[highlightedId]?.provinces ?: emptySet()
                project.computeBorderSegmentsForProvinces(stProvs)
            }

            MapViewMode.STRATEGIC_REGIONS -> {
                val regProvs = project.strategicRegionsById[highlightedId]?.provinces ?: emptySet()
                project.computeBorderSegmentsForProvinces(regProvs)
            }

            else -> emptyList()
        }
    }

    val readyMsg = stringResource(Res.string.status_ready)
    val savedMsg = stringResource(Res.string.status_saved)
    var statusMessage by remember { mutableStateOf(readyMsg) }

    var showNewProvinceDialog by remember { mutableStateOf(false) }
    var showNewStateDialog by remember { mutableStateOf(false) }
    var showNewRegionDialog by remember { mutableStateOf(false) }
    var showAddAdjacencyDialog by remember { mutableStateOf(false) }

    fun refreshRenderedMap() {
        composeMapBitmap = project.renderMapModeImage(mapViewMode, highlightedId).toComposeImageBitmap()
        paintOverlayTiles.clear()
        editRevision++
    }

    fun refreshPaintTiles(bounds: PixelBounds) {
        val tilesPerRow = (project.mapImage.width + MAP_TILE_SIZE - 1) / MAP_TILE_SIZE
        val firstTileX = bounds.left / MAP_TILE_SIZE
        val lastTileX = bounds.right / MAP_TILE_SIZE
        val firstTileY = bounds.top / MAP_TILE_SIZE
        val lastTileY = bounds.bottom / MAP_TILE_SIZE

        for (tileY in firstTileY..lastTileY) {
            for (tileX in firstTileX..lastTileX) {
                val x = tileX * MAP_TILE_SIZE
                val y = tileY * MAP_TILE_SIZE
                val tileWidth = (project.mapImage.width - x).coerceAtMost(MAP_TILE_SIZE)
                val tileHeight = (project.mapImage.height - y).coerceAtMost(MAP_TILE_SIZE)
                val tile = project.renderMapModeRegion(
                    mode = mapViewMode,
                    highlightedStateId = highlightedId,
                    x = x,
                    y = y,
                    regionWidth = tileWidth,
                    regionHeight = tileHeight
                ).toComposeImageBitmap()
                paintOverlayTiles[tileY * tilesPerRow + tileX] = tile
            }
        }
        editRevision++
    }

    fun handleProvinceSelection(prov: ProvinceDef?, toggleMultiSelect: Boolean) {
        if (prov == null) {
            if (!toggleMultiSelect) {
                selectedProvinceIds.clear()
                primaryProvince = null
            }
            return
        }

        if (toggleMultiSelect) {
            if (selectedProvinceIds.contains(prov.id)) {
                selectedProvinceIds.remove(prov.id)
                primaryProvince = selectedProvinceIds.lastOrNull()?.let { project.provincesById[it] }
            } else {
                selectedProvinceIds.add(prov.id)
                primaryProvince = prov
            }
        } else {
            selectedProvinceIds.clear()
            selectedProvinceIds.add(prov.id)
            primaryProvince = prov
        }

        val active = primaryProvince
        if (active != null) {
            val stId = project.provinceToStateId[active.id]
            val regId = project.provinceToStrategicRegionId[active.id]
            selectedState = stId?.let { project.statesById[it] }

            if (mapViewMode == MapViewMode.STATES && highlightedId != stId) {
                highlightedId = stId
                refreshRenderedMap()
            } else if (mapViewMode == MapViewMode.STRATEGIC_REGIONS && highlightedId != regId) {
                highlightedId = regId
                refreshRenderedMap()
            }
        }
    }

    fun stageBatchProvinceDefinitionEdit(transform: (ProvinceDef) -> ProvinceDef) {
        val targetIds = if (selectedProvinceIds.isNotEmpty()) selectedProvinceIds.toList()
        else listOfNotNull(primaryProvince?.id)

        for (id in targetIds) {
            val current = project.provincesById[id] ?: continue
            val updated = transform(current)
            project.updateProvinceDefinition(updated)
            if (primaryProvince?.id == id) {
                primaryProvince = updated
            }
        }

        if (mapViewMode == MapViewMode.TERRAIN || mapViewMode == MapViewMode.COASTAL) {
            refreshRenderedMap()
        } else {
            editRevision++
        }
    }

    fun assignSelectedProvincesToState(targetStateId: Int) {
        val ids = if (selectedProvinceIds.isNotEmpty()) selectedProvinceIds.toList()
        else listOfNotNull(primaryProvince?.id)
        if (ids.isEmpty()) return

        for (pid in ids) {
            project.assignProvinceToState(pid, targetStateId)
        }
        selectedState = project.statesById[targetStateId]
        highlightedId = targetStateId
        refreshRenderedMap()
        statusMessage = "Assigned ${ids.size} province(s) to State #$targetStateId (${selectedState?.name})"
    }

    fun assignSelectedProvincesToRegion(targetRegId: Int) {
        val ids = if (selectedProvinceIds.isNotEmpty()) selectedProvinceIds.toList()
        else listOfNotNull(primaryProvince?.id)
        if (ids.isEmpty()) return

        for (pid in ids) {
            project.assignProvinceToStrategicRegion(pid, targetRegId)
        }
        val reg = project.strategicRegionsById[targetRegId]
        highlightedId = targetRegId
        refreshRenderedMap()
        statusMessage = "Assigned ${ids.size} province(s) to Strategic Region #$targetRegId (${reg?.name})"
    }

    fun centerCameraAt(mapX: Int, mapY: Int, minZoom: Float = 5f) {
        val targetScale = scale.coerceAtLeast(minZoom)
        scale = targetScale
        offset = Offset(
            x = (canvasSize.width / 2f) - (mapX * targetScale),
            y = (canvasSize.height / 2f) - (mapY * targetScale)
        )
    }

    fun centerCameraOnProvince(prov: ProvinceDef) {
        val center = project.findProvinceCenter(prov)
        if (center != null) {
            centerCameraAt(center.first, center.second, 5f)
            statusMessage = "Centered on Province #${prov.id} at (${center.first}, ${center.second})"
        } else {
            statusMessage = "Province #${prov.id} has no pixels on the map yet."
        }
    }

    fun centerCameraOnState(state: StateDef) {
        val center = project.findStateCenter(state)
        if (center != null) {
            centerCameraAt(center.first, center.second, 3.5f)
            statusMessage = "Centered on State #${state.id} (${state.name})"
        } else {
            statusMessage = "State #${state.id} has no painted pixels on the map."
        }
    }

    fun copyActiveProvince() {
        val prov = primaryProvince ?: return
        val stateId = project.provinceToStateId[prov.id]
        val regId = project.provinceToStrategicRegionId[prov.id]
        copiedClipboard = CopiedProvinceClipboard(
            sourceProvinceId = prov.id,
            type = prov.type,
            coastal = prov.coastal,
            terrain = prov.terrain,
            continent = prov.continent,
            stateId = stateId,
            strategicRegionId = regId
        )
        //clipboardManager.setText(AnnotatedString("${prov.id};${prov.r};${prov.g};${prov.b}"))
        statusMessage = "Copied Province #${prov.id} (Properties, State & Region) to clipboard!"
    }

    fun pasteToSelectedProvinces() {
        val clip = copiedClipboard ?: return
        val targetIds = if (selectedProvinceIds.isNotEmpty()) selectedProvinceIds.toList()
        else listOfNotNull(primaryProvince?.id)
        if (targetIds.isEmpty()) return

        for (id in targetIds) {
            val prov = project.provincesById[id] ?: continue
            val updated = prov.copy(
                type = clip.type,
                coastal = clip.coastal,
                terrain = clip.terrain,
                continent = clip.continent
            )
            project.updateProvinceDefinition(updated)
            if (clip.stateId != null) {
                project.assignProvinceToState(prov.id, clip.stateId)
            }
            if (clip.strategicRegionId != null) {
                project.assignProvinceToStrategicRegion(prov.id, clip.strategicRegionId)
            }
            if (primaryProvince?.id == id) {
                primaryProvince = updated
            }
        }
        refreshRenderedMap()
        statusMessage = "Pasted properties from #${clip.sourceProvinceId} onto ${targetIds.size} province(s)!"
    }

    fun finishActiveRailway() {
        if (activeRailwayPath.size >= 2) {
            val created = project.addOrUpdateRailway(railwayLevel.roundToInt(), activeRailwayPath.toList())
            activeRailwayPath.clear()
            editRevision++
            if (created != null) {
                statusMessage = "Staged new Level ${created.level} Railway (${created.provinces.size} provinces)."
            }
        } else {
            activeRailwayPath.clear()
        }
    }

    fun triggerSaveAll() {
        try {
            if (activeRailwayPath.size >= 2) finishActiveRailway()
            project.saveAll()
            editRevision++
            statusMessage = savedMsg
        } catch (e: Exception) {
            statusMessage = "Save error: ${e.message}"
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
                isCtrlHeld = event.isCtrlPressed || event.isMetaPressed
                if (event.key == Key.Spacebar) {
                    isSpacePressed = (event.type == KeyEventType.KeyDown)
                    return@onPreviewKeyEvent true
                }
                if (event.type == KeyEventType.KeyDown) {
                    val isCtrl = event.isCtrlPressed || event.isMetaPressed
                    val panStep = 60f
                    when {
                        isCtrl && event.key == Key.C -> {
                            copyActiveProvince(); true
                        }

                        isCtrl && event.key == Key.V -> {
                            pasteToSelectedProvinces(); true
                        }

                        isCtrl && event.key == Key.Z -> {
                            if (project.undoLastStroke()) {
                                refreshRenderedMap()
                                statusMessage = "Undid last paint stroke."
                            }
                            true
                        }

                        isCtrl && event.key == Key.Y -> {
                            if (project.redoLastStroke()) {
                                refreshRenderedMap()
                                statusMessage = "Redid last paint stroke."
                            }
                            true
                        }

                        isCtrl && event.key == Key.S -> {
                            triggerSaveAll()
                            true
                        }

                        event.key == Key.Enter -> {
                            if (activeRailwayPath.isNotEmpty()) {
                                finishActiveRailway()
                                true
                            } else false
                        }

                        event.key == Key.Escape -> {
                            when {
                                mapPickMode != MapPickMode.NONE -> {
                                    mapPickMode = MapPickMode.NONE
                                    statusMessage = "Exited map pick mode."
                                    true
                                }

                                activeRailwayPath.isNotEmpty() -> {
                                    activeRailwayPath.clear()
                                    statusMessage = "Cancelled railway track."
                                    true
                                }

                                selectedProvinceIds.isNotEmpty() -> {
                                    selectedProvinceIds.clear()
                                    primaryProvince = null
                                    statusMessage = "Cleared selection."
                                    true
                                }

                                else -> false
                            }
                        }

                        !isCtrl && (event.key == Key.DirectionLeft || event.key == Key.A) -> {
                            offset += Offset(panStep, 0f); true
                        }

                        !isCtrl && (event.key == Key.DirectionRight || event.key == Key.D) -> {
                            offset += Offset(-panStep, 0f); true
                        }

                        !isCtrl && (event.key == Key.DirectionUp || event.key == Key.W) -> {
                            offset += Offset(0f, panStep); true
                        }

                        !isCtrl && (event.key == Key.DirectionDown || event.key == Key.S) -> {
                            offset += Offset(0f, -panStep); true
                        }

                        !isCtrl && event.key == Key.B -> {
                            mapPickMode = MapPickMode.NONE; activeTool = EditorTool.BRUSH; true
                        }

                        !isCtrl && event.key == Key.G -> {
                            mapPickMode = MapPickMode.NONE; activeTool = EditorTool.BUCKET; true
                        }

                        !isCtrl && event.key == Key.R -> {
                            mapPickMode = MapPickMode.NONE; activeTool = EditorTool.RAILWAY; showRailwaysOverlay =
                                true; true
                        }

                        !isCtrl && event.key == Key.H -> {
                            mapPickMode = MapPickMode.NONE; activeTool = EditorTool.SUPPLY_NODE; showSupplyOverlay =
                                true; true
                        }

                        !isCtrl && event.key == Key.P -> {
                            mapPickMode = MapPickMode.NONE; activeTool = EditorTool.BUILDING; showBuildingsOverlay =
                                true; true
                        }

                        !isCtrl && (event.key == Key.I || event.key == Key.E) -> {
                            mapPickMode = MapPickMode.NONE; activeTool = EditorTool.EYEDROPPER; true
                        }

                        !isCtrl && event.key == Key.M -> {
                            mapPickMode = MapPickMode.NONE; activeTool = EditorTool.NAVIGATE; true
                        }

                        else -> false
                    }
                } else false
            }
    ) {
        // --- 1. PERSISTENT TOP WINDOW TASKBAR (FILE / EDIT / VIEW / UNDO / REDO / SAVE) ---
        TopMenuBar(
            project = project,
            editRevision = editRevision,
            currentLang = currentLang,
            onLanguageChange = onLanguageChange,
            onSaveAll = ::triggerSaveAll,
            onReloadProject = onReloadProject,
            onSwitchProject = onCloseProject,
            onUndo = { if (project.undoLastStroke()) refreshRenderedMap() },
            onRedo = { if (project.redoLastStroke()) refreshRenderedMap() },
            onCopy = ::copyActiveProvince,
            onPaste = ::pasteToSelectedProvinces,
            onClearSelection = {
                selectedProvinceIds.clear()
                primaryProvince = null
            },
            onAutoFixCoastal = {
                val c = project.autoFixCoastalFlags()
                refreshRenderedMap()
                statusMessage = "Staged coastal flag fixes for $c provinces!"
            },
            onRecalcCenters = {
                project.recalculateAllProvinceCenters()
                editRevision++
                statusMessage = "Recalculated all province centers and bounds."
            },
            onFitView = {
                val fitScale = (canvasSize.width.toFloat() / project.mapImage.width).coerceAtLeast(0.2f)
                scale = fitScale
                offset = Offset.Zero
            },
            showSelectionOutline = showSelectionOutline,
            onToggleSelectionOutline = { showSelectionOutline = it },
            showRailwaysOverlay = showRailwaysOverlay,
            onToggleRailways = { showRailwaysOverlay = it },
            showSupplyOverlay = showSupplyOverlay,
            onToggleSupply = { showSupplyOverlay = it },
            showBuildingsOverlay = showBuildingsOverlay,
            onToggleBuildings = { showBuildingsOverlay = it },
            showAdjacenciesOverlay = showAdjacenciesOverlay,
            onToggleAdjacencies = { showAdjacenciesOverlay = it }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // --- MAIN WORKSPACE (MAP AREA LEFT + SIDEBAR RIGHT) ---
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // --- LEFT SIDE: DEDICATED MAP COLUMN (ALWAYS-VISIBLE MAP TOOLBAR + INTERACTIVE CANVAS) ---
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

                // --- 2. ALWAYS-VISIBLE MAP TOOLBAR DIRECTLY ABOVE THE MAP ---
                MapHeaderToolbar(
                    activeTool = activeTool,
                    onToolChange = {
                        mapPickMode = MapPickMode.NONE
                        activeTool = it
                    },
                    brushRadius = brushRadius,
                    onBrushRadiusChange = { brushRadius = it },
                    activeRailwayNodesCount = activeRailwayPath.size,
                    onFinishRailway = ::finishActiveRailway,
                    mapViewMode = mapViewMode,
                    onMapViewModeChange = { newMode ->
                        mapViewMode = newMode
                        highlightedId = when (newMode) {
                            MapViewMode.STATES -> selectedState?.id
                            MapViewMode.STRATEGIC_REGIONS -> primaryProvince?.let { project.provinceToStrategicRegionId[it.id] }
                            else -> null
                        }
                        refreshRenderedMap()
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Interactive Banner when Map-Click Assignment Mode is active
                if (mapPickMode != MapPickMode.NONE) {
                    val bannerText = when (mapPickMode) {
                        MapPickMode.PICK_STATE_FOR_SELECTED ->
                            stringResource(Res.string.banner_pick_state, selectedProvinceIds.size.coerceAtLeast(1))

                        MapPickMode.PICK_REGION_FOR_SELECTED ->
                            stringResource(Res.string.banner_pick_region, selectedProvinceIds.size.coerceAtLeast(1))

                        MapPickMode.CLICK_PROVINCES_INTO_ACTIVE_STATE ->
                            stringResource(
                                Res.string.banner_paint_state,
                                selectedState?.id ?: 0,
                                selectedState?.name ?: ""
                            )

                        else -> ""
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    bannerText,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            AppOutlinedButton(
                                onClick = { mapPickMode = MapPickMode.NONE },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    stringResource(Res.string.cancel_pick_mode_btn),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .background(Color(0xFF0A0E14))
                        .onSizeChanged { canvasSize = it }
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .onPointerEvent(PointerEventType.Scroll) { event ->
                                val change = event.changes.first()
                                val scrollDelta = change.scrollDelta.y
                                val zoomFactor = if (scrollDelta < 0) 1.25f else 0.8f
                                val newScale = (scale * zoomFactor).coerceIn(0.15f, 60f)

                                val mousePos = change.position
                                offset = mousePos - (mousePos - offset) * (newScale / scale)
                                scale = newScale
                            }
                            .onPointerEvent(PointerEventType.Move) { event ->
                                isCtrlHeld =
                                    event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                                val pos = event.changes.first().position
                                hoverScreenPos = pos
                                val mx = ((pos.x - offset.x) / scale).toInt()
                                val my = ((pos.y - offset.y) / scale).toInt()
                                val newMapCoords =
                                    if (mx in 0 until project.mapImage.width && my in 0 until project.mapImage.height) {
                                        mx to my
                                    } else null
                                if (newMapCoords != hoverMapCoords) hoverMapCoords = newMapCoords
                            }
                            .pointerInput(
                                activeTool, primaryProvince, brushRadius, mapViewMode,
                                isSpacePressed, buildingTypeToPlace, buildingSeaProvinceInput,
                                mapPickMode, selectedState
                            ) {
                                awaitPointerEventScope {
                                    var isPaintingStroke = false

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: continue
                                        val buttons = event.buttons
                                        val ctrlDown =
                                            event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed || isCtrlHeld
                                        val mx = ((change.position.x - offset.x) / scale).toInt()
                                        val my = ((change.position.y - offset.y) / scale).toInt()
                                        val img = project.mapImage

                                        when (event.type) {
                                            PointerEventType.Press -> {
                                                focusRequester.requestFocus()
                                                if (buttons.isPrimaryPressed && !isSpacePressed &&
                                                    mx in 0 until img.width && my in 0 until img.height
                                                ) {
                                                    val clickedRgb = img.getRGB(mx, my) and 0xFFFFFF
                                                    val clickedProv = project.provincesByRgb[clickedRgb]

                                                    // 1. Check Interactive Map-Click Assignment Modes FIRST!
                                                    when (mapPickMode) {
                                                        MapPickMode.PICK_STATE_FOR_SELECTED -> {
                                                            val targetStId =
                                                                clickedProv?.let { project.provinceToStateId[it.id] }
                                                            if (targetStId != null) {
                                                                assignSelectedProvincesToState(targetStId)
                                                                mapPickMode = MapPickMode.NONE
                                                            } else {
                                                                statusMessage =
                                                                    "Clicked province has no State! Click on an existing State or press '+ Move to New State'."
                                                            }
                                                        }

                                                        MapPickMode.PICK_REGION_FOR_SELECTED -> {
                                                            val targetRegId =
                                                                clickedProv?.let { project.provinceToStrategicRegionId[it.id] }
                                                            if (targetRegId != null) {
                                                                assignSelectedProvincesToRegion(targetRegId)
                                                                mapPickMode = MapPickMode.NONE
                                                            } else {
                                                                statusMessage =
                                                                    "Clicked province has no Strategic Region!"
                                                            }
                                                        }

                                                        MapPickMode.CLICK_PROVINCES_INTO_ACTIVE_STATE -> {
                                                            val activeSt = selectedState
                                                            if (activeSt != null && clickedProv != null) {
                                                                if (project.provinceToStateId[clickedProv.id] == activeSt.id) {
                                                                    project.removeProvinceFromState(
                                                                        clickedProv.id,
                                                                        activeSt.id
                                                                    )
                                                                    statusMessage =
                                                                        "Removed Province #${clickedProv.id} from State #${activeSt.id}"
                                                                } else {
                                                                    project.assignProvinceToState(
                                                                        clickedProv.id,
                                                                        activeSt.id
                                                                    )
                                                                    statusMessage =
                                                                        "Added Province #${clickedProv.id} to State #${activeSt.id} (${activeSt.name})"
                                                                }
                                                                selectedState = project.statesById[activeSt.id]?.copy()
                                                                refreshRenderedMap()
                                                            }
                                                        }

                                                        MapPickMode.NONE -> {
                                                            // 2. Normal Tool Execution (or Ctrl+Click Multi-Select)
                                                            if (ctrlDown || activeTool == EditorTool.EYEDROPPER || activeTool == EditorTool.NAVIGATE) {
                                                                handleProvinceSelection(
                                                                    clickedProv,
                                                                    toggleMultiSelect = ctrlDown
                                                                )
                                                                statusMessage = if (clickedProv != null) {
                                                                    val st = project.provinceToStateId[clickedProv.id]
                                                                    val reg =
                                                                        project.provinceToStrategicRegionId[clickedProv.id]
                                                                    if (selectedProvinceIds.size > 1) {
                                                                        "Multi-Select: ${selectedProvinceIds.size} provinces selected (last: #${clickedProv.id})"
                                                                    } else {
                                                                        "Province #${clickedProv.id} (${clickedProv.terrain}) | State: ${st ?: "-"} | Region: ${reg ?: "-"}"
                                                                    }
                                                                } else {
                                                                    val r = (clickedRgb shr 16) and 0xFF
                                                                    val g = (clickedRgb shr 8) and 0xFF
                                                                    val b = clickedRgb and 0xFF
                                                                    "Undefined RGB ($r, $g, $b) at ($mx, $my)"
                                                                }
                                                            } else {
                                                                when (activeTool) {
                                                                    EditorTool.BRUSH -> {
                                                                        primaryProvince?.let { prov ->
                                                                            project.beginPaintStroke()
                                                                            isPaintingStroke = true
                                                                            project.paintCircleWithBounds(
                                                                                mx,
                                                                                my,
                                                                                brushRadius.roundToInt(),
                                                                                prov.rgbInt
                                                                            )
                                                                                ?.let(::refreshPaintTiles)
                                                                        }
                                                                    }

                                                                    EditorTool.BUCKET -> {
                                                                        primaryProvince?.let { prov ->
                                                                            if (project.floodFill(
                                                                                    mx,
                                                                                    my,
                                                                                    prov.rgbInt
                                                                                )
                                                                            ) {
                                                                                refreshRenderedMap()
                                                                                statusMessage =
                                                                                    "Flood-filled area with Province #${prov.id}"
                                                                            }
                                                                        }
                                                                    }

                                                                    EditorTool.RAILWAY -> {
                                                                        if (clickedProv != null && activeRailwayPath.lastOrNull() != clickedProv.id) {
                                                                            activeRailwayPath.add(clickedProv.id)
                                                                            statusMessage = "Railway path: ${
                                                                                activeRailwayPath.joinToString(" -> ")
                                                                            } (Press Enter to finish)"
                                                                        }
                                                                    }

                                                                    EditorTool.SUPPLY_NODE -> {
                                                                        if (clickedProv != null) {
                                                                            val added =
                                                                                project.toggleSupplyNode(clickedProv.id)
                                                                            editRevision++
                                                                            statusMessage = if (added) {
                                                                                "Added Supply Hub at Province #${clickedProv.id}"
                                                                            } else {
                                                                                "Removed Supply Hub from Province #${clickedProv.id}"
                                                                            }
                                                                        }
                                                                    }

                                                                    EditorTool.BUILDING -> {
                                                                        val seaId =
                                                                            buildingSeaProvinceInput.toIntOrNull() ?: 0
                                                                        val added = project.addBuildingAtBmpCoords(
                                                                            mx,
                                                                            my,
                                                                            buildingTypeToPlace,
                                                                            seaId
                                                                        )
                                                                        if (added != null) {
                                                                            handleProvinceSelection(
                                                                                clickedProv,
                                                                                toggleMultiSelect = false
                                                                            )
                                                                            editRevision++
                                                                            statusMessage =
                                                                                "Placed ${added.type} in State #${added.stateId} at HoI4 (${added.x.toInt()}, ${added.z.toInt()})"
                                                                        } else {
                                                                            statusMessage =
                                                                                "Cannot place building here: Province must belong to a State!"
                                                                        }
                                                                    }

                                                                    else -> {}
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            PointerEventType.Move -> {
                                                val dragDelta = change.position - change.previousPosition
                                                if (dragDelta != Offset.Zero) {
                                                    if (buttons.isSecondaryPressed || buttons.isTertiaryPressed ||
                                                        (buttons.isPrimaryPressed && (isSpacePressed || ctrlDown || mapPickMode != MapPickMode.NONE || activeTool != EditorTool.BRUSH))
                                                    ) {
                                                        offset += dragDelta
                                                        change.consume()
                                                    } else if (buttons.isPrimaryPressed && activeTool == EditorTool.BRUSH) {
                                                        primaryProvince?.let { prov ->
                                                            if (!isPaintingStroke) {
                                                                project.beginPaintStroke()
                                                                isPaintingStroke = true
                                                            }
                                                            project.paintCircleWithBounds(
                                                                mx,
                                                                my,
                                                                brushRadius.roundToInt(),
                                                                prov.rgbInt
                                                            )
                                                                ?.let(::refreshPaintTiles)
                                                            change.consume()
                                                        }
                                                    }
                                                }
                                            }

                                            PointerEventType.Release -> {
                                                if (isPaintingStroke) {
                                                    project.endPaintStroke()
                                                    isPaintingStroke = false

                                                    val tilesPerRow =
                                                        (project.mapImage.width + MAP_TILE_SIZE - 1) / MAP_TILE_SIZE
                                                    val tilesPerColumn =
                                                        (project.mapImage.height + MAP_TILE_SIZE - 1) / MAP_TILE_SIZE
                                                    val totalTiles = tilesPerRow * tilesPerColumn
                                                    val overlayTileLimit = minOf(64, maxOf(8, totalTiles / 4))
                                                    if (paintOverlayTiles.size > overlayTileLimit) refreshRenderedMap()
                                                    else editRevision++
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        // 1. Base Map Image
                        drawImage(
                            image = composeMapBitmap,
                            dstOffset = IntOffset(offset.x.roundToInt(), offset.y.roundToInt()),
                            dstSize = IntSize(
                                (composeMapBitmap.width * scale).roundToInt(),
                                (composeMapBitmap.height * scale).roundToInt()
                            ),
                            filterQuality = FilterQuality.None
                        )

                        // 2. Dirty Paint Tiles
                        val tilesPerRow = (project.mapImage.width + MAP_TILE_SIZE - 1) / MAP_TILE_SIZE
                        for ((tileKey, tileBitmap) in paintOverlayTiles) {
                            val tileX = tileKey % tilesPerRow
                            val tileY = tileKey / tilesPerRow
                            drawImage(
                                image = tileBitmap,
                                dstOffset = IntOffset(
                                    (offset.x + tileX * MAP_TILE_SIZE * scale).roundToInt(),
                                    (offset.y + tileY * MAP_TILE_SIZE * scale).roundToInt()
                                ),
                                dstSize = IntSize(
                                    (tileBitmap.width * scale).roundToInt().coerceAtLeast(1),
                                    (tileBitmap.height * scale).roundToInt().coerceAtLeast(1)
                                ),
                                filterQuality = FilterQuality.None
                            )
                        }

                        // 3. Highlighted State / Strategic Region Outline (Cyan)
                        if (showSelectionOutline && groupBorderSegments.isNotEmpty()) {
                            for (seg in groupBorderSegments) {
                                val start = Offset(offset.x + seg.x1 * scale, offset.y + seg.y1 * scale)
                                val end = Offset(offset.x + seg.x2 * scale, offset.y + seg.y2 * scale)
                                drawLine(
                                    color = Color(0xFF00E5FF),
                                    start = start,
                                    end = end,
                                    strokeWidth = 2.5f
                                )
                            }
                        }

                        // 4. Selected Province(s) Outline (Dual-pass Black + Bright Yellow)
                        if (showSelectionOutline && selectionBorderSegments.isNotEmpty()) {
                            for (seg in selectionBorderSegments) {
                                val start = Offset(offset.x + seg.x1 * scale, offset.y + seg.y1 * scale)
                                val end = Offset(offset.x + seg.x2 * scale, offset.y + seg.y2 * scale)
                                drawLine(
                                    color = Color.Black.copy(alpha = 0.85f),
                                    start = start,
                                    end = end,
                                    strokeWidth = 4.2f
                                )
                                drawLine(
                                    color = Color(0xFFFFEA00),
                                    start = start,
                                    end = end,
                                    strokeWidth = 2.0f
                                )
                            }
                        }

                        // 5. Adjacencies / Straits Overlay
                        if (showAdjacenciesOverlay) {
                            for (adj in project.adjacencies) {
                                val c1 = project.provinceCenters[adj.fromProvince] ?: continue
                                val c2 = project.provinceCenters[adj.toProvince] ?: continue
                                val p1 =
                                    Offset(offset.x + (c1.first + 0.5f) * scale, offset.y + (c1.second + 0.5f) * scale)
                                val p2 =
                                    Offset(offset.x + (c2.first + 0.5f) * scale, offset.y + (c2.second + 0.5f) * scale)
                                drawLine(
                                    color = if (adj.type == "impassable") Color.Red else Color.Cyan,
                                    start = p1,
                                    end = p2,
                                    strokeWidth = (1.8f * scale.coerceIn(0.8f, 3f)),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                                )
                            }
                        }

                        // 6. Railways Overlay
                        if (showRailwaysOverlay || activeTool == EditorTool.RAILWAY) {
                            val strokeW = (1.2f * scale.coerceIn(0.8f, 3.5f))
                            for (rail in project.railways) {
                                val prows = rail.provinces
                                for (i in 0 until prows.size - 1) {
                                    val c1 = project.provinceCenters[prows[i]] ?: continue
                                    val c2 = project.provinceCenters[prows[i + 1]] ?: continue
                                    val p1 = Offset(
                                        offset.x + (c1.first + 0.5f) * scale,
                                        offset.y + (c1.second + 0.5f) * scale
                                    )
                                    val p2 = Offset(
                                        offset.x + (c2.first + 0.5f) * scale,
                                        offset.y + (c2.second + 0.5f) * scale
                                    )
                                    drawLine(
                                        color = Color(0xFFFFB703),
                                        start = p1,
                                        end = p2,
                                        strokeWidth = strokeW
                                    )
                                }
                            }
                            for (i in 0 until activeRailwayPath.size - 1) {
                                val c1 = project.provinceCenters[activeRailwayPath[i]] ?: continue
                                val c2 = project.provinceCenters[activeRailwayPath[i + 1]] ?: continue
                                val p1 =
                                    Offset(offset.x + (c1.first + 0.5f) * scale, offset.y + (c1.second + 0.5f) * scale)
                                val p2 =
                                    Offset(offset.x + (c2.first + 0.5f) * scale, offset.y + (c2.second + 0.5f) * scale)
                                drawLine(
                                    color = Color.Green,
                                    start = p1,
                                    end = p2,
                                    strokeWidth = strokeW * 1.4f
                                )
                            }
                        }

                        // 7. Supply Hubs Overlay
                        if (showSupplyOverlay || activeTool == EditorTool.SUPPLY_NODE) {
                            val boxSize = (3.5f * scale.coerceIn(1f, 4f))
                            for (nodeProvId in project.supplyNodes) {
                                val c = project.provinceCenters[nodeProvId] ?: continue
                                val sx = offset.x + (c.first + 0.5f) * scale - boxSize / 2f
                                val sy = offset.y + (c.second + 0.5f) * scale - boxSize / 2f
                                drawRect(
                                    color = Color(0xFF00F5D4),
                                    topLeft = Offset(sx, sy),
                                    size = Size(boxSize, boxSize)
                                )
                            }
                        }

                        // 8. Buildings Overlay
                        if (showBuildingsOverlay || activeTool == EditorTool.BUILDING) {
                            val mapH = project.mapImage.height
                            val activeStId = selectedState?.id
                            val radius = (2.2f * scale.coerceIn(1f, 4f))
                            for (b in project.buildings) {
                                if (scale < 2.5f && activeStId != null && b.stateId != activeStId) continue
                                val bx = b.x.toFloat()
                                val by = (mapH - b.z).toFloat()
                                val screenPos = Offset(offset.x + bx * scale, offset.y + by * scale)
                                val col = when {
                                    b.type.contains("naval") || b.type.contains("harbor") || b.type == "dockyard" -> Color.Cyan
                                    b.type == "arms_factory" -> Color(0xFFFF595E)
                                    b.type == "industrial_complex" -> Color(0xFFFFCA3A)
                                    else -> Color.White
                                }
                                drawCircle(color = col, radius = radius, center = screenPos)
                            }
                        }

                        // 9. Live Brush Preview Circle
                        if (activeTool == EditorTool.BRUSH && mapPickMode == MapPickMode.NONE && hoverScreenPos != null) {
                            drawCircle(
                                color = Color.White,
                                radius = (brushRadius + 0.5f) * scale,
                                center = hoverScreenPos!!,
                                style = Stroke(width = 1.5f)
                            )
                        }
                    }

                    // Bottom-Left Overlay: Status Bar with Live Hover Preview of Province / State / Region
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, AppCardShape),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        shape = AppCardShape
                    ) {
                        val hoverInfoText = hoverMapCoords?.let { (bx, by) ->
                            val hoi4Z = project.mapImage.height - by
                            val rgb = project.mapImage.getRGB(bx, by) and 0xFFFFFF
                            val hProv = project.provincesByRgb[rgb]
                            val hState = hProv?.let { project.provinceToStateId[it.id] }?.let { project.statesById[it] }
                            val hReg = hProv?.let { project.provinceToStrategicRegionId[it.id] }
                                ?.let { project.strategicRegionsById[it] }
                            val ownerLabel = when {
                                mapPickMode == MapPickMode.PICK_STATE_FOR_SELECTED || mapViewMode == MapViewMode.STATES ->
                                    if (hState != null) " [State #${hState.id}: ${hState.name}]" else " [Unassigned]"

                                mapPickMode == MapPickMode.PICK_REGION_FOR_SELECTED || mapViewMode == MapViewMode.STRATEGIC_REGIONS ->
                                    if (hReg != null) " [Region #${hReg.id}: ${hReg.name}]" else " [No Region]"

                                hProv != null -> " [#${hProv.id}]"
                                else -> ""
                            }
                            "BMP: ($bx, $by) | HoI4: ($bx, $hoi4Z)$ownerLabel  •  "
                        } ?: ""
                        Text(
                            text = "${hoverInfoText}Zoom: ${(scale * 100).toInt()}%  •  $statusMessage",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Vertical Border between Map Column and Sidebar
            VerticalDivider(color = MaterialTheme.colorScheme.outline)

            // --- RIGHT SIDE: SIDEBAR WITH 5 TABS ---
            Surface(
                modifier = Modifier.width(440.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 8.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Text(
                                    stringResource(Res.string.tab_inspector),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Text(
                                    stringResource(Res.string.tab_provinces),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = {
                                selectedTab = 2
                                if (mapViewMode != MapViewMode.STATES) {
                                    mapViewMode = MapViewMode.STATES
                                    refreshRenderedMap()
                                }
                            },
                            text = {
                                Text(
                                    stringResource(Res.string.tab_states),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = {
                                Text(
                                    stringResource(Res.string.tab_infra),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 4,
                            onClick = { selectedTab = 4 },
                            text = {
                                Text(
                                    stringResource(Res.string.tab_diagnostics),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedTab) {
                            0 -> InspectorTabContent(
                                project = project,
                                editRevision = editRevision,
                                onShowNewProvinceDialog = { showNewProvinceDialog = true },
                                selectedProvinceIds = selectedProvinceIds,
                                primaryProvince = primaryProvince,
                                onRemoveFromSelection = { idToRemove ->
                                    selectedProvinceIds.remove(idToRemove)
                                    if (primaryProvince?.id == idToRemove) {
                                        primaryProvince =
                                            selectedProvinceIds.lastOrNull()?.let { project.provincesById[it] }
                                    }
                                },
                                onClearSelection = {
                                    selectedProvinceIds.clear()
                                    primaryProvince = null
                                },
                                copiedClipboard = copiedClipboard,
                                onCenterOnProvince = { centerCameraOnProvince(it) },
                                onCopyProvince = ::copyActiveProvince,
                                onPasteProvince = ::pasteToSelectedProvinces,
                                mapPickMode = mapPickMode,
                                onStartPickStateOnMap = {
                                    if (mapPickMode == MapPickMode.PICK_STATE_FOR_SELECTED) {
                                        mapPickMode = MapPickMode.NONE
                                    } else {
                                        mapPickMode = MapPickMode.PICK_STATE_FOR_SELECTED
                                        if (mapViewMode != MapViewMode.STATES) {
                                            mapViewMode = MapViewMode.STATES
                                            refreshRenderedMap()
                                        }
                                    }
                                },
                                onMoveSelectedToNewState = { showNewStateDialog = true },
                                onStartPickRegionOnMap = {
                                    if (mapPickMode == MapPickMode.PICK_REGION_FOR_SELECTED) {
                                        mapPickMode = MapPickMode.NONE
                                    } else {
                                        mapPickMode = MapPickMode.PICK_REGION_FOR_SELECTED
                                        if (mapViewMode != MapViewMode.STRATEGIC_REGIONS) {
                                            mapViewMode = MapViewMode.STRATEGIC_REGIONS
                                            refreshRenderedMap()
                                        }
                                    }
                                },
                                onMoveSelectedToNewRegion = { showNewRegionDialog = true },
                                onBatchProvinceEdit = ::stageBatchProvinceDefinitionEdit
                            )

                            1 -> ProvinceListViewTab(
                                project = project,
                                editRevision = editRevision,
                                selectedProvinceIds = selectedProvinceIds,
                                isCtrlHeld = isCtrlHeld,
                                onSelectProvince = { prov, ctrlClick ->
                                    handleProvinceSelection(prov, toggleMultiSelect = ctrlClick)
                                },
                                onJumpToProvince = { prov ->
                                    handleProvinceSelection(prov, toggleMultiSelect = false)
                                    centerCameraOnProvince(prov)
                                }
                            )

                            2 -> StateListViewTab(
                                project = project,
                                editRevision = editRevision,
                                selectedState = selectedState,
                                selectedProvinceCount = selectedProvinceIds.size,
                                isPaintingStateProvinces = mapPickMode == MapPickMode.CLICK_PROVINCES_INTO_ACTIVE_STATE,
                                onTogglePaintStateProvinces = {
                                    if (mapPickMode == MapPickMode.CLICK_PROVINCES_INTO_ACTIVE_STATE) {
                                        mapPickMode = MapPickMode.NONE
                                    } else if (selectedState != null) {
                                        mapPickMode = MapPickMode.CLICK_PROVINCES_INTO_ACTIVE_STATE
                                        if (mapViewMode != MapViewMode.STATES) {
                                            mapViewMode = MapViewMode.STATES
                                            refreshRenderedMap()
                                        }
                                    }
                                },
                                onAssignSelectedProvincesToState = { st ->
                                    assignSelectedProvincesToState(st.id)
                                },
                                onSelectState = { st ->
                                    selectedState = st
                                    highlightedId = st.id
                                    refreshRenderedMap()
                                },
                                onJumpToState = { st ->
                                    selectedState = st
                                    highlightedId = st.id
                                    refreshRenderedMap()
                                    centerCameraOnState(st)
                                },
                                onJumpToProvince = { provId ->
                                    project.provincesById[provId]?.let { prov ->
                                        handleProvinceSelection(prov, toggleMultiSelect = false)
                                        centerCameraOnProvince(prov)
                                    }
                                },
                                onRemoveProvinceFromState = { provId, stId ->
                                    project.removeProvinceFromState(provId, stId)
                                    selectedState = project.statesById[stId]?.copy()
                                    refreshRenderedMap()
                                },
                                onCreateNewState = { showNewStateDialog = true }
                            )

                            3 -> InfrastructureTabContent(
                                project = project,
                                editRevision = editRevision,
                                activeTool = activeTool,
                                onSelectTool = {
                                    mapPickMode = MapPickMode.NONE
                                    activeTool = it
                                },
                                railwayLevel = railwayLevel,
                                onRailwayLevelChange = { railwayLevel = it },
                                activeRailwayNodesCount = activeRailwayPath.size,
                                onFinishRailway = ::finishActiveRailway,
                                onDeleteLastRailway = {
                                    if (project.removeLastRailway()) {
                                        editRevision++
                                        statusMessage = "Deleted last railway track."
                                    }
                                },
                                buildingType = buildingTypeToPlace,
                                onBuildingTypeChange = { buildingTypeToPlace = it },
                                buildingSeaProv = buildingSeaProvinceInput,
                                onBuildingSeaProvChange = { buildingSeaProvinceInput = it },
                                selectedState = selectedState,
                                onAutoGenerateBuildings = { stId ->
                                    val count = project.autoGenerateBuildingsForState(stId)
                                    showBuildingsOverlay = true
                                    editRevision++
                                    statusMessage = "Generated $count buildings for State #$stId (staged in RAM)!"
                                },
                                onDeleteBuilding = { b ->
                                    project.removeBuilding(b)
                                    editRevision++
                                },
                                onJumpToCoords = { bx, by -> centerCameraAt(bx, by, 8f) },
                                onShowAddAdjacency = { showAddAdjacencyDialog = true },
                                onDeleteAdjacency = { adj ->
                                    project.removeAdjacency(adj)
                                    editRevision++
                                }
                            )

                            4 -> DiagnosticsTabContent(
                                project = project,
                                onJumpToProvince = { provId ->
                                    project.provincesById[provId]?.let { prov ->
                                        handleProvinceSelection(prov, toggleMultiSelect = false)
                                        centerCameraOnProvince(prov)
                                    }
                                },
                                onJumpToState = { stId ->
                                    project.statesById[stId]?.let { st ->
                                        selectedState = st
                                        highlightedId = st.id
                                        mapViewMode = MapViewMode.STATES
                                        refreshRenderedMap()
                                        centerCameraOnState(st)
                                    }
                                },
                                onJumpToCoordinates = { (x, y) ->
                                    centerCameraAt(x, y, 12f)
                                },
                                onCoastalFixed = { count ->
                                    refreshRenderedMap()
                                    statusMessage = "Auto-fixed coastal flags for $count provinces (staged in RAM)!"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewProvinceDialog) {
        NewProvinceDialog(
            onDismiss = { showNewProvinceDialog = false },
            onCreate = { type, coastal, terrain, continent ->
                val created = project.createNewProvince(type, coastal, terrain, continent)
                handleProvinceSelection(created, toggleMultiSelect = false)
                mapPickMode = MapPickMode.NONE
                activeTool = EditorTool.BRUSH
                showNewProvinceDialog = false
                editRevision++
                statusMessage = "Staged new Province #${created.id} with RGB(${created.r},${created.g},${created.b})"
            }
        )
    }

    if (showNewStateDialog) {
        NewStateDialog(
            onDismiss = { showNewStateDialog = false },
            onCreate = { name, manpower, category ->
                val initialIds = if (selectedProvinceIds.isNotEmpty()) selectedProvinceIds.toList()
                else listOfNotNull(primaryProvince?.id)
                val created = project.createNewState(name, manpower, category, initialIds)
                selectedState = created
                highlightedId = created.id
                mapPickMode = MapPickMode.NONE
                showNewStateDialog = false
                refreshRenderedMap()
                statusMessage =
                    "Created State #${created.id} (${created.name}) and assigned ${initialIds.size} province(s)!"
            }
        )
    }

    if (showNewRegionDialog) {
        NewStrategicRegionDialog(
            onDismiss = { showNewRegionDialog = false },
            onCreate = { name ->
                val initialIds = if (selectedProvinceIds.isNotEmpty()) selectedProvinceIds.toList()
                else listOfNotNull(primaryProvince?.id)
                val created = project.createNewStrategicRegion(name, initialIds)
                highlightedId = created.id
                mapPickMode = MapPickMode.NONE
                showNewRegionDialog = false
                refreshRenderedMap()
                statusMessage =
                    "Created Strategic Region #${created.id} (${created.name}) and assigned ${initialIds.size} province(s)!"
            }
        )
    }

    if (showAddAdjacencyDialog) {
        AddAdjacencyDialog(
            onDismiss = { showAddAdjacencyDialog = false },
            onAdd = { fromId, toId, throughSeaId, ruleName, comment ->
                project.addAdjacency(fromId, toId, throughSeaId, ruleName, comment)
                showAdjacenciesOverlay = true
                showAddAdjacencyDialog = false
                editRevision++
                statusMessage = "Staged adjacency #$fromId <-> #$toId in RAM."
            }
        )
    }
}

/**
 * Dedicated, always-visible toolbar docked directly above the Map Canvas.
 * Contains all Editor Tools, inline Brush/Railway settings, and Map View Mode switches.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapHeaderToolbar(
    activeTool: EditorTool,
    onToolChange: (EditorTool) -> Unit,
    brushRadius: Float,
    onBrushRadiusChange: (Float) -> Unit,
    activeRailwayNodesCount: Int,
    onFinishRailway: () -> Unit,
    mapViewMode: MapViewMode,
    onMapViewModeChange: (MapViewMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Editor Tools + Context-Sensitive Inline Controls (Brush Size / Railway Finish)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppFilterChip(
                        selected = activeTool == EditorTool.NAVIGATE,
                        onClick = { onToolChange(EditorTool.NAVIGATE) },
                        label = {
                            Text(
                                stringResource(Res.string.tool_navigate),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.OpenWith,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                    AppFilterChip(
                        selected = activeTool == EditorTool.EYEDROPPER,
                        onClick = { onToolChange(EditorTool.EYEDROPPER) },
                        label = {
                            Text(
                                stringResource(Res.string.tool_eyedropper),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Colorize,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                    AppFilterChip(
                        selected = activeTool == EditorTool.BRUSH,
                        onClick = { onToolChange(EditorTool.BRUSH) },
                        label = {
                            Text(
                                stringResource(Res.string.tool_brush),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Brush,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                    AppFilterChip(
                        selected = activeTool == EditorTool.BUCKET,
                        onClick = { onToolChange(EditorTool.BUCKET) },
                        label = {
                            Text(
                                stringResource(Res.string.tool_bucket),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.FormatColorFill,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                    AppFilterChip(
                        selected = activeTool == EditorTool.RAILWAY,
                        onClick = { onToolChange(EditorTool.RAILWAY) },
                        label = {
                            Text(
                                stringResource(Res.string.tool_railway),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Timeline,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                    AppFilterChip(
                        selected = activeTool == EditorTool.SUPPLY_NODE,
                        onClick = { onToolChange(EditorTool.SUPPLY_NODE) },
                        label = {
                            Text(
                                stringResource(Res.string.tool_supply),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                    AppFilterChip(
                        selected = activeTool == EditorTool.BUILDING,
                        onClick = { onToolChange(EditorTool.BUILDING) },
                        label = {
                            Text(
                                stringResource(Res.string.tool_building),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Factory,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }

                // Inline Brush Radius Slider when Brush is active
                if (activeTool == EditorTool.BRUSH) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.width(220.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.brush_radius, brushRadius.roundToInt()),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Slider(
                            value = brushRadius,
                            onValueChange = onBrushRadiusChange,
                            valueRange = 0f..30f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else if (activeTool == EditorTool.RAILWAY && activeRailwayNodesCount >= 2) {
                    AppButton(
                        onClick = onFinishRailway,
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(
                            stringResource(Res.string.finish_railway_btn, activeRailwayNodesCount),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Row 2: Map View Mode Switcher (Always visible above the map)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                AppFilterChip(
                    selected = mapViewMode == MapViewMode.PROVINCES,
                    onClick = { onMapViewModeChange(MapViewMode.PROVINCES) },
                    label = {
                        Text(
                            stringResource(Res.string.map_mode_provinces),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
                AppFilterChip(
                    selected = mapViewMode == MapViewMode.STATES,
                    onClick = { onMapViewModeChange(MapViewMode.STATES) },
                    label = {
                        Text(
                            stringResource(Res.string.map_mode_states),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
                AppFilterChip(
                    selected = mapViewMode == MapViewMode.STRATEGIC_REGIONS,
                    onClick = { onMapViewModeChange(MapViewMode.STRATEGIC_REGIONS) },
                    label = {
                        Text(
                            stringResource(Res.string.map_mode_regions),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
                AppFilterChip(
                    selected = mapViewMode == MapViewMode.TERRAIN,
                    onClick = { onMapViewModeChange(MapViewMode.TERRAIN) },
                    label = {
                        Text(
                            stringResource(Res.string.map_mode_terrain),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
                AppFilterChip(
                    selected = mapViewMode == MapViewMode.COASTAL,
                    onClick = { onMapViewModeChange(MapViewMode.COASTAL) },
                    label = {
                        Text(
                            stringResource(Res.string.map_mode_coastal),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun TopMenuBar(
    project: Hoi4MapProject?,
    editRevision: Int,
    currentLang: String,
    onLanguageChange: (String) -> Unit,
    onSaveAll: () -> Unit,
    onReloadProject: () -> Unit,
    onSwitchProject: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onClearSelection: () -> Unit,
    onAutoFixCoastal: () -> Unit,
    onRecalcCenters: () -> Unit,
    onFitView: () -> Unit,
    showSelectionOutline: Boolean,
    onToggleSelectionOutline: (Boolean) -> Unit,
    showRailwaysOverlay: Boolean,
    onToggleRailways: (Boolean) -> Unit,
    showSupplyOverlay: Boolean,
    onToggleSupply: (Boolean) -> Unit,
    showBuildingsOverlay: Boolean,
    onToggleBuildings: (Boolean) -> Unit,
    showAdjacenciesOverlay: Boolean,
    onToggleAdjacencies: (Boolean) -> Unit
) {
    var fileMenuExpanded by remember { mutableStateOf(false) }
    var editMenuExpanded by remember { mutableStateOf(false) }
    var viewMenuExpanded by remember { mutableStateOf(false) }

    val hasProject = project != null
    val isDirty = remember(editRevision, project?.hasUnsavedChanges) { project?.hasUnsavedChanges == true }
    val summary = remember(editRevision, project?.hasUnsavedChanges) { project?.unsavedSummary().orEmpty() }
    val canUndo = remember(editRevision, project) { project?.canUndo() == true }
    val canRedo = remember(editRevision, project) { project?.canRedo() == true }

    Surface(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = AppButtonShape
                ) {
                    Text(
                        text = project?.modPath?.name ?: stringResource(Res.string.no_project_loaded),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                // FILE MENU
                Box {
                    AppTextButton(onClick = { fileMenuExpanded = true }) {
                        Text(stringResource(Res.string.menu_file))
                    }
                    DropdownMenu(expanded = fileMenuExpanded, onDismissRequest = { fileMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_file_save_all)) },
                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                            enabled = hasProject,
                            onClick = { fileMenuExpanded = false; onSaveAll() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_file_reload)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            enabled = hasProject,
                            onClick = { fileMenuExpanded = false; onReloadProject() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_file_switch_project)) },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            enabled = hasProject,
                            onClick = { fileMenuExpanded = false; onSwitchProject() }
                        )
                    }
                }

                // EDIT MENU
                Box {
                    AppTextButton(onClick = { editMenuExpanded = true }, enabled = hasProject) {
                        Text(stringResource(Res.string.menu_edit))
                    }
                    DropdownMenu(expanded = editMenuExpanded, onDismissRequest = { editMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_edit_undo)) },
                            leadingIcon = { Icon(Icons.Default.Undo, contentDescription = null) },
                            enabled = canUndo,
                            onClick = { editMenuExpanded = false; onUndo() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_edit_redo)) },
                            leadingIcon = { Icon(Icons.Default.Redo, contentDescription = null) },
                            enabled = canRedo,
                            onClick = { editMenuExpanded = false; onRedo() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_edit_copy)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = { editMenuExpanded = false; onCopy() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_edit_paste)) },
                            leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                            onClick = { editMenuExpanded = false; onPaste() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_edit_clear_selection)) },
                            leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) },
                            onClick = { editMenuExpanded = false; onClearSelection() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_edit_autofix_coastal)) },
                            leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) },
                            onClick = { editMenuExpanded = false; onAutoFixCoastal() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_edit_recalc_centers)) },
                            leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                            onClick = { editMenuExpanded = false; onRecalcCenters() }
                        )
                    }
                }

                // VIEW & OVERLAYS MENU
                Box {
                    AppTextButton(onClick = { viewMenuExpanded = true }, enabled = hasProject) {
                        Text(stringResource(Res.string.menu_view))
                    }
                    DropdownMenu(expanded = viewMenuExpanded, onDismissRequest = { viewMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.menu_view_fit)) },
                            leadingIcon = { Icon(Icons.Default.FitScreen, contentDescription = null) },
                            onClick = { viewMenuExpanded = false; onFitView() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.overlay_selection_outline)) },
                            leadingIcon = { Checkbox(checked = showSelectionOutline, onCheckedChange = null) },
                            onClick = { onToggleSelectionOutline(!showSelectionOutline) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.overlay_railways)) },
                            leadingIcon = { Checkbox(checked = showRailwaysOverlay, onCheckedChange = null) },
                            onClick = { onToggleRailways(!showRailwaysOverlay) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.overlay_supply)) },
                            leadingIcon = { Checkbox(checked = showSupplyOverlay, onCheckedChange = null) },
                            onClick = { onToggleSupply(!showSupplyOverlay) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.overlay_buildings)) },
                            leadingIcon = { Checkbox(checked = showBuildingsOverlay, onCheckedChange = null) },
                            onClick = { onToggleBuildings(!showBuildingsOverlay) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.overlay_adjacencies)) },
                            leadingIcon = { Checkbox(checked = showAdjacenciesOverlay, onCheckedChange = null) },
                            onClick = { onToggleAdjacencies(!showAdjacenciesOverlay) }
                        )
                    }
                }

                if (hasProject) {
                    VerticalDivider(
                        modifier = Modifier.height(24.dp).padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.outline
                    )

                    AppOutlinedButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.undo_stroke), style = MaterialTheme.typography.labelSmall)
                    }

                    AppOutlinedButton(
                        onClick = onRedo,
                        enabled = canRedo,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Redo, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.redo_stroke), style = MaterialTheme.typography.labelSmall)
                    }

                    AppOutlinedButton(
                        onClick = onFitView,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.FitScreen, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.reset_view), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasProject) {
                    if (isDirty) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = AppButtonShape
                        ) {
                            Text(
                                text = stringResource(Res.string.unsaved_indicator, summary),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(Res.string.saved_indicator),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AppButton(
                        onClick = onSaveAll,
                        enabled = isDirty,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.save_now_btn), style = MaterialTheme.typography.labelMedium)
                    }

                    VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.outline)
                }

                LanguageSwitcher(currentLang = currentLang, onLanguageChange = onLanguageChange)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InspectorTabContent(
    project: Hoi4MapProject,
    editRevision: Int,
    onShowNewProvinceDialog: () -> Unit,
    selectedProvinceIds: List<Int>,
    primaryProvince: ProvinceDef?,
    onRemoveFromSelection: (Int) -> Unit,
    onClearSelection: () -> Unit,
    copiedClipboard: CopiedProvinceClipboard?,
    onCenterOnProvince: (ProvinceDef) -> Unit,
    onCopyProvince: () -> Unit,
    onPasteProvince: () -> Unit,
    mapPickMode: MapPickMode,
    onStartPickStateOnMap: () -> Unit,
    onMoveSelectedToNewState: () -> Unit,
    onStartPickRegionOnMap: () -> Unit,
    onMoveSelectedToNewRegion: () -> Unit,
    onBatchProvinceEdit: ((ProvinceDef) -> ProvinceDef) -> Unit
) {
    val knownTypes = listOf("land", "sea", "lake")
    val knownTerrains =
        listOf("plains", "forest", "hills", "mountain", "urban", "jungle", "marsh", "desert", "ocean", "lakes")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppButton(onClick = onShowNewProvinceDialog, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.create_province_btn), fontWeight = FontWeight.SemiBold)
        }

        Text(
            text = stringResource(Res.string.nav_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

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
            val prov = project.provincesById[primaryProvince.id] ?: primaryProvince
            val currentOwnerStateId = remember(editRevision, prov.id) { project.provinceToStateId[prov.id] }
            val currentOwnerState = currentOwnerStateId?.let { project.statesById[it] }
            val currentRegionId = remember(editRevision, prov.id) { project.provinceToStrategicRegionId[prov.id] }
            val currentRegion = currentRegionId?.let { project.strategicRegionsById[it] }

            if (selectedProvinceIds.size > 1) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary, AppCardShape),
                    shape = AppCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = 0.5f
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(Res.string.multi_select_title, selectedProvinceIds.size),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            AppOutlinedButton(
                                onClick = onClearSelection,
                                modifier = Modifier.height(26.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    stringResource(Res.string.clear_selection_btn),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            selectedProvinceIds.forEach { pid ->
                                AssistChip(
                                    onClick = { project.provincesById[pid]?.let(onCenterOnProvince) },
                                    label = { Text("#$pid", style = MaterialTheme.typography.labelSmall) },
                                    shape = AppButtonShape,
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp).clickable { onRemoveFromSelection(pid) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Primary Province Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, AppCardShape),
                shape = AppCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    .background(Color(prov.r, prov.g, prov.b), AppButtonShape)
                                    .border(1.dp, Color.White, AppButtonShape)
                            )
                            Column {
                                Text(stringResource(Res.string.province_title, prov.id), fontWeight = FontWeight.Bold)
                                Text(
                                    "RGB: (${prov.r}, ${prov.g}, ${prov.b})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        IconButton(onClick = { onCenterOnProvince(prov) }) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = stringResource(Res.string.center_on_province)
                            )
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
                        AppOutlinedButton(onClick = onCopyProvince, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.copy_province), style = MaterialTheme.typography.labelSmall)
                        }
                        AppOutlinedButton(
                            onClick = onPasteProvince,
                            enabled = copiedClipboard != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.paste_province), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // --- MAP-CLICK STATE & STRATEGIC REGION ASSIGNMENT (NO TYPING IDS!) ---
            Text(
                stringResource(Res.string.state_assign_header),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // State Assignment Row: "Pick State on Map" | "+ Move to New State"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val isPickingState = mapPickMode == MapPickMode.PICK_STATE_FOR_SELECTED
                AppButton(
                    onClick = onStartPickStateOnMap,
                    containerColor = if (isPickingState) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    contentColor = if (isPickingState) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.pick_state_on_map_btn), style = MaterialTheme.typography.labelMedium)
                }

                AppOutlinedButton(
                    onClick = onMoveSelectedToNewState,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(Res.string.move_to_new_state_btn), style = MaterialTheme.typography.labelMedium)
                }
            }

            // Strategic Region Assignment Row: "Pick Region on Map" | "+ Move to New Region"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val isPickingRegion = mapPickMode == MapPickMode.PICK_REGION_FOR_SELECTED
                AppButton(
                    onClick = onStartPickRegionOnMap,
                    containerColor = if (isPickingRegion) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                    contentColor = if (isPickingRegion) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(Res.string.pick_region_on_map_btn),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                AppOutlinedButton(
                    onClick = onMoveSelectedToNewRegion,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(Res.string.move_to_new_region_btn),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Instant Reactive Province Definition Editor (Applies to all selected provinces!)
            Text(
                stringResource(Res.string.edit_def_header),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                knownTypes.forEach { t ->
                    AppFilterChip(
                        selected = prov.type == t,
                        onClick = {
                            onBatchProvinceEdit { p ->
                                val newTerrain = if (t == "sea" && p.terrain != "ocean") "ocean" else p.terrain
                                p.copy(type = t, terrain = newTerrain)
                            }
                        },
                        label = { Text(t) }
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                knownTerrains.forEach { terr ->
                    AppFilterChip(
                        selected = prov.terrain == terr,
                        onClick = { onBatchProvinceEdit { p -> p.copy(terrain = terr) } },
                        label = { Text(terr, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            OutlinedTextField(
                value = prov.terrain,
                onValueChange = { newTerr -> onBatchProvinceEdit { p -> p.copy(terrain = newTerr.trim()) } },
                label = { Text(stringResource(Res.string.field_terrain)) },
                modifier = Modifier.fillMaxWidth(),
                shape = AppButtonShape,
                singleLine = true
            )

            OutlinedTextField(
                value = prov.continent.toString(),
                onValueChange = { str ->
                    val c = str.filter { it.isDigit() }.toIntOrNull() ?: 0
                    onBatchProvinceEdit { p -> p.copy(continent = c) }
                },
                label = { Text(stringResource(Res.string.field_continent)) },
                modifier = Modifier.fillMaxWidth(),
                shape = AppButtonShape,
                singleLine = true
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = prov.coastal,
                    onCheckedChange = { isCoastal -> onBatchProvinceEdit { p -> p.copy(coastal = isCoastal) } }
                )
                Text(stringResource(Res.string.field_coastal))
            }
        }
    }
}

@Composable
fun InfrastructureTabContent(
    project: Hoi4MapProject,
    editRevision: Int,
    activeTool: EditorTool,
    onSelectTool: (EditorTool) -> Unit,
    railwayLevel: Float,
    onRailwayLevelChange: (Float) -> Unit,
    activeRailwayNodesCount: Int,
    onFinishRailway: () -> Unit,
    onDeleteLastRailway: () -> Unit,
    buildingType: String,
    onBuildingTypeChange: (String) -> Unit,
    buildingSeaProv: String,
    onBuildingSeaProvChange: (String) -> Unit,
    selectedState: StateDef?,
    onAutoGenerateBuildings: (Int) -> Unit,
    onDeleteBuilding: (BuildingEntry) -> Unit,
    onJumpToCoords: (Int, Int) -> Unit,
    onShowAddAdjacency: () -> Unit,
    onDeleteAdjacency: (AdjacencyEntry) -> Unit
) {
    val commonBuildingTypes = listOf(
        "arms_factory", "industrial_complex", "air_base", "bunker",
        "coastal_bunker", "dockyard", "naval_base_spawn", "floating_harbor",
        "anti_air_building", "synthetic_refinery", "nuclear_reactor_spawn", "naval_supply_hub"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            stringResource(Res.string.infra_header),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        // 1. Railways & Supply Hubs
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, AppCardShape),
            shape = AppCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.railway_tool_section), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFilterChip(
                        selected = activeTool == EditorTool.RAILWAY,
                        onClick = { onSelectTool(EditorTool.RAILWAY) },
                        label = { Text(stringResource(Res.string.tool_railway)) }
                    )
                    AppFilterChip(
                        selected = activeTool == EditorTool.SUPPLY_NODE,
                        onClick = { onSelectTool(EditorTool.SUPPLY_NODE) },
                        label = { Text(stringResource(Res.string.tool_supply)) }
                    )
                }
                Text(
                    stringResource(Res.string.railway_level_label, railwayLevel.roundToInt()),
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(value = railwayLevel, onValueChange = onRailwayLevelChange, valueRange = 1f..5f, steps = 3)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AppButton(
                        onClick = onFinishRailway,
                        enabled = activeRailwayNodesCount >= 2,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(Res.string.finish_railway_btn, activeRailwayNodesCount),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    AppOutlinedButton(onClick = onDeleteLastRailway, modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(Res.string.delete_last_railway_btn),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // 2. Buildings Placement & State Building List
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, AppCardShape),
            shape = AppCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(Res.string.building_tool_section), fontWeight = FontWeight.Bold)
                    AppFilterChip(
                        selected = activeTool == EditorTool.BUILDING,
                        onClick = { onSelectTool(EditorTool.BUILDING) },
                        label = { Text(stringResource(Res.string.tool_building)) }
                    )
                }

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    commonBuildingTypes.forEach { bType ->
                        AppFilterChip(
                            selected = buildingType == bType,
                            onClick = {
                                onBuildingTypeChange(bType)
                                onSelectTool(EditorTool.BUILDING)
                            },
                            label = { Text(bType, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = buildingType,
                    onValueChange = onBuildingTypeChange,
                    label = { Text(stringResource(Res.string.building_type_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppButtonShape,
                    singleLine = true
                )

                if (buildingType == "naval_base_spawn" || buildingType == "floating_harbor") {
                    OutlinedTextField(
                        value = buildingSeaProv,
                        onValueChange = { onBuildingSeaProvChange(it.filter { c -> c.isDigit() }) },
                        label = { Text(stringResource(Res.string.building_sea_prov_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppButtonShape,
                        singleLine = true
                    )
                }

                if (selectedState != null) {
                    val stateBuildings = remember(editRevision, selectedState.id) {
                        project.buildings.filter { it.stateId == selectedState.id }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = stringResource(Res.string.state_buildings_list, selectedState.id, stateBuildings.size),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    AppButton(
                        onClick = { onAutoGenerateBuildings(selectedState.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(Res.string.auto_generate_state_buildings, selectedState.id),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    stateBuildings.take(15).forEach { b ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${b.type} (${b.x.toInt()}, ${b.z.toInt()}) ${if (b.adjacentSeaProvince > 0) "-> Sea #${b.adjacentSeaProvince}" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.clickable {
                                    onJumpToCoords(b.x.toInt(), (project.mapImage.height - b.z).toInt())
                                }
                            )
                            IconButton(onClick = { onDeleteBuilding(b) }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }

        // 3. Adjacencies / Straits
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, AppCardShape),
            shape = AppCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val adjList = remember(editRevision) { project.adjacencies.toList() }
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.adjacencies_section, adjList.size), fontWeight = FontWeight.Bold)
                AppButton(onClick = onShowAddAdjacency, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.add_adjacency_btn))
                }
                adjList.takeLast(10).reversed().forEach { adj ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${adj.fromProvince} <-> #${adj.toProvince} (${adj.ruleName.ifBlank { adj.type }}) ${adj.comment}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        IconButton(onClick = { onDeleteAdjacency(adj) }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProvinceListViewTab(
    project: Hoi4MapProject,
    editRevision: Int,
    selectedProvinceIds: List<Int>,
    isCtrlHeld: Boolean,
    onSelectProvince: (ProvinceDef, Boolean) -> Unit,
    onJumpToProvince: (ProvinceDef) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val allProvinces = remember(editRevision, project.provincesById.size) {
        project.provincesById.values.filter { it.id > 0 }.sortedByDescending { it.id }
    }

    val filteredProvinces = remember(searchQuery, allProvinces) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            allProvinces
        } else {
            allProvinces.filter { p ->
                val stateId = project.provinceToStateId[p.id]
                p.id.toString().contains(q) ||
                        p.terrain.lowercase().contains(q) ||
                        p.type.lowercase().contains(q) ||
                        (stateId != null && "state $stateId".contains(q))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text(stringResource(Res.string.search_provinces_label)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = AppButtonShape,
            singleLine = true
        )

        Text(
            text = stringResource(Res.string.provinces_count_format, filteredProvinces.size, allProvinces.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredProvinces, key = { it.id }) { prov ->
                val isSelected = selectedProvinceIds.contains(prov.id)
                val stateId = project.provinceToStateId[prov.id]

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = AppCardShape
                        )
                        .clickable { onSelectProvince(prov, isCtrlHeld) },
                    shape = AppCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(Color(prov.r, prov.g, prov.b), AppButtonShape)
                                    .border(1.dp, Color.White, AppButtonShape)
                            )
                            Column {
                                Text(
                                    text = "#${prov.id}  •  ${prov.terrain} (${prov.type})",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "RGB(${prov.r}, ${prov.g}, ${prov.b}) | State: ${stateId ?: "-"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        IconButton(onClick = { onJumpToProvince(prov) }) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = stringResource(Res.string.center_on_province)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StateListViewTab(
    project: Hoi4MapProject,
    editRevision: Int,
    selectedState: StateDef?,
    selectedProvinceCount: Int,
    isPaintingStateProvinces: Boolean,
    onTogglePaintStateProvinces: () -> Unit,
    onAssignSelectedProvincesToState: (StateDef) -> Unit,
    onSelectState: (StateDef) -> Unit,
    onJumpToState: (StateDef) -> Unit,
    onJumpToProvince: (Int) -> Unit,
    onRemoveProvinceFromState: (Int, Int) -> Unit,
    onCreateNewState: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val allStates = remember(editRevision, project.statesById.size, selectedState) {
        project.statesById.values.sortedByDescending { it.id }
    }

    val filteredStates = remember(searchQuery, allStates) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            allStates
        } else {
            allStates.filter { st ->
                st.id.toString().contains(q) ||
                        st.name.lowercase().contains(q) ||
                        st.provinces.any { it.toString() == q }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppButton(onClick = onCreateNewState, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.create_state_btn), fontWeight = FontWeight.SemiBold)
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text(stringResource(Res.string.search_states_label)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = AppButtonShape,
            singleLine = true
        )

        if (selectedState != null) {
            val currentSt = project.statesById[selectedState.id] ?: selectedState
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary, AppCardShape),
                shape = AppCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(Res.string.selected_state_provinces, currentSt.id, currentSt.name),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Interactive Button: Click Provinces on the map to add/remove them for this State!
                    AppButton(
                        onClick = onTogglePaintStateProvinces,
                        containerColor = if (isPaintingStateProvinces) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        contentColor = if (isPaintingStateProvinces) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(Res.string.paint_state_provinces_btn, currentSt.id),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        currentSt.provinces.sorted().forEach { provId ->
                            AssistChip(
                                onClick = { onJumpToProvince(provId) },
                                label = { Text("#$provId") },
                                shape = AppButtonShape,
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { onRemoveProvinceFromState(provId, currentSt.id) }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredStates, key = { it.id }) { state ->
                val isSelected = selectedState?.id == state.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = AppCardShape
                        )
                        .clickable { onSelectState(state) },
                    shape = AppCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "State #${state.id} • ${state.name}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (state.isModifiedOrInMod) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiary,
                                        shape = AppButtonShape
                                    ) {
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
                                text = stringResource(Res.string.state_provinces_count, state.provinces.size) +
                                        " (${
                                            state.provinces.sorted().take(6).joinToString(", ")
                                        }${if (state.provinces.size > 6) "..." else ""})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (selectedProvinceCount > 0) {
                                AppOutlinedButton(
                                    onClick = { onAssignSelectedProvincesToState(state) },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(
                                            Res.string.assign_selected_to_this_state_btn,
                                            selectedProvinceCount
                                        ),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            IconButton(onClick = { onJumpToState(state) }) {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = stringResource(Res.string.center_on_province)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticsTabContent(
    project: Hoi4MapProject,
    onJumpToProvince: (Int) -> Unit,
    onJumpToState: (Int) -> Unit,
    onJumpToCoordinates: (Pair<Int, Int>) -> Unit,
    onCoastalFixed: (Int) -> Unit
) {
    var issues by remember { mutableStateOf<List<MapIssue>?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(Res.string.diag_header),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(Res.string.diag_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AppButton(
            onClick = { issues = project.runDiagnostics() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.run_scan_btn), fontWeight = FontWeight.SemiBold)
        }

        AppOutlinedButton(
            onClick = {
                val count = project.autoFixCoastalFlags()
                onCoastalFixed(count)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.autofix_coastal_btn))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (issues != null) {
            if (issues!!.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.primary, AppCardShape),
                    shape = AppCardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.no_issues_found), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Text(
                    text = stringResource(Res.string.issues_count_format, issues!!.size),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(issues!!) { issue ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.error, AppCardShape)
                                .clickable {
                                    when {
                                        issue.mapCoordinates != null -> onJumpToCoordinates(issue.mapCoordinates)
                                        issue.provinceId != null -> onJumpToProvince(issue.provinceId)
                                        issue.stateId != null -> onJumpToState(issue.stateId)
                                    }
                                },
                            shape = AppCardShape,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        issue.title,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Text(issue.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewProvinceDialog(
    onDismiss: () -> Unit,
    onCreate: (type: String, coastal: Boolean, terrain: String, continent: Int) -> Unit
) {
    var type by remember { mutableStateOf("land") }
    var coastal by remember { mutableStateOf(false) }
    var terrain by remember { mutableStateOf("plains") }
    var continent by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppCardShape,
        title = { Text(stringResource(Res.string.dialog_new_prov_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.dialog_new_prov_desc), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text(stringResource(Res.string.field_type)) },
                    shape = AppButtonShape,
                    singleLine = true
                )
                OutlinedTextField(
                    value = terrain,
                    onValueChange = { terrain = it },
                    label = { Text(stringResource(Res.string.field_terrain)) },
                    shape = AppButtonShape,
                    singleLine = true
                )
                OutlinedTextField(
                    value = continent,
                    onValueChange = { continent = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(Res.string.field_continent)) },
                    shape = AppButtonShape,
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = coastal, onCheckedChange = { coastal = it })
                    Text(stringResource(Res.string.field_coastal))
                }
            }
        },
        confirmButton = {
            AppButton(onClick = { onCreate(type.trim(), coastal, terrain.trim(), continent.toIntOrNull() ?: 1) }) {
                Text(stringResource(Res.string.dialog_create_btn))
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel_btn)) }
        }
    )
}

@Composable
fun NewStateDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, manpower: Int, category: String) -> Unit
) {
    var name by remember { mutableStateOf("STATE_NEW") }
    var manpower by remember { mutableStateOf("150000") }
    var category by remember { mutableStateOf("town") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppCardShape,
        title = { Text(stringResource(Res.string.dialog_new_state_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.field_state_name)) },
                    shape = AppButtonShape,
                    singleLine = true
                )
                OutlinedTextField(
                    value = manpower,
                    onValueChange = { manpower = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(Res.string.field_manpower)) },
                    shape = AppButtonShape,
                    singleLine = true
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(Res.string.field_state_cat)) },
                    shape = AppButtonShape,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            AppButton(onClick = { onCreate(name.trim(), manpower.toIntOrNull() ?: 10000, category.trim()) }) {
                Text(stringResource(Res.string.open_create_mod))
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel_btn)) }
        }
    )
}

@Composable
fun NewStrategicRegionDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String) -> Unit
) {
    var name by remember { mutableStateOf("STRATEGICREGION_NEW") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppCardShape,
        title = { Text(stringResource(Res.string.dialog_new_region_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.field_region_name)) },
                    shape = AppButtonShape,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            AppButton(onClick = { onCreate(name.trim()) }) {
                Text(stringResource(Res.string.open_create_mod))
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel_btn)) }
        }
    )
}

@Composable
fun AddAdjacencyDialog(
    onDismiss: () -> Unit,
    onAdd: (fromId: Int, toId: Int, throughSeaId: Int, ruleName: String, comment: String) -> Unit
) {
    var fromProv by remember { mutableStateOf("") }
    var toProv by remember { mutableStateOf("") }
    var throughProv by remember { mutableStateOf("8657") }
    var ruleName by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("Custom Strait") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppCardShape,
        title = { Text("Add Strait / Adjacency") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fromProv,
                    onValueChange = { fromProv = it.filter { c -> c.isDigit() } },
                    label = { Text("From Province ID") },
                    shape = AppButtonShape,
                    singleLine = true
                )
                OutlinedTextField(
                    value = toProv,
                    onValueChange = { toProv = it.filter { c -> c.isDigit() } },
                    label = { Text("To Province ID") },
                    shape = AppButtonShape,
                    singleLine = true
                )
                OutlinedTextField(
                    value = throughProv,
                    onValueChange = { throughProv = it.filter { c -> c.isDigit() || c == '-' } },
                    label = { Text("Through Sea Province ID") },
                    shape = AppButtonShape,
                    singleLine = true
                )
                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = { Text("Adjacency Rule (optional, e.g. ATLAS_STRAIT)") },
                    shape = AppButtonShape,
                    singleLine = true
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment") },
                    shape = AppButtonShape,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            AppButton(
                onClick = {
                    val f = fromProv.toIntOrNull()
                    val t = toProv.toIntOrNull()
                    if (f != null && t != null) {
                        onAdd(f, t, throughProv.toIntOrNull() ?: -1, ruleName, comment)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel_btn)) }
        }
    )
}

suspend fun chooseDirectory(initialPath: String): String? {
    return FileKit.openDirectoryPicker(PlatformFile(initialPath))?.path
}