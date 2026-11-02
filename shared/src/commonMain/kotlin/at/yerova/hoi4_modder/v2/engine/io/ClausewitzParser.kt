package at.yerova.hoi4_modder.v2.engine.io

import at.yerova.hoi4_modder.v2.engine.model.*
import java.io.File

data class LoadedStatesData(
    val statesById: Map<Int, StateDef>,
    val provinceToStateId: Map<Int, Int>,
    val duplicateStateAssignments: List<Pair<Int, List<Int>>>
)

data class LoadedRegionsData(
    val regionsById: Map<Int, StrategicRegionDef>,
    val provinceToRegionId: Map<Int, Int>
)

object ClausewitzParser {

    private val blockIdRegex = Regex("""\bid\s*=\s*(\d+)""")
    private val blockNameRegex = Regex("""\bname\s*=\s*"([^"]+)"""")
    private val provincesBlockRegex = Regex("""\bprovinces\s*=\s*\{([^}]*)\}""")
    private val provincesReplaceRegex = Regex("""(\bprovinces\s*=\s*\{)[^}]*(\})""")
    private val numberRegex = Regex("""\d+""")
    private val maxProvincesRegex = Regex("""max_provinces\s*=\s*(\d+)""")
    private val whitespaceRegex = Regex("""\s+""")

    fun loadDefinitions(file: File): Map<Int, ProvinceDef> {
        require(file.exists()) { "Missing definition.csv: ${file.absolutePath}" }
        val result = LinkedHashMap<Int, ProvinceDef>()

        file.useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                val parts = trimmed.split(";")
                if (parts.size >= 8) {
                    val id = parts[0].toIntOrNull() ?: return@forEach
                    val r = parts[1].toIntOrNull() ?: return@forEach
                    val g = parts[2].toIntOrNull() ?: return@forEach
                    val b = parts[3].toIntOrNull() ?: return@forEach
                    val def = ProvinceDef(
                        id = id,
                        r = r,
                        g = g,
                        b = b,
                        type = parts[4],
                        coastal = parts[5].toBoolean(),
                        terrain = parts[6],
                        continent = parts[7].toIntOrNull() ?: 1
                    )
                    result[id] = def
                }
            }
        }
        return result
    }

    fun saveDefinitions(targetFile: File, provincesById: Map<Int, ProvinceDef>) {
        targetFile.parentFile?.mkdirs()
        val sb = StringBuilder(provincesById.size * 36)
        provincesById.keys.sorted().forEach { id ->
            val p = provincesById[id] ?: return@forEach
            sb.append(p.toCsvLine()).append("\n")
        }
        targetFile.writeText(sb.toString())
    }

    fun readConfiguredMaxProvinces(defaultMapFile: File): Int? {
        if (!defaultMapFile.exists()) return null
        val text = defaultMapFile.readText()
        return maxProvincesRegex.find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun saveDefaultMap(sourceFile: File, targetFile: File, maxProvinceId: Int) {
        targetFile.parentFile?.mkdirs()
        val newMaxProvinces = maxProvinceId + 1
        val content = sourceFile.readText()
        val updated = content.replace(
            Regex("""max_provinces\s*=\s*\d+"""),
            "max_provinces = $newMaxProvinces"
        )
        targetFile.writeText(updated)
    }

    fun loadStates(vanillaStatesDir: File, modStatesDir: File): LoadedStatesData {
        val stateFiles = mutableMapOf<Int, Pair<File, Boolean>>()

        vanillaStatesDir.listFiles { f -> f.extension == "txt" }?.forEach { file ->
            extractBlockId(file.readText())?.let { id ->
                stateFiles[id] = file to false
            }
        }

        modStatesDir.listFiles { f -> f.extension == "txt" }?.forEach { file ->
            extractBlockId(file.readText())?.let { id ->
                stateFiles[id] = file to true
            }
        }

        val statesById = mutableMapOf<Int, StateDef>()
        val provinceToStateId = mutableMapOf<Int, Int>()
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

        val duplicates = provOwners.entries
            .filter { it.value.size > 1 }
            .map { it.key to it.value.toList() }

        return LoadedStatesData(statesById, provinceToStateId, duplicates)
    }

    fun saveDirtyStates(modStatesDir: File, dirtyStateIds: Set<Int>, statesById: Map<Int, StateDef>) {
        if (dirtyStateIds.isEmpty()) return
        modStatesDir.mkdirs()
        for (stateId in dirtyStateIds) {
            statesById[stateId]?.let { state ->
                File(modStatesDir, state.fileName).writeText(state.rawText)
            }
        }
    }

    fun formatUpdatedState(state: StateDef, updatedProvinces: Set<Int>): StateDef {
        val sortedProvs = updatedProvinces.sorted().joinToString(" ")
        val updatedRaw = state.rawText.replace(
            provincesReplaceRegex,
            "$1\n\t\t$sortedProvs\n\t$2"
        )
        return state.copy(
            provinces = updatedProvinces.toSet(),
            rawText = updatedRaw,
            isModifiedOrInMod = true
        )
    }

    fun loadStrategicRegions(vanillaRegionsDir: File, modRegionsDir: File): LoadedRegionsData {
        val regionFiles = mutableMapOf<Int, Pair<File, Boolean>>()

        vanillaRegionsDir.listFiles { f -> f.extension == "txt" }?.forEach { file ->
            extractBlockId(file.readText())?.let { id ->
                regionFiles[id] = file to false
            }
        }

        modRegionsDir.listFiles { f -> f.extension == "txt" }?.forEach { file ->
            extractBlockId(file.readText())?.let { id ->
                regionFiles[id] = file to true
            }
        }

        val regionsById = mutableMapOf<Int, StrategicRegionDef>()
        val provinceToRegionId = mutableMapOf<Int, Int>()

        regionFiles.forEach { (_, pair) ->
            val (file, isFromMod) = pair
            parseStrategicRegionFile(file, isFromMod)?.let { regDef ->
                regionsById[regDef.id] = regDef
                regDef.provinces.forEach { provId ->
                    provinceToRegionId[provId] = regDef.id
                }
            }
        }

        return LoadedRegionsData(regionsById, provinceToRegionId)
    }

    fun saveDirtyStrategicRegions(
        modRegionsDir: File,
        dirtyRegionIds: Set<Int>,
        regionsById: Map<Int, StrategicRegionDef>
    ) {
        if (dirtyRegionIds.isEmpty()) return
        modRegionsDir.mkdirs()
        for (regId in dirtyRegionIds) {
            regionsById[regId]?.let { reg ->
                File(modRegionsDir, reg.fileName).writeText(reg.rawText)
            }
        }
    }

    fun formatUpdatedStrategicRegion(region: StrategicRegionDef, updatedProvinces: Set<Int>): StrategicRegionDef {
        val sortedProvs = updatedProvinces.sorted().joinToString(" ")
        val updatedRaw = region.rawText.replace(
            provincesReplaceRegex,
            "$1\n\t\t$sortedProvs\n\t$2"
        )
        return region.copy(
            provinces = updatedProvinces.toSet(),
            rawText = updatedRaw,
            isModifiedOrInMod = true
        )
    }

    private fun extractBlockId(content: String): Int? {
        val clean = content.lines().joinToString("\n") { it.substringBefore("#") }
        return blockIdRegex.find(clean)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun parseStateFile(file: File, isFromMod: Boolean): StateDef? {
        val raw = file.readText()
        val clean = raw.lines().joinToString("\n") { it.substringBefore("#") }

        val id = blockIdRegex.find(clean)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val name = blockNameRegex.find(clean)?.groupValues?.get(1) ?: "STATE_$id"
        val provsBlock = provincesBlockRegex.find(clean)?.groupValues?.get(1) ?: ""

        val provs = numberRegex.findAll(provsBlock)
            .mapNotNull { it.value.toIntOrNull() }
            .toSet()

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

        val id = blockIdRegex.find(clean)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val name = blockNameRegex.find(clean)?.groupValues?.get(1) ?: "STRATEGICREGION_$id"
        val provsBlock = provincesBlockRegex.find(clean)?.groupValues?.get(1) ?: ""

        val provs = numberRegex.findAll(provsBlock)
            .mapNotNull { it.value.toIntOrNull() }
            .toSet()

        return StrategicRegionDef(
            id = id,
            name = name,
            fileName = file.name,
            provinces = provs,
            rawText = raw,
            isModifiedOrInMod = isFromMod
        )
    }

    fun loadBuildings(file: File): List<BuildingEntry> {
        if (!file.exists()) return emptyList()
        val list = mutableListOf<BuildingEntry>()
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
                    list.add(BuildingEntry(stId, type, x, y, z, rot, seaProv))
                }
            }
        }
        return list
    }

    fun saveBuildings(targetFile: File, buildings: List<BuildingEntry>) {
        targetFile.parentFile?.mkdirs()
        val sb = StringBuilder()
        buildings.sortedBy { it.stateId }.forEach { b ->
            sb.append(b.toLine()).append("\n")
        }
        targetFile.writeText(sb.toString())
    }

    fun loadRailways(file: File): List<RailwayEntry> {
        if (!file.exists()) return emptyList()
        val list = mutableListOf<RailwayEntry>()
        file.useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                val parts = trimmed.split(whitespaceRegex)
                if (parts.size >= 4) {
                    val level = parts[0].toIntOrNull() ?: return@forEach
                    val provs = parts.drop(2).mapNotNull { it.toIntOrNull() }
                    if (provs.size >= 2) {
                        list.add(RailwayEntry(level, provs))
                    }
                }
            }
        }
        return list
    }

    fun saveRailways(targetFile: File, railways: List<RailwayEntry>) {
        targetFile.parentFile?.mkdirs()
        val sb = StringBuilder()
        railways.forEach { r ->
            sb.append(r.toLine()).append("\n")
        }
        targetFile.writeText(sb.toString())
    }

    fun loadSupplyNodes(file: File): Set<Int> {
        if (!file.exists()) return emptySet()
        val nodes = LinkedHashSet<Int>()
        file.useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                val parts = trimmed.split(whitespaceRegex)
                if (parts.size >= 2) {
                    parts[1].toIntOrNull()?.let { nodes.add(it) }
                }
            }
        }
        return nodes
    }

    fun saveSupplyNodes(targetFile: File, supplyNodes: Set<Int>) {
        targetFile.parentFile?.mkdirs()
        val sb = StringBuilder()
        supplyNodes.sorted().forEach { provId ->
            sb.append("1 ").append(provId).append("\n")
        }
        targetFile.writeText(sb.toString())
    }

    fun loadAdjacencies(file: File): List<AdjacencyEntry> {
        if (!file.exists()) return emptyList()
        val list = mutableListOf<AdjacencyEntry>()
        file.useLines { lines ->
            lines.forEachIndexed { idx, line ->
                val trimmed = line.trim()
                if (idx == 0 || trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-1;-1")) {
                    return@forEachIndexed
                }
                val parts = line.split(";")
                if (parts.size >= 9) {
                    val from = parts[0].toIntOrNull() ?: return@forEachIndexed
                    val to = parts[1].toIntOrNull() ?: return@forEachIndexed
                    if (from <= 0 || to <= 0) return@forEachIndexed
                    list.add(
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
        return list
    }

    fun saveAdjacencies(targetFile: File, adjacencies: List<AdjacencyEntry>) {
        targetFile.parentFile?.mkdirs()
        val sb = StringBuilder()
        sb.append("From;To;Type;Through;start_x;start_y;stop_x;stop_y;adjacency_rule_name;Comment\n")
        adjacencies.forEach { a ->
            sb.append(a.toCsvLine()).append("\n")
        }
        sb.append("-1;-1;;-1;-1;-1;-1;-1;-1\n")
        targetFile.writeText(sb.toString())
    }
}