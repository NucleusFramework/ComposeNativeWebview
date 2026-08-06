@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget()
    jvm()
    wasmJs {
        browser()
    }

    val isMacHost = System.getProperty("os.name")?.contains(
        other = "Mac",
        ignoreCase = true
    ) == true
    if (isMacHost) {
        listOf(
            iosArm64(),
            iosSimulatorArm64(),
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "e2eShared"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serializationJson)

            implementation(project(":webview-compose"))
        }

        jvmMain.dependencies {
            implementation(compose.desktop.common)
            // Isolated native WebView construction + LocalTaoWindow for suite host.
            implementation(libs.nucleus.decorated.window.tao)
            implementation(libs.nucleus.core.runtime)
        }

        wasmJsMain.dependencies { }

        if (isMacHost) {
            iosMain.dependencies { }
        }
    }
}

android {
    namespace = "dev.nucleusframework.webview.e2e.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    // Lint is unstable with this KMP + AGP setup in CI.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

tasks.matching { it.name.startsWith("lint") }.configureEach {
    enabled = false
}
