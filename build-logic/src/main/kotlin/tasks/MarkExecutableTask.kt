package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class MarkExecutableTask : DefaultTask() {
    @get:Internal
    abstract val directory: DirectoryProperty

    @get:Input
    abstract val executableNames: ListProperty<String>

    @TaskAction
    fun markExecutable() {
        executableNames.get().forEach { executableName ->
            directory.get().file(executableName).asFile.setExecutable(true, false)
        }
    }
}