package com.valb3r.bpmn.intellij.plugin.camunda.parser.nodes.process

import com.fasterxml.jackson.annotation.JsonMerge
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.tasks.BpmnScriptTask
import com.valb3r.bpmn.intellij.plugin.camunda.parser.nodes.BpmnMappable
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers

data class ScriptTask(
    @JacksonXmlProperty(isAttribute = true) val id: String,
    @JacksonXmlProperty(isAttribute = true) val name: String?,
    val documentation: String?,
    @JacksonXmlProperty(isAttribute = true) val asyncBefore: Boolean?,
    @JacksonXmlProperty(isAttribute = true) val asyncAfter: Boolean?,
    @JacksonXmlProperty(isAttribute = true) val isForCompensation: Boolean?,
    @JacksonXmlProperty(isAttribute = false, localName = "script") val scriptBody: String?,
    @JacksonXmlProperty(isAttribute = true) val scriptFormat: String?,
    @JacksonXmlProperty(isAttribute = true) val autoStoreVariables: Boolean?,
    @JsonMerge @JacksonXmlElementWrapper(useWrapping = false) val incoming: List<String>?,
    @JsonMerge @JacksonXmlElementWrapper(useWrapping = false) val outgoing: List<String>?,
): BpmnMappable<BpmnScriptTask> {

    override fun toElement(): BpmnScriptTask {
        return Mappers.getMapper(ScriptTaskMapping::class.java).convertToDto(this)
    }

    @Mapper(uses = [BpmnElementIdMapper::class])
    interface ScriptTaskMapping {

        @Mapping(source = "forCompensation", target = "isForCompensation")
        fun convertToDto(input: ScriptTask) : BpmnScriptTask
    }
}