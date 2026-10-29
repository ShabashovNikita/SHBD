import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.screenshot)
}

// Release signing config is read from android/keystore.properties (gitignored).
// When the file is absent (fresh checkout, CI without secrets), assembleRelease
// still works but emits an unsigned APK. Generate one with:
//   keytool -genkey -v -keystore fudai-release.jks -keyalg RSA -keysize 2048 \
//           -validity 10000 -alias fudai
// then create keystore.properties with storeFile / storePassword / keyAlias / keyPassword.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}

// Optional dev secrets from the gitignored secrets.properties in the repository
// root. The app runs AI only on the embedded on-device model, so no AI provider
// key is read. JEV_API_KEY (LLM-classifier experimentation) is baked into the
// DEBUG BuildConfig only; release builds always get an empty string — never ship a key.
val secretsProps = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun bcString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val jevDebugApiKey: String = (secretsProps.getProperty("JEV_API_KEY") ?: "").trim()
// Optional: -PreleaseAbi=arm64-v8a for a single-ABI release APK (local smoke test /
// F-Droid). Uses ndk.abiFilters with ABI splits disabled so the artifact is the
// plain app-*-release(-unsigned).apk name F-Droid discovers without `output:`.
// Full (non-F-Droid) releases are ARM-only: arm64-v8a + armeabi-v7a. x86/x86_64
// stay in debug for emulators and are omitted from the Codeberg universal APK.
val releaseAbi: String? = providers.gradleProperty("releaseAbi").orNull

android {
    namespace = "app.shbd"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    testOptions {
        unitTests {
            // Robolectric (AndroidAppIconManager tests) needs merged resources/manifest.
            isIncludeAndroidResources = true
            // The app forces the embedded on-device model (EmbeddedAi.enforced).
            // JVM tests of the retained cloud clients opt out; enforcement tests opt back in.
            all { it.systemProperty("shbd.embeddedAi.enforce", "false") }
        }
    }

    defaultConfig {
        applicationId = "app.shbd"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (releaseAbi != null) {
            ndk {
                abiFilters += releaseAbi
            }
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "NONE"
                // Phone ABIs only. Debug keeps x86/x86_64 for emulators.
                // When -PreleaseAbi is set (F-Droid), defaultConfig already pinned
                // that single ABI: do not add more here or the F-Droid APK grows.
                if (releaseAbi == null) {
                    abiFilters += listOf("arm64-v8a", "armeabi-v7a")
                }
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only attach the signing config if keystore.properties exists. Without
            // it, gradle emits app-release-unsigned.apk and you sign manually with
            // apksigner before uploading to a store or release host.
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // Never bake an API key into a shippable build.
            buildConfigField("String", "JEV_API_KEY", bcString(""))
        }
        debug {
            // Suffix the package + version so the debug build installs side-by-side
            // with a release build on the same device.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "JEV_API_KEY", bcString(jevDebugApiKey))
        }
        create("debug2") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".debug2"
            versionNameSuffix = "-debug2"
        }
    }
    // Offline food-database assets (USDA ~3.7 MB, Swiss ~1.6 MB) live under
    // src/main/assets so the Add Food "Search food" feature ships in all build
    // types. The heavier grounded-entry code (orchestrator, tool loop, candidate
    // sheets) stays in src/grounded and is compiled for debug/test only; release
    // gets thin stubs while GroundedEntryFeature.ENABLED stays false. debug2
    // reuses the debug-only on-device LLM assets from src/debug/assets.
    sourceSets {
        getByName("debug") {
            kotlin.srcDir("src/grounded/java")
        }
        getByName("debug2") {
            assets.srcDir("src/debug/assets")
            kotlin.srcDir("src/grounded/java")
        }
        getByName("release") {
            kotlin.srcDir("src/groundedStubs/java")
        }
        getByName("test") {
            kotlin.srcDir("src/grounded/java")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    // IzzyOnDroid (and F-Droid infra) strongly prefer keeping APKs small.
    // Compress native *.so libs to reduce the on-disk size of "fat" builds.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Avoid Google-encrypted "dependency info" blobs in the APK.
    // These are often flagged during Izzy/F-Droid scanning and add size overhead.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    // Full releases: per-ABI APKs + universal (ARM only). Single-ABI
    // (-PreleaseAbi=…): no splits — plain APK name + abiFilters only.
    // splits.include does not filter the universal APK; release ndk.abiFilters
    // is what keeps x86/x86_64 out of Chompass-fdroid-*.apk.
    splits {
        abi {
            if (releaseAbi != null) {
                isEnable = false
            } else {
                isEnable = true
                reset()
                include("arm64-v8a", "armeabi-v7a")
                isUniversalApk = true
            }
        }
    }
}

// Parity fixtures live at repo-root testdata/parity/ (the Gradle project is the repo root).
tasks.withType<Test>().configureEach {
    val parityDir = rootProject.projectDir.resolve("testdata/parity").normalize()
    systemProperty("shbd.parity.dir", parityDir.absolutePath)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.coil.compose)
    // Gson is only used by the debug on-device LLM smoke test (src/debug).
    debugImplementation(libs.gson)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.cpp.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.vico.compose.m3)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.mockwebserver)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // On-device LLM (LiteRT-LM) — see docs/ON_DEVICE_LLM.md. Kept as a normal
    // (release) implementation dependency so ON_DEVICE dispatch works in shipping
    // builds when the user enables the feature / downloads a model. Behind the
    // onDeviceFeatureVisible flag (default off). Whether a runtime fetch of a
    // non-buildable binary blob from Hugging Face clears F-Droid's guidelines
    // is still an open question (production plan Phase 3). The Google Play
    // distribution flavor is disabled for now — see docs/DISTRIBUTION.md.
    implementation(libs.litertlm.android)

    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}
