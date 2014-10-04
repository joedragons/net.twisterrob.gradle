package net.twisterrob.gradle.android

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.ApkVariant
import com.android.build.gradle.api.ApkVariantOutput
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.BadPluginException
import com.android.build.gradle.ndk.NdkExtension
import net.twisterrob.gradle.vcs.VCSExtension
import net.twisterrob.gradle.vcs.VCSPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.DefaultDomainObjectSet

class AndroidVersionExtension {
    /** Default versionCode pattern is MMMNNPPBBB (what fits into 2147483648) */
    boolean autoVersion = true
    String nameFormat = '%1$d.%2$d.%3$d.%4$d'
    File versionFile
    int major = 0 // M 0..213
    int minor = 0 // N 0..99
    int minorMagnitude = 100
    int patch = 0 // P 0..99
    int patchMagnitude = 100
    int build = 0 // B 0..999
    int buildMagnitude = 1000

    void versionByVCS(vcs) {
        minorMagnitude = 10
        patchMagnitude = 10
        buildMagnitude = 100000
        build = vcs.revisionNumber
    }

    boolean renameAPK = true
    String renameFormat = '%1$s-v%2$s-%3$d.apk'
}

public class AndroidVersionPlugin implements Plugin<Project> {
    private AndroidVersionExtension version

    @Override
    void apply(Project project) {
        if (!project.plugins.hasPlugin('com.android.application')) {
            throw new BadPluginException("Can only use versioning with Android applications")
        }
        AppExtension android = project.android
        version = android.defaultConfig.extensions.create('version', AndroidVersionExtension)
        version.versionFile = project.file('version.properties')

        VCSPluginExtension vcs = project.VCS
        if (vcs && vcs.current.available) {
            version.versionByVCS(vcs.current)
        }

        project.afterEvaluate {
            android.applicationVariants.all { variant ->
                if (version.autoVersion) {
                    autoVersion(variant)
                }
                if (version.renameAPK) {
                    appendVersionNameVersionCode(variant)
                }
            }
        }

        /*// Not sure if needed to overwrite by default
        if (version.autoVersion) {
            android.defaultConfig.with {
                versionName = calculateVersionName()
                versionCode = calculateVersionCode()
            }
        }*/
    }

    void appendVersionNameVersionCode(ApplicationVariant variant) {
        def rename = { String name ->
            def base = name.endsWith(".apk") ? name.substring(0, name.length() - ".apk".length()) : name
            String.format(version.renameFormat, base, variant.versionName, variant.versionCode)
        }
        for (ApkVariantOutput output : variant.outputs) {
            if (output.zipAlign) {
                output.zipAlign.doFirst {
                    outputFile = new File(outputFile.parent, rename(outputFile.name))
                }
            }

            if (output.packageApplication) {
                output.packageApplication.doFirst {
                    outputFile = new File(outputFile.parent, rename(outputFile.name))
                }
            }
        }
    }

    void autoVersion(ApplicationVariant variant) {
        variant.preBuild.doFirst {
            variant.mergedFlavor.setVersionName(calculateVersionName())
            variant.mergedFlavor.setVersionCode(calculateVersionCode())
        }
    }

    String calculateVersionName() {
        readFromFileIfNeeded()
        return String.format(version.nameFormat, version.major, version.minor, version.patch, version.build)
    }

    int calculateVersionCode() {
        readFromFileIfNeeded()
        return ((version.major * version.minorMagnitude + version.minor) * version.patchMagnitude + version.patch) * version.buildMagnitude + version.build
    }

    private void readFromFileIfNeeded() {
        if (version.versionFile) {
            def versionProps = readVersion(version.versionFile)
            if (versionProps['major']) {
                version.major = versionProps['major'] as int
            }
            if (versionProps['minor']) {
                version.minor = versionProps['minor'] as int
            }
            if (versionProps['patch']) {
                version.patch = versionProps['patch'] as int
            }
            if (versionProps['build']) {
                version.build = versionProps['build'] as int
            }
        }
    }

    static Properties readVersion(File file) {
        def version = new Properties()
        def stream
        try {
            stream = new FileInputStream(file)
            version.load(stream)
        } catch (FileNotFoundException ignore) {
        } finally {
            if (stream != null) stream.close()
        }
        return version
    }

}
