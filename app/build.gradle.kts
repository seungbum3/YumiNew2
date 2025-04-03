plugins {
    id("com.google.gms.google-services")
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.example.yumi2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.yumi2"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation ("com.google.android.material:material:1.9.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    implementation ("com.github.bumptech.glide:glide:4.12.0") // ✅ Glide 추가
    annotationProcessor ("com.github.bumptech.glide:compiler:4.12.0") // ✅ Glide 컴파일러 추가
    implementation ("com.google.code.gson:gson:2.8.8")

    implementation  (platform("com.google.firebase:firebase-bom:33.8.0"))
    implementation  ("com.google.firebase:firebase-analytics-ktx")
    implementation  ("com.google.firebase:firebase-firestore-ktx")
    implementation ("com.google.firebase:firebase-auth-ktx")
    implementation ("com.google.firebase:firebase-database-ktx")
    implementation ("com.google.firebase:firebase-auth:22.1.2") // 최신 버전 사용
    implementation ("com.google.firebase:firebase-firestore:24.8.1") // Firestore도 최신으로 추가
    implementation ("com.google.firebase:firebase-functions:20.1.1")
    implementation ("com.google.firebase:firebase-storage:20.2.1")
    implementation ("com.google.firebase:firebase-auth:21.0.1")


    implementation ("androidx.appcompat:appcompat:1.4.0")
    implementation ("androidx.constraintlayout:constraintlayout:2.1.0")

    implementation ("com.google.android.gms:play-services-auth:20.7.0")

    implementation ("androidx.activity:activity-ktx:1.7.2")
    implementation ("androidx.fragment:fragment-ktx:1.5.7")
    implementation ("com.squareup.picasso:picasso:2.71828")

    implementation("com.google.android.gms:play-services-auth:20.7.0")

    implementation ("com.squareup.okhttp3:okhttp:4.9.3") // 🚀 OkHttp 추가
    implementation ("com.squareup.okhttp3:logging-interceptor:4.9.3")  // 🚀 로그 인터셉터 추가
    implementation ("com.github.bumptech.glide:glide:4.15.1")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.15.1")
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation ("androidx.recyclerview:recyclerview:1.3.1")
    implementation ("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")


    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}