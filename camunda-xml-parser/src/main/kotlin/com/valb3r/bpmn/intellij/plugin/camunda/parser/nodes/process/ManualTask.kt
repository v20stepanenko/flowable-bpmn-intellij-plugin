package com.valb3r.bpmn.intellij.plugin.camunda.parser.nodes.process

import com.fasterxml.jackson.annotation.JsonMerge
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.tasks.BpmnManualTask
import com.valb3r.bpmn.intellij.plugin.camunda.parser.nodes.BpmnMappable
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers

data class ManualTask(
    @JacksonXmlProperty(isAttribute = true) val id: String,
    @JacksonXmlProperty(isAttribute = true) val name: String?,
    @JacksonXmlProperty(isAttribute = true) val documentation: String?,
    @JacksonXmlProperty(isAttribute = true) val asyncBefore: Boolean?,
    @JacksonXmlProperty(isAttribute = true) val asyncAfter: Boolean?,
    @JacksonXmlProperty(isAttribute = true) val isForCompensation: Boolean?,
    @JsonMerge @JacksonXmlElementWrapper(useWrapping = false) val incoming: List<String>?,
    @JsonMerge @JacksonXmlElementWrapper(useWrapping = false) val outgoing: List<String>?,
): BpmnMappable<BpmnManualTask> {

    override fun toElement(): BpmnManualTask {
        return Mappers.getMapper(ManualTaskMapping::class.java).convertToDto(this)
    }

    @Mapper(uses = [BpmnElementIdMapper::class])
    interface ManualTaskMapping {
        @Mapping(source = "forCompensation", target = "isForCompensation")
        fun convertToDto(input: ManualTask) : BpmnManualTask
    }
}