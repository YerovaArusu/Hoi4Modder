package at.yerova.hoi4_modder.v2.engine.render

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import at.yerova.hoi4_modder.v2.engine.grid.MapGridEngine
import at.yerova.hoi4_modder.v2.engine.model.MapViewMode
import at.yerova.hoi4_modder.v2.engine.model.PixelBounds
import at.yerova.hoi4_modder.v2.engine.model.ProvinceDef
import java.awt.image.BufferedImage
import java.awt.Color as AwtColor

class MapTileRenderer {

    companion object {
        const val MAP_TILE_SIZE = 256
    }

    private var colorLutDirty = true
    private var cachedColorLutMode: MapViewMode? = null
    private var cachedColorLutHighlight: Int? = null
    private var cachedColorLut: Map<Int, Int> = emptyMap()

    fun invalidateColorLut() {
        colorLutDirty = true
    }

    fun renderMapModeImage(
        grid: MapGridEngine,
        mode: MapViewMode,
        highlightedId: Int?,
        provincesByRgb: Map<Int, ProvinceDef>,
        provinceToStateId: Map<Int, Int>,
        provinceToStrategicRegionId: Map<Int, Int>
    ): BufferedImage {
        if (mode == MapViewMode.PROVINCES && highlightedId == null) return grid.mapImage

        val width = grid.width
        val height = grid.height
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val outRow = IntArray(width)
        val lut = colorLookupFor(
            mode = mode,
            highlightedId = highlightedId,
            provincesByRgb = provincesByRgb,
            provinceToStateId = provinceToStateId,
            provinceToStrategicRegionId = provinceToStrategicRegionId
        )

        val mapPixels = grid.mapPixels
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

    fun renderDirtyTiles(
        grid: MapGridEngine,
        bounds: PixelBounds,
        mode: MapViewMode,
        highlightedId: Int?,
        provincesByRgb: Map<Int, ProvinceDef>,
        provinceToStateId: Map<Int, Int>,
        provinceToStrategicRegionId: Map<Int, Int>
    ): Map<Int, ImageBitmap> {
        val width = grid.width
        val height = grid.height
        val tilesPerRow = (width + MAP_TILE_SIZE - 1) / MAP_TILE_SIZE
        val firstTileX = (bounds.left / MAP_TILE_SIZE).coerceAtLeast(0)
        val lastTileX = (bounds.right / MAP_TILE_SIZE).coerceAtMost(tilesPerRow - 1)
        val firstTileY = (bounds.top / MAP_TILE_SIZE).coerceAtLeast(0)
        val lastTileY = (bounds.bottom / MAP_TILE_SIZE).coerceAtMost((height - 1) / MAP_TILE_SIZE)

        val updatedTiles = LinkedHashMap<Int, ImageBitmap>()

        for (tileY in firstTileY..lastTileY) {
            for (tileX in firstTileX..lastTileX) {
                val x = tileX * MAP_TILE_SIZE
                val y = tileY * MAP_TILE_SIZE
                val tileWidth = (width - x).coerceAtMost(MAP_TILE_SIZE)
                val tileHeight = (height - y).coerceAtMost(MAP_TILE_SIZE)

                val tileImg = renderMapModeRegion(
                    grid = grid,
                    mode = mode,
                    highlightedId = highlightedId,
                    x = x,
                    y = y,
                    regionWidth = tileWidth,
                    regionHeight = tileHeight,
                    provincesByRgb = provincesByRgb,
                    provinceToStateId = provinceToStateId,
                    provinceToStrategicRegionId = provinceToStrategicRegionId
                )
                updatedTiles[tileY * tilesPerRow + tileX] = tileImg.toComposeImageBitmap()
            }
        }
        return updatedTiles
    }

    fun renderMapModeRegion(
        grid: MapGridEngine,
        mode: MapViewMode,
        highlightedId: Int?,
        x: Int,
        y: Int,
        regionWidth: Int,
        regionHeight: Int,
        provincesByRgb: Map<Int, ProvinceDef>,
        provinceToStateId: Map<Int, Int>,
        provinceToStrategicRegionId: Map<Int, Int>
    ): BufferedImage {
        val width = grid.width
        val height = grid.height
        require(regionWidth > 0 && regionHeight > 0)
        require(x >= 0 && y >= 0 && x + regionWidth <= width && y + regionHeight <= height)

        val out = BufferedImage(regionWidth, regionHeight, BufferedImage.TYPE_INT_RGB)
        val outRow = IntArray(regionWidth)
        val plainProvinceView = mode == MapViewMode.PROVINCES && highlightedId == null
        val lut = if (plainProvinceView) {
            emptyMap()
        } else {
            colorLookupFor(
                mode = mode,
                highlightedId = highlightedId,
                provincesByRgb = provincesByRgb,
                provinceToStateId = provinceToStateId,
                provinceToStrategicRegionId = provinceToStrategicRegionId
            )
        }

        val mapPixels = grid.mapPixels
        for (row in 0 until regionHeight) {
            val sourceStart = (y + row) * width + x
            for (column in 0 until regionWidth) {
                val rgb = mapPixels[sourceStart + column] and 0xFFFFFF
                outRow[column] = if (plainProvinceView) rgb else (lut[rgb] ?: 0xFF0000)
            }
            out.setRGB(0, row, regionWidth, 1, outRow, 0, regionWidth)
        }
        return out
    }

    private fun colorLookupFor(
        mode: MapViewMode,
        highlightedId: Int?,
        provincesByRgb: Map<Int, ProvinceDef>,
        provinceToStateId: Map<Int, Int>,
        provinceToStrategicRegionId: Map<Int, Int>
    ): Map<Int, Int> {
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

    private fun goldenRatioColorFromId(id: Int): Int {
        val hue = ((id * 137.508f) % 360f) / 360f
        val sat = 0.55f + ((id * 31) % 30) / 100f
        val bri = 0.70f + ((id * 17) % 20) / 100f
        return AwtColor.HSBtoRGB(hue, sat.coerceIn(0f, 1f), bri.coerceIn(0f, 1f)) and 0xFFFFFF
    }

    private fun brightenRgb(rgb: Int, factor: Float): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
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
}