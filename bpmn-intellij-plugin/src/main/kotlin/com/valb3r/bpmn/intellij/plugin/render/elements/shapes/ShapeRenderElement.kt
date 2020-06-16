package com.valb3r.bpmn.intellij.plugin.render.elements.shapes

import com.valb3r.bpmn.intellij.plugin.Colors
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.BpmnSequenceFlow
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.WithParentId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.DiagramElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.elements.BoundsElement
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.elements.EdgeElement
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.elements.ShapeElement
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.elements.WaypointElement
import com.valb3r.bpmn.intellij.plugin.bpmn.api.events.Event
import com.valb3r.bpmn.intellij.plugin.bpmn.api.events.LocationUpdateWithId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.info.PropertyType
import com.valb3r.bpmn.intellij.plugin.events.*
import com.valb3r.bpmn.intellij.plugin.newelements.newElementsFactory
import com.valb3r.bpmn.intellij.plugin.render.*
import com.valb3r.bpmn.intellij.plugin.render.elements.ACTIONS_ICO_SIZE
import com.valb3r.bpmn.intellij.plugin.render.elements.BaseBpmnRenderElement
import com.valb3r.bpmn.intellij.plugin.render.elements.RenderState
import com.valb3r.bpmn.intellij.plugin.render.elements.internal.CascadeTranslationOrChangesToWaypoint
import com.valb3r.bpmn.intellij.plugin.render.elements.viewtransform.NullViewTransform
import com.valb3r.bpmn.intellij.plugin.state.CurrentState
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.util.*

const val WAYPOINT_LEN = 40.0f

abstract class ShapeRenderElement(
        override val elementId: DiagramElementId,
        override val bpmnElementId: BpmnElementId,
        protected val shape: ShapeElement,
        state: RenderState
) : BaseBpmnRenderElement(elementId, bpmnElementId, state) {

    protected open val cascadeTo = computeCascadables()

    override fun doRenderWithoutChildren(ctx: RenderContext): Map<DiagramElementId, AreaWithZindex> {
        val elem = state.currentState.elementByDiagramId[shape.id]
        val props = state.currentState.elemPropertiesByStaticElementId[elem]
        val name = props?.get(PropertyType.NAME)?.value as String?

        state.ctx.interactionContext.dragEndCallbacks[elementId] = {
            dx: Float, dy: Float, droppedOn: BpmnElementId?, allDroppedOn: SortedMap<AreaType, BpmnElementId> -> onDragEnd(dx, dy, droppedOn, allDroppedOn)
        }

        val shapeCtx = ShapeCtx(shape.id, elem, currentRect(ctx.canvas.camera), props, name)
        if (state.history.contains(bpmnElementId)) {
            val indexes = state.history.mapIndexed {pos, id -> if (id == bpmnElementId) pos else null}.filterNotNull()
            state.ctx.canvas.drawTextNoCameraTransform(
                    Point2D.Float(shapeCtx.shape.x, shapeCtx.shape.y), indexes.toString(), Colors.INNER_TEXT_COLOR.color, Colors.DEBUG_ELEMENT_COLOR.color
            )
        }
        return doRender(ctx, shapeCtx)
    }

    override fun drawActions(x: Float, y: Float): Map<DiagramElementId, AreaWithZindex> {
        val spaceCoeff = 1.3f
        val start = state.ctx.canvas.camera.fromCameraView(Point2D.Float(0.0f, 0.0f))
        val end = state.ctx.canvas.camera.fromCameraView(Point2D.Float(0.0f, ACTIONS_ICO_SIZE * spaceCoeff))
        val ySpacing = end.y - start.y

        var currY = y
        val delId = DiagramElementId("DEL:$elementId")
        val deleteIconArea = state.ctx.canvas.drawIcon(BoundsElement(x, currY, ACTIONS_ICO_SIZE, ACTIONS_ICO_SIZE), state.icons.recycleBin)
        state.ctx.interactionContext.clickCallbacks[delId] = { dest ->
            dest.addElementRemovedEvent(listOf(DiagramElementRemovedEvent(elementId)), listOf(BpmnElementRemovedEvent(shape.bpmnElement)))
        }

        currY += ySpacing
        val newLinkId = DiagramElementId("NEWLINK:$elementId")
        val newLinkArea = state.ctx.canvas.drawIcon(BoundsElement(x, currY, ACTIONS_ICO_SIZE, ACTIONS_ICO_SIZE), state.icons.sequence)
        state.ctx.interactionContext.clickCallbacks[newLinkId] = { dest ->
            state.currentState.elementByBpmnId[shape.bpmnElement]?.let { it: WithParentId ->
                val newSequenceBpmn = newElementsFactory().newOutgoingSequence(it.element)
                val bounds = currentRect(state.ctx.canvas.camera)
                val width = bounds.width
                val height = bounds.height

                val newSequenceDiagram = newElementsFactory().newDiagramObject(EdgeElement::class, newSequenceBpmn)
                        .copy(waypoint = listOf(
                                WaypointElement(bounds.x + width, bounds.y + height / 2.0f),
                                WaypointElement(bounds.x + width + WAYPOINT_LEN, bounds.y + height / 2.0f)
                        ))
                dest.addObjectEvent(
                        BpmnEdgeObjectAddedEvent(
                                WithParentId(parents.first().bpmnElementId, newSequenceBpmn),
                                EdgeElementState(newSequenceDiagram),
                                newElementsFactory().propertiesOf(newSequenceBpmn)
                        )
                )
            }
        }

        return mutableMapOf(
                delId to AreaWithZindex(deleteIconArea, AreaType.POINT, mutableSetOf(), mutableSetOf(), ICON_Z_INDEX, elementId),
                newLinkId to AreaWithZindex(newLinkArea, AreaType.POINT, mutableSetOf(), mutableSetOf(), ICON_Z_INDEX, elementId)
        )
    }

    abstract fun doRender(ctx: RenderContext, shapeCtx: ShapeCtx): Map<DiagramElementId, AreaWithZindex>

    override fun doDragToWithoutChildren(dx: Float, dy: Float) {
        // NOP
    }

    override fun onDragEnd(dx: Float, dy: Float, droppedOn: BpmnElementId?, allDroppedOn: SortedMap<AreaType, BpmnElementId>): MutableList<Event> {
        // Avoid double dragging by cascade and then by children
        val result = doOnDragEndWithoutChildren(dx, dy, null, allDroppedOn)
        val alreadyDraggedLocations = result.filterIsInstance<LocationUpdateWithId>().map { it.diagramElementId }.toMutableSet()
        children.forEach {
            for (event in it.onDragEnd(dx, dy, null, sortedMapOf())) { // Children do not change parent - sortedMapOf()
                handleChildDrag(event, alreadyDraggedLocations, result)
            }
        }

        viewTransform = NullViewTransform()
        return result
    }

    override fun doOnDragEndWithoutChildren(dx: Float, dy: Float, droppedOn: BpmnElementId?, allDroppedOn: SortedMap<AreaType, BpmnElementId>): MutableList<Event> {
        val events = mutableListOf<Event>()
        events += DraggedToEvent(elementId, dx, dy, null, null)
        val cascadeTargets = cascadeTo.filter { target -> target.cascadeSource == shape.bpmnElement } // TODO check if this comparison is still needed
        events += cascadeTargets
                .map { cascadeTo -> DraggedToEvent(cascadeTo.waypointId, dx, dy, cascadeTo.parentEdgeId, cascadeTo.internalId) }

        if (allDroppedOn.isEmpty()) {
            return events
        }

        events += handlePossibleNestingTo(allDroppedOn, cascadeTargets)

        return events
    }

    override fun doResizeWithoutChildren(dw: Float, dh: Float) {
        TODO("Not yet implemented")
    }

    override fun doResizeEndWithoutChildren(dw: Float, dh: Float): MutableList<Event> {
        TODO("Not yet implemented")
    }

    override fun afterStateChangesAppliedNoChildren() {
        if (viewTransform is NullViewTransform) {
            return
        }

        cascadeTo.mapNotNull { state.elemMap[it.waypointId] }.forEach {
            it.viewTransform = viewTransform
        }
    }

    override fun waypointAnchors(camera: Camera): MutableSet<Point2D.Float> {
        val rect = currentRect(camera)
        val halfWidth = rect.width / 2.0f
        val halfHeight = rect.height / 2.0f

        val cx = rect.x + rect.width / 2.0f
        val cy = rect.y + rect.height / 2.0f
        return mutableSetOf(
                Point2D.Float(cx - halfWidth, cy),
                Point2D.Float(cx + halfWidth, cy),
                Point2D.Float(cx, cy - halfHeight),
                Point2D.Float(cx, cy + halfHeight),

                Point2D.Float(cx - halfWidth / 2.0f, cy - halfHeight),
                Point2D.Float(cx + halfWidth / 2.0f, cy - halfHeight),
                Point2D.Float(cx - halfWidth / 2.0f, cy + halfHeight),
                Point2D.Float(cx + halfWidth / 2.0f, cy + halfHeight),

                Point2D.Float(cx - halfWidth, cy - halfHeight / 2.0f),
                Point2D.Float(cx - halfWidth, cy + halfHeight / 2.0f),
                Point2D.Float(cx + halfWidth, cy - halfHeight / 2.0f),
                Point2D.Float(cx + halfWidth, cy + halfHeight / 2.0f)
        )
    }

    override fun shapeAnchors(camera: Camera): MutableSet<Point2D.Float> {
        val rect = currentRect(camera)
        val cx = rect.x + rect.width / 2.0f
        val cy = rect.y + rect.height / 2.0f
        return mutableSetOf(
                Point2D.Float(cx, cy)
        )
    }

    override fun currentRect(camera: Camera): Rectangle2D.Float {
        return viewTransform.transform(shape.rectBounds())
    }

    open protected fun handlePossibleNestingTo(allDroppedOn: SortedMap<AreaType, BpmnElementId>,  cascadeTargets: List<CascadeTranslationOrChangesToWaypoint>): MutableList<Event> {
        val nests = allDroppedOn[AreaType.SHAPE_THAT_NESTS]
        val parentProcess = allDroppedOn[AreaType.PARENT_PROCESS_SHAPE]
        val currentParent = parents.firstOrNull()
        val newEvents = mutableListOf<Event>()

        if (allDroppedOn[allDroppedOn.firstKey()] == currentParent?.bpmnElementId) {
            return newEvents
        }

        if (null != nests && nests != currentParent?.bpmnElementId) {
            newEvents += BpmnParentChangedEvent(shape.bpmnElement, nests)
            // Cascade parent change to waypoint owning edge
            newEvents += cascadeTargets.mapNotNull { state.currentState.elementByDiagramId[it.parentEdgeId] }.map { BpmnParentChangedEvent(it, nests) }

        } else if (null != parentProcess && parentProcess != parents.firstOrNull()?.bpmnElementId) {
            newEvents += BpmnParentChangedEvent(shape.bpmnElement, parentProcess)
            // Cascade parent change to waypoint owning edge
            newEvents += cascadeTargets.mapNotNull { state.currentState.elementByDiagramId[it.parentEdgeId] }.map { BpmnParentChangedEvent(it, parentProcess) }
        }
        return newEvents
    }

    protected fun computeCascadables(): Set<CascadeTranslationOrChangesToWaypoint> {
        val idCascadesTo = setOf(PropertyType.SOURCE_REF, PropertyType.TARGET_REF)
        val result = mutableSetOf<CascadeTranslationOrChangesToWaypoint>()
        val elemToDiagramId = mutableMapOf<BpmnElementId, MutableSet<DiagramElementId>>()
        state.currentState.elementByDiagramId.forEach { elemToDiagramId.computeIfAbsent(it.value) { mutableSetOf() }.add(it.key) }
        state.currentState.elemPropertiesByStaticElementId.forEach { (owner, props) ->
            idCascadesTo.intersect(props.keys).filter { props[it]?.value == shape.bpmnElement.id }.forEach { type ->
                when (state.currentState.elementByBpmnId[owner]?.element) {
                    is BpmnSequenceFlow -> { result += computeCascadeToWaypoint(state.currentState, shape.bpmnElement, owner, type) }
                }
            }

        }
        return result
    }

    protected fun computeCascadeToWaypoint(state: CurrentState, cascadeTrigger: BpmnElementId, owner: BpmnElementId, type: PropertyType): Collection<CascadeTranslationOrChangesToWaypoint> {
        return state.edges
                .filter { it.bpmnElement == owner }
                .map {
                    val index = if (type == PropertyType.SOURCE_REF) 0 else it.waypoint.size - 1
                    val waypoint = it.waypoint[index]
                    CascadeTranslationOrChangesToWaypoint(cascadeTrigger, waypoint.id, Point2D.Float(waypoint.x, waypoint.y), it.id, waypoint.internalPhysicalPos)
                }
    }

    private fun handleChildDrag(event: Event, alreadyDraggedLocations: MutableSet<DiagramElementId>, result: MutableList<Event>) {
        if (event !is LocationUpdateWithId) {
            result += event
            return
        }

        if (alreadyDraggedLocations.contains(event.diagramElementId)) {
            return
        }

        alreadyDraggedLocations += event.diagramElementId
        result += event
    }
}