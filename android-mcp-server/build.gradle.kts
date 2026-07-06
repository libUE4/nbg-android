plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

android {
  namespace = "com.nbg.android.mcpserver"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.nbg.android.mcpserver"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "0.1.0"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_17
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.coroutines.android)
  implementation(libs.org.json)
  testImplementation(libs.junit)
}
