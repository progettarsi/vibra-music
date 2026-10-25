/* Vibra (2026) - GPL-3.0. Derived from ArchiveTune c Rukamori. */
plugins { id("com.android.test") }

android {
    namespace = "app.vibra.benchmark"
    compileSdk = 37
    compileSdkMinor = 2
    targetProjectPath = ":app"
    // Macrobenchmarks must be able to kill/relaunch the target process.
    experimentalProperties["android.experimental.self-instrumenting"] = true
    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("distribution", "gms")
        missingDimensionStrategy("device", "mobile")
        missingDimensionStrategy("abi", "universal")
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            buildConfigField("boolean", "GENERATE_PROFILE", "false")
            matchingFallbacks += listOf("release")
        }
        create("nonMinifiedBenchmark") {
            isDebuggable = true
            buildConfigField("boolean", "GENERATE_PROFILE", "true")
            matchingFallbacks += listOf("release")
        }
    }
}
androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType != "debug" }
}
dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
}
