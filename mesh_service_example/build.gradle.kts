/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.meshtastic.android.meshserviceexample"
    compileSdk = Configs.COMPILE_SDK
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    sourceSets["main"].aidl.srcDirs(
        listOf("${rootDir}/app/src/main/aidl")
    )

    defaultConfig {
        applicationId = "com.meshtastic.android.meshserviceexample"
        minSdk = 26
        targetSdk = Configs.TARGET_SDK
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
    buildFeatures {
        aidl = true
    }
}

kotlin {
    jvmToolchain(21)
}

// per protobuf-gradle-plugin docs, this is recommended for android
protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java")
                create("kotlin")
            }
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)


    implementation(libs.bundles.androidx)
    implementation(libs.bundles.protobuf)

    implementation(libs.kotlinx.serialization.json)

    // OSM
    implementation(libs.bundles.osm)
    implementation(libs.osmdroid.geopackage) {
        exclude(group = "com.j256.ormlite")
    }
    detektPlugins(libs.detekt.formatting)
}

detekt {
    config.setFrom("../config/detekt/detekt.yml")
    baseline = file("../config/detekt/detekt-baseline-meshserviceexample.xml")
}
