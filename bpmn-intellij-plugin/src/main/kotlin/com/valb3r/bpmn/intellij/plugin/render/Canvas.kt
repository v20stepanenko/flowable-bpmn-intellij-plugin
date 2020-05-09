package com.valb3r.bpmn.intellij.plugin.render

import com.google.common.cache.CacheBuilder
import com.intellij.ui.EditorTextField
import com.valb3r.bpmn.intellij.plugin.Colors
import com.valb3r.bpmn.intellij.plugin.bpmn.api.BpmnProcessObjectView
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.DiagramElementId
import com.valb3r.bpmn.intellij.plugin.events.DraggedToEvent
import com.valb3r.bpmn.intellij.plugin.events.updateEventsRegistry
import com.valb3r.bpmn.intellij.plugin.properties.PropertiesVisualizer
import com.valb3r.bpmn.intellij.plugin.state.currentStateProvider
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Area
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import javax.swing.JTable
import kotlin.math.max
import kotlin.math.min

class Canvas: JPanel() {

    private val epsilon = 0.1f
    private val zoomFactor = 1.2f
    private val cursorSize = 3
    private val defaultCameraOrigin = Point2D.Float(0f, 0f)
    private val defaultZoomRatio = 1f
    private val stateProvider = currentStateProvider()
    private val updateEvents = updateEventsRegistry()

    private var selectedElements: MutableSet<DiagramElementId> = mutableSetOf()
    private var camera = Camera(defaultCameraOrigin, Point2D.Float(defaultZoomRatio, defaultZoomRatio))
    private var dragCtx: ElementDragContext = ElementDragContext(mutableSetOf(), Point2D.Float(), Point2D.Float())
    private var processObject: BpmnProcessObjectView? = null
    private var renderer: BpmnProcessRenderer? = null
    private var areaByElement: Map<DiagramElementId, Area>? = null
    private var propertiesVisualizer: PropertiesVisualizer? = null

    private val cachedIcons = CacheBuilder.newBuilder()
            .expireAfterAccess(10L, TimeUnit.SECONDS)
            .maximumSize(100)
            .build<String, BufferedImage>()

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)

        val graphics2D = setupGraphics(graphics)
        areaByElement = renderer?.render(
                RenderContext(
                        CanvasPainter(graphics2D, camera.copy(), cachedIcons),
                        selectedElements.toSet(),
                        dragCtx.copy(),
                        stateProvider
                )
        )
    }

    fun reset(properties: JTable, editorFactory: (value: String) -> EditorTextField, processObject: BpmnProcessObjectView, renderer: BpmnProcessRenderer) {
        this.processObject = processObject
        this.renderer = renderer
        this.camera = Camera(defaultCameraOrigin, Point2D.Float(defaultZoomRatio, defaultZoomRatio))
        this.propertiesVisualizer = PropertiesVisualizer(properties, editorFactory)
        this.stateProvider.resetStateTo(processObject)
    }

    fun click(location: Point2D.Float) {
        this.selectedElements.clear()
        dragCtx = ElementDragContext(mutableSetOf(), Point2D.Float(), Point2D.Float())

        val cursor = cursorRect(location)
        areaByElement
                ?.filter { it.value.intersects(cursor) }
                ?.forEach { this.selectedElements.add(it.key) }

        repaint()

        val selectedElementId = selectedElements.firstOrNull()
        processObject?.elementByDiagramId
                ?.get(selectedElementId)
                ?.let { elemId ->
                    processObject?.elemPropertiesByElementId?.get(elemId)?.let { propertiesVisualizer?.visualize(elemId, it) }
                }
    }

    fun dragCanvas(start: Point2D.Float, current: Point2D.Float) {
        val newCameraOrigin = Point2D.Float(
                camera.origin.x - current.x + start.x,
                camera.origin.y - current.y + start.y
        )
        camera = Camera(newCameraOrigin, Point2D.Float(camera.zoom.x, camera.zoom.y))
        repaint()
    }

    fun startDragWithButton(current: Point2D.Float) {
        val cursor = cursorRect(current)
        val elemsUnderCursor = areaByElement?.filter { it.value.intersects(cursor) }?.map { it.key }
        if (selectedElements.intersect(elemsUnderCursor ?: emptyList()).isEmpty()) {
            dragCtx = ElementDragContext(emptySet(), camera.fromCameraView(current), camera.fromCameraView(current))
            return
        }

        dragCtx = ElementDragContext(selectedElements.toSet(), camera.fromCameraView(current), camera.fromCameraView(current))
    }

    fun dragWithWheel(previous: Point2D.Float, current: Point2D.Float) {
        dragCanvas(previous, current)
    }

    fun dragWithLeftButton(previous: Point2D.Float, current: Point2D.Float) {
        if (selectedElements.isEmpty() || dragCtx.draggedIds.isEmpty()) {
            dragCanvas(previous, current)
            return
        }

        dragCtx = dragCtx.copy(current = camera.fromCameraView(current))
        repaint()
    }

    fun stopDrag() {
        if (dragCtx.draggedIds.isNotEmpty() && (dragCtx.current.distance(dragCtx.start) > epsilon)) {
            dragCtx.draggedIds
                    .forEach { updateEvents.addLocationUpdateEvent(
                            DraggedToEvent(
                                    it,
                                    dragCtx.current.x - dragCtx.start.x,
                                    dragCtx.current.y - dragCtx.start.y
                            )
                    ) }
        }

        dragCtx = dragCtx.copy(draggedIds = emptySet())
        repaint()
    }

    fun zoom(anchor: Point2D.Float, factor: Int) {
        val scale = Math.pow(zoomFactor.toDouble(), factor.toDouble()).toFloat()

        if (min(camera.zoom.x, camera.zoom.y) * scale < 0.3f || max(camera.zoom.x, camera.zoom.y) * scale > 2.0f) {
            return
        }

        val scenePoint = camera.fromCameraView(anchor)
        camera = Camera(
                Point2D.Float(
                        camera.origin.x + scenePoint.x * camera.zoom.x * (scale - 1.0f),
                        camera.origin.y + scenePoint.y * camera.zoom.y * (scale - 1.0f)
                ),
                Point2D.Float(camera.zoom.x * scale, camera.zoom.y * scale)
        )
        repaint()
    }

    private fun setupGraphics(graphics: Graphics): Graphics2D {
        // set up the drawing panel
        val graphics2D = graphics as Graphics2D
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // fill the background for entire canvas
        graphics2D.color = Colors.BACKGROUND_COLOR.color
        graphics2D.fillRect(0, 0, this.width, this.height)

        return graphics2D
    }

    // to handle small area shapes
    private fun cursorRect(location: Point2D.Float): Rectangle2D {
        val left = Point2D.Float(location.x - cursorSize, location.y - cursorSize)
        val right = Point2D.Float(location.x + cursorSize, location.y + cursorSize)

        return Rectangle2D.Float(
                left.x,
                left.y,
                right.x - left.x,
                right.y - left.y
        )
    }
}