import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Optional: set a build-time default server URL, e.g.
//   ./gradlew :customer:assembleRelease -PSERVER_URL=https://railbite-api.onrender.com
val defaultServerUrl: String =
    (project.findProperty("SERVER_URL") as String?)
        ?: (project.findProperty("railbiteDefaultServerUrl") as String?)
        ?: "https://railbite-api.onrender.com"

android {
    namespace = "com.railbite.customer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.railbite.customer"
        minSdk = 29
        targetSdk = 35
        versionCode = 6
        versionName = "1.2.2"
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"$defaultServerUrl\"")
    }

    signingConfigs {
        create("release") {
            // Signing material lives in android/keystore.properties (gitignored).
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                val props = Properties().apply {
                    propsFile.inputStream().use { load(it) }
                }
                val ksFile = rootProject.file(props.getProperty("storeFile"))
                if (ksFile.exists()) {
                    storeFile = ksFile
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ""
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            // HiveMQ client bundles netty; every netty jar ships these markers
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // Firebase Phone Auth — requires google-services.json for com.railbite.customer
    // (enable "Phone" in the Firebase console + register the app's SHA-1/SHA-256).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.play.services.tasks)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
