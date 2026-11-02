package at.yerova.hoi4_modder.v2.engine.grid

import at.yerova.hoi4_modder.v2.engine.model.*
import java.awt.image.BufferedImage

class MapGridEngine {

    lateinit var mapImage: BufferedImage
        private set

    lateinit var mapPixels: IntArray
        private set

    val isInitialized: Boolean
        get() = ::mapImage.isInitialized

    val width: Int
        get() = if (isInitialized) mapImage.width else 0

    val height: Int
        get() = if (isInitialized) mapImage.height else 0

    val provinceCenters = mutableMapOf<Int, Pair<Int, Int>>()
    val provinceBounds = mutableMapOf<Int, PixelBounds>()

    private val undoStack = ArrayDeque<PaintAction>()
    private val redoStack = ArrayDeque<PaintAction>()
    private var currentStrokeChanges = mutableMapOf<Int, Int>()
    private var currentStrokeBounds: PixelBounds? = null

    fun attachBitmap(image: BufferedImage, pixels: IntArray) {
        this.mapImage = image
        this.mapPixels = pixels
        undoStack.clear()
        redoStack.clear()
        currentStrokeChanges.clear()
        currentStrokeBounds = null
    }

    fun getRgbAt(x: Int, y: Int): Int? {
        if (!isInitialized || x !in 0 until width || y !in 0 until height) return null
        return mapPixels[y * width + x] and 0xFFFFFF
    }

    /**
     * Single-pass Berechnung aller Provinz-Zentren und Bounding-Boxes über die gesamte Karte.
     */
    fun recalculateAllProvinceCenters(
        provincesById: Map<Int, ProvinceDef>,
        provincesByRgb: Map<Int, ProvinceDef>
    ) {
        if (!isInitialized) return
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

        val w = width
        val h = height

        for (y in 0 until h) {
            val rowStart = y * w
            for (x in 0 until w) {
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

    fun findProvinceCenter(province: ProvinceDef): Pair<Int, Int>? {
        provinceCenters[province.id]?.let { return it }
        if (!isInitialized) return null

        val targetRgb = province.rgbInt
        val w = width
        val h = height
        var sumX = 0L
        var sumY = 0L
        var count = 0L

        for (y in 0 until h step 2) {
            val rowStart = y * w
            for (x in 0 until w step 2) {
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

    /**
     * Extrahiert verschmolzene horizontale und vertikale Umriss-Linien für die gegebenen Provinzen.
     */
    fun computeBorderSegmentsForProvinces(
        provinceIds: Set<Int>,
        provincesById: Map<Int, ProvinceDef>
    ): List<BorderSegment> {
        if (!isInitialized || provinceIds.isEmpty()) return emptyList()

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
        val w = width
        val h = height

        val scanLeft = (bounds.left - 1).coerceAtLeast(0)
        val scanRight = (bounds.right + 1).coerceAtMost(w - 1)
        val scanTop = (bounds.top - 1).coerceAtLeast(0)
        val scanBottom = (bounds.bottom + 1).coerceAtMost(h - 1)

        fun isInside(x: Int, y: Int): Boolean {
            return !(x !in 0 until w || y !in 0 until h) && targetRgbs.contains(mapPixels[y * w + x] and 0xFFFFFF)
        }

        val segments = ArrayList<BorderSegment>(256)

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

    fun beginPaintStroke() {
        currentStrokeChanges = mutableMapOf()
        currentStrokeBounds = null
    }

    fun paintCircleWithBounds(
        mapX: Int,
        mapY: Int,
        radius: Int,
        targetRgb: Int,
        provincesByRgb: Map<Int, ProvinceDef>
    ): PixelBounds? {
        if (!isInitialized || radius < 0) return null
        val w = width
        val h = height
        val target = targetRgb and 0xFFFFFF
        val rSq = radius * radius
        val left = (mapX - radius).coerceAtLeast(0)
        val right = (mapX + radius).coerceAtMost(w - 1)
        val top = (mapY - radius).coerceAtLeast(0)
        val bottom = (mapY + radius).coerceAtMost(h - 1)
        if (left > right || top > bottom) return null

        var dirtyLeft = w
        var dirtyTop = h
        var dirtyRight = -1
        var dirtyBottom = -1

        for (py in top..bottom) {
            val dy = py - mapY
            val rowStart = py * w
            for (px in left..right) {
                val dx = px - mapX
                if (dx * dx + dy * dy > rSq) continue

                val index = rowStart + px
                val oldRgb = mapPixels[index] and 0xFFFFFF
                if (oldRgb == target) continue

                currentStrokeChanges.putIfAbsent(index, oldRgb)
                mapPixels[index] = target
                if (px < dirtyLeft) dirtyLeft = px
                if (py < dirtyTop) dirtyTop = py
                if (px > dirtyRight) dirtyRight = px
                if (py > dirtyBottom) dirtyBottom = py
            }
        }

        return if (dirtyRight >= dirtyLeft && dirtyBottom >= dirtyTop) {
            val dirtyBounds = PixelBounds(dirtyLeft, dirtyTop, dirtyRight, dirtyBottom)
            currentStrokeBounds = currentStrokeBounds?.union(dirtyBounds) ?: dirtyBounds
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

    fun floodFill(
        startX: Int,
        startY: Int,
        targetRgb: Int,
        provincesByRgb: Map<Int, ProvinceDef>
    ): PixelBounds? {
        if (!isInitialized) return null
        val w = width
        val h = height
        if (startX !in 0 until w || startY !in 0 until h) return null

        val target = targetRgb and 0xFFFFFF
        val sourceRgb = mapPixels[startY * w + startX] and 0xFFFFFF
        if (sourceRgb == target) return null

        beginPaintStroke()
        val totalPixels = w * h
        var queue = LongArray(minOf(totalPixels, 1024).coerceAtLeast(1))
        var head = 0
        var tail = 0

        var dirtyLeft = w
        var dirtyTop = h
        var dirtyRight = -1
        var dirtyBottom = -1

        fun enqueueRun(y: Int, seedX: Int) {
            val rowStart = y * w
            var left = seedX
            while (left > 0 && (mapPixels[rowStart + left - 1] and 0xFFFFFF) == sourceRgb) left--
            var right = seedX
            while (right < w - 1 && (mapPixels[rowStart + right + 1] and 0xFFFFFF) == sourceRgb) right++

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
            val y = firstIndex / w
            val left = firstIndex % w
            val right = lastIndex % w

            for (neighborY in intArrayOf(y - 1, y + 1)) {
                if (neighborY !in 0 until h) continue
                val neighborRowStart = neighborY * w
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

        val dirtyBounds = if (dirtyRight >= dirtyLeft && dirtyBottom >= dirtyTop) {
            PixelBounds(dirtyLeft, dirtyTop, dirtyRight, dirtyBottom).also { bounds ->
                currentStrokeBounds = bounds
                provincesByRgb[target]?.let { prov ->
                    val existing = provinceBounds[prov.id]
                    provinceBounds[prov.id] = existing?.union(bounds) ?: bounds
                }
            }
        } else null

        endPaintStroke()
        return dirtyBounds
    }

    fun endPaintStroke(): Boolean {
        var committed = false
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
                val bounds = currentStrokeBounds ?: PixelBounds(0, 0, width - 1, height - 1)
                if (undoStack.size >= 30) undoStack.removeFirst()
                undoStack.add(PaintAction(indexes, oldColors, newColors, bounds))
                redoStack.clear()
                committed = true
            }
            currentStrokeChanges = mutableMapOf()
            currentStrokeBounds = null
        }
        return committed
    }

    fun undoLastStroke(): PixelBounds? {
        val action = undoStack.removeLastOrNull() ?: return null
        for (i in action.pixelIndexes.indices) {
            mapPixels[action.pixelIndexes[i]] = action.oldColors[i]
        }
        redoStack.add(action)
        return action.affectedBounds
    }

    fun redoLastStroke(): PixelBounds? {
        val action = redoStack.removeLastOrNull() ?: return null
        for (i in action.pixelIndexes.indices) {
            mapPixels[action.pixelIndexes[i]] = action.newColors[i]
        }
        undoStack.add(action)
        return action.affectedBounds
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
}