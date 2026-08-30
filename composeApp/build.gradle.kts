import org.gradle.kotlin.dsl.sourceSets
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "com.pierbezuhoff.dodeclusters.shared"
        //noinspection GradleDependency
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources {
            enable = true
        }
        withJava()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
            testTask {
                useKarma {
                    useChromium()
                    useFirefox()
                }
            }
        }
        compilerOptions {
            freeCompilerArgs.add("-Xwasm-debugger-custom-formatters")
        }
        binaries.executable()
    }

    jvm("desktop")

    sourceSets {
        commonMain {
            languageSettings {
                progressiveMode = true // cries about deprecations and stuff more
            }
        }
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
            }
        }
        androidMain {
            // this adds shared resources to 'Android' project view in AS
            resources.srcDirs("src/commonMain/composeResources")
        }
        wasmJsMain {
            resources.srcDir("src/wasnJsMain/resources") // dont work
        }

        val desktopMain by getting

        commonMain.dependencies {
            // NOTE: compose.X translates into "org.jetbrains.compose.X:X" with compose-multiplatform version
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
//            implementation(libs.compose.ui.tooling) // cannot find 1.11.1 for wasm
            implementation(libs.compose.ui.toolingPreview)
            implementation(libs.compose.resources)
            implementation(libs.compose.ui.graphics)
            implementation(libs.compose.material3)
            implementation(libs.compose.material3.adaptive)
//            implementation(libs.compose.material3.adaptive.navigation3)
            implementation(libs.compose.material3.window.size.klass)
            implementation(libs.compose.material.icons)
            implementation(libs.compose.lifecycle.runtime)
            implementation(libs.compose.lifecycle.viewmodel)
            implementation(libs.compose.lifecycle.viewmodel.navigation3)
            implementation(libs.compose.navigation3.ui)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.colormath)
            implementation(libs.kaml)
            implementation(libs.kstore)
        }
        androidMain.dependencies {
            implementation(libs.compose.ui.tooling)
            implementation(libs.activity.compose)
            implementation(libs.core.ktx)
            implementation(libs.coroutines.android)
            implementation(libs.kstore.file)
            implementation(libs.appdirs)
            implementation(libs.accompanist)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.ui.tooling)
            implementation(libs.coroutines.swing)
            implementation(libs.kstore.file)
            implementation(libs.appdirs)
        }
        wasmJsMain.dependencies {
            implementation(libs.kstore.storage)
            implementation(libs.compose.navigation3.browser)
            implementation(npm("js-yaml", "4.1.0"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(kotlin("test-annotations-common"))
        }
    }
}

compose.desktop {
    application {
        // NOTE: uncomment the following if you want to build desktop distribution locally
        //  Android Studio's built-in jbr17 for some reason doesn't  have (?) jpackage
        // javaHome = "/usr/lib/jvm/java-17-openjdk/" // should point to your locally installed jdk 17
        mainClass = "MainKt"

        buildTypes.release.proguard {
            isEnabled = false
        }

        nativeDistributions {
            targetFormats(
                TargetFormat.Msi, // Windows installer
                TargetFormat.Exe,
                TargetFormat.AppImage, // universal Linux
                TargetFormat.Deb, // Debian-based
                // github doesn't presently have VMs with non-Ubuntu Linux, so idk about generating rpm-s
                // TargetFormat.Rpm, // Red Hat, Fedora, OpenSUSE, CentOS (doesn't seem it can be generated on arch)
                // TargetFormat.Dmg, // macOS
            )
            packageName = "com.pierbezuhoff.dodeclusters"
            packageVersion = libs.versions.dodeclusters.desktop.packageVersion.get()
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

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}

dependencies {
    androidRuntimeClasspath(libs.compose.ui.tooling)
}