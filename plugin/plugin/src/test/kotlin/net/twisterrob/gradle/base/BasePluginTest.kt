package net.twisterrob.gradle.base

import org.gradle.api.ProjectConfigurationException
import org.gradle.util.GradleVersion
import org.junit.Test
import kotlin.test.assertFailsWith

// TODO Parameterize
class BasePluginTest {

	@Test fun `fails with very old version`() {
		assertFailsWith<ProjectConfigurationException> {
			BasePlugin.checkGradleVersion(GradleVersion.version("3.5.1"))
		}
	}

	@Test fun `allows newer version`() {
		BasePlugin.checkGradleVersion(GradleVersion.version("5.0"))
	}

	@Test fun `fails for incompatible version`() {
		assertFailsWith<ProjectConfigurationException> {
			BasePlugin.checkGradleVersion(GradleVersion.version("4.0"))
		}
	}

	@Test fun `passes compatible version`() {
		BasePlugin.checkGradleVersion(GradleVersion.version("4.1"))
	}

	@Test fun `passes compatible patched version`() {
		BasePlugin.checkGradleVersion(GradleVersion.version("4.5.1"))
	}

	@Test fun `passes new compatible version`() {
		BasePlugin.checkGradleVersion(GradleVersion.version("4.6"))
	}
}
