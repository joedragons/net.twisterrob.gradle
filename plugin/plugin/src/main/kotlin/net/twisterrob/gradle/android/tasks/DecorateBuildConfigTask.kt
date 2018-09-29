package net.twisterrob.gradle.android.tasks

import com.android.build.gradle.BaseExtension
import net.twisterrob.gradle.vcs.VCSPluginExtension
import org.codehaus.groovy.runtime.DateGroovyMethods
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByName
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit

private val DAY = TimeUnit.DAYS.toMillis(1)

open class DecorateBuildConfigTask : DefaultTask() {

	private val buildConfigField: (type: String, name: String, value: String) -> Unit

	var enableVCS: Boolean = true
	var enableBuild: Boolean = true

	/**
	 * Default implementation returns a one-day precise time
	 * to minimize `compile*JavaWithJavac` rebuilds due to a single number change in BuildConfig.java.
	 *
	 * It can be overridden like this:
	 * `tasks.decorateBuildConfig.configure { getBuildTime = { System.currentTimeMillies() }}`
	 *
	 * @returns a long representing the UTC time of the build.
	 */
	var getBuildTime = { System.currentTimeMillis() / DAY * DAY }

	init {
		val android: BaseExtension = project.extensions.getByName<BaseExtension>("android")
		buildConfigField = android.defaultConfig::buildConfigField
	}

	@TaskAction
	fun addVCSInformation() {
		if (enableVCS) {
			val vcs = project.extensions.getByName<VCSPluginExtension>("VCS")
			buildConfigField("String", "REVISION", "\"${vcs.current.revision}\"")
			buildConfigField("int", "REVISION_NUMBER", "${vcs.current.revisionNumber}")
		}
	}

	@TaskAction
	fun addBuildInfo() {
		if (enableBuild) {
			val buildTime = getBuildTime()
			buildConfigField(
				"java.util.Date",
				"BUILD_TIME",
				"new java.util.Date(${buildTime}L) /* ${dateFormat(buildTime)} */"
			)
		}
	}

	private fun dateFormat(date: Long): String =
		DateGroovyMethods.format(Date(date), "yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"))
}
