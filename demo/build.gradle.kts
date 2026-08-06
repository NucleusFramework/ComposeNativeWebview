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
            implementation(project(":demo-shared"))
            // Tao backend required for desktop WebView (NativeView / WebKit2GTK).
            implementation(libs.nucleus.application)
            implementation(libs.nucleus.decorated.window.tao)
            implementation(libs.nucleus.core.runtime)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.nucleus.application)
            implementation(libs.nucleus.decorated.window.tao)
            implementation(libs.nucleus.core.runtime)
            implementation(project(":webview-compose"))
        }
    }
}

// Nucleus application plugin: JVM run, packaging, GraalVM native-image.
nucleus.application {
    mainClass = "dev.nucleusframework.webview.demo.MainKt"

    graalvm {
        isEnabled = true
        javaLanguageVersion = 25
        jvmVendor = JvmVendorSpec.BELLSOFT
        imageName = "composewebview-demo"
        // Leave unset for the per-platform default; -PnativeMarch=native overrides it locally.
        providers.gradleProperty("nativeMarch").orNull?.let {
            march = NativeImageMarch.valueOf(it.uppercase())
        }
        buildArgs.addAll(
            "-H:+AddAllCharsets",
            "-Djava.awt.headless=false",
            "-Os",
            "-H:-IncludeMethodData",
        )
    }

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Nsis, TargetFormat.Deb)
        appName = "ComposeNativeWebView Demo"
        packageName = "ComposeNativeWebviewDemo"
        packageVersion = "1.0.0"

        linux {
            // WebKit2GTK is a system dependency of the embedded Linux backend.
            debMaintainer = "NucleusFramework <dev@nucleusframework.dev>"
        }

        macOS {
            bundleID = "dev.nucleusframework.webview.demo"
        }
    }
}

// Visual e2e needs the host-OS native WebView backend on the runtime classpath.
// Natives are gitignored — build them before run if missing.
tasks.matching { it.name == "run" || it.name == "jvmRun" }.configureEach {
    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        dependsOn(":webview-compose:buildNativeWindows")
    } else if (Os.isFamily(Os.FAMILY_UNIX) && !Os.isFamily(Os.FAMILY_MAC)) {
        dependsOn(":webview-compose:buildNativeLinux")
    }
}
