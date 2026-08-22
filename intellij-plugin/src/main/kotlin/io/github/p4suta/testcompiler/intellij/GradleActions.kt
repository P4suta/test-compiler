package io.github.p4suta.testcompiler.intellij

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.gradle.tooling.GradleConnector

abstract class GradleTaskAction : AnAction() {
    protected abstract fun invocation(project: Project): Invocation?

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val invocation = invocation(project) ?: return
        object : Task.Backgroundable(project, invocation.title, true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val base = project.basePath ?: return
                GradleConnector.newConnector().forProjectDirectory(java.io.File(base)).connect().use { connection ->
                    val cancellation = GradleConnector.newCancellationTokenSource()
                    if (indicator.isCanceled) cancellation.cancel()
                    connection.newBuild()
                        .forTasks(invocation.task)
                        .withArguments(*invocation.arguments.toTypedArray())
                        .withCancellationToken(cancellation.token())
                        .run()
                }
            }
        }.queue()
    }

    protected data class Invocation(val title: String, val task: String, val arguments: List<String> = emptyList())
}

class CheckAction : GradleTaskAction() {
    override fun invocation(project: Project) = Invocation("test-compiler check", "testCompilerCheck")
}

class BaselineAction : GradleTaskAction() {
    override fun invocation(project: Project) = Invocation("test-compiler baseline", "testCompilerBaseline")
}

class ReplayAction : GradleTaskAction() {
    override fun invocation(project: Project): Invocation? {
        val id = project.getService(ReportIndex::class.java).all().firstOrNull()?.id ?: return null
        return Invocation("test-compiler replay $id", "testCompilerReplay", listOf("-PtestCompiler.finding=$id"))
    }
}
