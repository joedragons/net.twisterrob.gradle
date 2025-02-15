plugins {
	id("net.twisterrob.gradle.build.module.gradle-plugin")
	id("net.twisterrob.gradle.build.publish")
	id("org.gradle.java-test-fixtures")
}

base.archivesName.set("twister-convention-reporting")
description = "Reporting Convention Plugin: Gradle tasks to support reporting."

gradlePlugin {
	disableGradlePluginValidation()
	plugins {
		// Not created, internal plugins only used by class reference.
	}
}

dependencies {
	implementation(gradleApiWithoutKotlin())
	api(projects.plugin.base)
	implementation(projects.compat.agp)
	compileOnly(libs.android.gradle)
	// Need com.android.utils.FileUtils for TestReportGenerator.generate().
	compileOnly(libs.android.tools.common)

	// This plugin is part of the net.twisterrob.gradle.plugin.android-app plugin, not designed to work on its own.
	runtimeOnly(projects.plugin)

	testImplementation(projects.test.internal)
	testImplementation(testFixtures(projects.plugin.base))
}
