package at.yerova.hoi4_modder

import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.prefs.Preferences
import javax.imageio.ImageIO
import kotlin.collections.ArrayDeque
import kotlin.collections.ArrayList
import kotlin.collections.Collection
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.copyOf
import kotlin.collections.drop
import kotlin.collections.emptyList
import kotlin.collections.emptyMap
import kotlin.collections.filter
import kotlin.collections.first
import kotlin.collections.firstOrNull
import kotlin.collections.forEach
import kotlin.collections.forEachIndexed
import kotlin.collections.getOrNull
import kotlin.collections.getOrPut
import kotlin.collections.indices
import kotlin.collections.isNotEmpty
import kotlin.collections.iterator
import kotlin.collections.joinToString
import kotlin.collections.lastOrNull
import kotlin.collections.listOf
import kotlin.collections.mapNotNull
import kotlin.collections.maxOrNull
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.collections.set
import kotlin.collections.sorted
import kotlin.collections.sortedBy
import kotlin.collections.sumOf
import kotlin.collections.toList
import kotlin.io.copyTo
import kotlin.io.extension
import kotlin.io.readText
import kotlin.io.use
import kotlin.io.useLines
import kotlin.io.writeText
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.text.Regex
import kotlin.text.StringBuilder
import kotlin.text.format
import kotlin.text.ifBlank
import kotlin.text.isEmpty
import kotlin.text.lines
import kotlin.text.lowercase
import kotlin.text.replace
import kotlin.text.split
import kotlin.text.startsWith
import kotlin.text.substringBefore
import kotlin.text.toBoolean
import kotlin.text.toDoubleOrNull
import kotlin.text.toInt
import kotlin.text.toIntOrNull
import kotlin.text.trim
import kotlin.text.trimIndent
import java.awt.Color as AwtColor

object AppPreferences {
    private val prefs: Preferences = Preferences.userNodeForPackage(Hoi4MapProject::class.java)

    private const val KEY_VANILLA_PATH = "vanilla_path"
    private const val KEY_LAST_MOD_PATH = "last_mod_path"
    private const val KEY_AUTO_OPEN = "auto_open_last"
    private const val KEY_LANGUAGE = "app_language"

    var vanillaPath: String
        get() = prefs.get(KEY_VANILLA_PATH, "C:\\Program Files (x86)\\Steam\\steamapps\\common\\Hearts of Iron IV")
        set(value) = prefs.put(KEY_VANILLA_PATH, value)

    var lastModPath: String
        get() = prefs.get(
            KEY_LAST_MOD_PATH,
            System.getProperty("user.home") + "\\Documents\\Paradox Interactive\\Hearts of Iron IV\\mod\\atlantis_mod"
        )
        set(value) = prefs.put(KEY_LAST_MOD_PATH, value)

    var autoOpenLastProject: Boolean
        get() = prefs.getBoolean(KEY_AUTO_OPEN, true)
        set(value) = prefs.putBoolean(KEY_AUTO_OPEN, value)

    var language: String
        get() = prefs.get(KEY_LANGUAGE, "en")
        set(value) {
            prefs.put(KEY_LANGUAGE, value)
            Locale.setDefault(Locale.forLanguageTag(value))
        }

    fun applySavedLocale() {
        Locale.setDefault(Locale.forLanguageTag(language))
    }
}

enum class MapViewMode {
    PROVINCES,
    STATES,
    STRATEGIC_REGIONS,
    TERRAIN,
    COASTAL
}

enum class IssueSeverity {
    CRITICAL,
    WARNING
}

data class MapIssue(
    val severity: IssueSeverity,
    val title: String,
    val description: String,
    val provinceId: Int? = null,
    val stateId: Int? = null,
    val mapCoordinates: Pair<Int, Int>? = null
)

data class ProvinceDef(
    val id: Int,
    val r: Int,
    val g: Int,
    val b: Int,
    val type: String = "land",
    val coastal: Boolean = false,
    val terrain: String = "plains",
    val continent: Int = 1
) {
    val rgbInt: Int get() = (r shl 16) or (g shl 8) or b

    fun toCsvLine(): String = "$id;$r;$g;$b;$type;$coastal;$terrain;$continent"
}

data class CopiedProvinceClipboard(
    val sourceProvinceId: Int,
    val type: String,
    val coastal: Boolean,
    val terrain: String,
    val continent: Int,
    val stateId: Int?,
    val strategicRegionId: Int?
)

data class StateDef(
    val id: Int,
    val name: String,
    val fileName: String,
    val provinces: MutableSet<Int>,
    val rawText: String,
    var isModifiedOrInMod: Boolean
)

data class StrategicRegionDef(
    val id: Int,
    val name: String,
    val fileName: String,
    val provinces: MutableSet<Int>,
    val rawText: String,
    var isModifiedOrInMod: Boolean
)

data class BuildingEntry(
    val stateId: Int,
    val type: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val rotation: Double,
    val adjacentSeaProvince: Int = 0
) {
    fun toLine(): String = String.format(
        Locale.US,
        "%d;%s;%.2f;%.2f;%.2f;%.2f;%d",
        stateId, type, x, y, z, rotation, adjacentSeaProvince
    )
}

data class RailwayEntry(
    val level: Int,
    val provinces: List<Int>
) {
    fun toLine(): String = "$level ${provinces.size} ${provinces.joinToString(" ")}"
}

data class AdjacencyEntry(
    val fromProvince: Int,
    val toProvince: Int,
    val type: String = "sea",
    val throughProvince: Int = -1,
    val startX: Int = -1,
    val startY: Int = -1,
    val stopX: Int = -1,
    val stopY: Int = -1,
    val ruleName: String = "",
    val comment: String = ""
) {
    fun toCsvLine(): String =
        "$fromProvince;$toProvince;$type;$throughProvince;$startX;$startY;$stopX;$stopY;$ruleName;$comment"
}

/** Inclusive pixel-space rectangle that was changed by an edit operation or bounds a province. */
data class PixelBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left + 1
    val height: Int get() = bottom - top + 1

    fun union(other: PixelBounds): PixelBounds = PixelBounds(
        left = min(left, other.left),
        top = min(top, other.top),
        right = max(right, other.right),
        bottom = max(bottom, other.bottom)
    )
}

/** Merged horizontal or vertical pixel boundary segment in map coordinates. */
data class BorderSegment(
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int
)

/** Compact undo record: primitive arrays avoid one Map entry and Pair allocation per pixel. */
class PaintAction(
    val pixelIndexes: IntArray,
    val oldColors: IntArray,
    val newColors: IntArray
)

class Hoi4MapProject(
    val vanillaPath: File,
    val modPath: File
) {
    val mapDir = File(modPath, "map")
    val statesDir = File(modPath, "history/states")
    val strategicRegionsDir = File(modPath, "map/strategicregions")

    val provincesFile = File(mapDir, "provinces.bmp")
    val definitionFile = File(mapDir, "definition.csv")
    val defaultMapFile = File(mapDir, "default.map")
    val buildingsFile = File(mapDir, "buildings.txt")
    val railwaysFile = File(mapDir, "railways.txt")
    val supplyNodesFile = File(mapDir, "supply_nodes.txt")
    val adjacenciesFile = File(mapDir, "adjacencies.csv")

    lateinit var mapImage: BufferedImage
        private set

    private lateinit var mapPixels: IntArray

    private var colorLutDirty = true
    private var cachedColorLutMode: MapViewMode? = null
    private var cachedColorLutHighlight: Int? = null
    private var cachedColorLut: Map<Int, Int> = emptyMap()

    // Core Data Structures (In-Memory Staged)
    val provincesById = mutableMapOf<Int, ProvinceDef>()
    val provincesByRgb = mutableMapOf<Int, ProvinceDef>()
    val provinceCenters = mutableMapOf<Int, Pair<Int, Int>>() // ProvID -> (bmpX, bmpY)
    val provinceBounds = mutableMapOf<Int, PixelBounds>()     // ProvID -> Bounding Box on map

    val statesById = mutableMapOf<Int, StateDef>()
    val provinceToStateId = mutableMapOf<Int, Int>()
    val duplicateStateAssignments = mutableListOf<Pair<Int, List<Int>>>()

    val strategicRegionsById = mutableMapOf<Int, StrategicRegionDef>()
    val provinceToStrategicRegionId = mutableMapOf<Int, Int>()

    val buildings = mutableListOf<BuildingEntry>()
    val railways = mutableListOf<RailwayEntry>()
    val supplyNodes = mutableSetOf<Int>()
    val adjacencies = mutableListOf<AdjacencyEntry>()

    // Granular Dirty Tracking for In-Memory Staging (Saved on Ctrl+S)
    var dirtyBmp = false
        private set
    var dirtyDefinitions = false
        private set
    var dirtyDefaultMap = false
        private set
    val dirtyStateIds = mutableSetOf<Int>()
    val dirtyStrategicRegionIds = mutableSetOf<Int>()
    var dirtyBuildings = false
        private set
    var dirtyRailways = false
        private set
    var dirtySupplyNodes = false
        private set
    var dirtyAdjacencies = false
        private set

    val hasUnsavedChanges: Boolean
        get() = dirtyBmp || dirtyDefinitions || dirtyDefaultMap ||
                dirtyStateIds.isNotEmpty() || dirtyStrategicRegionIds.isNotEmpty() ||
                dirtyBuildings || dirtyRailways || dirtySupplyNodes || dirtyAdjacencies

    fun unsavedSummary(): String {
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

    private val undoStack = ArrayDeque<PaintAction>()
    private val redoStack = ArrayDeque<PaintAction>()
    private var currentStrokeChanges = mutableMapOf<Int, Int>()

    fun initializeAndLoad() {
        if (!mapDir.exists()) mapDir.mkdirs()
        if (!statesDir.exists()) statesDir.mkdirs()
        if (!strategicRegionsDir.exists()) strategicRegionsDir.mkdirs()

        val descriptorFile = File(modPath, "descriptor.mod")
        if (!descriptorFile.exists()) {
            descriptorFile.writeText(
                """
                version="1.0"
                tags={ "Map" "Gameplay" }
                name="${modPath.name}"
                supported_version="1.15.*"
                """.trimIndent()
            )
        }

        copyFromVanillaIfMissing("map/provinces.bmp", provincesFile)
        copyFromVanillaIfMissing("map/definition.csv", definitionFile)
        copyFromVanillaIfMissing("map/default.map", defaultMapFile)

        loadDefinitions()
        loadStates()
        loadStrategicRegions()
        loadBitmap()
        recalculateAllProvinceCenters()
        loadBuildings()
        loadRailways()
        loadSupplyNodes()
        loadAdjacencies()

        clearDirtyFlags()
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

    private fun resolveReadFile(relativePath: String, modFile: File): File {
        return if (modFile.exists()) modFile else File(vanillaPath, relativePath)
    }

    private fun copyFromVanillaIfMissing(relativePath: String, targetFile: File) {
        if (!targetFile.exists()) {
            val sourceFile = File(vanillaPath, relativePath)
            require(sourceFile.exists()) { "Missing Vanilla file: ${sourceFile.absolutePath}" }
            sourceFile.copyTo(targetFile, overwrite = false)
        }
    }

    private fun loadDefinitions() {
        provincesById.clear()
        provincesByRgb.clear()

        definitionFile.useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                val parts = trimmed.split(";")
                if (parts.size >= 8) {
                    val id = parts[0].toIntOrNull() ?: return@forEach
                    val r = parts[1].toInt()
                    val g = parts[2].toInt()
                    val b = parts[3].toInt()
                    val def = ProvinceDef(
                        id = id, r = r, g = g, b = b,
                        type = parts[4],
                        coastal = parts[5].toBoolean(),
                        terrain = parts[6],
                        continent = parts[7].toIntOrNull() ?: 1
                    )
                    provincesById[id] = def
                    if (id > 0) {
                        provincesByRgb[def.rgbInt] = def
                    }
                }
            }
        }
        invalidateColorLut()
    }

    private fun loadStates() {
        statesById.clear()
        provinceToStateId.clear()
        duplicateStateAssignments.clear()

        val vanillaStatesDir = File(vanillaPath, "history/states")
        val stateFiles = mutableMapOf<Int, Pair<File, Boolean>>()

        vanillaStatesDir.listFiles { f -> f.extension == "txt" }?.forEach { file ->
            extractBlockId(file.readText())?.let { id ->
                stateFiles[id] = file to false
            }
        }

        statesDir.listFiles { f -> f.extension == "txt" }?.forEach { file ->
            extractBlockId(file.readText())?.let { id ->
                stateFiles[id] = file to true
            }
        }

        val provOwners = mutableMapOf<Int, MutableList<Int>>()

        stateFiles.forEach { (_, pair) ->
            val (file, isFromMod) = pair
            parseStateFile(file, isFromMod)?.let { stateDef ->
                statesById[stateDef.id] = stateDef
                stateDef.provinces.forEach { provId ->
                    provinceToStateId[provId] = stateDef.id
                    provOwners.getOrPut(provId) { mutableListOf() }.add(stateDef.id)
                }
            }
        }

        provOwners.forEach { (provId, owners) ->
            if (owners.size > 1) {
                duplicateStateAssignments.add(provId to owners)
            }
        }
        invalidateColorLut()
    }

    private fun loadStrategicRegions() {
        strategicRegionsById.clear()
        provinceToStrategicRegionId.clear()

        val vanillaRegionsDir = File(vanillaPath, "map/strategicregions")
        val regionFiles = mutableMapOf<Int, Pair<File, Boolean>>()

        vanillaRegionsDir.listFiles { f -> f.extension == "txt" }?.forEach { file ->
            extractBlockId(file.readText())?.let { id ->
                regionFiles[id] = file to false
            }
        }

        strategicRegionsDir.listFiles { f -> f.extension == "txt" }?.forEach { file ->
            extractBlockId(file.readText())?.let { id ->
                regionFiles[id] = file to true
            }
        }

        regionFiles.forEach { (_, pair) ->
            val (file, isFromMod) = pair
            parseStrategicRegionFile(file, isFromMod)?.let { regDef ->
                strategicRegionsById[regDef.id] = regDef
                regDef.provinces.forEach { provId ->
                    provinceToStrategicRegionId[provId] = regDef.id
                }
            }
        }
    }

    private fun extractBlockId(content: String): Int? {
        val clean = content.lines().joinToString("\n") { it.substringBefore("#") }
        val match = Regex("""\bid\s*=\s*(\d+)""").find(clean)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun parseStateFile(file: File, isFromMod: Boolean): StateDef? {
        val raw = file.readText()
        val clean = raw.lines().joinToString("\n") { it.substringBefore("#") }

        val id = Regex("""\bid\s*=\s*(\d+)""").find(clean)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val name = Regex("""\bname\s*=\s*"([^"]+)"""").find(clean)?.groupValues?.get(1) ?: "STATE_$id"
        val provsBlock = Regex("""\bprovinces\s*=\s*\{([^}]*)\}""").find(clean)?.groupValues?.get(1) ?: ""

        val provs = Regex("""\d+""").findAll(provsBlock)
            .mapNotNull { it.value.toIntOrNull() }
            .toMutableSet()

        return StateDef(
            id = id,
            name = name,
            fileName = file.name,
            provinces = provs,
            rawText = raw,
            isModifiedOrInMod = isFromMod
        )
    }

    private fun parseStrategicRegionFile(file: File, isFromMod: Boolean): StrategicRegionDef? {
        val raw = file.readText()
        val clean = raw.lines().joinToString("\n") { it.substringBefore("#") }

        val id = Regex("""\bid\s*=\s*(\d+)""").find(clean)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val name = Regex("""\bname\s*=\s*"([^"]+)"""").find(clean)?.groupValues?.get(1) ?: "STRATEGICREGION_$id"
        val provsBlock = Regex("""\bprovinces\s*=\s*\{([^}]*)\}""").find(clean)?.groupValues?.get(1) ?: ""

        val provs = Regex("""\d+""").findAll(provsBlock)
            .mapNotNull { it.value.toIntOrNull() }
            .toMutableSet()

        return StrategicRegionDef(
            id = id,
            name = name,
            fileName = file.name,
            provinces = provs,
            rawText = raw,
            isModifiedOrInMod = isFromMod
        )
    }

    private fun loadBitmap() {
        val loaded = ImageIO.read(provincesFile)
            ?: error("Could not read provinces.bmp: ${provincesFile.absolutePath}")
        mapImage = BufferedImage(loaded.width, loaded.height, BufferedImage.TYPE_INT_RGB)
        mapImage.createGraphics().useGraphics { it.drawImage(loaded, 0, 0, null) }
        mapPixels = (mapImage.raster.dataBuffer as DataBufferInt).data
    }

    /**
     * Single-pass calculation of all province centroids AND bounding boxes across the entire map.
     */
    fun recalculateAllProvinceCenters() {
        provinceCenters.clear()
        provinceBounds.clear()
        val maxId = (provincesById.keys.maxOrNull() ?: 0) + 1
        val sumX = LongArray(maxId)
        val sumY = LongArray(maxId)
        val count = IntArray(maxId)
        val minX = IntArray(maxId) { Int.MAX_VALUE }
        val minY = IntArray(maxId) { Int.MAX_VALUE }
        val maxX = IntArray(maxId) { -1 }
        val maxY = IntArray(maxId) { -1 }

        val width = mapImage.width
        val height = mapImage.height

        for (y in 0 until height) {
            val rowStart = y * width
            for (x in 0 until width) {
                val rgb = mapPixels[rowStart + x] and 0xFFFFFF
                val prov = provincesByRgb[rgb] ?: continue
                val id = prov.id
                if (id in 1 until maxId) {
                    sumX[id] += x.toLong()
                    sumY[id] += y.toLong()
                    count[id]++
                    if (x < minX[id]) minX[id] = x
                    if (y < minY[id]) minY[id] = y
                    if (x > maxX[id]) maxX[id] = x
                    if (y > maxY[id]) maxY[id] = y
                }
            }
        }

        for (id in 1 until maxId) {
            val c = count[id]
            if (c > 0) {
                provinceCenters[id] = (sumX[id] / c).toInt() to (sumY[id] / c).toInt()
                provinceBounds[id] = PixelBounds(minX[id], minY[id], maxX[id], maxY[id])
            }
        }
    }

    /**
     * Extracts merged horizontal and vertical boundary segments around the given set of provinces.
     * Because it only scans the union of their PixelBounds, it runs in < 0.2ms for typical selections!
     */
    fun computeBorderSegmentsForProvinces(provinceIds: Set<Int>): List<BorderSegment> {
        if (provinceIds.isEmpty()) return emptyList()

        val targetRgbs = HashSet<Int>(provinceIds.size * 2)
        var unionBounds: PixelBounds? = null

        for (id in provinceIds) {
            val prov = provincesById[id] ?: continue
            targetRgbs.add(prov.rgbInt)
            val b = provinceBounds[id]
            if (b != null) {
                unionBounds = if (unionBounds == null) b else unionBounds.union(b)
            }
        }

        val bounds = unionBounds ?: return emptyList()
        val width = mapImage.width
        val height = mapImage.height

        val scanLeft = (bounds.left - 1).coerceAtLeast(0)
        val scanRight = (bounds.right + 1).coerceAtMost(width - 1)
        val scanTop = (bounds.top - 1).coerceAtLeast(0)
        val scanBottom = (bounds.bottom + 1).coerceAtMost(height - 1)

        fun isInside(x: Int, y: Int): Boolean {
            if (x !in 0 until width || y !in 0 until height) return false
            return targetRgbs.contains(mapPixels[y * width + x] and 0xFFFFFF)
        }

        val segments = ArrayList<BorderSegment>(256)

        // 1. Horizontal boundary segments
        for (y in scanTop..(scanBottom + 1)) {
            var runStart = -1
            for (x in scanLeft..scanRight) {
                val above = isInside(x, y - 1)
                val below = isInside(x, y)
                if (above != below) {
                    if (runStart == -1) runStart = x
                } else if (runStart != -1) {
                    segments.add(BorderSegment(runStart, y, x, y))
                    runStart = -1
                }
            }
            if (runStart != -1) {
                segments.add(BorderSegment(runStart, y, scanRight + 1, y))
            }
        }

        // 2. Vertical boundary segments
        for (x in scanLeft..(scanRight + 1)) {
            var runStart = -1
            for (y in scanTop..scanBottom) {
                val left = isInside(x - 1, y)
                val right = isInside(x, y)
                if (left != right) {
                    if (runStart == -1) runStart = y
                } else if (runStart != -1) {
                    segments.add(BorderSegment(x, runStart, x, y))
                    runStart = -1
                }
            }
            if (runStart != -1) {
                segments.add(BorderSegment(x, runStart, x, scanBottom + 1))
            }
        }

        return segments
    }

    private fun loadBuildings() {
        buildings.clear()
        val file = resolveReadFile("map/buildings.txt", buildingsFile)
        if (!file.exists()) return

        file.useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                val parts = trimmed.split(";")
                if (parts.size >= 7) {
                    val stId = parts[0].toIntOrNull() ?: return@forEach
                    val type = parts[1].trim()
                    val x = parts[2].toDoubleOrNull() ?: return@forEach
                    val y = parts[3].toDoubleOrNull() ?: 9.5
                    val z = parts[4].toDoubleOrNull() ?: return@forEach
                    val rot = parts[5].toDoubleOrNull() ?: 0.0
                    val seaProv = parts[6].toIntOrNull() ?: 0
                    buildings.add(BuildingEntry(stId, type, x, y, z, rot, seaProv))
                }
            }
        }
    }

    private fun loadRailways() {
        railways.clear()
        val file = resolveReadFile("map/railways.txt", railwaysFile)
        if (!file.exists()) return

        file.useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val level = parts[0].toIntOrNull() ?: return@forEach
                    val provs = parts.drop(2).mapNotNull { it.toIntOrNull() }
                    if (provs.size >= 2) {
                        railways.add(RailwayEntry(level, provs))
                    }
                }
            }
        }
    }

    private fun loadSupplyNodes() {
        supplyNodes.clear()
        val file = resolveReadFile("map/supply_nodes.txt", supplyNodesFile)
        if (!file.exists()) return

        file.useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    parts[1].toIntOrNull()?.let { supplyNodes.add(it) }
                }
            }
        }
    }

    private fun loadAdjacencies() {
        adjacencies.clear()
        val file = resolveReadFile("map/adjacencies.csv", adjacenciesFile)
        if (!file.exists()) return

        file.useLines { lines ->
            lines.forEachIndexed { idx, line ->
                val trimmed = line.trim()
                if (idx == 0 || trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-1;-1")) return@forEachIndexed
                val parts = line.split(";")
                if (parts.size >= 9) {
                    val from = parts[0].toIntOrNull() ?: return@forEachIndexed
                    val to = parts[1].toIntOrNull() ?: return@forEachIndexed
                    if (from <= 0 || to <= 0) return@forEachIndexed
                    adjacencies.add(
                        AdjacencyEntry(
                            fromProvince = from,
                            toProvince = to,
                            type = parts[2],
                            throughProvince = parts[3].toIntOrNull() ?: -1,
                            startX = parts[4].toIntOrNull() ?: -1,
                            startY = parts[5].toIntOrNull() ?: -1,
                            stopX = parts[6].toIntOrNull() ?: -1,
                            stopY = parts[7].toIntOrNull() ?: -1,
                            ruleName = parts[8],
                            comment = parts.getOrNull(9) ?: ""
                        )
                    )
                }
            }
        }
    }

    private fun colorLookupFor(mode: MapViewMode, highlightedId: Int?): Map<Int, Int> {
        if (!colorLutDirty && cachedColorLutMode == mode && cachedColorLutHighlight == highlightedId) {
            return cachedColorLut
        }

        val lut = HashMap<Int, Int>(maxOf(16, provincesByRgb.size * 2))
        for ((rgb, prov) in provincesByRgb) {
            val mappedColor = when (mode) {
                MapViewMode.PROVINCES -> {
                    val stateId = provinceToStateId[prov.id]
                    if (highlightedId != null && stateId == highlightedId) {
                        brightenRgb(rgb, 0.45f)
                    } else {
                        darkenRgb(rgb, 0.55f)
                    }
                }

                MapViewMode.STATES -> {
                    val stateId = provinceToStateId[prov.id]
                    when {
                        stateId != null -> {
                            val base = goldenRatioColorFromId(stateId)
                            if (highlightedId == stateId) brightenRgb(base, 0.45f) else base
                        }

                        prov.type == "land" -> 0xFF00FF
                        prov.type == "lake" -> 0x2B5B84
                        else -> 0x162436
                    }
                }

                MapViewMode.STRATEGIC_REGIONS -> {
                    val regId = provinceToStrategicRegionId[prov.id]
                    when {
                        regId != null -> {
                            val base = goldenRatioColorFromId(regId * 7 + 3)
                            if (highlightedId == regId) brightenRgb(base, 0.45f) else base
                        }

                        else -> 0xFF0055
                    }
                }

                MapViewMode.TERRAIN -> terrainColor(prov.terrain, prov.type)
                MapViewMode.COASTAL -> when {
                    prov.type != "land" -> if (prov.coastal) 0x26547C else 0x142438
                    prov.coastal -> 0x06D6A0
                    else -> 0x4A7C59
                }
            }
            lut[rgb] = mappedColor
        }

        cachedColorLut = lut
        cachedColorLutMode = mode
        cachedColorLutHighlight = highlightedId
        colorLutDirty = false
        return cachedColorLut
    }

    private fun terrainColor(terrain: String, type: String): Int {
        if (type == "sea" || terrain == "ocean") return 0x13293D
        if (type == "lake" || terrain == "lakes") return 0x2A628F
        return when (terrain.lowercase()) {
            "plains" -> 0x8CB369
            "forest" -> 0x386641
            "hills" -> 0xBC6C25
            "mountain" -> 0x6C757D
            "urban" -> 0xE63946
            "jungle" -> 0x1B4332
            "marsh" -> 0x457B9D
            "desert" -> 0xF4A261
            else -> 0x9D4EDD
        }
    }

    private fun invalidateColorLut() {
        colorLutDirty = true
    }

    fun renderMapModeImage(
        mode: MapViewMode,
        highlightedStateId: Int? = null
    ): BufferedImage {
        if (mode == MapViewMode.PROVINCES && highlightedStateId == null) return mapImage

        val width = mapImage.width
        val height = mapImage.height
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val outRow = IntArray(width)
        val lut = colorLookupFor(mode, highlightedStateId)

        for (y in 0 until height) {
            val rowStart = y * width
            for (x in 0 until width) {
                val rgb = mapPixels[rowStart + x] and 0xFFFFFF
                outRow[x] = lut[rgb] ?: 0xFF0000
            }
            out.setRGB(0, y, width, 1, outRow, 0, width)
        }
        return out
    }

    fun renderMapModeRegion(
        mode: MapViewMode,
        highlightedStateId: Int?,
        x: Int,
        y: Int,
        regionWidth: Int,
        regionHeight: Int
    ): BufferedImage {
        require(regionWidth > 0 && regionHeight > 0)
        require(x >= 0 && y >= 0 && x + regionWidth <= mapImage.width && y + regionHeight <= mapImage.height)

        val out = BufferedImage(regionWidth, regionHeight, BufferedImage.TYPE_INT_RGB)
        val outRow = IntArray(regionWidth)
        val plainProvinceView = mode == MapViewMode.PROVINCES && highlightedStateId == null
        val lut = if (plainProvinceView) emptyMap() else colorLookupFor(mode, highlightedStateId)

        for (row in 0 until regionHeight) {
            val sourceStart = (y + row) * mapImage.width + x
            for (column in 0 until regionWidth) {
                val rgb = mapPixels[sourceStart + column] and 0xFFFFFF
                outRow[column] = if (plainProvinceView) rgb else (lut[rgb] ?: 0xFF0000)
            }
            out.setRGB(0, row, regionWidth, 1, outRow, 0, regionWidth)
        }
        return out
    }

    private inline fun <T> java.awt.Graphics2D.useGraphics(block: (java.awt.Graphics2D) -> T): T {
        return try {
            block(this)
        } finally {
            dispose()
        }
    }

    private fun goldenRatioColorFromId(id: Int): Int {
        val hue = ((id * 137.508f) % 360f) / 360f
        val sat = 0.55f + ((id * 31) % 30) / 100f
        val bri = 0.70f + ((id * 17) % 20) / 100f
        return AwtColor.HSBtoRGB(hue, sat.coerceIn(0f, 1f), bri.coerceIn(0f, 1f)) and 0xFFFFFF
    }

    private fun brightenRgb(rgb: Int, factor: Float): Int {
        val r = ((rgb shr 16) and 0xFF)
        val g = ((rgb shr 8) and 0xFF)
        val b = (rgb and 0xFF)
        val nr = (r + (255 - r) * factor).toInt().coerceIn(0, 255)
        val ng = (g + (255 - g) * factor).toInt().coerceIn(0, 255)
        val nb = (b + (255 - b) * factor).toInt().coerceIn(0, 255)
        return (nr shl 16) or (ng shl 8) or nb
    }

    private fun darkenRgb(rgb: Int, factor: Float): Int {
        val r = (((rgb shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((rgb shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((rgb and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    fun beginPaintStroke() {
        currentStrokeChanges = mutableMapOf()
    }

    fun paintCircleWithBounds(mapX: Int, mapY: Int, radius: Int, targetRgb: Int): PixelBounds? {
        if (radius < 0) return null
        val width = mapImage.width
        val height = mapImage.height
        val target = targetRgb and 0xFFFFFF
        val rSq = radius * radius
        val left = (mapX - radius).coerceAtLeast(0)
        val right = (mapX + radius).coerceAtMost(width - 1)
        val top = (mapY - radius).coerceAtLeast(0)
        val bottom = (mapY + radius).coerceAtMost(height - 1)
        if (left > right || top > bottom) return null

        var dirtyLeft = width
        var dirtyTop = height
        var dirtyRight = -1
        var dirtyBottom = -1

        for (py in top..bottom) {
            val dy = py - mapY
            val rowStart = py * width
            for (px in left..right) {
                val dx = px - mapX
                if (dx * dx + dy * dy > rSq) continue

                val index = rowStart + px
                val oldRgb = mapPixels[index] and 0xFFFFFF
                if (oldRgb == target) continue

                currentStrokeChanges.putIfAbsent(index, oldRgb)
                mapPixels[index] = target
                dirtyBmp = true
                if (px < dirtyLeft) dirtyLeft = px
                if (py < dirtyTop) dirtyTop = py
                if (px > dirtyRight) dirtyRight = px
                if (py > dirtyBottom) dirtyBottom = py
            }
        }

        return if (dirtyRight >= dirtyLeft && dirtyBottom >= dirtyTop) {
            val dirtyBounds = PixelBounds(dirtyLeft, dirtyTop, dirtyRight, dirtyBottom)
            provincesByRgb[target]?.let { prov ->
                val existing = provinceBounds[prov.id]
                provinceBounds[prov.id] = existing?.union(dirtyBounds) ?: dirtyBounds
                if (!provinceCenters.containsKey(prov.id)) {
                    provinceCenters[prov.id] = mapX to mapY
                }
            }
            dirtyBounds
        } else null
    }

    fun floodFill(startX: Int, startY: Int, targetRgb: Int): Boolean {
        val width = mapImage.width
        val height = mapImage.height
        if (startX !in 0 until width || startY !in 0 until height) return false

        val target = targetRgb and 0xFFFFFF
        val sourceRgb = mapPixels[startY * width + startX] and 0xFFFFFF
        if (sourceRgb == target) return false

        beginPaintStroke()
        val totalPixels = width * height
        var queue = LongArray(minOf(totalPixels, 1024).coerceAtLeast(1))
        var head = 0
        var tail = 0

        var dirtyLeft = width
        var dirtyTop = height
        var dirtyRight = -1
        var dirtyBottom = -1

        fun enqueueRun(y: Int, seedX: Int) {
            val rowStart = y * width
            var left = seedX
            while (left > 0 && (mapPixels[rowStart + left - 1] and 0xFFFFFF) == sourceRgb) left--
            var right = seedX
            while (right < width - 1 && (mapPixels[rowStart + right + 1] and 0xFFFFFF) == sourceRgb) right++

            for (x in left..right) {
                val index = rowStart + x
                currentStrokeChanges.putIfAbsent(index, sourceRgb)
                mapPixels[index] = target
            }
            if (left < dirtyLeft) dirtyLeft = left
            if (right > dirtyRight) dirtyRight = right
            if (y < dirtyTop) dirtyTop = y
            if (y > dirtyBottom) dirtyBottom = y

            if (tail == queue.size) {
                val newSize = minOf(totalPixels, maxOf(queue.size + 1, queue.size * 2))
                queue = queue.copyOf(newSize)
            }
            val firstIndex = rowStart + left
            val lastIndex = rowStart + right
            queue[tail++] = (firstIndex.toLong() shl 32) or (lastIndex.toLong() and 0xFFFFFFFFL)
        }

        enqueueRun(startY, startX)
        while (head < tail) {
            val span = queue[head++]
            val firstIndex = (span shr 32).toInt()
            val lastIndex = span.toInt()
            val y = firstIndex / width
            val left = firstIndex % width
            val right = lastIndex % width

            for (neighborY in intArrayOf(y - 1, y + 1)) {
                if (neighborY !in 0 until height) continue
                val neighborRowStart = neighborY * width
                var x = left
                while (x <= right) {
                    if ((mapPixels[neighborRowStart + x] and 0xFFFFFF) == sourceRgb) {
                        enqueueRun(neighborY, x)
                        while (x <= right && (mapPixels[neighborRowStart + x] and 0xFFFFFF) != sourceRgb) x++
                    } else {
                        x++
                    }
                }
            }
        }

        if (dirtyRight >= dirtyLeft && dirtyBottom >= dirtyTop) {
            val dirtyBounds = PixelBounds(dirtyLeft, dirtyTop, dirtyRight, dirtyBottom)
            provincesByRgb[target]?.let { prov ->
                val existing = provinceBounds[prov.id]
                provinceBounds[prov.id] = existing?.union(dirtyBounds) ?: dirtyBounds
            }
        }

        dirtyBmp = true
        endPaintStroke()
        return true
    }

    fun endPaintStroke() {
        if (currentStrokeChanges.isNotEmpty()) {
            val changedEntries = currentStrokeChanges.entries.filter { (index, oldRgb) ->
                (mapPixels[index] and 0xFFFFFF) != oldRgb
            }
            if (changedEntries.isNotEmpty()) {
                val size = changedEntries.size
                val indexes = IntArray(size)
                val oldColors = IntArray(size)
                val newColors = IntArray(size)
                changedEntries.forEachIndexed { i, entry ->
                    indexes[i] = entry.key
                    oldColors[i] = entry.value
                    newColors[i] = mapPixels[entry.key] and 0xFFFFFF
                }
                if (undoStack.size >= 30) undoStack.removeFirst()
                undoStack.add(PaintAction(indexes, oldColors, newColors))
                redoStack.clear()
                dirtyBmp = true
            }
            currentStrokeChanges = mutableMapOf()
        }
    }

    fun undoLastStroke(): Boolean {
        val action = undoStack.removeLastOrNull() ?: return false
        for (i in action.pixelIndexes.indices) {
            mapPixels[action.pixelIndexes[i]] = action.oldColors[i]
        }
        redoStack.add(action)
        dirtyBmp = true
        return true
    }

    fun redoLastStroke(): Boolean {
        val action = redoStack.removeLastOrNull() ?: return false
        for (i in action.pixelIndexes.indices) {
            mapPixels[action.pixelIndexes[i]] = action.newColors[i]
        }
        undoStack.add(action)
        dirtyBmp = true
        return true
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun findProvinceCenter(province: ProvinceDef): Pair<Int, Int>? {
        provinceCenters[province.id]?.let { return it }
        val targetRgb = province.rgbInt
        val width = mapImage.width
        val height = mapImage.height
        var sumX = 0L
        var sumY = 0L
        var count = 0L

        for (y in 0 until height step 2) {
            val rowStart = y * width
            for (x in 0 until width step 2) {
                if ((mapPixels[rowStart + x] and 0xFFFFFF) == targetRgb) {
                    sumX += x
                    sumY += y
                    count++
                }
            }
        }
        if (count == 0L) return null
        val center = (sumX / count).toInt() to (sumY / count).toInt()
        provinceCenters[province.id] = center
        return center
    }

    fun findStateCenter(state: StateDef): Pair<Int, Int>? {
        val centers = state.provinces.mapNotNull { provinceCenters[it] }
        if (centers.isNotEmpty()) {
            val avgX = centers.sumOf { it.first } / centers.size
            val avgY = centers.sumOf { it.second } / centers.size
            return avgX to avgY
        }
        return null
    }

    // --- INFRASTRUCTURE: RAILWAYS, SUPPLY NODES, BUILDINGS, ADJACENCIES ---

    fun toggleSupplyNode(provinceId: Int): Boolean {
        val added = if (supplyNodes.contains(provinceId)) {
            supplyNodes.remove(provinceId)
            false
        } else {
            supplyNodes.add(provinceId)
            true
        }
        dirtySupplyNodes = true
        return added
    }

    fun addOrUpdateRailway(level: Int, rawPath: List<Int>): RailwayEntry? {
        val cleaned = mutableListOf<Int>()
        for (p in rawPath) {
            if (cleaned.lastOrNull() != p) {
                cleaned.add(p)
            }
        }
        if (cleaned.size < 2) return null
        val entry = RailwayEntry(level.coerceIn(1, 5), cleaned)
        railways.add(entry)
        dirtyRailways = true
        return entry
    }

    fun removeLastRailway(): Boolean {
        if (railways.isEmpty()) return false
        railways.removeLast()
        dirtyRailways = true
        return true
    }

    fun addBuildingAtBmpCoords(
        bmpX: Int,
        bmpY: Int,
        buildingType: String,
        explicitSeaProvinceId: Int = 0
    ): BuildingEntry? {
        val width = mapImage.width
        val height = mapImage.height
        if (bmpX !in 0 until width || bmpY !in 0 until height) return null

        val rgb = mapPixels[bmpY * width + bmpX] and 0xFFFFFF
        val prov = provincesByRgb[rgb] ?: return null
        val stateId = provinceToStateId[prov.id] ?: return null

        val hoi4X = bmpX.toDouble()
        val hoi4Z = (height - bmpY).toDouble()

        val requiresSeaProv = buildingType == "naval_base_spawn" || buildingType == "floating_harbor"
        val seaProv = if (requiresSeaProv) {
            if (explicitSeaProvinceId > 0) explicitSeaProvinceId
            else findNearestSeaProvinceId(bmpX, bmpY)
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
        dirtyBuildings = true
        return entry
    }

    fun removeBuilding(entry: BuildingEntry) {
        if (buildings.remove(entry)) {
            dirtyBuildings = true
        }
    }

    fun autoGenerateBuildingsForState(stateId: Int): Int {
        val state = statesById[stateId] ?: return 0
        val provIds = state.provinces.toList()
        if (provIds.isEmpty()) return 0

        val height = mapImage.height
        val typesToSpawn = listOf(
            "arms_factory", "arms_factory", "industrial_complex", "industrial_complex",
            "air_base", "bunker", "anti_air_building", "synthetic_refinery", "nuclear_reactor_spawn"
        )

        var addedCount = 0
        typesToSpawn.forEachIndexed { idx, type ->
            val targetProvId = provIds[idx % provIds.size]
            val center = provinceCenters[targetProvId] ?: return@forEachIndexed
            val hoi4X = (center.first + (idx % 3) - 1).toDouble()
            val hoi4Z = (height - (center.second + (idx / 3) - 1)).toDouble()
            buildings.add(BuildingEntry(stateId, type, hoi4X, 9.80, hoi4Z, 0.0, 0))
            addedCount++
        }

        val coastalProvId = provIds.firstOrNull { provincesById[it]?.coastal == true }
        if (coastalProvId != null) {
            val c = provinceCenters[coastalProvId]
            if (c != null) {
                val seaProv = findNearestSeaProvinceId(c.first, c.second)
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

        if (addedCount > 0) dirtyBuildings = true
        return addedCount
    }

    private fun findNearestSeaProvinceId(bmpX: Int, bmpY: Int): Int {
        var bestId = 0
        var bestDist = Double.MAX_VALUE
        for (prov in provincesById.values) {
            if (prov.type == "sea") {
                val c = provinceCenters[prov.id] ?: continue
                val d = hypot((c.first - bmpX).toDouble(), (c.second - bmpY).toDouble())
                if (d < bestDist) {
                    bestDist = d
                    bestId = prov.id
                }
            }
        }
        return bestId
    }

    fun addAdjacency(fromId: Int, toId: Int, throughSeaId: Int, ruleName: String, comment: String) {
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
        dirtyAdjacencies = true
    }

    fun removeAdjacency(entry: AdjacencyEntry) {
        if (adjacencies.remove(entry)) {
            dirtyAdjacencies = true
        }
    }

    // --- DIAGNOSTICS ---

    fun runDiagnostics(): List<MapIssue> {
        val issues = mutableListOf<MapIssue>()

        val maxId = provincesById.keys.maxOrNull() ?: 0
        val defaultMapText = defaultMapFile.readText()
        val configuredMax = Regex("""max_provinces\s*=\s*(\d+)""")
            .find(defaultMapText)?.groupValues?.get(1)?.toIntOrNull()

        if (configuredMax != null && !dirtyDefaultMap && configuredMax != maxId + 1) {
            issues.add(
                MapIssue(
                    severity = IssueSeverity.CRITICAL,
                    title = "max_provinces Mismatch in default.map",
                    description = "default.map specifies max_provinces = $configuredMax, but highest province ID is $maxId (expected ${maxId + 1})."
                )
            )
        }

        for ((provId, stateIds) in duplicateStateAssignments) {
            issues.add(
                MapIssue(
                    severity = IssueSeverity.CRITICAL,
                    title = "Province #$provId assigned to multiple States",
                    description = "Assigned to States: ${stateIds.joinToString(", ")}. This causes instant crashes.",
                    provinceId = provId,
                    stateId = stateIds.first()
                )
            )
        }

        provincesById.values.filter { it.id > 0 }.forEach { prov ->
            if (prov.type == "land" && !provinceToStateId.containsKey(prov.id)) {
                issues.add(
                    MapIssue(
                        severity = IssueSeverity.CRITICAL,
                        title = "Unassigned Land Province #${prov.id}",
                        description = "Province #${prov.id} is 'land' in definition.csv, but not assigned to any State.",
                        provinceId = prov.id
                    )
                )
            }
            if (!provinceToStrategicRegionId.containsKey(prov.id)) {
                issues.add(
                    MapIssue(
                        severity = IssueSeverity.CRITICAL,
                        title = "Province #${prov.id} missing Strategic Region",
                        description = "Every province must belong to a Strategic Region in map/strategicregions/.",
                        provinceId = prov.id
                    )
                )
            }
        }

        statesById.values.forEach { state ->
            if (state.provinces.isEmpty()) {
                issues.add(
                    MapIssue(
                        severity = IssueSeverity.CRITICAL,
                        title = "Empty State #${state.id} (${state.name})",
                        description = "File ${state.fileName} has 0 provinces assigned.",
                        stateId = state.id
                    )
                )
            }
        }

        railways.forEachIndexed { idx, rail ->
            for (i in 0 until rail.provinces.size - 1) {
                if (rail.provinces[i] == rail.provinces[i + 1]) {
                    issues.add(
                        MapIssue(
                            severity = IssueSeverity.CRITICAL,
                            title = "Railway #${idx + 1} has consecutive duplicate province #${rail.provinces[i]}",
                            description = "A railway connecting a province to itself crashes the HoI4 map loader.",
                            provinceId = rail.provinces[i]
                        )
                    )
                }
            }
        }

        val width = mapImage.width
        val height = mapImage.height
        val unknownRgbs = mutableMapOf<Int, Pair<Int, Int>>()

        for (y in 0 until height step 2) {
            val rowStart = y * width
            for (x in 0 until width step 2) {
                val rgb = mapPixels[rowStart + x] and 0xFFFFFF
                if (!provincesByRgb.containsKey(rgb) && !unknownRgbs.containsKey(rgb)) {
                    unknownRgbs[rgb] = x to y
                    if (unknownRgbs.size >= 25) break
                }
            }
            if (unknownRgbs.size >= 25) break
        }

        for ((rgb, coords) in unknownRgbs) {
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            issues.add(
                MapIssue(
                    severity = IssueSeverity.CRITICAL,
                    title = "Undefined RGB ($r, $g, $b) on provinces.bmp",
                    description = "Found pixel at (${coords.first}, ${coords.second}) not in definition.csv (anti-aliasing).",
                    mapCoordinates = coords
                )
            )
        }

        return issues
    }

    fun autoFixCoastalFlags(): Int {
        val width = mapImage.width
        val height = mapImage.height
        val coastalLandRgbSet = HashSet<Int>()

        for (y in 0 until height) {
            val rowStart = y * width
            for (x in 0 until width) {
                val rgbA = mapPixels[rowStart + x] and 0xFFFFFF
                if (x > 0) {
                    val rgbB = mapPixels[rowStart + x - 1] and 0xFFFFFF
                    if (rgbA != rgbB) checkCoastalPair(rgbA, rgbB, coastalLandRgbSet)
                }
                if (y > 0) {
                    val rgbB = mapPixels[rowStart - width + x] and 0xFFFFFF
                    if (rgbA != rgbB) checkCoastalPair(rgbA, rgbB, coastalLandRgbSet)
                }
            }
        }

        var updatedCount = 0
        provincesById.values.filter { it.id > 0 && it.type == "land" }.forEach { prov ->
            val shouldBeCoastal = coastalLandRgbSet.contains(prov.rgbInt)
            if (prov.coastal != shouldBeCoastal) {
                val updated = prov.copy(coastal = shouldBeCoastal)
                provincesById[prov.id] = updated
                provincesByRgb[updated.rgbInt] = updated
                updatedCount++
            }
        }

        if (updatedCount > 0) {
            dirtyDefinitions = true
            invalidateColorLut()
        }
        return updatedCount
    }

    private fun checkCoastalPair(rgbA: Int, rgbB: Int, coastalLandRgbs: HashSet<Int>) {
        val pA = provincesByRgb[rgbA] ?: return
        val pB = provincesByRgb[rgbB] ?: return
        if (pA.type == "land" && pB.type == "sea") coastalLandRgbs.add(rgbA)
        else if (pB.type == "land" && pA.type == "sea") coastalLandRgbs.add(rgbB)
    }

    // --- STAGED IN-MEMORY MUTATIONS (NO DISK I/O UNTIL CTRL+S) ---

    fun createNewProvince(
        type: String,
        coastal: Boolean,
        terrain: String,
        continent: Int
    ): ProvinceDef {
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

        val newProv = ProvinceDef(
            id = nextId,
            r = r, g = g, b = b,
            type = type,
            coastal = coastal,
            terrain = terrain,
            continent = continent
        )

        provincesById[nextId] = newProv
        provincesByRgb[rgb] = newProv
        dirtyDefinitions = true
        dirtyDefaultMap = true
        invalidateColorLut()

        return newProv
    }

    fun updateProvinceDefinition(updated: ProvinceDef) {
        val old = provincesById[updated.id]
        if (old == updated) return
        if (old != null && old.rgbInt != updated.rgbInt) {
            provincesByRgb.remove(old.rgbInt)
        }
        provincesById[updated.id] = updated
        if (updated.id > 0) {
            provincesByRgb[updated.rgbInt] = updated
        }
        dirtyDefinitions = true
        invalidateColorLut()
    }

    fun createNewState(
        name: String,
        manpower: Int,
        category: String,
        initialProvinceIds: Collection<Int>
    ): StateDef {
        val nextId = (statesById.keys.maxOrNull() ?: 0) + 1
        val cleanName = name.ifBlank { "STATE_$nextId" }
        val fileName = "$nextId-${cleanName.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.txt"

        val initialProvs = mutableSetOf<Int>()
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
            provinces = initialProvs,
            rawText = rawContent,
            isModifiedOrInMod = true
        )

        statesById[nextId] = stateDef
        dirtyStateIds.add(nextId)

        for (provId in initialProvinceIds) {
            assignProvinceToState(provId, nextId)
        }
        invalidateColorLut()
        return statesById[nextId]!!
    }

    fun createNewStrategicRegion(
        name: String,
        initialProvinceIds: Collection<Int>
    ): StrategicRegionDef {
        val nextId = (strategicRegionsById.keys.maxOrNull() ?: 0) + 1
        val cleanName = name.ifBlank { "STRATEGICREGION_$nextId" }
        val fileName = "$nextId-${cleanName.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.txt"

        val initialProvs = mutableSetOf<Int>()
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
            provinces = initialProvs,
            rawText = rawContent,
            isModifiedOrInMod = true
        )

        strategicRegionsById[nextId] = regDef
        dirtyStrategicRegionIds.add(nextId)

        for (provId in initialProvinceIds) {
            assignProvinceToStrategicRegion(provId, nextId)
        }
        invalidateColorLut()
        return strategicRegionsById[nextId]!!
    }

    fun assignProvinceToState(provinceId: Int, targetStateId: Int) {
        val currentOwnerId = provinceToStateId[provinceId]
        if (currentOwnerId == targetStateId) return

        if (currentOwnerId != null) {
            statesById[currentOwnerId]?.let { oldState ->
                oldState.provinces.remove(provinceId)
                stageStateUpdateInMemory(oldState)
            }
        }

        val targetState = statesById[targetStateId] ?: return
        targetState.provinces.add(provinceId)
        provinceToStateId[provinceId] = targetStateId
        stageStateUpdateInMemory(targetState)
        invalidateColorLut()
    }

    fun removeProvinceFromState(provinceId: Int, stateId: Int) {
        val state = statesById[stateId] ?: return
        if (state.provinces.remove(provinceId)) {
            if (provinceToStateId[provinceId] == stateId) {
                provinceToStateId.remove(provinceId)
            }
            stageStateUpdateInMemory(state)
            invalidateColorLut()
        }
    }

    private fun stageStateUpdateInMemory(state: StateDef) {
        val sortedProvs = state.provinces.sorted().joinToString(" ")
        val updatedRaw = state.rawText.replace(
            Regex("""(\bprovinces\s*=\s*\{)[^}]*(\})"""),
            "$1\n\t\t$sortedProvs\n\t$2"
        )
        statesById[state.id] = state.copy(rawText = updatedRaw, isModifiedOrInMod = true)
        dirtyStateIds.add(state.id)
    }

    fun assignProvinceToStrategicRegion(provinceId: Int, targetRegionId: Int) {
        val currentRegId = provinceToStrategicRegionId[provinceId]
        if (currentRegId == targetRegionId) return

        if (currentRegId != null) {
            strategicRegionsById[currentRegId]?.let { oldReg ->
                oldReg.provinces.remove(provinceId)
                stageStrategicRegionUpdateInMemory(oldReg)
            }
        }

        val targetReg = strategicRegionsById[targetRegionId] ?: return
        targetReg.provinces.add(provinceId)
        provinceToStrategicRegionId[provinceId] = targetRegionId
        stageStrategicRegionUpdateInMemory(targetReg)
        invalidateColorLut()
    }

    private fun stageStrategicRegionUpdateInMemory(region: StrategicRegionDef) {
        val sortedProvs = region.provinces.sorted().joinToString(" ")
        val updatedRaw = region.rawText.replace(
            Regex("""(\bprovinces\s*=\s*\{)[^}]*(\})"""),
            "$1\n\t\t$sortedProvs\n\t$2"
        )
        strategicRegionsById[region.id] = region.copy(rawText = updatedRaw, isModifiedOrInMod = true)
        dirtyStrategicRegionIds.add(region.id)
    }

    // --- UNIFIED SAVE ALL (CTRL + S) ---

    fun saveAll() {
        if (dirtyBmp || !provincesFile.exists()) {
            saveProvincesBmp()
        }
        if (dirtyDefinitions || !definitionFile.exists()) {
            saveDefinitionsCsv()
        }
        if (dirtyDefaultMap || !defaultMapFile.exists()) {
            saveDefaultMap()
        }
        for (stateId in dirtyStateIds) {
            statesById[stateId]?.let { state ->
                File(statesDir, state.fileName).writeText(state.rawText)
            }
        }
        for (regId in dirtyStrategicRegionIds) {
            strategicRegionsById[regId]?.let { reg ->
                File(strategicRegionsDir, reg.fileName).writeText(reg.rawText)
            }
        }
        if (dirtyBuildings) {
            saveBuildingsTxt()
        }
        if (dirtyRailways) {
            saveRailwaysTxt()
        }
        if (dirtySupplyNodes) {
            saveSupplyNodesTxt()
        }
        if (dirtyAdjacencies) {
            saveAdjacenciesCsv()
        }
        clearDirtyFlags()
    }

    fun saveDefinitionsCsv() {
        val sb = StringBuilder()
        provincesById.keys.sorted().forEach { id ->
            val p = provincesById[id]!!
            sb.append(p.toCsvLine()).append("\n")
        }
        definitionFile.writeText(sb.toString())
    }

    private fun saveDefaultMap() {
        val maxId = provincesById.keys.maxOrNull() ?: 0
        val newMaxProvinces = maxId + 1
        val defaultMapContent = defaultMapFile.readText()
        val updatedDefaultMap = defaultMapContent.replace(
            Regex("""max_provinces\s*=\s*\d+"""),
            "max_provinces = $newMaxProvinces"
        )
        defaultMapFile.writeText(updatedDefaultMap)
    }

    private fun saveBuildingsTxt() {
        val sb = StringBuilder()
        buildings.sortedBy { it.stateId }.forEach { b ->
            sb.append(b.toLine()).append("\n")
        }
        buildingsFile.writeText(sb.toString())
    }

    private fun saveRailwaysTxt() {
        val sb = StringBuilder()
        railways.forEach { r ->
            sb.append(r.toLine()).append("\n")
        }
        railwaysFile.writeText(sb.toString())
    }

    private fun saveSupplyNodesTxt() {
        val sb = StringBuilder()
        supplyNodes.sorted().forEach { provId ->
            sb.append("1 ").append(provId).append("\n")
        }
        supplyNodesFile.writeText(sb.toString())
    }

    private fun saveAdjacenciesCsv() {
        val sb = StringBuilder()
        sb.append("From;To;Type;Through;start_x;start_y;stop_x;stop_y;adjacency_rule_name;Comment\n")
        adjacencies.forEach { a ->
            sb.append(a.toCsvLine()).append("\n")
        }
        sb.append("-1;-1;;-1;-1;-1;-1;-1;-1\n")
        adjacenciesFile.writeText(sb.toString())
    }

    fun saveProvincesBmp() {
        val width = mapImage.width
        val height = mapImage.height
        val rowStride = width * 3
        val padding = (4 - (rowStride % 4)) % 4
        val paddedRowSize = rowStride + padding
        val imageSize = paddedRowSize * height
        val fileSize = 54 + imageSize

        BufferedOutputStream(FileOutputStream(provincesFile), 65536).use { out ->
            val header = ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN)
            header.put('B'.code.toByte())
            header.put('M'.code.toByte())
            header.putInt(fileSize)
            header.putShort(0)
            header.putShort(0)
            header.putInt(54)

            header.putInt(40)
            header.putInt(width)
            header.putInt(height)
            header.putShort(1)
            header.putShort(24)
            header.putInt(0)
            header.putInt(imageSize)
            header.putInt(2835)
            header.putInt(2835)
            header.putInt(0)
            header.putInt(0)

            out.write(header.array())

            val rowBuffer = ByteArray(paddedRowSize)

            for (y in (height - 1) downTo 0) {
                val rowStart = y * width
                var idx = 0
                for (x in 0 until width) {
                    val rgb = mapPixels[rowStart + x]
                    rowBuffer[idx++] = (rgb and 0xFF).toByte()
                    rowBuffer[idx++] = ((rgb shr 8) and 0xFF).toByte()
                    rowBuffer[idx++] = ((rgb shr 16) and 0xFF).toByte()
                }
                for (p in 0 until padding) {
                    rowBuffer[idx++] = 0
                }
                out.write(rowBuffer)
            }
        }
    }
}