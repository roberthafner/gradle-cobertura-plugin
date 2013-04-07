package org.gradle.api.plugins.cobertura

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.ConventionMapping
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.plugins.cobertura.tasks.CoberturaBaseTask
import org.gradle.api.plugins.cobertura.tasks.CoberturaReportTask
import org.gradle.api.plugins.cobertura.tasks.InstrumentCoberturaTask
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

class CoberturaPlugin implements Plugin<Project> {

    private static final String ANT_CONFIGURATION_NAME = 'cobertura'

    void apply(final Project project) {
        project.apply(plugin: ReportingBasePlugin)
        project.apply(plugin: JavaBasePlugin)

        def reportingExtension = project.extensions.getByType(ReportingExtension)
        def extension = project.extensions.create(CoberturaPluginExtension.NAME, CoberturaPluginExtension, project)

        // Base for all reports
        extension.conventionMapping.map("reportsDir") { reportingExtension.file("cobertura") }

        Configuration coberturaClasspath = project.configurations.add("cobertura")
        Dependency coberturaDependency = project.dependencies.create('net.sourceforge.cobertura:cobertura:1.9.4.1')
        coberturaClasspath.dependencies.add(coberturaDependency)
        extension.classpath = coberturaClasspath

        SourceSetContainer sourceSets = project.sourceSets
        sourceSets.all { SourceSet sourceSet ->
            addInstrumentation(project, extension, sourceSet)
        }

        // Check produces all cobertura reports
        project.tasks.getByName("check").dependsOn(project.tasks.withType(CoberturaReportTask))

        // Go ahead and wire the conventional test task up, if we are being used with the java plugin
        project.plugins.withType(JavaPlugin) {
            Test testTask = project.test
            SourceSet mainSourceSet = project.sourceSets.main
            extension.applyTo(testTask, mainSourceSet)
        }
    }

    private void configureDefaultDependencies(final Project project, final CoberturaPluginExtension extension) {
        project.dependencies {
            coberturaAnt "net.sourceforge.cobertura:cobertura:${extension.toolVersion}"
        }
    }

    /**
     * Applies cobertura coverage to Test tasks.
     * @param project the project with the tasks to configure
     * @param extension cobertura plugin extension
     */
    private void applyToDefaultTasks(final Project project, final CoberturaPluginExtension extension) {
        final SourceSet mainSourceSet = project.sourceSets.main
        extension.applyTo(project.tasks.withType(Test), mainSourceSet)
    }

    private void configureTaskClasspaths(final Project project) {
        project.tasks.withType(CoberturaBaseTask) {
            coberturaClasspath = project.configurations[ANT_CONFIGURATION_NAME]
        }
    }

    private void addInstrumentation(Project project, CoberturaPluginExtension projectExtension, SourceSet sourceSet) {
        // Extend the source set with cobertura stuff
        final CoberturaSourceSetExtension sourceSetExtension = sourceSet.extensions.create(CoberturaPluginExtension.NAME, CoberturaSourceSetExtension, sourceSet)
        final ConventionMapping sourceSetConventionMapping = sourceSetExtension.conventionMapping
        sourceSetConventionMapping.with {
            map("coberturaClasspath") { projectExtension.classpath }
            map("serFile") { project.file("$project.buildDir/cobertura/$sourceSet.name/cobertura.ser") }
            map("classesDir") { project.file("$project.buildDir/cobertura/$sourceSet.name/classes") }
            map("ignores") { projectExtension.getIgnores() }
        }

        // Create a task to instrument this source set, wired to the extension
        final InstrumentCoberturaTask task = project.tasks.add(sourceSet.getTaskName("coberturaInstrument", null), InstrumentCoberturaTask)
        task.group = "Verification"
        task.description = "Instruments classes for the '${sourceSet.name}' source set"
        task.source { sourceSet.output }
        task.conventionMapping.with {
            map("coberturaClasspath") { sourceSetExtension.getCoberturaClasspath() }
            map("serFile") { sourceSetExtension.getSerFile() }
            map("classesDir") { sourceSetExtension.getClassesDir() }
            map("ignores") { sourceSetExtension.getIgnores() }
            map("includes") { projectExtension.getIncludes() as Set }
            map("excludes") { projectExtension.getExcludes() as Set }
        }

        // wire the output of the task to the extension
        sourceSetConventionMapping.map("output") { task.getInstrumentedClassFiles() }
    }
}
