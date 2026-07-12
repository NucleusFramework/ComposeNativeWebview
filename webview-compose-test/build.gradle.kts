import com.vanniktech.maven.publish.KotlinMultiplatform

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":webview-compose"))
        }

        jvmMain.dependencies {
            api(libs.playwright)
        }
    }
}

mavenPublishing {
    configure(KotlinMultiplatform(sourcesJar = true))
    publishToMavenCentral()
    if (project.findProperty("signingInMemoryKey") != null) {
        signAllPublications()
    }
    coordinates(artifactId = "composewebview-test")
    pom {
        name.set("ComposeWebView Testing")
        description.set("Testing utilities for Compose Multiplatform WebView library")
    }
}
