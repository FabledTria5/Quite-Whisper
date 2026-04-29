package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import kotlin.collections.toSet

abstract class MarkBundledExecutablesTask : DefaultTask() {
    @get:Internal
    abstract val binariesDirectory: DirectoryProperty

    @get:Input
    abstract val executableNames: ListProperty<String>

    @TaskAction
    fun markExecutables() {
        val names = executableNames.get().toSet()
        binariesDirectory.get().asFile
            .walkTopDown()
            .filter { it.isFile && it.name in names }
            .forEach { it.setExecutable(true, false) }
    }
}