package com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.gateways

import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.BpmnElementId
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.WithBpmnId

data class BpmnParallelGateway(
        override val id: BpmnElementId,
        val name: String?,
        val defaultElement: String?,
        val documentation: String?
): WithBpmnId {

    override fun updateBpmnElemId(newId: BpmnElementId): WithBpmnId {
        return copy(id = newId)
    }
}