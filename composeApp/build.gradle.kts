import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                // source maps for wasm are WIP: https://kotlinlang.org/docs/wasm-debugging.html
                devtool = "source-map" // not working aside from live localhost testing
                sourceMaps = true
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(project.rootDir.path)
                        add(project.projectDir.path)
                    }
                }
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
        commonMain.languageSettings {
            progressiveMode = true // cries about deprecations and stuff more
        }
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
            }
        }

        val desktopMain by getting

        commonMain.dependencies {
            // NOTE: compose.X translates into "org.jetbrains.compose.X:X" with compose-multiplatform version
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material) // used only for icons
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.compose.ui.graphics)
            implementation(compose.components.resources)
            implementation(libs.compose.lifecycle.viewmodel)
            implementation(libs.coroutines.core)
            implementation(libs.compose.material3.window.size.klass)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.colormath)
            implementation(libs.kaml)
            implementation(libs.kstore)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.coroutines.android)
            implementation(libs.kstore.file)
            implementation(libs.appdirs)
            implementation(libs.accompanist)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.coroutines.swing)
            implementation(libs.kstore.file)
            implementation(libs.appdirs)
        }
        wasmJsMain.dependencies {
            implementation(libs.kstore.storage)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(kotlin("test-annotations-common"))
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