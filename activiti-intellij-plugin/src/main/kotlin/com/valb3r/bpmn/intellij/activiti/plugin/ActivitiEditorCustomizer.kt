package com.valb3r.bpmn.intellij.activiti.plugin

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer
import com.valb3r.bpmn.intellij.activiti.plugin.com.valb3r.bpmn.intellij.activiti.plugin.toolbar.ToolbarPanel
import com.valb3r.bpmn.intellij.activiti.plugin.meta.ActivitiFileType


internal class ActivitiEditorCustomizer : TextEditorCustomizer {
    override fun customize(textEditor: TextEditor) {
        if (shouldAcceptEditor(textEditor)) {
            val toolbar = (ToolbarPanel("BpmnActivitiDiagramActionGroup").panel)
            textEditor.editor.headerComponent = toolbar
        }
    }

    private fun shouldAcceptEditor(editor: TextEditor): Boolean {
        val file = editor.file
        return file!!.fileType == ActivitiFileType.INSTANCE
    }
}