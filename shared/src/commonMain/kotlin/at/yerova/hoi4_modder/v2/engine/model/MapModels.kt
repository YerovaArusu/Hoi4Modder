package at.yerova.hoi4_modder.v2.engine.model

import androidx.compose.runtime.Immutable
import at.yerova.hoi4_modder.v2.engine.Hoi4ModderEngine
import java.util.*
import java.util.prefs.Preferences
import kotlin.math.max
import kotlin.math.min

object AppPreferences {
    private val prefs: Preferences = Preferences.userNodeForPackage(Hoi4ModderEngine::class.java)

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

@Immutable
data class MapIssue(
    val severity: IssueSeverity,
    val title: String,
    val description: String,
    val provinceId: Int? = null,
    val stateId: Int? = null,
    val mapCoordinates: Pair<Int, Int>? = null
)

@Immutable
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

@Immutable
data class CopiedProvinceClipboard(
    val sourceProvinceId: Int,
    val type: String,
    val coastal: Boolean,
    val terrain: String,
    val continent: Int,
    val stateId: Int?,
    val strategicRegionId: Int?
)

@Immutable
data class StateDef(
    val id: Int,
    val name: String,
    val fileName: String,
    val provinces: Set<Int>,
    val rawText: String,
    val isModifiedOrInMod: Boolean
)

@Immutable
data class StrategicRegionDef(
    val id: Int,
    val name: String,
    val fileName: String,
    val provinces: Set<Int>,
    val rawText: String,
    val isModifiedOrInMod: Boolean
)

@Immutable
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

@Immutable
data class RailwayEntry(
    val level: Int,
    val provinces: List<Int>
) {
    fun toLine(): String = "$level ${provinces.size} ${provinces.joinToString(" ")}"
}

@Immutable
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
@Immutable
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
@Immutable
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
    val newColors: IntArray,
    val affectedBounds: PixelBounds
)