package at.yerova.hoi4_modder.v2.engine.io

import java.io.File

class ModFileResolver(
    val vanillaPath: File,
    val modPath: File
) {
    val modMapDir = File(modPath, "map")
    val modStatesDir = File(modPath, "history/states")
    val modStrategicRegionsDir = File(modPath, "map/strategicregions")

    val vanillaStatesDir = File(vanillaPath, "history/states")
    val vanillaStrategicRegionsDir = File(vanillaPath, "map/strategicregions")

    val modProvincesFile = File(modMapDir, "provinces.bmp")
    val modDefinitionFile = File(modMapDir, "definition.csv")
    val modDefaultMapFile = File(modMapDir, "default.map")
    val modBuildingsFile = File(modMapDir, "buildings.txt")
    val modRailwaysFile = File(modMapDir, "railways.txt")
    val modSupplyNodesFile = File(modMapDir, "supply_nodes.txt")
    val modAdjacenciesFile = File(modMapDir, "adjacencies.csv")

    fun resolveReadFile(relativePath: String): File {
        val modFile = File(modPath, relativePath)
        if (modFile.exists()) return modFile
        return File(vanillaPath, relativePath)
    }

    fun ensureModDirectoriesAndDescriptor() {
        if (!modMapDir.exists()) modMapDir.mkdirs()
        if (!modStatesDir.exists()) modStatesDir.mkdirs()
        if (!modStrategicRegionsDir.exists()) modStrategicRegionsDir.mkdirs()

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
    }
}