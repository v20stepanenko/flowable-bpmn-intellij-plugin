package com.valb3r.bpmn.intellij.plugin.flowable.parser.nodes.process

import com.fasterxml.jackson.annotation.JsonMerge
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.ExeсutionListener
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.ListenerField
import com.valb3r.bpmn.intellij.plugin.bpmn.api.bpmn.elements.tasks.BpmnBusinessRuleTask
import com.valb3r.bpmn.intellij.plugin.flowable.parser.nodes.BpmnMappable
import com.valb3r.bpmn.intellij.plugin.flowable.parser.nodes.process.nested.formprop.ExecutionListener
import com.valb3r.bpmn.intellij.plugin.flowable.parser.nodes.process.nested.formprop.ExtensionElement
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers

data class BusinessRuleTask(
        @JacksonXmlProperty(isAttribute = true) val id: String,
        @JacksonXmlProperty(isAttribute = true) val name: String?,
        val documentation: String?,
        @JacksonXmlProperty(isAttribute = true) val async: Boolean?,
        @JacksonXmlProperty(isAttribute = true) val isForCompensation: Boolean?,
        @JacksonXmlProperty(isAttribute = true) val ruleVariablesInput: String?,
        @JacksonXmlProperty(isAttribute = true) val rules: String?,
        @JacksonXmlProperty(isAttribute = true) val resultVariable: String?,
        @JacksonXmlProperty(isAttribute = true) val exclude: Boolean?,
        @JsonMerge @JacksonXmlElementWrapper(useWrapping = true) val extensionElements: List<ExtensionElement>? = null
): BpmnMappable<BpmnBusinessRuleTask> {

    override fun toElement(): BpmnBusinessRuleTask {
        return Mappers.getMapper(BusinessRuleTaskMapping::class.java).convertToDto(this)
    }

    @Mapper(uses = [BpmnElementIdMapper::class])
    abstract class BusinessRuleTaskMapping {

        @Mapping(source = "forCompensation", target = "isForCompensation")
        protected abstract fun doConvertToDto(input: BusinessRuleTask) : BpmnBusinessRuleTask

        fun convertToDto(input: BusinessRuleTask) : BpmnBusinessRuleTask {
            val task = doConvertToDto(input)
            return task.copy(
                executionListener = input.extensionElements?.filterIsInstance<ExecutionListener>()?.map { ExeсutionListener(it.clazz, it.event, it.fields?.map { ListenerField(it.name, it.string) }) },
            )
        }
    }
}