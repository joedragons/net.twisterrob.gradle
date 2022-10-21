package net.twisterrob.gradle.common

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.ParameterizedTest.INDEX_PLACEHOLDER
import org.junit.jupiter.params.provider.CsvSource
import org.junitpioneer.jupiter.ClearSystemProperty
import org.junitpioneer.jupiter.SetSystemProperty
import kotlin.reflect.full.declaredMembers

class AGPVersionsTest {

	@Test fun `olderThan4NotSupported returns the right message`() {
		val version = AGPVersion(1, 2, AGPVersion.ReleaseType.Stable, 4)

		val ex = assertThrows<IllegalStateException> {
			AGPVersions.olderThan4NotSupported(version)
		}

		assertEquals("AGP 1.2.Stable.4 is not supported, because it's older than 4.*.*.*", ex.message)
	}

	@Test fun `olderThan7NotSupported returns the right message`() {
		val version = AGPVersion(1, 2, AGPVersion.ReleaseType.Stable, 4)

		val ex = assertThrows<IllegalStateException> {
			AGPVersions.olderThan7NotSupported(version)
		}

		assertEquals("AGP 1.2.Stable.4 is not supported, because it's older than 7.*.*.*", ex.message)
	}

	@Test fun `otherThan4NotSupported returns the right message`() {
		val version = AGPVersion(1, 2, AGPVersion.ReleaseType.Stable, 4)

		val ex = assertThrows<IllegalStateException> {
			AGPVersions.otherThan4NotSupported(version)
		}

		assertEquals("AGP 1.2.Stable.4 is not supported, because it's not compatible with 4.*.*.*", ex.message)
	}

	@Test fun `CLASSPATH version is what the project is compiled with`() {
		// This is not using AGPVersion() because Renovate needs to update this one. See "Update AGP version test.".
		val expected = AGPVersion.parse("7.3.0")

		val actual = AGPVersions.CLASSPATH

		assertEquals(expected, actual)
	}

	@SetSystemProperty(key = "net.twisterrob.test.android.pluginVersion", value = "1.2.3")
	@Test fun `UNDER_TEST reads system property`() {
		val expected = AGPVersion(1, 2, AGPVersion.ReleaseType.Stable, 3)

		val actual = AGPVersions.UNDER_TEST

		assertEquals(expected, actual)
	}

	@SetSystemProperty(key = "net.twisterrob.test.android.pluginVersion", value = "x.y.z")
	@Test fun `UNDER_TEST fails when system property invalid`() {
		val ex = assertThrows<IllegalStateException> {
			AGPVersions.UNDER_TEST
		}

		assertThat(ex.message, containsString("x.y.z"))
	}

	@ClearSystemProperty(key = "net.twisterrob.test.android.pluginVersion")
	@Test fun `UNDER_TEST fails when system property missing`() {
		val ex = assertThrows<IllegalStateException> {
			AGPVersions.UNDER_TEST
		}

		assertThat(ex.message, containsString("net.twisterrob.test.android.pluginVersion"))
	}

	@CsvSource(
		"3, 2",
		"3, 3",
		"3, 4",
		"3, 6",
		"4, ",
		"4, 0",
		"4, 1",
		"4, 2",
		"7, ",
		"7, 0",
		"7, 1",
		"7, 4",
	)
	@ParameterizedTest(name = "[$INDEX_PLACEHOLDER] v{0}.{1}.x")
	fun `vXXX constants have the right version`(major: Int, minor: Int?) {
		val name = "v${major}${minor ?: 'x'}x"
		val member = AGPVersions::class.declaredMembers.singleOrNull { it.name == name }
			?: fail("Cannot find $name field")

		val actual = member.call(AGPVersions)

		assertEquals(AGPVersion(major, minor, null, null), actual)
	}
}
