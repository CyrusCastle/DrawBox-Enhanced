@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidLibrary)
    id("com.vanniktech.maven.publish") version "0.36.0"
}

group = Library.GROUP
version = Library.VERSION

kotlin {
    // Android
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
        publishLibraryVariants("release")
    }

    // JVM
    jvm()

    // Web
    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    // iOS
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Drawbox-Enhanced"
            isStatic = true
        }
    }

    // etc
    sourceSets {
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.foundation)
        }
    }
}

android {
    namespace = "uk.codecymru.drawbox"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    val signingTasks = tasks.withType<Sign>()
    mustRunAfter(signingTasks)
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()
}

mavenPublishing {
    coordinates(Library.GROUP, Library.NAME, Library.VERSION)

    pom {
        name.set(Library.NAME)
        description.set(Library.DESCRIPTION)
        url.set(Library.URL)

        licenses {
            license {
                name.set(Library.License.NAME)
                url.set(Library.License.URL)
            }
        }
        developers {
            developer {
                id.set(Library.Author.ID)
                name.set(Library.Author.NAME)
                email.set(Library.Author.EMAIL)
            }

            developer {
                id.set(Library.OriginalAuthor.ID)
                name.set(Library.OriginalAuthor.NAME)
                email.set(Library.OriginalAuthor.EMAIL)
            }
        }
        scm {
            url.set(Library.URL)
        }
    }
}