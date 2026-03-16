// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.2" apply false // укажите вашу версию AGP
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    id("com.google.dagger.hilt.android") version "2.51" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.7.5" apply false
    id("com.chaquo.python") version "15.0.1" apply false
    kotlin("kapt") version "1.9.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false // Попробуйте откатить с 2.0 на 1.9.22 для стабильности
}
buildscript {
    dependencies {
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.51")
        classpath ("org.jetbrains.kotlin:kotlin-serialization:1.8.10") // Используйте вашу версию Kotlin
    }
}