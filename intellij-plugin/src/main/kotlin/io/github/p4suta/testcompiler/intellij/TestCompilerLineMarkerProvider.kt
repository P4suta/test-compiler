package io.github.p4suta.testcompiler.intellij

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import java.nio.file.Path

class TestCompilerLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.firstChild != null) return null
        val file = element.containingFile ?: return null
        val virtualFile = file.virtualFile ?: return null
        val document = PsiDocumentManager.getInstance(element.project).getDocument(file) ?: return null
        val line = document.getLineNumber(element.textOffset) + 1
        val finding = element.project.getService(ReportIndex::class.java)
            .forPath(Path.of(virtualFile.path))
            .firstOrNull { it.line == line }
            ?: return null
        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.General.Warning,
            { "${finding.title}: ${finding.message}\n${finding.replayCommand}" },
            null,
            com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.LEFT,
            { "test-compiler ${finding.id}" },
        )
    }
}
