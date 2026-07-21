import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    id("com.google.gms.google-services")
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.navigation.compose)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)

            implementation(libs.gitlive.firebase.common)
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.storage)

            implementation(libs.multiplatform.settings)

            implementation(libs.kotlincrypto.hmac.sha2)
            implementation(libs.kotlincrypto.secure.random)

            implementation(libs.coil.compose)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.activity.ktx)
            implementation(compose.uiTooling)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.googleid)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.example.offline_first"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.offline_first_v2"
        minSdk = 24
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // GitLive's Android artifacts depend on com.google.firebase:* with no version,
    // expecting the Firebase BoM to pin them — without it dependency resolution fails.
    add("androidMainImplementation", platform(libs.firebase.bom))

    add("kspAndroid", libs.room.compiler)
    add("kspIosX64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}
