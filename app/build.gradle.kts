import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Private local configuration or CI environment; no private key is tracked.
val releaseKeyProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use { load(it) }
}
fun signingValue(environment: String, property: String): String? =
    providers.environmentVariable(environment).orNull?.takeIf { it.isNotBlank() }
        ?: releaseKeyProperties.getProperty(property)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("RELEASE_STORE_FILE", "storeFile")
val releaseStorePassword = signingValue("RELEASE_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("RELEASE_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("RELEASE_KEY_PASSWORD", "keyPassword")
val signingValues = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val hasReleaseSigning = signingValues.all { it != null }
check(signingValues.all { it == null } || hasReleaseSigning) {
    "Incomplete Release signing configuration. See docs/RELEASE.md."
}

android {
    namespace = "com.yagay.ListCleaner"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }

    defaultConfig {
        applicationId = "com.yagay.ListCleaner"
        minSdk = 31
        targetSdk = 37
        versionCode = 31
        versionName = "1.6.6"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging.resources.merges += "META-INF/xposed/*"
    
    sourceSets {
        getByName("main") {
            resources.srcDirs("src/main/resources")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = signingValue("RELEASE_STORE_TYPE", "storeType") ?: "PKCS12"
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

// An unsigned build is allowed only when explicitly requested for CI validation.
val validateReleaseKey = tasks.register("validateReleaseKey") {
    val unsignedValidation = providers.gradleProperty("allowUnsignedRelease").orNull == "true"
    doLast {
        check(hasReleaseSigning || unsignedValidation) {
            "Release signing is required. Configure keystore.properties or RELEASE_* variables."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseKey)
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation("junit:junit:4.13.2")
}
