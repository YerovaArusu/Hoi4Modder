package at.yerova.hoi4_modder.v2.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface EditorRoute : NavKey

/**
 * Displays the province map and province inspector.
 */
@Serializable
data class ProvincesRoute(
    val provinceId: Int? = null
) : EditorRoute

/**
 * Displays the States Map.
 * If [assignMode] is true, clicking a state on the map or in the list assigns [sourceProvinceIds]
 * to that state and automatically navigates back.
 */
@Serializable
data class StatesRoute(
    val assignMode: Boolean = false,
    val sourceProvinceId: Int? = null,
    val sourceProvinceIds: List<Int> = emptyList()
) : EditorRoute {
    val allSourceProvinces: List<Int>
        get() = sourceProvinceIds.ifEmpty { listOfNotNull(sourceProvinceId) }
}

/**
 * Displays the Strategic Regions Map.
 * Supports the same [assignMode] return-flow as [StatesRoute].
 */
@Serializable
data class StrategicRegionsRoute(
    val assignMode: Boolean = false,
    val sourceProvinceIds: List<Int> = emptyList()
) : EditorRoute

/**
 * Displays the Terrain Map and terrain brush/batch editor.
 */
@Serializable
data object TerrainRoute : EditorRoute

/**
 * Displays the Coastal Map and coastal auto-fix tools.
 */
@Serializable
data object CoastalRoute : EditorRoute