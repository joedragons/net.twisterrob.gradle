plugins {
	kotlin
	`java-library`
	`java-test-fixtures`
	id("java-gradle-plugin")
	id("net.twisterrob.gradle.build.publishing")
}

base.archivesName.set("twister-convention-base")
description = "Utilities: utility functions and classes to write convention plugins."

gradlePlugin {
	plugins {
		create("net.twisterrob.root") {
			id = "net.twisterrob.root"
			implementationClass = "net.twisterrob.gradle.root.RootPlugin"
		}
	}
}

dependencies {
	implementation(gradleApiWithoutKotlin())
	api(projects.common)
	compileOnly(libs.android.gradle)
	compileOnly(libs.annotations.jetbrains)
	implementation(projects.compat.agp)
	implementation(projects.compat.agp40x)
	implementation(projects.compat.agp41x)
	implementation(projects.compat.agp42x)
	implementation(projects.compat.agp70x)

	testImplementation(projects.test.internal)

	testFixturesImplementation(projects.compat.agp)
	testFixturesImplementation(projects.test.internal)
	testFixturesCompileOnly(projects.test)
	testFixturesApi(libs.dexMemberList)
	// GradleTestHelpersKt.hasDevices uses com.android.ddmlib.AndroidDebugBridge.
	testFixturesImplementation(libs.android.tools.ddmLib)
}
