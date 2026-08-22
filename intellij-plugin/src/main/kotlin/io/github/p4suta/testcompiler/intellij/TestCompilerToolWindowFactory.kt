package io.github.p4suta.testcompiler.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JPanel

class TestCompilerToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val index = project.getService(ReportIndex::class.java)
        val model = DefaultListModel<String>()
        index.all().forEach { finding ->
            model.addElement("${finding.id} · ${finding.title} · ${finding.path}:${finding.line}")
        }
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(JBLabel("Authoritative report: build/reports/test-compiler/run-report-v1.json"), BorderLayout.NORTH)
            add(JBScrollPane(JBList(model)), BorderLayout.CENTER)
        }
        val content = toolWindow.contentManager.factory.createContent(panel, "Findings", false)
        toolWindow.contentManager.addContent(content)
    }
}
