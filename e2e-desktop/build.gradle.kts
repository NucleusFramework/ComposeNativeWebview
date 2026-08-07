import dev.nucleusframework.desktop.application.dsl.NativeImageMarch
import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.nucleus)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(project(":e2e-shared"))
            // Tao backend required for desktop WebView (NativeView / WebKit2GTK).
            implementation(libs.nucleus.application)
            implementation(libs.nucleus.decorated.window.tao)
            implementation(libs.nucleus.core.runtime)
        }
    }
}

// Nucleus application plugin: JVM run, packaging, GraalVM native-image.
nucleus.application {
    mainClass = "dev.nucleusframework.webview.e2e.MainKt"

    graalvm {
        isEnabled = true
        javaLanguageVersion = 25
        jvmVendor = JvmVendorSpec.BELLSOFT
        imageName = "composewebview-e2e"
        // Leave unset for the per-platform default; -PnativeMarch=native overrides it locally.
        providers.gradleProperty("nativeMarch").orNull?.let {
            march = NativeImageMarch.valueOf(it.uppercase())
        }
    }

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Nsis, TargetFormat.Deb)
        appName = "ComposeNativeWebView E2E"
        packageName = "ComposeNativeWebviewE2e"
        packageVersion = "1.0.0"

        linux {
            // WebKit2GTK is a system dependency of the embedded Linux backend.
            debMaintainer = "NucleusFramework <dev@nucleusframework.dev>"
        }

        macOS {
            bundleID = "dev.nucleusframework.webview.e2e"
        }
    }
}

// Visual e2e needs the host-OS native WebView backend on the runtime classpath.
// Natives are gitignored — build them before run if missing.
tasks.matching { it.name == "run" || it.name == "jvmRun" }.configureEach {
    when {
        Os.isFamily(Os.FAMILY_WINDOWS) ->
            dependsOn(":webview-compose:buildNativeWindows")
        Os.isFamily(Os.FAMILY_MAC) ->
            dependsOn(":webview-compose:buildNativeMacos")
        Os.isFamily(Os.FAMILY_UNIX) ->
            dependsOn(":webview-compose:buildNativeLinux")
    }
}
