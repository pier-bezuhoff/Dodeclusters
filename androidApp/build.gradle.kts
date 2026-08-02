import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

android {
    namespace = "com.pierbezuhoff.dodeclusters"
    //noinspection GradleDependency
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.pierbezuhoff.dodeclusters"
        minSdk = libs.versions.android.minSdk.get().toInt()
        //noinspection OldTargetApi
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = libs.versions.dodeclusters.android.versionCode.get().toInt()
        versionName = libs.versions.dodeclusters.version.get()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// ive seen deps being put in kotlin block
dependencies {
    // we only need deps used in MainActivity here
    implementation(projects.composeApp)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
//    implementation(libs.compose.ui.toolingPreview)
//    implementation(libs.compose.resources)
//    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
//    implementation(libs.compose.material3.adaptive)
//    implementation(libs.compose.material3.adaptive.navigation3)
//    implementation(libs.compose.material3.window.size.klass)
//    implementation(libs.compose.material.icons)
    implementation(libs.compose.lifecycle.runtime)
//    implementation(libs.compose.lifecycle.viewmodel)
//    implementation(libs.compose.lifecycle.viewmodel.navigation3)
//    implementation(libs.compose.navigation3.ui)
    implementation(libs.coroutines.core)
//    implementation(libs.kotlinx.serialization.json)
//    implementation(libs.colormath)
//    implementation(libs.kaml)
    implementation(libs.kstore)
    implementation(libs.compose.activity)
    // android-specific
    implementation(libs.androidx.core.ktx)
//    implementation(libs.androidx.appcompat)
    implementation(libs.coroutines.android)
    implementation(libs.kstore.file)
    implementation(libs.appdirs)
//    implementation(libs.accompanist)
    debugImplementation(libs.compose.ui.tooling)
}