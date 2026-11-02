package at.yerova.hoi4_modder.v2.engine.diagnostics

import at.yerova.hoi4_modder.v2.engine.grid.MapGridEngine
import at.yerova.hoi4_modder.v2.engine.model.*

object MapDiagnosticsScanner {

    fun runDiagnostics(
        grid: MapGridEngine,
        configuredMaxProvinces: Int?,
        dirtyDefaultMap: Boolean,
        provincesById: Map<Int, ProvinceDef>,
        provincesByRgb: Map<Int, ProvinceDef>,
        statesById: Map<Int, StateDef>,
        provinceToStateId: Map<Int, Int>,
        duplicateStateAssignments: List<Pair<Int, List<Int>>>,
        provinceToStrategicRegionId: Map<Int, Int>,
        railways: List<RailwayEntry>
    ): List<MapIssue> {
        val issues = mutableListOf<MapIssue>()

        val maxId = provincesById.keys.maxOrNull() ?: 0
        if (configuredMaxProvinces != null && !dirtyDefaultMap && configuredMaxProvinces != maxId + 1) {
            issues.add(
                MapIssue(
                    severity = IssueSeverity.CRITICAL,
                    title = "max_provinces Mismatch in default.map",
                    description = "default.map specifies max_provinces = $configuredMaxProvinces, but highest province ID is $maxId (expected ${maxId + 1})."
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

        if (grid.isInitialized) {
            val width = grid.width
            val height = grid.height
            val mapPixels = grid.mapPixels
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
        }

        return issues
    }

    fun computeCoastalFlagUpdates(
        grid: MapGridEngine,
        provincesById: Map<Int, ProvinceDef>,
        provincesByRgb: Map<Int, ProvinceDef>
    ): List<ProvinceDef> {
        if (!grid.isInitialized) return emptyList()
        val width = grid.width
        val height = grid.height
        val mapPixels = grid.mapPixels
        val coastalLandRgbSet = HashSet<Int>()

        for (y in 0 until height) {
            val rowStart = y * width
            for (x in 0 until width) {
                val rgbA = mapPixels[rowStart + x] and 0xFFFFFF
                if (x > 0) {
                    val rgbB = mapPixels[rowStart + x - 1] and 0xFFFFFF
                    if (rgbA != rgbB) checkCoastalPair(rgbA, rgbB, provincesByRgb, coastalLandRgbSet)
                }
                if (y > 0) {
                    val rgbB = mapPixels[rowStart - width + x] and 0xFFFFFF
                    if (rgbA != rgbB) checkCoastalPair(rgbA, rgbB, provincesByRgb, coastalLandRgbSet)
                }
            }
        }

        val updatedProvinces = mutableListOf<ProvinceDef>()
        provincesById.values.filter { it.id > 0 && it.type == "land" }.forEach { prov ->
            val shouldBeCoastal = coastalLandRgbSet.contains(prov.rgbInt)
            if (prov.coastal != shouldBeCoastal) {
                updatedProvinces.add(prov.copy(coastal = shouldBeCoastal))
            }
        }
        return updatedProvinces
    }

    private fun checkCoastalPair(
        rgbA: Int,
        rgbB: Int,
        provincesByRgb: Map<Int, ProvinceDef>,
        coastalLandRgbs: HashSet<Int>
    ) {
        val pA = provincesByRgb[rgbA] ?: return
        val pB = provincesByRgb[rgbB] ?: return
        if (pA.type == "land" && pB.type == "sea") coastalLandRgbs.add(rgbA)
        else if (pB.type == "land" && pA.type == "sea") coastalLandRgbs.add(rgbB)
    }
}