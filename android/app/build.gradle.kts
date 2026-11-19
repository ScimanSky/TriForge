import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// google-services.json belongs to the real Firebase project and is not
// committed. Without it the app still builds (CI, a fresh checkout) and shows
// a setup screen instead of crashing.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Base URL of the Garmin sync service on Cloud Run. Set it in
// android/local.properties as triforge.garminServiceUrl=https://...
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val garminServiceUrl: String =
    (localProperties.getProperty("triforge.garminServiceUrl")
        ?: providers.gradleProperty("triforge.garminServiceUrl").orNull
        ?: "").trim().trimEnd('/')

// The version is the number of commits behind the build. Every merge on main
// makes it higher, so each test build sent to a phone is newer than the one
// installed: that is how the in-app update recognises it. Nothing to bump by
// hand, and no two branches fighting over the same number. Without git or
// its history the build still works, as version 1, and is not for distribution.
val commitCount: Int = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        workingDir = rootDir
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toInt()
}.getOrDefault(1).coerceAtLeast(1)

android {
    namespace = "com.triforge.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.triforge.app"
        minSdk = 26
        targetSdk = 35
        versionCode = commitCount
        versionName = "0.1.$commitCount"

        buildConfigField("String", "GARMIN_SERVICE_URL", "\"$garminServiceUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // In-app updates for test builds. Every build gets the API, which does
    // nothing on its own; only debug builds, the ones sent to testers, carry
    // the SDK that downloads and installs. It must never ship to a store.
    implementation(libs.firebase.appdistribution.api)
    debugImplementation(libs.firebase.appdistribution)
    // The SDK's install screen is an AppCompat activity and needs an AppCompat
    // theme, which the debug manifest gives it.
    debugImplementation(libs.androidx.appcompat)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
