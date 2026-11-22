import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing.
 *
 * Priority:
 *  1. `LUMORA_KEYSTORE` env var (used by CI, secrets or a generated throwaway keystore).
 *  2. `keystore/lumora-release.jks` inside the repo (git-ignored, for local release builds).
 *  3. The debug keystore (`~/.android/debug.keystore`), so `assembleRelease` always produces
 *     an installable APK even without any configuration.
 */
val ciKeystore = System.getenv("LUMORA_KEYSTORE")?.let { File(it) }?.takeIf { it.exists() }
val localKeystore = rootProject.file("keystore/lumora-release.jks").takeIf { it.exists() }
val debugKeystore = File(System.getProperty("user.home").orEmpty(), ".android/debug.keystore")
    .takeIf { it.exists() }

val signingFile = ciKeystore ?: localKeystore ?: debugKeystore
val usingDebugKey = signingFile != null && signingFile == debugKeystore && ciKeystore == null && localKeystore == null

val storePasswordValue = System.getenv("LUMORA_KEYSTORE_PASSWORD") ?: if (usingDebugKey) "android" else "lumora"
val keyAliasValue = System.getenv("LUMORA_KEY_ALIAS") ?: if (usingDebugKey) "androiddebugkey" else "lumora"
val keyPasswordValue = System.getenv("LUMORA_KEY_PASSWORD") ?: if (usingDebugKey) "android" else "lumora"

android {
    namespace = "app.lumora"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.lumora.wallpapers"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Store endpoints (GitHub Pages publishes `docs/`, heavy assets live in GitHub Releases).
        buildConfigField(
            "String",
            "CATALOG_URL",
            "\"https://karmaoriginal.github.io/lumora/catalog.json\"",
        )
        buildConfigField(
            "String",
            "CATALOG_FALLBACK_URL",
            "\"https://raw.githubusercontent.com/karmaoriginal/lumora/main/docs/catalog.json\"",
        )
        // Optional FFmpeg backend (ffmpeg-kit). Disabled by default to keep the APK tiny,
        // see docs/OPTIONAL_FFMPEG.md. The app always ships the Media3-based enhancer.
        buildConfigField("boolean", "ENABLE_FFMPEG", "false")
    }

    signingConfigs {
        if (signingFile != null) {
            create("release") {
                storeFile = signingFile
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    bundle {
        language { enableSplit = false }
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
                "META-INF/versions/9/previous-compilation-data.bin",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.media3.common.util.UnstableApi",
        )
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
