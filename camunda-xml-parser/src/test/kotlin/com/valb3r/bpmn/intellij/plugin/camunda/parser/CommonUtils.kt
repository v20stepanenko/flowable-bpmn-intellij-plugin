package com.valb3r.bpmn.intellij.plugin.camunda.parser

import com.valb3r.bpmn.intellij.plugin.bpmn.api.BpmnProcessObject
import com.valb3r.bpmn.intellij.plugin.bpmn.api.events.EventPropagatableToXml
import com.valb3r.bpmn.intellij.plugin.camunda.parser.CamundaParser
import org.amshove.kluent.shouldNotBeNull
import java.nio.charset.StandardCharsets

fun String.asResource(): String? = object {}::class.java.classLoader.getResource(this)?.readText(StandardCharsets.UTF_8)

fun readAndUpdateProcess(parser: CamundaParser, event: EventPropagatableToXml): BpmnProcessObject {
    return readAndUpdateProcess(parser, "simple-nested.bpmn20.xml", event)
}

fun readAndUpdateProcess(parser: CamundaParser, processName: String, event: EventPropagatableToXml): BpmnProcessObject {
    return readAndUpdateProcess(parser, processName, listOf(event))
}

fun readAndUpdateProcess(parser: CamundaParser, processName: String, events: List<EventPropagatableToXml>): BpmnProcessObject {
    val updated = updateBpmnFile(parser, processName, events)
    return parser.parse(updated)
}

fun updateBpmnFile(parser: CamundaParser, processName: String, events: List<EventPropagatableToXml>): String {
    val updated = parser.update(
        processName.asResource()!!,
        events
    )

    updated.shouldNotBeNull()
    return updated
}