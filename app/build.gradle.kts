import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.mxmvncnt.teou"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mxmvncnt.teou"
        minSdk = 29
        targetSdk = 36
        versionCode = 51
        versionName = "2.22.0"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("unifiedpush") {
            dimension = "distribution"
        }
        create("fcm") {
            dimension = "distribution"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../sosring-release.jks")
            storePassword = localProps.getProperty("STORE_PASSWORD", "")
            keyAlias = "sosring"
            keyPassword = localProps.getProperty("KEY_PASSWORD", "")
        }
    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            vcsInfo {
                include = false
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.withType<com.google.gms.googleservices.GoogleServicesTask>().configureEach {
    if (name.startsWith("processUnifiedpush")) enabled = false
}

androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
    val variantName = variant.name.replaceFirstChar { it.uppercaseChar() }
    val adb = androidComponents.sdkComponents.adb
    val applicationId = variant.applicationId
    tasks.register("run$variantName") {
        group = "install"
        description = "Install and launch ${variant.name} on connected devices"
        dependsOn("install$variantName")
        doLast {
            val adbPath = adb.get().asFile.absolutePath
            val serial = providers.gradleProperty("android.injected.device.serial")
                .orElse(providers.environmentVariable("ANDROID_SERIAL")).orNull
            val devices = serial?.let { listOf(it) } ?: providers.exec {
                commandLine(adbPath, "devices")
            }.standardOutput.asText.get().lineSequence()
                .filter { it.endsWith("\tdevice") }.map { it.substringBefore('\t') }.toList()
            check(devices.isNotEmpty()) { "No authorized Android device connected" }
            devices.forEach { device ->
                println(providers.exec {
                    commandLine(adbPath, "-s", device, "shell", "am", "start", "-n",
                        "${applicationId.get()}/com.mxmvncnt.teou.app.MainActivity")
                }.standardOutput.asText.get())
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")

    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.maplibre.gl:android-sdk:13.6.1")

    // QR pairing
    implementation("com.google.zxing:core:3.5.4")

    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")

    "unifiedpushImplementation"("org.unifiedpush.android:connector:3.3.5")
    "fcmImplementation"(platform("com.google.firebase:firebase-bom:34.19.0"))
    "fcmImplementation"("com.google.firebase:firebase-messaging")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}
