@file:OptIn(ExperimentalWasmDsl::class)

import com.vanniktech.maven.publish.KotlinMultiplatform
import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

// Real WebView e2e needs a display (WebKit2GTK + Tao). Don't fail the whole
// build when DISPLAY is missing (CI without xvfb) — tests self-skip.
tasks.withType<Test>().configureEach {
    // Each e2e suite owns the Tao event loop once per process.
    maxParallelForks = 1
}

// ── Native build (Linux WebKit2GTK) ─────────────────────────────────────────
// Same pattern as Nucleus: compile host-arch .so into
// src/jvmMain/resources/nucleus/native/linux-{x64,aarch64}/.
// CI builds both arches via matrix and downloads artifacts before package/publish.
// Locally, only the host arch is built (and only if the .so is missing).

val nativeLinuxDir = layout.projectDirectory.dir("src/jvmMain/native/linux")
val nativeResourceDir = layout.projectDirectory.dir("src/jvmMain/resources/nucleus/native")

val buildNativeLinux by tasks.registering(Exec::class) {
    description = "Compiles the WebKit2GTK JNI backend into libcompose_webview_linux.so"
    group = "build"
    val arch = System.getProperty("os.arch").lowercase()
    val archDir =
        if (arch.contains("aarch64") || arch.contains("arm64")) "linux-aarch64" else "linux-x64"
    val checkFile = nativeResourceDir.file("$archDir/libcompose_webview_linux.so").asFile
    onlyIf {
        Os.isFamily(Os.FAMILY_UNIX) &&
            !Os.isFamily(Os.FAMILY_MAC) &&
            !checkFile.exists()
    }
    inputs.dir(nativeLinuxDir)
    outputs.file(checkFile)
    workingDir(nativeLinuxDir.asFile)
    commandLine("bash", "build.sh")
}

// Ensure JVM resources include the native lib when packaging on Linux hosts.
tasks.matching { it.name == "jvmProcessResources" || it.name == "processJvmMainResources" }.configureEach {
    dependsOn(buildNativeLinux)
}

tasks.configureEach {
    if (name == "sourcesJar" || name == "jvmSourcesJar") {
        dependsOn(buildNativeLinux)
    }
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget()
    jvm()
    wasmJs {
        browser()
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "composewebview"
            isStatic = true
        }
        iosTarget.setUpiOSObserver()
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serializationJson)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutinesAndroid)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
            // Desktop WebView embeds via NativeView and requires the Tao backend.
            api(libs.nucleus.decorated.window.tao)
            implementation(libs.nucleus.core.runtime)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
            implementation(libs.compose.ui.test.junit4)
            implementation(compose.desktop.currentOs)
            implementation(libs.nucleus.application)
            implementation(libs.kotlinx.coroutinesSwing)
        }

        iosMain.dependencies { }

        wasmJsMain.dependencies { }
    }
}

android {
    namespace = "dev.nucleusframework.webview"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    // Lint is currently unstable with this KMP + AGP setup in CI (UAST disposal crash).
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

tasks.matching { it.name.startsWith("lint") }.configureEach {
    enabled = false
}

fun org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget.setUpiOSObserver() {
    val path = projectDir.resolve("src/nativeInterop/cinterop/observer")

    binaries.all {
        linkerOpts("-F $path")
        linkerOpts("-ObjC")
    }

    compilations.getByName("main") {
        cinterops.create("observer") {
            compilerOpts("-F $path")
        }
    }
}

mavenPublishing {
    configure(KotlinMultiplatform(sourcesJar = true))
    publishToMavenCentral()
    if (project.findProperty("signingInMemoryKey") != null) {
        signAllPublications()
    }
    coordinates(artifactId = "composewebview")
    pom {
        name.set("ComposeWebView")
        description.set("Compose Multiplatform WebView library for Desktop, Android and iOS")
    }
}
