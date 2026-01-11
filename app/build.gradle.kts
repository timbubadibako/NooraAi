plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
android.buildFeatures.buildConfig = true

android {
    namespace = "com.example.nooraai"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.nooraai"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${project.property("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${project.property("SUPABASE_ANON_KEY")}\"")
    }

    buildFeatures {
        viewBinding=true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}


dependencies {
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("io.coil-kt:coil:2.4.0")
// atau Glide jika prefer

    // Supabase modules (no explicit versions thanks to BOM)
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.0.3")
    implementation("io.github.jan-tennert.supabase:auth-kt:3.0.3")
    implementation("io.github.jan-tennert.supabase:storage-kt:3.0.3")

    // Ktor client (required by Supabase v3)
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-android:3.0.3")
    implementation("io.ktor:ktor-client-logging:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")

    // kotlinx.serialization (JSON)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // Networking (Retrofit/OkHttp) - keep if you still use Retrofit for custom endpoints
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // JSON parsing for Retrofit (Moshi)
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")

    // Coroutines (single version)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // AndroidX & Lifecycle (use single consistent versions)
    implementation("androidx.core:core-ktx:1.12.0")           // adjust to your project version
    implementation("androidx.appcompat:appcompat:1.7.0")     // adjust as needed
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.1")
    implementation(libs.androidx.activity)

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

}