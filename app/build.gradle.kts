import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.soul.islandmiddle"
    compileSdk = 37
    compileSdkExtension = 0

    defaultConfig {
        applicationId = "com.soul.islandmiddle"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile", "keystore/island-middle.jks"))
            storePassword = keystoreProps.getProperty("storePassword", "")
            keyAlias = keystoreProps.getProperty("keyAlias", "islandmiddle")
            keyPassword = keystoreProps.getProperty("keyPassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation(platform("androidx.compose:compose-bom:2025.07.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")

    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.3")
    implementation("androidx.navigationevent:navigationevent-android:1.1.2")
    implementation("androidx.navigationevent:navigationevent-compose-android:1.1.2")

    compileOnly("de.robv.android.xposed:api:82")

    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib") {
            version { require("2.4.10") }
        }
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7") {
            version { require("2.4.10") }
        }
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8") {
            version { require("2.4.10") }
        }
    }

    debugImplementation("androidx.compose.ui:ui-tooling")
}
