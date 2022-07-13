package com.valb3r.bpmn.intellij.activiti.plugin.com.valb3r.bpmn.intellij.activiti.plugin.toolbar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.util.ui.JBEmptyBorder
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JPanel


class ToolbarPanel(val groupId: String) :

    JPanel(BorderLayout()) {
    val panel = JPanel(BorderLayout())

    init {
        val leftMargin = 45
        val myLinksPanel = JPanel(FlowLayout())
        panel.border = BorderFactory.createEmptyBorder(1, leftMargin, 1, 5)
        panel.add("West", myLinksPanel)
        panel.minimumSize = Dimension(0, 0)
        this.add("Center", panel)
        val toolbar = createToolbarFromGroupId(groupId)
        panel.add(toolbar.component)
    }

    companion object {
        private fun createToolbarFromGroupId(groupId: String): ActionToolbar {
            val actionManager = ActionManager.getInstance()
            check(actionManager.isGroup(groupId)) { "$groupId should have been a group" }
            val toolbarGroup = DefaultActionGroup()
            val group = actionManager.getAction(groupId) as DefaultActionGroup
            val children = group.getChildren(null)

            for (child in children) {
                toolbarGroup.addAction(child)
            }
            val editorToolbar =
                actionManager.createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, toolbarGroup, true) as ActionToolbarImpl
            editorToolbar.isOpaque = false
            editorToolbar.border = JBEmptyBorder(0, 2, 0, 2)
            return editorToolbar
        }
    }
}