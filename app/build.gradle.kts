plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

android {
  namespace = "com.nbg.android"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.nbg.android"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "0.1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters += listOf("arm64-v8a")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
    create("profile") {
      initWith(getByName("release"))
      matchingFallbacks += listOf("release")
      signingConfig = signingConfigs.getByName("debug")
      isDebuggable = false
    }
  }

  androidResources {
    noCompress += listOf("gz", "xz", "nbgpack")
  }

  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }

  lint {
    // Compose lint bundled with the current Android Gradle Plugin crashes on
    // Kotlin 2.2 metadata while reading HanakoBridge.kt. Keep the rest of lint
    // enabled so the project still has a working static-analysis gate.
    disable += "StateFlowValueCalledInComposition"
  }
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_17
  }
}

dependencies {
  implementation(project(":terminal-core"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.activity.compose)
  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.animation)
  implementation(libs.compose.ui.graphics)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.material3)
  implementation(libs.compose.material.icons.extended)
  implementation(libs.huge.icons) {
    exclude(group = "androidx.compose")
    exclude(group = "androidx.compose.animation")
    exclude(group = "androidx.compose.foundation")
    exclude(group = "androidx.compose.material")
    exclude(group = "androidx.compose.material3")
    exclude(group = "androidx.compose.runtime")
    exclude(group = "androidx.compose.ui")
    exclude(group = "androidx.core")
    exclude(group = "androidx.lifecycle")
    exclude(group = "androidx.transition")
    exclude(group = "androidx.activity")
    exclude(group = "androidx.appcompat")
    exclude(group = "androidx.fragment")
    exclude(group = "androidx.constraintlayout")
    exclude(group = "androidx.viewpager2")
    exclude(group = "com.google.android.material")
  }
  implementation(libs.coroutines.android)
  implementation(libs.okhttp)
  implementation(libs.org.json)
  implementation(libs.termux.terminal.view)
  testImplementation(libs.junit)
  androidTestImplementation(platform(libs.compose.bom))
  androidTestImplementation(libs.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.rules)
  debugImplementation(libs.compose.ui.test.manifest)
}
