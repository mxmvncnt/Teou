import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}

subprojects {
    plugins.withId("com.android.application") {
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) load(f.inputStream())
        }

        extensions.configure<ApplicationExtension> {
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
                create(project.name) {
                    dimension = "distribution"
                }
            }

            sourceSets {
                getByName("main").setRoot(rootProject.file("app/src/main").path)
                getByName(project.name).setRoot(rootProject.file("app/src/${project.name}").path)
                getByName("test").setRoot(rootProject.file("app/src/test").path)
                getByName("androidTest").setRoot(rootProject.file("app/src/androidTest").path)
                getByName("debug").setRoot(rootProject.file("app/src/debug").path)
            }

            signingConfigs {
                create("release") {
                    storeFile = file("../teou-release.jks")
                    keyAlias = "teou"
                    storePassword = System.getenv("STORE_PASSWORD")
                        ?: localProps.getProperty("STORE_PASSWORD", "")
                    keyPassword = System.getenv("KEY_PASSWORD")
                        ?: localProps.getProperty("KEY_PASSWORD", "")
                }
            }

            buildTypes {
                getByName("release") {
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

        dependencies {
            add("implementation", "androidx.core:core-ktx:1.19.1")
            add("implementation", "androidx.appcompat:appcompat:1.8.0")
            add("implementation", "com.google.android.material:material:1.14.0")

            add("implementation", "com.squareup.okhttp3:okhttp:5.5.0")
            add("implementation", "org.maplibre.gl:android-sdk:13.6.1")

            // QR pairing
            add("implementation", "com.google.zxing:core:3.5.4")

            add("implementation", "androidx.camera:camera-core:1.6.2")
            add("implementation", "androidx.camera:camera-camera2:1.6.2")
            add("implementation", "androidx.camera:camera-lifecycle:1.6.2")
            add("implementation", "androidx.camera:camera-view:1.6.2")

            add("testImplementation", "junit:junit:4.13.2")
            add("testImplementation", "org.json:json:20260814")
        }
    }
}
