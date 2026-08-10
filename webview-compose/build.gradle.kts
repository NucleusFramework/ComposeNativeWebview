@file:OptIn(ExperimentalWasmDsl::class)

import com.vanniktech.maven.publish.KotlinMultiplatform
import groovy.json.JsonSlurper
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

// ── Native build (Linux / macOS / Windows) ──────────────────────────────────
// Same pattern as Nucleus: compile host-arch natives into
// src/jvmMain/resources/nucleus/native/{linux,darwin,win32}-{x64,aarch64}/.
// CI builds via matrix and downloads artifacts before package/publish.
// Locally, only the host platform is built (and only if the artifact is missing).

val nativeLinuxDir = layout.projectDirectory.dir("src/jvmMain/native/linux")
val nativeMacosDir = layout.projectDirectory.dir("src/jvmMain/native/macos")
val nativeWindowsDir = layout.projectDirectory.dir("src/jvmMain/native/windows")
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

val buildNativeMacos by tasks.registering(Exec::class) {
    description = "Compiles the WKWebView JNI backend into libcompose_webview_macos.dylib"
    group = "build"
    // build.sh produces both arm64 and x86_64 dylibs.
    val checkArm = nativeResourceDir.file("darwin-aarch64/libcompose_webview_macos.dylib").asFile
    val checkX64 = nativeResourceDir.file("darwin-x64/libcompose_webview_macos.dylib").asFile
    onlyIf {
        Os.isFamily(Os.FAMILY_MAC) && (!checkArm.exists() || !checkX64.exists())
    }
    inputs.dir(nativeMacosDir)
    outputs.files(checkArm, checkX64)
    workingDir(nativeMacosDir.asFile)
    commandLine("bash", "build.sh")
}

val buildNativeWindows by tasks.registering(Exec::class) {
    description = "Compiles the WebView2 JNI backend into compose_webview_windows.dll"
    group = "build"
    val arch = System.getProperty("os.arch").lowercase()
    val archDir =
        if (arch.contains("aarch64") || arch.contains("arm64")) "win32-aarch64" else "win32-x64"
    val checkFile = nativeResourceDir.file("$archDir/compose_webview_windows.dll").asFile
    val loaderFile = nativeResourceDir.file("$archDir/WebView2Loader.dll").asFile
    onlyIf {
        Os.isFamily(Os.FAMILY_WINDOWS) && (!checkFile.exists() || !loaderFile.exists())
    }
    inputs.dir(nativeWindowsDir)
    outputs.files(checkFile, loaderFile)
    workingDir(nativeWindowsDir.asFile)
    commandLine("cmd", "/c", "build.bat")
    doLast {
        check(checkFile.exists()) {
            "buildNativeWindows finished but ${checkFile.name} is missing. " +
                "Need MSVC (vcvarsall) + JAVA_HOME. Run: " +
                "webview-compose\\src\\jvmMain\\native\\windows\\build.bat"
        }
        check(loaderFile.exists()) {
            "buildNativeWindows finished but WebView2Loader.dll is missing next to ${checkFile.name}"
        }
    }
}

// Ensure JVM resources / jar include the native lib when packaging on host OS.
tasks.matching {
    it.name == "jvmProcessResources" ||
        it.name == "processJvmMainResources" ||
        it.name == "jvmJar"
}.configureEach {
    dependsOn(buildNativeLinux, buildNativeMacos, buildNativeWindows)
}

tasks.configureEach {
    if (name == "sourcesJar" || name == "jvmSourcesJar") {
        dependsOn(buildNativeLinux, buildNativeMacos, buildNativeWindows)
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

        commonTest.dependencies {
            implementation(kotlin("test"))
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

// The root KMP module metadata redirects every target variant to a sibling module
// through an `available-at` entry. Targets that are disabled on the publishing host
// (Apple targets anywhere but macOS) keep Gradle's default project coordinates, so
// the release ships pointing at `composewebview:webview-compose-iosarm64:unspecified`
// and no iOS consumer can resolve it (issue #51). Fail the publish instead.
val verifyPublicationCoordinates by tasks.registering {
    description = "Checks that the KMP root module metadata only points at modules being published"
    group = "verification"

    val metadataFile = tasks.named<GenerateModuleMetadata>(
        "generateMetadataFileForKotlinMultiplatformPublication"
    ).flatMap { it.outputFile }

    inputs.file(metadataFile)

    doLast {
        @Suppress("UNCHECKED_CAST")
        val root = JsonSlurper().parse(metadataFile.get().asFile) as Map<String, Any>

        @Suppress("UNCHECKED_CAST")
        val component = root["component"] as Map<String, Any>
        val group = component["group"]
        val version = component["version"]

        @Suppress("UNCHECKED_CAST")
        val variants = root["variants"] as? List<Map<String, Any>> ?: emptyList()
        val dangling = variants.mapNotNull { variant ->
            @Suppress("UNCHECKED_CAST")
            val at = variant["available-at"] as? Map<String, Any> ?: return@mapNotNull null
            if (at["group"] == group && at["version"] == version) return@mapNotNull null
            "${variant["name"]} -> ${at["group"]}:${at["module"]}:${at["version"]}"
        }

        check(dangling.isEmpty()) {
            buildString {
                appendLine("Root module metadata points at modules outside this publication:")
                dangling.forEach { appendLine("  $it") }
                appendLine("Expected $group:*:$version.")
                append(
                    "Publish from a host that can build every declared target " +
                        "(macOS is required for the iOS targets)."
                )
            }
        }
        logger.lifecycle("Publication coordinates OK: ${variants.size} variants under $group:*:$version")
    }
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(verifyPublicationCoordinates)
}
