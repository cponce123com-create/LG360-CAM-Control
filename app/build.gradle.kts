plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "pe.lgcam.control"
 compileSdk = 35
 defaultConfig { applicationId = "pe.lgcam.control"; minSdk = 26; targetSdk = 28; versionCode = 5; versionName = "0.3.1" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
