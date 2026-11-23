import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

/**
 * The release key is read from [rootProject]/local.properties, which is machine-local and names the
 * store rather than carrying it into the source. An absent store is not an error: AGP then writes
 * `app-release-unsigned.apk`, and the file's own name says what is wrong with it.
 */
val localProps = Properties().apply {
    val file = rootProject.layout.projectDirectory.file("local.properties").asFile
    if (file.exists()) file.inputStream().use(::load)
}

fun signingProperty(name: String): String? = localProps.getProperty(name) ?: System.getenv(name)

android {
    namespace = "dev.aurastore.store"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.aurastore.store"
        minSdk = 27
        targetSdk = 36
        // One number, two readings: the name is what a GitHub tag carries, the code is what Android
        // compares. The code is derived here so a release cannot publish a name and a code that
        // disagree — major × 10 000 + minor × 100 + patch, so 1.4.0 is 10 400. The bounds are the
        // ones the scheme can hold, and they are also the ones AppVersion parses.
        val appVersionName = "1.8.3"
        versionName = appVersionName
        versionCode = appVersionName.split('.').let { parts ->
            require(parts.size == 3) { "versionName must be major.minor.patch, was $appVersionName" }
            val numbers = parts.map { part ->
                part.toIntOrNull() ?: error("versionName is not numeric: $appVersionName")
            }
            val (major, minor, patch) = numbers
            require(major in 0..999 && minor in 0..99 && patch in 0..99) {
                "versionName parts are outside the versionCode scheme: $appVersionName"
            }
            major * 10_000 + minor * 100 + patch
        }
        // The AndroidX runner, because the instrumented tests below use `ActivityScenario` and Compose's
        // test rule; AGP's default is the legacy framework runner, which cannot host either.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val store = signingProperty("AuraStoreFile")?.let { file(it) }
        val storePassword = signingProperty("AuraStorePassword")
        if (store != null && store.isFile && storePassword != null) {
            create("release") {
                storeFile = store
                this.storePassword = storePassword
                keyAlias = signingProperty("AuraStoreKeyAlias") ?: "aurastore"
                keyPassword = signingProperty("AuraStoreKeyPassword") ?: storePassword
            }
        }
    }

    buildTypes {
        release {
            // Shrinking is on, and its keep rules are the two enums and the worker in
            // proguard-rules.pro. It was off while a release artefact could not be run here; the
            // phone pass that closes that gap is the one that turned it on: the release APK is
            // installed and exercised, so a rule the shrinker eats now fails a check instead of a
            // user's install.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // The storefront animates its own rows, morphs and routes, so animation is a direct dependency
  // rather than something borrowed through material3.
  implementation(libs.androidx.compose.animation)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  // The Android `org.json` is a stub on the unit-test classpath, so the parser tests below need a
  // real implementation of the same API to parse their fixtures.
  testImplementation(libs.json)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Catalogue client: HTTP + JSON + images
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)

  // Background update checks — the policy must be honoured when no screen is open.
  implementation(libs.androidx.work.runtime.ktx)
}
