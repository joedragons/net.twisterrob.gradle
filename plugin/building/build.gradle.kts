plugins {
	kotlin
	id("java-gradle-plugin")
	`java-test-fixtures`
	id("net.twisterrob.gradle.build.publishing")
}

base.archivesName.set("twister-convention-building")
description = "Build Convention Plugin: Gradle Plugin to handle conventional builds."

dependencies {
	implementation(gradleApiWithoutKotlin())
	api(projects.plugin.base)
	implementation(projects.plugin.versioning) // TODO decouple
	compileOnly(libs.android.gradle)
	compileOnly(libs.annotations.jetbrains)

	// This plugin is part of the net.twisterrob.android-app plugin, not designed to work on its own.
	runtimeOnly(projects.plugin)

	testImplementation(projects.test.internal)
	testImplementation(testFixtures(projects.plugin.base))
	// AndroidInstallRunnerTaskTest calls production code directly,
	// so need com.android.xml.AndroidXPathFactory for AndroidInstallRunnerTask.Companion.getMainActivity$plugin.
	testImplementation(libs.android.tools.common)
}

disableGradlePluginValidation()
