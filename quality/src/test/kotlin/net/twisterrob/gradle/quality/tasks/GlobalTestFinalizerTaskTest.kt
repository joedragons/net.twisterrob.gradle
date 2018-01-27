package net.twisterrob.gradle.quality.tasks

import net.twisterrob.gradle.test.GradleRunnerRule
import net.twisterrob.gradle.test.assertHasOutputLine
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GlobalTestFinalizerTaskTest {

	@Rule @JvmField val gradle = GradleRunnerRule()

	@Test fun `gathers results from root app`() {
		`given`@
		@Language("java")
		val testFile = """
			import org.junit.*;

			@SuppressWarnings("NewMethodNamingConvention")
			public class Tests {
				@Test public void success1() {}
				@Test public void success2() {}
				@Test public void success3() {}

				@Test(expected = RuntimeException.class) public void fail1() {}
				@Test public void fail2() { Assert.fail("failure message"); }

				@Test @Ignore public void ignored1() { Assert.fail("should not be executed"); }
				@Test public void ignored2() { Assume.assumeTrue(false); }
			}
		""".trimIndent()
		gradle.file(testFile, "src", "test", "java", "Tests.java")

		@Language("gradle")
		val script = """
			dependencies {
				testImplementation 'junit:junit:4.12'
			}
			task('tests', type: ${GlobalTestFinalizerTask::class.java.name})
		""".trimIndent()

		val result: BuildResult
		`when`@
		result = gradle
				.basedOn("android-root_app")
				.run(script, "test", "tests")
				.buildAndFail()

		`then`@
		assertEquals(TaskOutcome.SUCCESS, result.task(":test")!!.outcome)
		assertEquals(TaskOutcome.FAILED, result.task(":tests")!!.outcome)
		result.assertHasOutputLine("> There were ${2 + 2} failing tests. See the report at: .*".toRegex())
	}
}
