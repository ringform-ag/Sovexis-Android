plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.sovexis.platform"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sovexis.platform"
        minSdk = 30  // Android 11+ (StrongBox API 30+)
        targetSdk = 34
        versionCode = 1
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    androidResources {
        additionalParameters += listOf("--no-version-vectors", "--no-version-transitions")
        noCompress += listOf("txt")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
            // 预编译原生库已剥离调试符号，禁止 AGP 重复 strip
            keepDebugSymbols += listOf(
                "**/libgojni.so",
                "**/libjnidispatch.so",
                "**/libsovexis_zkp.so"
            )
        }
    }
}

dependencies {
    // ========== TSS AAR (gomobile compiled tss-lib) ==========
    implementation(files("libs/tssbridge.aar"))
    
    // ========== Kotlin Serialization (for TSS messages) ==========
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // ========== Gson (JSON parsing) ==========
    implementation("com.google.code.gson:gson:2.10.1")
    
    // ========== ZXing (QR Code for TSS) ==========
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // ========== AndroidX Core ==========
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // ========== Jetpack Compose (BOM 2024.05.00) ==========
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ========== Navigation ==========
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // ========== Lifecycle ==========
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // ========== Hilt (DI) ==========
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // ========== Security ==========
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.crypto.tink:tink-android:1.12.0")
    implementation("com.madgag.spongycastle:core:1.58.0.0")
    implementation("com.madgag.spongycastle:prov:1.58.0.0")

    // ========== Room (SQLite) ==========
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // ========== DataStore ==========
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ========== Network ==========
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ========== Ktor (WebSocket/HTTP Client) ==========
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-okhttp:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-client-websockets:2.3.7")
    implementation("io.ktor:ktor-client-logging:2.3.7")

    // ========== JSON ==========
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // ========== QR Code ==========
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // ========== Image Loading ==========
    implementation("io.coil-kt:coil-compose:2.5.0")

    // ========== Coroutines ==========
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ========== Biometric ==========
    implementation("androidx.biometric:biometric:1.1.0")

    // ========== Mopro (ZKP via Circom Groth16) ==========
    // ⚠️ Mopro 是纯 Rust 项目，仓库根目录无 build.gradle，
    //    JitPack 无法直接构建 AAR。需通过 CLI 生成后手动引入：
    //    1. `git clone https://github.com/zkmopro/mopro && cd mopro`
    //    2. `cargo run --bin android` 生成 Kotlin 绑定 + .so
    //    3. 将生成的 .aar/.jar 放入 app/libs/，将 .so 放入 app/src/main/jniLibs/
    //    4. 取消下方注释：
    //    implementation(files("libs/mopro-ffi.aar"))
    //    JNA — Mopro UniFFI bindings 依赖 JNA 加载原生库
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    // ========== Debug ==========
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ========== Testing ==========
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
