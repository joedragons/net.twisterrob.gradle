package net.twisterrob.gradle.test

import org.gradle.api.internal.tasks.testing.worker.TestWorker
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.gradle.util.GradleVersion
import org.intellij.lang.annotations.Language
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.net.URI

/**
 * Simplified [org.junit.Rule] around [GradleRunner] to reduce code repetition.
 */
@Suppress("BooleanPropertyNaming") // These are clear names.
open class GradleRunnerRule : TestRule {

	private val temp = TemporaryFolder()

	/**
	 * Defines if a tests Gradle folder should be removed after a failed execution.
	 * Precedence:
	 *  * this property (`null` &rarr; next)
	 *  * `net.twisterrob.gradle.runner.clearAfterFailure` system property (not set &rarr; next)
	 *  * automatically clear (when none of the above are specified)
	 *
	 * @param value `null` = automatic, `true` clean, `false` keep
	 * @see GradleRunner.getProjectDir `runner.projectDir`
	 */
	@Suppress("KDocUnresolvedReference")
	var clearAfterFailure: Boolean? = null
	private val needClearAfterFailure: Boolean
		get() = listOfNotNull(
			clearAfterFailure,
			System.getProperty("net.twisterrob.gradle.runner.clearAfterFailure")?.toBoolean(),
			true
		).first()

	/**
	 * Defines if a tests Gradle folder should be removed after a failed execution.
	 * Precedence:
	 *  * this property (`null` &rarr; next)
	 *  * `net.twisterrob.gradle.runner.clearAfterFailure` system property (not set &rarr; next)
	 *  * automatically clear (when none of the above are specified)
	 *
	 * @param value `null` = automatic, `true` clean, `false` keep
	 * @see GradleRunner.getProjectDir `runner.projectDir`
	 */
	@Suppress("KDocUnresolvedReference")
	var clearAfterSuccess: Boolean? = null
	private val needClearAfterSuccess: Boolean
		get() = listOfNotNull(
			clearAfterSuccess,
			System.getProperty("net.twisterrob.gradle.runner.clearAfterSuccess")?.toBoolean(),
			true
		).first()

	lateinit var runner: GradleRunner private set

	//@Test:given/@Before
	var gradleVersion: GradleVersion = GradleVersion.current()
		set(value) {
			val distributionUrl =
				URI.create("https://services.gradle.org/distributions/gradle-${value.version}-all.zip")
			runner
				.withGradleVersion(value.version)
				.withGradleDistribution(distributionUrl)
			field = value
		}

	var javaHome = File(System.getProperty("java.home"))
		set(value) {
			setJavaHome(value)
			field = value
		}

	@Suppress("MemberVisibilityCanBePrivate") // API
	lateinit var buildFile: File
		private set

	val settingsFile: File
		get() = File(runner.projectDir, "settings.gradle")

	val propertiesFile: File
		get() = File(runner.projectDir, "gradle.properties")

	open val extraArgs: Array<String> = arrayOf("--stacktrace")

	//region TestRule
	override fun apply(base: Statement, description: Description): Statement {
		return object : Statement() {

			override fun evaluate() {
				var success = false
				try {
					before()
					base.evaluate()
					success = true
				} finally {
					after(success)
				}
			}
		}
	}

	internal fun before() {
		temp.create()
		setUp()
	}

	internal fun after(success: Boolean) {
		tearDown()
		@Suppress("ComplexCondition")
		if ((success && needClearAfterSuccess) || (!success && needClearAfterFailure)) {
			temp.delete()
		}
	}

	//endregion

	//region GradleRunner wrapper
	//@Before(automatic with @Rule)
	protected open fun setUp() {
		buildFile = temp.newFile("build.gradle")
		runner = GradleRunner
			.create()
			//.forwardOutput() // need to customize forwarding because of test output
			.forwardStdOutput(WriteOnlyWhenLineCompleteWriter(System.out.writer()))
			.forwardStdError(WriteOnlyWhenLineCompleteWriter(System.err.writer()))
			.withProjectDir(temp.root)
			.withPluginClasspath()
		check(buildFile == File(runner.projectDir, "build.gradle")) {
			"${buildFile} is not within ${runner.projectDir}."
		}
		fixClassPath(runner)
		System.getProperty("net.twisterrob.gradle.runner.gradleVersion", null)?.let {
			gradleVersion = GradleVersion.version(it)
		}
	}

	protected open fun tearDown() {
		// not used yet, but useful for debugging
	}

	//@Test:when
	fun run(@Language("gradle") script: String?, vararg tasks: String): GradleRunner {
		if (script != null) {
			buildFile.appendText(script)
		}
		val args = arrayOf(*tasks, *extraArgs)
		val gradleTestWorkerId: String? by systemProperty(TestWorker.WORKER_ID_SYS_PROPERTY)
		val testKitDir = (runner as? DefaultGradleRunner)?.testKitDirProvider?.dir
		val javaVendor: String? by systemProperty("java.vendor")
		val javaVersion: String? by systemProperty("java.version")
		val javaVersionDate: String? by systemProperty("java.version.date")
		val javaRuntimeName: String? by systemProperty("java.runtime.name")
		val javaRuntimeVersion: String? by systemProperty("java.runtime.version")
		val javaHome: String? by systemProperty("java.home")
		@Suppress("NullableToStringCall") // Debug info, null is OK.
		val java = "${javaVendor} ${javaRuntimeName} ${javaVersion} (${javaRuntimeVersion} ${javaVersionDate})"
		@Suppress("ForbiddenMethodCall") // TODO abstract logging.
		println(
			@Suppress("MultilineRawStringIndentation") """
				Test Java: ${java} from ${javaHome}.
				Requesting ${gradleVersion} in worker #${gradleTestWorkerId} at ${testKitDir?.absolutePath}.
				Gradle properties:
				```properties
${propertiesContentForLogging().prependIndent("\t\t\t\t")}
				```
				Running `gradle ${args.joinToString(" ")}`
				on ${buildFile.absolutePath}:
				```gradle
${buildContentForLogging().prependIndent("\t\t\t\t")}
				```
				Execution output:
			""".trimIndent()
		)
		return runner.withArguments(*args)
	}


	private fun buildContentForLogging(): String =
		buildFile.readText()

	private fun propertiesContentForLogging(): String =
		if (propertiesFile.exists()) {
			propertiesFile.readText()
		} else {
			"${propertiesFile.name} does not exist."
		}

	/**
	 * This is a workaround because runner.withPluginClasspath() doesn't seem to work.
	 */
	private fun fixClassPath(runner: GradleRunner) {
		val classPaths = runner
			.pluginClasspath
			.joinToString(System.lineSeparator()) {
				"classpath files('${it.absolutePath.replace("\\", "\\\\")}')"
			}
		@Language("gradle")
		val buildscript = @Suppress("MultilineRawStringIndentation") """
			buildscript {
				dependencies {
${classPaths.prependIndent("\t\t\t\t\t")}
				}
			}
		""".trimIndent() + System.lineSeparator()
		buildFile.appendText(buildscript)
	}
	//endregion

	//region Helper methods

	//@Test:given/@Before
	@Deprecated(
		message = "Use gradleVersion property instead.",
		replaceWith = ReplaceWith("gradleVersion = GradleVersion.version(version)")
	)
	fun setGradleVersion(version: String) {
		gradleVersion = GradleVersion.version(version)
	}

	//@Test:given/@Before
	fun file(contents: String, path: String) {
		file(contents, path.split("""[/\\]""".toRegex()))
	}

	//@Test:given/@Before
	fun file(contents: String, path: Collection<String>) {
		file(contents, *path.toTypedArray())
	}

	fun file(contents: String, vararg path: String) {
		getFile(*path).appendText(contents)
	}

	@Suppress("ReturnCount")
	private fun getFile(vararg path: String): File {
		if (path.size == 1 && path[0] == "build.gradle") {
			return buildFile
		}
		if (path.size == 1 && path[0] == "gradle.properties") {
			return propertiesFile
		}
		if (1 < path.size) {
			val folders = path.sliceArray(0..path.size - 2)
			if (!File(temp.root, folders.joinToString(File.separator)).exists()) {
				temp.newFolder(*folders)
			}
		}
		val existing = temp.root.resolve(path.joinToString(File.separator))
		if (existing.exists()) {
			return existing
		}
		return temp.newFile(path.joinToString(File.separator))
	}

	//@Test:given/@Before
	@JvmOverloads
	fun basedOn(folder: String, relativeTo: Any? = null): GradleRunnerRule =
		@Suppress("DEPRECATION")
		basedOn(templateFile(folder, relativeTo))

	@JvmOverloads
	@Deprecated("This method does not belong on the Gradle rule, use some other utility to load from resources.")
	fun templateFile(path: String, relativeTo: Any? = null): File {
		val container = when (relativeTo) {
			is String -> relativeTo
			null -> ""
			else -> "/${relativeTo.javaClass.`package`.name}"
		}
		val resource = this.javaClass.getResource("${container}/${path}")
			?: throw IllegalArgumentException("Cannot find ${path} relative to {${relativeTo ?: "nothing (i.e. absolute)"}}")
		return File(resource.file)
	}

	//@Test:given/@Before
	fun basedOn(folder: File): GradleRunnerRule {
		@Suppress("ForbiddenMethodCall") // TODO abstract logging.
		println("Deploying ${folder} into ${temp.root}")
		folder.copyRecursively(temp.root, overwrite = false) onError@{ file, ex ->
			if (file == buildFile && ex is FileAlreadyExistsException) {
				val originalBuildFile = buildFile.readText()
				val newBuildFile = folder.resolve(buildFile.name).readText()
				buildFile.writeText(originalBuildFile + newBuildFile)
				return@onError OnErrorAction.SKIP
			}
			throw ex
		}
		return this
	}
	//endregion
}
