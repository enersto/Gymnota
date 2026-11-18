import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.ksp)// [修改] 使用 ksp 插件
//    id("kotlin-kapt")
}

val rustHistoryEnabled = providers.gradleProperty("rustHistory").orElse("true").get().toBooleanStrict()

// Keep migration archives in source control, but package only the WebP runtime assets.
val packagedAssets = layout.buildDirectory.dir("generated/packagedAssets")
val preparePackagedAssets by tasks.registering(Sync::class) {
    from("src/main/assets") {
        exclude("catalog/videos/*.gif", "catalog/gymnota-catalog-v4.sqlite.bin")
    }
    into(packagedAssets)
    includeEmptyDirs = false
    doFirst {
        val videos = file("src/main/assets/catalog/videos")
        val originals = videos.listFiles().orEmpty().filter { it.extension == "gif" }
        check(originals.all { file("${videos.path}/${it.nameWithoutExtension}.webp").isFile }) {
            "Archived GIF has no WebP replacement"
        }
        check(file("src/main/assets/catalog/gymnota-catalog-v5.sqlite.bin").isFile)
    }
}


android {
    namespace = "com.example.myfit"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        buildConfigField("boolean", "RUST_HISTORY_ENABLED", rustHistoryEnabled.toString())
        applicationId = "com.example.myfit"
        ndk {
            val previewAbi = providers.gradleProperty("previewAbi").orNull
            require(previewAbi == null || previewAbi in listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
            abiFilters += previewAbi?.let { listOf(it) } ?: listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
        minSdk = 28
        targetSdk = 35
        versionCode = 6400
        versionName = "6.4.0"

        testInstrumentationRunner = if (providers.gradleProperty("documentationFixture").orNull == "true")
            "com.example.myfit.flutter.DocumentationFixtureInstrumentation"
        else if (providers.gradleProperty("packagingInspector").orNull == "true")
            "com.example.myfit.flutter.PackagingInstrumentation" else "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    sourceSets.getByName("main").assets.setSrcDirs(listOf(packagedAssets))
    if (rustHistoryEnabled) sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/rustJniLibs"))
    sourceSets.getByName("androidTest").assets.srcDir(layout.buildDirectory.dir("generated/mediaBenchAssets"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Preserve the pre-API-28 APK compression policy after raising minSdk.
    packaging { dex { useLegacyPackaging = true } }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":flutter"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.3.0"))
    
    val room_version = "2.8.4"
    val nav_version = "2.7.7"

    // Room 数据库
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
//    kapt("androidx.room:room-compiler:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // Navigation 导航
    implementation("androidx.navigation:navigation-compose:$nav_version")

    // 图标库 (用于底部导航栏)
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // Liquid Glass UI Dependencies
    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation("io.github.kyant0:shapes:1.2.0")


}
// No checked-in native binaries. -PrustHistory=false builds the existing Kotlin fallback.
val rustAbis = providers.gradleProperty("previewAbi").orElse("armeabi-v7a,arm64-v8a,x86_64")
val rustNdk = providers.environmentVariable("ANDROID_NDK_HOME").orElse(providers.provider {
    val local = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { local.load(it) }
    val sdk = local.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: "${System.getenv("LOCALAPPDATA")}/Android/Sdk"
    File(sdk, "ndk/28.2.13676358").path
})
val rustLocalProperties = Properties().apply {
    rootProject.file("rust.local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val buildRustHistory by tasks.registering(Exec::class) {
    for ((key, envName) in mapOf("cargo.path" to "GYMNOTA_CARGO", "cargo.home" to "CARGO_HOME", "rustup.home" to "RUSTUP_HOME")) {
        rustLocalProperties.getProperty(key)?.let { environment(envName, it) }
    }
    rustLocalProperties.getProperty("cargo.path")?.let {
        environment("Path", File(it).parent + File.pathSeparator + System.getenv("Path"))
    }
    inputs.property("localRustConfig", rustLocalProperties.toString())
    onlyIf { rustHistoryEnabled }
    inputs.dir(rootProject.file("rust/history_core/src"))
    inputs.files(rootProject.file("rust/history_core/Cargo.toml"), rootProject.file("rust/history_core/Cargo.lock"), rootProject.file("tools/build_rust.ps1"))
    inputs.property("abis", rustAbis)
    inputs.property("ndk", rustNdk)
    outputs.dir(layout.buildDirectory.dir("generated/rustJniLibs"))
    commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", rootProject.file("tools/build_rust.ps1"),
        "-Abis", rustAbis.get(), "-OutputDir", layout.buildDirectory.dir("generated/rustJniLibs").get().asFile,
        "-Ndk", rustNdk.get())
}
tasks.named("preBuild").configure { dependsOn(buildRustHistory, preparePackagedAssets) }
