import com.github.triplet.gradle.androidpublisher.ResolutionStrategy
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.github.triplet.play") version "4.0.0"
    id("realm-android")
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")

    //----Dependency plugins start----
    id("kotlinDependencies")
    id("androidXDependencies")
    id("composeDependencies")
    id("daggerAndHiltDependencies")
    id("networkDependencies")
    id("testDependencies")
    //----Dependency plugins end----

    id("com.google.dagger.hilt.android")
    kotlin("plugin.compose")
}

android {
    compileSdk = BuildConfig.compileSdkVersion
    namespace = "org.zotero.android"

    defaultConfig {
        applicationId = BuildConfig.appId
        minSdk = BuildConfig.minSdkVersion
        targetSdk = BuildConfig.targetSdk
        // Bookshelf: CI passes an always-increasing build number so each new
        // APK installs over the previous one.
        versionCode = (project.findProperty("bookshelfVersionCode") as String?)?.toInt()
            ?: BuildConfig.versionCode
        versionName = (project.findProperty("bookshelfVersionName") as String?)
            ?: BuildConfig.version.name
        testInstrumentationRunner = Libs.androidJUnitRunner

        buildConfigField("String", "BASE_API_URL", "\"https://api.zotero.org\"")
        buildConfigField("boolean", "EVENT_AND_CRASH_LOGGING_ENABLED", "false")
        buildConfigField("String", "PSPDFKIT_KEY", "\"\"")
        manifestPlaceholders["enableCrashReporting"] = false

        //Making build version components to be accessible from within the app.
        buildConfigField("int", "BUILD_VERSION_MAJOR", "${BuildConfig.version.major}")
        buildConfigField("int", "BUILD_VERSION_MINOR", "${BuildConfig.version.minor}")
        buildConfigField("int", "BUILD_VERSION_PATCH", "${BuildConfig.version.patch}")

        ndk {
            // Bookshelf: phones only (keeps the APK smaller).
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }
    signingConfigs {
        // Bookshelf: personal signing key, written by CI from a repository secret.
        // The password is not secret on its own; the key file is.
        create("bookshelf") {
            storeFile = rootProject.file("bookshelf.keystore")
            storePassword = "bookshelf-sideload"
            keyAlias = "bookshelf"
            keyPassword = "bookshelf-sideload"
        }
        create("release") {
            storeFile = rootProject.file("zotero.release.keystore")

            if (rootProject.file("keystore-secrets.txt").exists()) {
                val secrets: List<String> = rootProject
                    .file("keystore-secrets.txt")
                    .readLines()
                keyAlias = secrets[0]
                storePassword = secrets[1]
                keyPassword = secrets[2]
            }
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    androidResources {
        noCompress += listOf("ttf", "mov", "avi", "json", "html", "csv", "obb")
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
            isMinifyEnabled = false
            signingConfigs
                .findByName("debug")
                ?.storeFile = rootProject.file("debug.keystore")

            buildConfigField("boolean", "EVENT_AND_CRASH_LOGGING_ENABLED", "false")
            manifestPlaceholders["enableCrashReporting"] = false
            extra.set("enableCrashlytics", false)

            //Prevent variables from being 'optimized out' during debug,
            //so it becomes possible to see their values in debugger
            tasks.withType<KotlinJvmCompile>().configureEach {
                compilerOptions {
                    freeCompilerArgs.addAll(listOf("-Xdebug"))
                }
            }

        }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = if (rootProject.file("bookshelf.keystore").exists()) {
                signingConfigs.getAt("bookshelf")
            } else {
                signingConfigs.getAt("release")
            }

            // Bookshelf: no crash reporting to Zotero's servers from this build.
            buildConfigField("boolean", "EVENT_AND_CRASH_LOGGING_ENABLED", "false")
            manifestPlaceholders["enableCrashReporting"] = false
        }
    }
    setDefaultProductFlavors()
    productFlavors {
        dev {
            resValue("string", "app_name", """"Zotero Debug""")
            buildConfigField("String", "PSPDFKIT_KEY", readPspdfkitKey())
            applicationIdSuffix = ".debug"
        }
        internal {
            resValue("string", "app_name", """"Bookshelf""")
            buildConfigField("String", "PSPDFKIT_KEY", readPspdfkitKey())
        }
    }

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(jvmTargetVersion)
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources.pickFirsts.add("META-INF/kotlinx-coroutines-core.kotlin_module")
    }

    androidComponents {
        beforeVariants { it.ignoreUnusedVariants() }
    }
}

play {
    track.set("internal")
    defaultToAppBundles.set(true)
    resolutionStrategy.set(ResolutionStrategy.AUTO)
}

dependencies {

    //Material design
    implementation(Libs.materialDesign)

    //Crash
    implementation(Libs.Firebase.Crashlytics.crashlytics)

    //PSPDFKIT
    implementation(Libs.nutrient)

    //GSON
    implementation(Libs.gson)

    //ExoPlayer
    implementation(Libs.ExoPlayer.exoPlayer)
    implementation(Libs.ExoPlayer.mediaUi)

    //Coil
    implementation(Libs.Coil.compose)

    //Commons
    implementation(Libs.Commons.text)

    //Other
    implementation(Libs.timber)
    implementation(Libs.jodaTime)
    implementation(Libs.eventBus)
    implementation(Libs.keyboardVisibility)
    implementation(Libs.codeScanner)

}

kapt {
    correctErrorTypes = true
}

hilt {
    enableAggregatingTask = false
}

fun readPspdfkitKey() : String {
    val file = rootProject
        .file("pspdfkit-key.txt")
    if (!file.exists()) {
        logger.warn("pspdfkit-key.txt file not found. Using PSPDFKit without a key")
        return "\"\""
    }
    val keys: List<String> = file
        .readLines()
    return "\"${keys[0]}\""
}