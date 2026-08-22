package io.github.p4suta.testcompiler.intellij

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import java.nio.file.Path

class TestCompilerInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                val virtualFile = file.virtualFile ?: return
                val findings = file.project.getService(ReportIndex::class.java).forPath(Path.of(virtualFile.path))
                val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return
                findings.forEach { finding ->
                    val line = (finding.line - 1).coerceIn(0, (document.lineCount - 1).coerceAtLeast(0))
                    val offset = document.getLineStartOffset(line)
                    val element = file.findElementAt(offset) ?: file
                    holder.registerProblem(element, "${finding.message} (${finding.id})")
                }
            }
        }
}
