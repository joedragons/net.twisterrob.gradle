package net.twisterrob.gradle.android

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.Variant
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.BaseExtension
import com.android.builder.model.BuildType
import net.twisterrob.gradle.common.BasePlugin
import net.twisterrob.gradle.internal.android.unwrapCast
import net.twisterrob.gradle.kotlin.dsl.extensions
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

class AndroidReleasePlugin : BasePlugin() {

	override fun apply(target: Project) {
		super.apply(target)
		val android = project.extensions.getByName<BaseExtension>("android")
		val releaseEachTask = registerReleaseEachTask()
		android.buildTypes.configureEach { buildType ->
			val releaseBuildTypeTask = registerReleaseTasks(android, buildType)
			releaseEachTask.configure { it.dependsOn(releaseBuildTypeTask) }
		}
	}

	private fun registerReleaseEachTask(): TaskProvider<Task> =
		project.tasks.register<Task>("release") {
			group = org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
			description = "Calls each release task for all build types"
		}

	private fun registerReleaseTasks(android: BaseExtension, buildType: BuildType): TaskProvider<Task> {
		val releaseBuildTypeTask = project.tasks.register<Task>("releaseAll${buildType.name.capitalize()}") {
			group = org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
			description = "Assembles and archives all ${buildType.name} builds"
		}
		LOG.debug("Creating tasks for {}", buildType.name)

		val version = android.defaultConfig.extensions.getByType<AndroidVersionExtension>()
		val withBuildType = project.androidComponents.selector().withBuildType(buildType.name)
		project.androidComponents.onVariants(withBuildType) { variant ->
			val appVariant = variant.unwrapCast<Variant, ApplicationVariant>()
			val releaseVariantTask = registerReleaseTask(version, appVariant)
			releaseBuildTypeTask.configure { it.dependsOn(releaseVariantTask) }
		}
		return releaseBuildTypeTask
	}

	private fun registerReleaseTask(
		version: AndroidVersionExtension,
		variant: ApplicationVariant
	): TaskProvider<Zip> =
		project.tasks.register<Zip>("release${variant.name.capitalize()}") {
			group = org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
			description = "Assembles and archives apk and its ProGuard mapping for ${variant.name} build"
			destinationDirectory.fileProvider(project.provider { defaultReleaseDir.resolve(DEFAULT_DIR) })
			val out = variant.outputs.single()
			inputs.property("variant-applicationId", variant.applicationId)
			inputs.property("variant-versionName", out.versionName)
			inputs.property("variant-versionCode", out.versionCode)

			archiveFileName.set(project.provider {
				@Suppress("UNNECESSARY_NOT_NULL_ASSERTION", "UnsafeCallOnNullableType")
				val versionCode = out.versionCode.get()!!.toLong()
				val versionName = out.versionName.get()
				val applicationId = variant.applicationId.get()
				version.formatArtifactName(project, "archive", applicationId, versionCode, versionName) + ".zip"
			})

			from(variant.artifacts.get(SingleArtifact.APK)) { copy ->
				copy.exclude(BuiltArtifactsImpl.METADATA_FILE_NAME)
			}

			if (variant.isMinifyEnabledCompat) {
				val mappingFileProvider = variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
				archiveMappingFile(mappingFileProvider.map { it.asFile })
			}

			variant.androidTestCompat?.let { androidTest ->
				from(androidTest.artifacts.get(SingleArtifact.APK)) { copy ->
					copy.exclude(BuiltArtifactsImpl.METADATA_FILE_NAME)
				}
			}

			doFirst(closureOf<Zip> { failIfAlreadyArchived() })
			doLast(closureOf<Zip> { printResultingArchive() })
		}

	companion object {

		private const val DEFAULT_DIR = "android"

		private val defaultReleaseDir: File
			get() {
				val envVarName = "RELEASE_HOME" // TODO bad for configuration cache.
				val releaseHome = checkNotNull(System.getenv(envVarName)) {
					"Please set ${envVarName} environment variable to an existing directory."
				}
				return File(releaseHome).also { releaseDir ->
					check(releaseDir.exists() && releaseDir.isDirectory) {
						"Please set ${envVarName} environment variable to an existing directory."
					}
				}
			}

	}
}

private fun Zip.archiveMappingFile(mappingFileProvider: Provider<File>) {
	from(mappingFileProvider.map { it.parentFile }) { copy ->
		copy.include("*")
		copy.rename("(.*)", "proguard_$1")
	}
}

private fun Zip.failIfAlreadyArchived() {
	val outFile = outputs.files.singleFile
	if (outFile.exists()) {
		throw IOException("Target zip file already exists.\nRelease archive: ${outFile}")
	}
}

private fun Zip.printResultingArchive() {
	logger.quiet("Published release artifacts to ${outputs.files.singleFile}:" + ZipFile(outputs.files.singleFile)
		.entries()
		.toList()
		.sortedBy { it.name }
		.joinToString(prefix = "\n", separator = "\n") { "\t * ${it}" }
	)
}
