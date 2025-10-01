import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
//        moduleName = "composeApp"
        outputModuleName = "composeApp"
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
                jvmTarget.set(JvmTarget.JVM_11)
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
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.components.resources)
            implementation(libs.compose.ui.graphics)
            implementation(libs.compose.lifecycle.viewmodel)
            implementation(libs.compose.material.icons)
            implementation(libs.compose.material3.window.size.klass)
            implementation(libs.coroutines.core)
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
            implementation(npm("js-yaml", "4.1.0"))
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
        versionCode = libs.versions.dodeclusters.android.versionCode.get().toInt()
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    dependencies {
        debugImplementation(libs.compose.ui.tooling)
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

compose.experimental {
//    web.application {}
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}