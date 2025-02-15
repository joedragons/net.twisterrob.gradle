package net.twisterrob.gradle.build.publishing

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.attributes.DocsType
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.internal.JvmPluginsHelper
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.support.serviceOf

/**
 * @param project [Project] this extension belongs to
 * @param artifactTask that produces the Documentation output files
 * @see org.gradle.api.plugins.internal.DefaultJavaPluginExtension.withJavadocJar
 */
fun JavaPluginExtension.withDokkaJar(project: Project, artifactTask: TaskProvider<out Task>) {
	JvmPluginsHelper.configureDocumentationVariantWithArtifact(
		JavaPlugin.JAVADOC_ELEMENTS_CONFIGURATION_NAME,
		null,
		DocsType.JAVADOC,
		emptyList(),
		sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).javadocJarTaskName,
		artifactTask,
		project.components["java"] as AdhocComponentWithVariants,
		project.configurations,
		project.tasks,
		project.objects,
		project.serviceOf(),
		project.serviceOf(),
	)
}
