package at.yerova.hoi4_modder.v2.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import at.yerova.hoi4_modder.v2.engine.EngineState
import at.yerova.hoi4_modder.v2.engine.model.BorderSegment
import at.yerova.hoi4_modder.v2.engine.model.MapViewMode
import at.yerova.hoi4_modder.v2.engine.render.MapTileRenderer.Companion.MAP_TILE_SIZE
import at.yerova.hoi4_modder.v2.ui.Hoi4ModderTheme
import at.yerova.hoi4_modder.v2.ui.model.*
import at.yerova.hoi4_modder.v2.ui.navigation.*
import at.yerova.hoi4_modder.v2.ui.state.MapEditorSessionState
import hoi4_modder.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import java.awt.Cursor
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Canvas as ComposeCanvas


private object WindowsMapCursors {
    val Default = PointerIcon.Default
    val Move = PointerIcon(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR))
    val Crosshair = PointerIcon(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))
    val Hand = PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
}

private fun resolveCanvasPointerIcon(
    session: MapEditorSessionState,
    currentRoute: EditorRoute
): PointerIcon {
    if (session.isPanningDrag || session.isSpacePressed || session.activeTool == EditorTool.NAVIGATE) {
        return WindowsMapCursors.Move
    }

    val isAssignMode = (currentRoute is StatesRoute && currentRoute.assignMode) ||
            (currentRoute is StrategicRegionsRoute && currentRoute.assignMode)
    if (isAssignMode || session.isPaintingStateProvinces) {
        return WindowsMapCursors.Hand
    }
    if (currentRoute is CoastalRoute && session.toggleCoastalOnMapClick) {
        return WindowsMapCursors.Hand
    }
    if (currentRoute is TerrainRoute && session.paintTerrainOnMapClick) {
        return WindowsMapCursors.Crosshair
    }

    if (session.isCtrlHeld) {
        return WindowsMapCursors.Hand
    }

    return when (session.activeTool) {
        EditorTool.EYEDROPPER -> WindowsMapCursors.Hand
        EditorTool.BRUSH,
        EditorTool.BUCKET,
        EditorTool.RAILWAY,
        EditorTool.SUPPLY_NODE,
        EditorTool.BUILDING -> WindowsMapCursors.Crosshair

        EditorTool.NAVIGATE -> WindowsMapCursors.Move
    }
}

private fun bakeColoredGlyphSprite(
    layout: TextLayoutResult,
    tintColor: Color,
    density: Density
): ImageBitmap {
    val w = (layout.size.width + 8).coerceAtLeast(1)
    val h = (layout.size.height + 8).coerceAtLeast(1)
    val bitmap = ImageBitmap(w, h)
    val canvas = ComposeCanvas(bitmap)
    val drawScope = CanvasDrawScope()

    drawScope.draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(w.toFloat(), h.toFloat())
    ) {
        drawRoundRect(
            color = Color(0xD90A0E14),
            topLeft = Offset(1f, 1f),
            size = Size(w - 2f, h - 2f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = tintColor.copy(alpha = 0.65f),
            topLeft = Offset(1f, 1f),
            size = Size(w - 2f, h - 2f),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 1f)
        )
        drawText(layout, color = Color.Black, topLeft = Offset(5f, 5f))
        drawText(layout, color = tintColor, topLeft = Offset(4f, 4f))
    }
    return bitmap
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BoxScope.MapCanvasViewport(
    session: MapEditorSessionState,
    engineState: EngineState,
    targetMapMode: MapViewMode,
    selectionBorderSegments: List<BorderSegment>,
    groupBorderSegments: List<BorderSegment>,
    focusRequester: FocusRequester
) {
    val engine = session.engine
    val currentRoute = session.currentRoute
    val mapIconTextMeasurer = rememberTextMeasurer()
    val symbolFont = Hoi4ModderTheme.symbolFont
    val density = LocalDensity.current

    val mapIconTextStyle = remember(symbolFont) {
        TextStyle(
            fontFamily = symbolFont,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal
        )
    }

    val coloredIconSprites = remember(mapIconTextMeasurer, mapIconTextStyle, density) {
        AllMapIconSpecs.associate { spec ->
            val layout = mapIconTextMeasurer.measure(AnnotatedString(spec.iconName), style = mapIconTextStyle)
            (spec.iconName to spec.color) to bakeColoredGlyphSprite(layout, spec.color, density)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF0A0E14))
            .onSizeChanged { session.canvasSize = it }
    ) {
        val canvasPointerIcon = resolveCanvasPointerIcon(session, currentRoute)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerHoverIcon(canvasPointerIcon)
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val change = event.changes.first()
                    val scrollDelta = change.scrollDelta.y
                    val zoomFactor = if (scrollDelta < 0) 1.25f else 0.8f
                    val newScale = (session.scale * zoomFactor).coerceIn(0.15f, 60f)
                    val mousePos = change.position
                    session.offset = mousePos - (mousePos - session.offset) * (newScale / session.scale)
                    session.scale = newScale
                }
                .onPointerEvent(PointerEventType.Move) { event ->
                    session.isCtrlHeld = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                    val pos = event.changes.first().position
                    if (session.activeTool == EditorTool.BRUSH && currentRoute is ProvincesRoute) {
                        session.hoverScreenPos = pos
                    } else if (session.hoverScreenPos != null) {
                        session.hoverScreenPos = null
                    }
                    val mx = ((pos.x - session.offset.x) / session.scale).toInt()
                    val my = ((pos.y - session.offset.y) / session.scale).toInt()
                    val newCoords = if (mx in 0 until engineState.mapWidth && my in 0 until engineState.mapHeight) {
                        mx to my
                    } else null
                    if (newCoords != session.hoverMapCoords) session.hoverMapCoords = newCoords
                }
                .pointerInput(
                    session.activeTool,
                    session.primaryProvinceId,
                    session.brushRadius,
                    currentRoute,
                    session.isSpacePressed,
                    session.buildingTypeToPlace,
                    session.buildingSeaProvinceInput,
                    session.isPaintingStateProvinces,
                    session.paintTerrainOnMapClick,
                    session.activeTerrainBrush,
                    session.toggleCoastalOnMapClick,
                    session.selectedStateId
                ) {
                    try {
                        awaitPointerEventScope {
                            var lastTerrainPaintedProvId: Int? = null

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                val buttons = event.buttons
                                val ctrlDown = event.keyboardModifiers.isCtrlPressed ||
                                        event.keyboardModifiers.isMetaPressed || session.isCtrlHeld
                                val mx = ((change.position.x - session.offset.x) / session.scale).toInt()
                                val my = ((change.position.y - session.offset.y) / session.scale).toInt()

                                when (event.type) {
                                    PointerEventType.Press -> {
                                        focusRequester.requestFocus()
                                        lastTerrainPaintedProvId = null

                                        if (buttons.isPrimaryPressed && !session.isSpacePressed &&
                                            mx in 0 until engineState.mapWidth && my in 0 until engineState.mapHeight
                                        ) {
                                            val clickedRgb = engine.getRgbAtFast(mx, my)
                                            val clickedProv = clickedRgb?.let { engineState.provincesByRgb[it] }

                                            when {
                                                currentRoute is StatesRoute && currentRoute.assignMode -> {
                                                    val targetStId =
                                                        clickedProv?.let { engineState.provinceToStateId[it.id] }
                                                    if (targetStId != null) {
                                                        engine.assignProvincesToStateAsync(
                                                            currentRoute.allSourceProvinces,
                                                            targetStId
                                                        )
                                                        session.selectedStateId = targetStId
                                                        session.popRouteIfPossible()
                                                    }
                                                }

                                                currentRoute is StrategicRegionsRoute && currentRoute.assignMode -> {
                                                    val targetRegId =
                                                        clickedProv?.let { engineState.provinceToStrategicRegionId[it.id] }
                                                    if (targetRegId != null) {
                                                        engine.assignProvincesToStrategicRegionAsync(
                                                            currentRoute.sourceProvinceIds,
                                                            targetRegId
                                                        )
                                                        session.selectedRegionId = targetRegId
                                                        session.popRouteIfPossible()
                                                    }
                                                }

                                                session.isPaintingStateProvinces && session.selectedStateId != null && clickedProv != null -> {
                                                    val activeStId = session.selectedStateId!!
                                                    if (engineState.provinceToStateId[clickedProv.id] == activeStId) {
                                                        engine.removeProvinceFromStateAsync(clickedProv.id, activeStId)
                                                    } else {
                                                        engine.assignProvincesToStateAsync(
                                                            listOf(clickedProv.id),
                                                            activeStId
                                                        )
                                                    }
                                                }

                                                currentRoute is TerrainRoute && session.paintTerrainOnMapClick && !ctrlDown && clickedProv != null -> {
                                                    val spec =
                                                        KnownTerrainSpecs.find { it.id == session.activeTerrainBrush }
                                                    val targetTerrain = session.activeTerrainBrush
                                                    lastTerrainPaintedProvId = clickedProv.id
                                                    engine.batchEditProvinceDefinitionsAsync(listOf(clickedProv.id)) { p ->
                                                        p.copy(
                                                            terrain = targetTerrain,
                                                            type = spec?.defaultProvinceType ?: p.type
                                                        )
                                                    }
                                                }

                                                currentRoute is CoastalRoute && session.toggleCoastalOnMapClick && !ctrlDown && clickedProv != null -> {
                                                    engine.batchEditProvinceDefinitionsAsync(listOf(clickedProv.id)) { p ->
                                                        p.copy(coastal = !p.coastal)
                                                    }
                                                    session.handleProvinceSelection(clickedProv, false, engineState)
                                                }

                                                else -> {
                                                    if (ctrlDown || session.activeTool == EditorTool.EYEDROPPER || session.activeTool == EditorTool.NAVIGATE) {
                                                        session.handleProvinceSelection(
                                                            clickedProv,
                                                            ctrlDown,
                                                            engineState
                                                        )
                                                    } else {
                                                        when (session.activeTool) {
                                                            EditorTool.BRUSH -> {
                                                                session.primaryProvince(engineState)?.let { prov ->
                                                                    if (!session.paintStrokeActive.value) {
                                                                        engine.beginPaintStrokeAsync()
                                                                        session.paintStrokeActive.value = true
                                                                    }
                                                                    engine.paintCircleAsync(
                                                                        mx,
                                                                        my,
                                                                        session.brushRadius.roundToInt(),
                                                                        prov.rgbInt
                                                                    )
                                                                }
                                                            }

                                                            EditorTool.BUCKET -> {
                                                                session.primaryProvince(engineState)?.let { prov ->
                                                                    engine.floodFillAsync(mx, my, prov.rgbInt, prov.id)
                                                                }
                                                            }

                                                            EditorTool.RAILWAY -> {
                                                                if (clickedProv != null && session.activeRailwayPath.lastOrNull() != clickedProv.id) {
                                                                    session.activeRailwayPath.add(clickedProv.id)
                                                                }
                                                            }

                                                            EditorTool.SUPPLY_NODE -> {
                                                                if (clickedProv != null) {
                                                                    engine.toggleSupplyNodeAsync(clickedProv.id)
                                                                }
                                                            }

                                                            EditorTool.BUILDING -> {
                                                                val seaId =
                                                                    session.buildingSeaProvinceInput.toIntOrNull() ?: 0
                                                                engine.addBuildingAtBmpCoordsAsync(
                                                                    mx, my, session.buildingTypeToPlace, seaId
                                                                )
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
                                            val isDraggingTerrainPaint = currentRoute is TerrainRoute &&
                                                    session.paintTerrainOnMapClick && !ctrlDown && !session.isSpacePressed
                                            val isDraggingPixelBrush = currentRoute is ProvincesRoute &&
                                                    session.activeTool == EditorTool.BRUSH && !ctrlDown && !session.isSpacePressed

                                            if (buttons.isSecondaryPressed || buttons.isTertiaryPressed ||
                                                (buttons.isPrimaryPressed && !isDraggingPixelBrush && !isDraggingTerrainPaint)
                                            ) {
                                                session.isPanningDrag = true
                                                session.offset += dragDelta
                                                change.consume()
                                            } else if (buttons.isPrimaryPressed && isDraggingPixelBrush) {
                                                session.primaryProvince(engineState)?.let { prov ->
                                                    if (!session.paintStrokeActive.value) {
                                                        engine.beginPaintStrokeAsync()
                                                        session.paintStrokeActive.value = true
                                                    }
                                                    engine.paintCircleAsync(
                                                        mx, my, session.brushRadius.roundToInt(), prov.rgbInt
                                                    )
                                                    change.consume()
                                                }
                                            } else if (buttons.isPrimaryPressed && isDraggingTerrainPaint) {
                                                if (mx in 0 until engineState.mapWidth && my in 0 until engineState.mapHeight) {
                                                    val hoveredRgb = engine.getRgbAtFast(mx, my)
                                                    val hoveredProv = hoveredRgb?.let { engineState.provincesByRgb[it] }
                                                    if (hoveredProv != null && hoveredProv.id != lastTerrainPaintedProvId) {
                                                        lastTerrainPaintedProvId = hoveredProv.id
                                                        val spec =
                                                            KnownTerrainSpecs.find { it.id == session.activeTerrainBrush }
                                                        val targetTerrain = session.activeTerrainBrush
                                                        engine.batchEditProvinceDefinitionsAsync(listOf(hoveredProv.id)) { p ->
                                                            p.copy(
                                                                terrain = targetTerrain,
                                                                type = spec?.defaultProvinceType ?: p.type
                                                            )
                                                        }
                                                    }
                                                }
                                                change.consume()
                                            }
                                        }
                                    }

                                    PointerEventType.Release -> {
                                        if (!buttons.isPrimaryPressed && !buttons.isSecondaryPressed && !buttons.isTertiaryPressed) {
                                            session.isPanningDrag = false
                                        }

                                        lastTerrainPaintedProvId = null
                                        if (session.paintStrokeActive.value) {
                                            session.paintStrokeActive.value = false
                                            engine.endPaintStrokeAsync()
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        if (session.paintStrokeActive.value) {
                            session.paintStrokeActive.value = false
                            engine.endPaintStrokeAsync()
                        }
                    }
                }
        ) {
            val scale = session.scale
            val offset = session.offset

            // 1. Base Map Bitmap
            engineState.baseMapBitmap?.let { baseBmp ->
                drawImage(
                    image = baseBmp,
                    dstOffset = IntOffset(offset.x.roundToInt(), offset.y.roundToInt()),
                    dstSize = IntSize(
                        (baseBmp.width * scale).roundToInt(),
                        (baseBmp.height * scale).roundToInt()
                    ),
                    filterQuality = FilterQuality.None
                )
            }

            // 2. Dirty Paint Tiles
            if (engineState.mapWidth > 0) {
                val tilesPerRow = (engineState.mapWidth + MAP_TILE_SIZE - 1) / MAP_TILE_SIZE
                for ((tileKey, tileBitmap) in engineState.paintOverlayTiles) {
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
            }

            // 3. Highlighted State / Strategic Region Outline (Cyan)
            if (session.showSelectionOutline && groupBorderSegments.isNotEmpty()) {
                for (seg in groupBorderSegments) {
                    val start = Offset(offset.x + seg.x1 * scale, offset.y + seg.y1 * scale)
                    val end = Offset(offset.x + seg.x2 * scale, offset.y + seg.y2 * scale)
                    drawLine(color = Color(0xFF00E5FF), start = start, end = end, strokeWidth = 2.5f)
                }
            }

            // 4. Selected Province(s) Outline (Dual-pass Black + Bright Yellow)
            if (session.showSelectionOutline && selectionBorderSegments.isNotEmpty()) {
                for (seg in selectionBorderSegments) {
                    val start = Offset(offset.x + seg.x1 * scale, offset.y + seg.y1 * scale)
                    val end = Offset(offset.x + seg.x2 * scale, offset.y + seg.y2 * scale)
                    drawLine(color = Color.Black.copy(alpha = 0.85f), start = start, end = end, strokeWidth = 4.2f)
                    drawLine(color = Color(0xFFFFEA00), start = start, end = end, strokeWidth = 2.0f)
                }
            }

            val iconMarginMapPx = 28f / scale
            val visibleMinX = (-offset.x / scale) - iconMarginMapPx
            val visibleMaxX = ((session.canvasSize.width - offset.x) / scale) + iconMarginMapPx
            val visibleMinY = (-offset.y / scale) - iconMarginMapPx
            val visibleMaxY = ((session.canvasSize.height - offset.y) / scale) + iconMarginMapPx

            // 5. Adjacencies / Straits Overlay
            if (session.showAdjacenciesOverlay || currentRoute is CoastalRoute) {
                for (adj in engineState.adjacencies) {
                    val c1 = engineState.provinceCenters[adj.fromProvince] ?: continue
                    val c2 = engineState.provinceCenters[adj.toProvince] ?: continue
                    if ((c1.first < visibleMinX && c2.first < visibleMinX) ||
                        (c1.first > visibleMaxX && c2.first > visibleMaxX) ||
                        (c1.second < visibleMinY && c2.second < visibleMinY) ||
                        (c1.second > visibleMaxY && c2.second > visibleMaxY)
                    ) continue

                    val p1 = Offset(offset.x + (c1.first + 0.5f) * scale, offset.y + (c1.second + 0.5f) * scale)
                    val p2 = Offset(offset.x + (c2.first + 0.5f) * scale, offset.y + (c2.second + 0.5f) * scale)
                    drawLine(
                        color = if (adj.type == "impassable") Color(0xFFF87171) else Color(0xFF38BDF8),
                        start = p1,
                        end = p2,
                        strokeWidth = 1.8f * scale.coerceIn(0.8f, 3f),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                }
            }

            // 6. Railways Overlay
            if (session.showRailwaysOverlay || session.activeTool == EditorTool.RAILWAY) {
                val strokeW = 1.2f * scale.coerceIn(0.8f, 3.5f)
                for (rail in engineState.railways) {
                    val prows = rail.provinces
                    for (i in 0 until prows.size - 1) {
                        val c1 = engineState.provinceCenters[prows[i]] ?: continue
                        val c2 = engineState.provinceCenters[prows[i + 1]] ?: continue
                        if ((c1.first < visibleMinX && c2.first < visibleMinX) ||
                            (c1.first > visibleMaxX && c2.first > visibleMaxX) ||
                            (c1.second < visibleMinY && c2.second < visibleMinY) ||
                            (c1.second > visibleMaxY && c2.second > visibleMaxY)
                        ) continue

                        val p1 = Offset(offset.x + (c1.first + 0.5f) * scale, offset.y + (c1.second + 0.5f) * scale)
                        val p2 = Offset(offset.x + (c2.first + 0.5f) * scale, offset.y + (c2.second + 0.5f) * scale)
                        drawLine(color = Color(0xFFFFB703), start = p1, end = p2, strokeWidth = strokeW)
                    }
                }
                for (i in 0 until session.activeRailwayPath.size - 1) {
                    val c1 = engineState.provinceCenters[session.activeRailwayPath[i]] ?: continue
                    val c2 = engineState.provinceCenters[session.activeRailwayPath[i + 1]] ?: continue
                    val p1 = Offset(offset.x + (c1.first + 0.5f) * scale, offset.y + (c1.second + 0.5f) * scale)
                    val p2 = Offset(offset.x + (c2.first + 0.5f) * scale, offset.y + (c2.second + 0.5f) * scale)
                    drawLine(color = Color(0xFF22C55E), start = p1, end = p2, strokeWidth = strokeW * 1.4f)
                }
            }

            // 7. Supply Hubs Overlay (Farbige Sprites + LOD)
            if (session.showSupplyOverlay || session.activeTool == EditorTool.SUPPLY_NODE) {
                val supplyKey = SupplyNodeVisualSpec.iconName to SupplyNodeVisualSpec.color
                val warehouseSprite = coloredIconSprites[supplyKey]
                val renderFullIcons = scale >= 1.5f && warehouseSprite != null

                for (nodeProvId in engineState.supplyNodes) {
                    val c = engineState.provinceCenters[nodeProvId] ?: continue
                    val mx = c.first + 0.5f
                    val my = c.second + 0.5f
                    if (mx !in visibleMinX..visibleMaxX || my !in visibleMinY..visibleMaxY) continue

                    val sx = offset.x + mx * scale
                    val sy = offset.y + my * scale

                    if (renderFullIcons && warehouseSprite != null) {
                        drawImage(
                            image = warehouseSprite,
                            topLeft = Offset(sx - warehouseSprite.width / 2f, sy - warehouseSprite.height / 2f)
                        )
                    } else {
                        drawRect(
                            color = SupplyNodeVisualSpec.color,
                            topLeft = Offset(sx - 2.5f, sy - 2.5f),
                            size = Size(5f, 5f)
                        )
                    }
                }
            }

            // 8. Buildings Overlay (Farbige HoI4-Sprites + Farbige LOD-Dots + Viewport-Culling)
            if (session.showBuildingsOverlay || session.activeTool == EditorTool.BUILDING) {
                val mapH = engineState.mapHeight
                val zoomedInForIcons = scale >= 2.2f

                for (b in engineState.buildings) {
                    val bx = b.x.toFloat()
                    val by = (mapH - b.z).toFloat()
                    if (bx !in visibleMinX..visibleMaxX || by !in visibleMinY..visibleMaxY) continue

                    val isSelectedStateBuilding =
                        session.selectedStateId != null && b.stateId == session.selectedStateId
                    val sx = offset.x + bx * scale
                    val sy = offset.y + by * scale
                    val spec = visualSpecForBuilding(b.type)

                    if (zoomedInForIcons || isSelectedStateBuilding) {
                        val sprite = coloredIconSprites[spec.iconName to spec.color]
                        if (sprite != null) {
                            drawImage(
                                image = sprite,
                                topLeft = Offset(sx - sprite.width / 2f, sy - sprite.height / 2f)
                            )
                        }
                    } else if (scale >= 0.85f) {
                        drawRect(
                            color = spec.color.copy(alpha = 0.85f),
                            topLeft = Offset(sx - 1.5f, sy - 1.5f),
                            size = Size(3f, 3f)
                        )
                    }
                }
            }

            // 9. Brush Preview Circle
            if (session.activeTool == EditorTool.BRUSH && currentRoute is ProvincesRoute && session.hoverScreenPos != null) {
                drawCircle(
                    color = Color.White,
                    radius = (session.brushRadius + 0.5f) * scale,
                    center = session.hoverScreenPos!!,
                    style = Stroke(width = 1.5f)
                )
            }
        }

        MapRouteSwitcher(
            currentRoute = currentRoute,
            onNavigate = { session.navigateToRoute(it) },
            modifier = Modifier.align(Alignment.TopStart)
        )

        HMMapOverlayPanel(modifier = Modifier.align(Alignment.BottomStart)) {
            val hoverInfoText = session.hoverMapCoords?.let { (bx, by) ->
                val hoi4Z = engineState.mapHeight - by
                val rgb = engine.getRgbAtFast(bx, by)
                val hProv = rgb?.let { engineState.provincesByRgb[it] }
                val hState = hProv?.let { engineState.provinceToStateId[it.id] }?.let { engineState.statesById[it] }
                val hReg = hProv?.let { engineState.provinceToStrategicRegionId[it.id] }
                    ?.let { engineState.strategicRegionsById[it] }

                val ownerLabel = when (targetMapMode) {
                    MapViewMode.STATES -> if (hState != null) {
                        stringResource(Res.string.hover_state_owner, hState.id, hState.name)
                    } else {
                        stringResource(Res.string.hover_state_unassigned)
                    }

                    MapViewMode.STRATEGIC_REGIONS -> if (hReg != null) {
                        stringResource(Res.string.hover_region_owner, hReg.id, hReg.name)
                    } else {
                        stringResource(Res.string.hover_region_unassigned)
                    }

                    else -> if (hProv != null) stringResource(Res.string.hover_province_id, hProv.id) else ""
                }
                stringResource(Res.string.map_coordinates_format, bx, by, bx, hoi4Z) + ownerLabel + "  •  "
            } ?: ""

            Text(
                text = "${hoverInfoText}${
                    stringResource(
                        Res.string.zoom_percent,
                        (session.scale * 100).toInt()
                    )
                }  •  ${localizedEngineStatus(engineState)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}