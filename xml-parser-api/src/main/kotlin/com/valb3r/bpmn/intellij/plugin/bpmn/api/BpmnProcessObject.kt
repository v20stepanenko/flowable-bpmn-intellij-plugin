package com.valb3r.bpmn.intellij.plugin.bpmn.api

import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnProcess
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.WithBpmnId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.DiagramElement
import com.valb3r.bpmn.intellij.plugin.bpmn.api.diagram.DiagramElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.info.Property
import com.valb3r.bpmn.intellij.plugin.bpmn.api.info.PropertyType

// TODO - move to some implementation module
data class BpmnProcessObject(val process: BpmnProcess, val diagram: List<DiagramElement>) {

    fun toView(factory: BpmnObjectFactory) : BpmnProcessObjectView {
        val elementByDiagramId = mutableMapOf<DiagramElementId, BpmnElementId>()
        val elementByStaticId = mutableMapOf<BpmnElementId, WithBpmnId>()
        val propertiesById = mutableMapOf<BpmnElementId, MutableMap<PropertyType, Property>>()

        // Events
        process.startEvent?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }
        process.endEvent?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }

        // Service-task alike
        process.userTask?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }
        process.scriptTask?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }
        process.serviceTask?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }
        process.businessRuleTask?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }
        process.receiveTask?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }
        process.camelTask?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }
        process.httpTask?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }
        process.muleTask?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }
        process.decisionTask?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }
        process.shellTask?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }

        // Sub-process alike
        process.callActivity?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }
        process.subProcess?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }
        process.transaction?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }
        process.adHocSubProcess?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }

        // Gateways
        process.exclusiveGateway?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }
        process.parallelGateway?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }
        process.inclusiveGateway?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }

        // Linking elements
        process.sequenceFlow?.forEach { fillFor(factory, it, elementByStaticId, propertiesById) }

        diagram.firstOrNull()
                ?.bpmnPlane
                ?.bpmnEdge
                ?.filter { null != it.bpmnElement }
                ?.forEach { elementByDiagramId[it.id] = it.bpmnElement!! }

        diagram.firstOrNull()
                ?.bpmnPlane
                ?.bpmnShape
                ?.forEach { elementByDiagramId[it.id] = it.bpmnElement }

        return BpmnProcessObjectView(
                process.id,
                elementByDiagramId,
                elementByStaticId,
                propertiesById,
                diagram
        )
    }

    private fun fillFor(
            factory: BpmnObjectFactory,
            activity: WithBpmnId,
            elementById: MutableMap<BpmnElementId, WithBpmnId>,
            propertiesByElemType: MutableMap<BpmnElementId, MutableMap<PropertyType, Property>>) {
        elementById[activity.id] = activity
        propertiesByElemType[activity.id] = factory.propertiesOf(activity).toMutableMap()
    }

}

data class BpmnProcessObjectView(
        val processId: BpmnElementId,
        val elementByDiagramId: Map<DiagramElementId, BpmnElementId>,
        val elementByStaticId: Map<BpmnElementId, WithBpmnId>,
        val elemPropertiesByElementId: Map<BpmnElementId, Map<PropertyType, Property>>,
        val diagram: List<DiagramElement>
)