plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace = "com.maz.blurrr"
 compileSdk = 35
 defaultConfig { applicationId = "com.maz.blurrr"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0.0" }
 signingConfigs {
  create("release") {
   val ks = file("release-key.jks")
   if (ks.exists()) { storeFile = ks; storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD"); keyAlias = System.getenv("ANDROID_KEY_ALIAS"); keyPassword = System.getenv("ANDROID_KEY_PASSWORD") }
  }
 }
 buildTypes {
  debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
  release { isMinifyEnabled = false; if (file("release-key.jks").exists()) signingConfig = signingConfigs.getByName("release") }
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
 debugImplementation("androidx.compose.ui:ui-tooling")
}
