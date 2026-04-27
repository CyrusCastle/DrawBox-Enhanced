plugins {
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

group = Library.group
version = Library.version

android {
    namespace = "io.github.markyav.drawbox.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "io.github.markyav.sample"
        versionCode = 1
        versionName = "1.0"
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":drawbox"))
    implementation(libs.androidx.activityCompose)
    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.coreKtx)
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
}