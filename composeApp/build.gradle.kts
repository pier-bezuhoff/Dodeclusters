import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
//    alias(libs.plugins.kotest.multiplatform)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    androidTarget {
        tasks.withType<KotlinJvmCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11) //JVM_1_8)
            }
        }
    }

    jvm("desktop")

//    tasks.withType<Test>().configureEach {
//        compilerOptions {
//            jvmToolchain(17)
//        }
//        useJUnitPlatform()
//    }

    sourceSets {
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
            }
        }

        val desktopMain by getting
        
        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kaml)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material) // used only for icons
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.compose.material3.window.size.klass)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.colormath)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kaml) // NOTE: kaml on wasm is experimental for now, maybe test it
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
//            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
//            implementation(libs.kotest.runner.junit5)
//            implementation(libs.kotest.assertions.core)
//            implementation(libs.kotest.framework.engine)
//            implementation(libs.kotest.property)
//            implementation(libs.kotest.framework.datatest)
        }
    }
}

android {
    namespace = "com.pierbezuhoff.dodeclusters"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/composeResources")

    defaultConfig {
        applicationId = "com.pierbezuhoff.dodeclusters"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = libs.versions.dodeclusters.versionCode.get().toInt()
        versionName = libs.versions.dodeclusters.version.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11 //VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_11 //VERSION_1_8
    }
    dependencies {
        debugImplementation(libs.compose.ui.tooling)
    }
}

compose.desktop {
    application {
//        javaHome = "/usr/lib/jvm/java-17-openjdk/" // cannot find jpackage in the normal jbr-17
        mainClass = "MainKt"

        buildTypes.release.proguard {
            isEnabled = false
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.pierbezuhoff.dodeclusters"
            packageVersion = "1.0.0" // cannot start with 0 it seems //libs.versions.dodeclusters.version.get()
            macOS {
                iconFile.set(project.file("icon.icns"))
            }
            windows {
                iconFile.set(project.file("icon.ico"))
            }
            linux {
                iconFile.set(project.file("icon.png")) // default recommendation is png
            }
        }
    }
}

compose.experimental {
//    web.application {}
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}