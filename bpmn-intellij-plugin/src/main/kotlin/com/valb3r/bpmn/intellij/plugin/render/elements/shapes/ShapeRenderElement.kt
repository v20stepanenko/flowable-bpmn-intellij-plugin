package com.valb3r.bpmn.intellij.plugin.render.elements.shapes

import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.BpmnSequenceFlow
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.DiagramElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.elements.ShapeElement
import com.valb3r.bpmn.intellij.plugin.bpmn.api.events.Event
import com.valb3r.bpmn.intellij.plugin.bpmn.api.info.PropertyType
import com.valb3r.bpmn.intellij.plugin.events.DraggedToEvent
import com.valb3r.bpmn.intellij.plugin.render.AreaWithZindex
import com.valb3r.bpmn.intellij.plugin.render.Camera
import com.valb3r.bpmn.intellij.plugin.render.RenderContext
import com.valb3r.bpmn.intellij.plugin.render.elements.BaseRenderElement
import com.valb3r.bpmn.intellij.plugin.render.elements.RenderState
import com.valb3r.bpmn.intellij.plugin.render.elements.internal.CascadeTranslationToWaypoint
import com.valb3r.bpmn.intellij.plugin.render.elements.viewtransform.NullViewTransform
import com.valb3r.bpmn.intellij.plugin.state.CurrentState
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

abstract class ShapeRenderElement(
        override val elementId: DiagramElementId,
        protected val shape: ShapeElement,
        state: RenderState
) : BaseRenderElement(elementId, state) {

    protected open val cascadeTo = computeCascadables()

    override fun doRenderWithoutChildren(ctx: RenderContext): Map<DiagramElementId, AreaWithZindex> {
        val elem = state.currentState.elementByDiagramId[shape.id]
        val props = state.currentState.elemPropertiesByStaticElementId[elem]
        val name = props?.get(PropertyType.NAME)?.value as String?

        return doRender(
                ctx,
                ShapeCtx(
                        shape.id,
                        elem,
                        currentRect(ctx.canvas.camera),
                        props,
                        name
                )
        )
    }

    abstract fun doRender(ctx: RenderContext, shapeCtx: ShapeCtx): Map<DiagramElementId, AreaWithZindex>

    override fun doDragToWithoutChildren(dx: Float, dy: Float) {
        // NOP
    }

    override fun doOnDragEndWithoutChildren(dx: Float, dy: Float, droppedOn: BpmnElementId?): MutableList<Event> {
        val events = mutableListOf<Event>()
        events += DraggedToEvent(elementId, dx, dy, null, null)
        events += cascadeTo
                .filter { target -> target.cascadeSource == shape.bpmnElement }
                .filter { target -> !state.ctx.interactionContext.draggedIds.contains(target.waypointId) }
                .map { cascadeTo -> DraggedToEvent(cascadeTo.waypointId, dx, dy, cascadeTo.parentEdgeId, cascadeTo.internalId) }
        return events
    }

    override fun doResizeWithoutChildren(dw: Float, dh: Float) {
        TODO("Not yet implemented")
    }

    override fun doResizeEndWithoutChildren(dw: Float, dh: Float): MutableList<Event> {
        TODO("Not yet implemented")
    }

    override fun afterStateChangesAppliedNoChildren(elemMap: Map<DiagramElementId, BaseRenderElement>) {
        if (viewTransform is NullViewTransform) {
            return
        }

        cascadeTo.mapNotNull { elemMap[it.waypointId] }.forEach {
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
                Point2D.Float(cx, cy),

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

    protected fun computeCascadables(): Set<CascadeTranslationToWaypoint> {
        val idCascadesTo = setOf(PropertyType.SOURCE_REF, PropertyType.TARGET_REF)
        val result = mutableSetOf<CascadeTranslationToWaypoint>()
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

    protected fun computeCascadeToWaypoint(state: CurrentState, cascadeTrigger: BpmnElementId, owner: BpmnElementId, type: PropertyType): Collection<CascadeTranslationToWaypoint> {
        return state.edges
                .filter { it.bpmnElement == owner }
                .map {
                    val index = if (type == PropertyType.SOURCE_REF) 0 else it.waypoint.size - 1
                    val waypoint = it.waypoint[index]
                    CascadeTranslationToWaypoint(cascadeTrigger, waypoint.id, Point2D.Float(waypoint.x, waypoint.y), it.id, waypoint.internalPhysicalPos)
                }
    }
}