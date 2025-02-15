import net.twisterrob.gradle.doNotNagAbout
import org.jetbrains.kotlin.utils.keysToMap
import java.time.Instant
import java.time.format.DateTimeFormatter

plugins {
	@Suppress("DSL_SCOPE_VIOLATION") // TODEL https://github.com/gradle/gradle/issues/22797
	alias(libs.plugins.nexus)
	id("net.twisterrob.gradle.build.module.root")
	id("idea")
}

val projectVersion: String by project

description = "Plugins for Gradle that support Android flavors."
allprojects {
	group = "net.twisterrob.gradle"
	version = projectVersion
}

resetGradleTestWorkerIdToDefault()

buildscript { enableDependencyLocking(project) }
allprojects { enableDependencyLocking() }

subprojects {
	// Extension with name 'libs' does not exist. Currently registered extension names: [ext, kotlin, kotlinTestRegistry, base, defaultArtifacts, sourceSets, reporting, java, javaToolchains, testing]
	// Needs to be called different from libs,
	// because com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.followElement
	// from idea-2021.1.3\plugins\android-gradle-dsl\lib\android-gradle-dsl-impl.jar
	// runs into an infinite loop on it.
	// TODEL Affects anything with Android Plugin < 2020.3.1 (i.e. AS 4.x, and IJ <2021.3)
	val deps = rootProject.libs
	replaceGradlePluginAutoDependenciesWithoutKotlin()

	configurations.all {
		replaceHamcrestDependencies(project)
	}

	tasks.withType<JavaCompile>().configureEach {
		options.compilerArgs.addAll(
			listOf(
				"-Werror", // fail on warnings
				"-Xlint:all", // enable all possible checks
				"-Xlint:-processing" // except "No processor claimed any of these annotations"
			)
		)
	}

	tasks.withType<GroovyCompile>().configureEach {
		options.compilerArgs.addAll(
			listOf(
				"-Werror", // fail on warnings
				"-Xlint:all" // enable all possible checks
			)
		)
		groovyOptions.configurationScript = rootProject.file("gradle/compileGroovy.groovy")
		// enable Java 7 invokeDynamic, since Java target is > 7 (Android requires Java 8 at least)
		// no need for groovy-all:ver-indy, because the classpath is provided from hosting Gradle project
		groovyOptions.optimizationOptions!!["indy"] = true
	}

	tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
		kotlinOptions.verbose = true
		kotlinOptions.languageVersion = deps.versions.kotlin.language.get()
		kotlinOptions.apiVersion = deps.versions.kotlin.language.get()
		kotlinOptions.jvmTarget = deps.versions.java.get()
		kotlinOptions.suppressWarnings = false
		kotlinOptions.allWarningsAsErrors = true
		kotlinOptions.freeCompilerArgs += listOf(
			// Caused by: java.lang.NoSuchMethodError: kotlin.jvm.internal.FunctionReferenceImpl.<init>(ILjava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;I)V
			//	at net.twisterrob.gradle.common.BaseQualityPlugin$apply$1$1.<init>(BaseQualityPlugin.kt)
			//	at net.twisterrob.gradle.common.BaseQualityPlugin$apply$1.execute(BaseQualityPlugin.kt:24)
			//	at net.twisterrob.gradle.common.BaseQualityPlugin$apply$1.execute(BaseQualityPlugin.kt:8)
			// https://youtrack.jetbrains.com/issue/KT-41852#focus=Comments-27-4604992.0-0
			"-Xno-optimized-callable-references",
			"-opt-in=kotlin.RequiresOptIn",
		)
		if (kotlinOptions.languageVersion == "1.4") {
			// Suppress "Language version 1.4 is deprecated and its support will be removed in a future version of Kotlin".
			kotlinOptions.freeCompilerArgs += "-Xsuppress-version-warnings"
		} else {
			TODO("Remove -Xsuppress-version-warnings")
		}
	}

	tasks.withType<Test>().configureEach {
		useJUnitPlatform()

		val propertyNamesToExposeToJUnitTests = listOf(
			// for GradleRunnerRule to use a different Gradle version for tests
			"net.twisterrob.gradle.runner.gradleVersion",
			// for tests to decide dynamically
			"net.twisterrob.test.gradle.javaHomeEnv",
			"net.twisterrob.test.android.pluginVersion",
			"net.twisterrob.test.kotlin.pluginVersion",
			"net.twisterrob.test.android.compileSdkVersion",
			// So that command line gradlew -P...=false works.
			// Will override earlier jvmArgs, if both specified.
			"net.twisterrob.gradle.runner.clearAfterSuccess",
			"net.twisterrob.gradle.runner.clearAfterFailure",
		)
		val properties = propertyNamesToExposeToJUnitTests
			.keysToMap { project.property(it) }
			.toMutableMap()
		if (System.getProperties().containsKey("idea.paths.selector")) {
			logger.debug("Keeping folder contents after test run from IDEA")
			// see net.twisterrob.gradle.test.GradleRunnerRule
			properties["net.twisterrob.gradle.runner.clearAfterSuccess"] = "false"
			properties["net.twisterrob.gradle.runner.clearAfterFailure"] = "false"
		}
		properties.forEach { (name, value) -> inputs.property(name, value) }
		properties.forEach { (name, value) -> value?.let { jvmArgs("-D${name}=${value}") } }
	}

	tasks.withType<ProcessResources>().configureEach {
		val propertyNamesToReplace = listOf(
			"net.twisterrob.test.android.pluginVersion",
			"net.twisterrob.test.kotlin.pluginVersion",
			"net.twisterrob.test.android.compileSdkVersion"
		)
		val properties = propertyNamesToReplace.keysToMap { project.property(it) }
		properties.forEach { (name, value) -> inputs.property(name, value) }
		filesMatching(listOf("**/build.gradle", "**/settings.gradle")) {
			val replacements = properties + mapOf(
				// custom replacements (`"name" to value`) would come here
			)
			filter(mapOf("tokens" to replacements), org.apache.tools.ant.filters.ReplaceTokens::class.java)
		}
	}

	plugins.withId("kotlin") {
		dependencies {
			// Make sure we don't have many versions of Kotlin lying around.
			add("compileOnly", enforcedPlatform(deps.kotlin.bom))
			add("testCompileOnly", enforcedPlatform(deps.kotlin.bom))
			plugins.withId("org.gradle.java-test-fixtures") {
				add("testFixturesCompileOnly", enforcedPlatform(deps.kotlin.bom))
			}

			add("compileOnly", deps.kotlin.dsl) {
				isTransitive = false // make sure to not pull in kotlin-compiler-embeddable
			}
			add("api", deps.kotlin.stdlib)
			add("api", deps.kotlin.stdlib.jdk8)
			add("api", deps.kotlin.reflect)

			add("testImplementation", deps.kotlin.test)
		}
	}

	plugins.withId("java") {
		val java = extensions.getByName<JavaPluginExtension>("java")
		java.sourceCompatibility = JavaVersion.toVersion(deps.versions.java.get())
		java.targetCompatibility = JavaVersion.toVersion(deps.versions.java.get())
		tasks.named<Test>("test") { testLogging.events("passed", "skipped", "failed") }
		afterEvaluate {
			// Delayed configuration, so that project.* is set up properly in corresponding modules' build.gradle.kts.
			tasks.named<Jar>("jar") {
				manifest {
					attributes(
						mapOf(
							// Implementation-* used by TestPlugin
							"Implementation-Vendor" to project.group,
							"Implementation-Title" to project.base.archivesName.get(),
							"Implementation-Version" to project.version,
							"Built-Date" to DateTimeFormatter.ISO_INSTANT.format(Instant.now())
						)
					)
				}
			}
		}
	}

	normalization {
		runtimeClasspath {
			metaInf {
				ignoreAttribute("Built-Date")
			}
		}
	}

	if (project.property("net.twisterrob.gradle.build.verboseReports").toString().toBoolean()) {
		tasks.withType<Test>().configureEach {
			configureVerboseReportsForGithubActions()
		}
	}
}
if (project.property("net.twisterrob.gradle.build.includeExamples").toString().toBoolean()) {
	tasks.register("assembleExamples") {
		dependsOn(gradle.includedBuilds.map { it.task(":assemble") })
	}
	tasks.register("checkExamples") {
		dependsOn(gradle.includedBuilds.map { it.task(":check") })
	}
}

tasks.register("check") {
	description = "Delegate task for checking included builds too."
	dependsOn(gradle.includedBuild("plugins").task(":check"))
}

project.tasks.register<TestReport>("testReport") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Run and report on all tests in the project. Add `-x test` to just generate report."
	destinationDirectory.set(file("${buildDir}/reports/tests/all"))

	val tests = subprojects
		.flatMap { it.tasks.withType(Test::class) } // Forces to create the tasks.
		.onEach { it.ignoreFailures = true } // Let the tests finish, to get a final "all" report.
	// Detach (.get()) the result directories,
	// simply using reportOn(tests) or the binaryResultsDirectory providers, task dependencies would be created.
	testResults.from(tests.map { it.binaryResultsDirectory.get() })
	// Force executing tests (if they're in the task graph), before reporting on them.
	mustRunAfter(tests)

	doLast {
		val reportFile = destinationDirectory.file("index.html").get().asFile
		val failureRegex = Regex("""(?s).*<div class="infoBox" id="failures">\s*<div class="counter">(\d+)<\/div>.*""")
		val failureMatch = failureRegex.matchEntire(reportFile.readText())
		val reportPath = reportFile.toURI().toString().replace("file:/([A-Z])".toRegex(), "file:///\$1")
		if (failureMatch == null) {
			throw GradleException("Cannot determine if the tests failed. See the report at: ${reportPath}")
		} else {
			val failCount = failureMatch.groups[1]!!.value
			if (failCount != "0") {
				throw GradleException("There were ${failCount} failing tests. See the report at: ${reportPath}")
			}
		}
	}
}

// To get gradle/dependency-locks run `gradlew :allDependencies --write-locks`.
project.tasks.register<Task>("allDependencies") {
	val projects = project.allprojects.sortedBy { it.name }
	doFirst {
		println(projects.joinToString(prefix = "Printing dependencies for modules:\n", separator = "\n") { " * ${it}" })
	}
	val dependenciesTasks = projects.map { it.tasks.named("dependencies") }
	// Builds a dependency chain: 1 <- 2 <- 3 <- 4, so when executed they're in order.
	dependenciesTasks.reduce { acc, task -> task.apply { get().dependsOn(acc) } }
	// Use finalizedBy instead of dependsOn to make sure this task executes first.
	this@register.finalizedBy(dependenciesTasks)
}

project.tasks.register<Delete>("cleanDebug") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Clean outputs generated by debug projects."
	// Hacky version of the following with intuitive results (i.e. the folders are also deleted):
	// ```
	// delete(fileTree(rootProject.file("docs/debug")) {
	//     include("*/.gradle")
	//     include("*/build")
	//     include("*/buildSrc/.gradle")
	//     include("*/buildSrc/build")
	// })
	// ```
	// See https://github.com/gradle/gradle/issues/14152#issuecomment-953610543.
	fileTree(rootProject.file("docs/debug")) {
		include("*/.gradle")
		include("*/buildSrc/.gradle")
	}.visit { if (name == ".gradle") delete(file) }
	fileTree(rootProject.file("docs/debug")) {
		include("*/build")
		include("*/buildSrc/build")
	}.visit { if (name == "build") delete(file) }
}

nexusPublishing {
	repositories {
		sonatype {
			// For :publish...PublicationToSonatypeRepository, projectVersion suffix chooses repo.
			nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
			snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))

			// For :closeAndReleaseSonatypeStagingRepository
			// Set via -PsonatypeStagingProfileId to gradlew, or ORG_GRADLE_PROJECT_sonatypeStagingProfileId env var.
			val sonatypeStagingProfileId: String? by project
			stagingProfileId.set(sonatypeStagingProfileId)

			val sonatypeUsername: String? by project
			val sonatypePassword: String? by project
			require((sonatypeUsername == null) == (sonatypePassword == null)) {
				// Explicit check for existence of both, because otherwise it just fails with a misleading error:
				// > Execution failed for task ':initializeSonatypeStagingRepository'.
				// > > Failed to load staging profiles, server at ${nexusUrl} responded with status code 401, body:
				"Missing username (${sonatypeUsername == null}) or password (${sonatypePassword == null})."
			}
			// For everything sonatype, but automatically done by the plugin.
			// Set via -PsonatypeUsername to gradlew, or ORG_GRADLE_PROJECT_sonatypeUsername env var.
			//username.set(sonatypeUsername)
			// Set via -PsonatypePassword to gradlew, or ORG_GRADLE_PROJECT_sonatypePassword env var.
			//password.set(sonatypePassword)
		}
	}
}

idea {
	module {
		val excludes = listOf(
			"docs/examples/local/.gradle",
			"docs/examples/local/build",
			"docs/examples/release/.gradle",
			"docs/examples/release/build",
			"docs/examples/snapshot/.gradle",
			"docs/examples/snapshot/build",
		) + allprojects.map { it.projectDir.relativeTo(rootDir).resolve("build/unPackagedTestResources").toString() } 
		excludeDirs.addAll(excludes.map { rootDir.resolve(it) })
	}
}

val gradleVersion: String = GradleVersion.current().version

// TODEL Gradle sync in IDEA 2022.3.1: https://youtrack.jetbrains.com/issue/IDEA-306975
@Suppress("MaxLineLength")
doNotNagAbout(
	"The AbstractArchiveTask.archivePath property has been deprecated. " +
			"This is scheduled to be removed in Gradle 9.0. " +
			"Please use the archiveFile property instead. " +
			"See https://docs.gradle.org/${gradleVersion}/dsl/org.gradle.api.tasks.bundling.AbstractArchiveTask.html#org.gradle.api.tasks.bundling.AbstractArchiveTask:archivePath for more details.",
	"at org.jetbrains.plugins.gradle.tooling.builder.ExternalProjectBuilderImpl\$_getSourceSets_closure"
)
// TODEL Gradle sync in IDEA 2022.3.1: https://youtrack.jetbrains.com/issue/IDEA-306975
@Suppress("MaxLineLength")
doNotNagAbout(
	"The AbstractArchiveTask.archivePath property has been deprecated. " +
			"This is scheduled to be removed in Gradle 9.0. " +
			"Please use the archiveFile property instead. " +
			"See https://docs.gradle.org/${gradleVersion}/dsl/org.gradle.api.tasks.bundling.AbstractArchiveTask.html#org.gradle.api.tasks.bundling.AbstractArchiveTask:archivePath for more details.",
	"at org.jetbrains.plugins.gradle.tooling.util.SourceSetCachedFinder.createArtifactsMap"
)
