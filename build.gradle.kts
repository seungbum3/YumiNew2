// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false

    id("com.google.gms.google-services") version "4.4.2" apply false
}

// Kotlin 및 Gradle 플러그인 버전 명시
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0") // 최신 Kotlin 버전
    }
}

