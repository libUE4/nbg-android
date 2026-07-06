plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
}

subprojects {
  configurations.all {
    resolutionStrategy.eachDependency {
      if (requested.group == "com.android.tools.build" && requested.name == "aapt2") {
        useTarget("com.android.tools.build:aapt2:${requested.version}:linux-aarch64")
      }
    }
  }
}
