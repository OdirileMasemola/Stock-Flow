import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Local API URL: set api.base.url in local.properties (gitignored).
// Emulator default is 10.0.2.2 (host loopback). Physical device: use your PC LAN IP.
val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}
val apiBaseUrl: String = localProperties.getProperty(
    "api.base.url",
    "https://stock-flow-trbq.onrender.com/"
)

// Upload key for Play. The keystore and passwords stay outside the repo.
data class UploadSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

fun loadUploadSigning(): UploadSigning? {
    val env = System.getenv()
    val propsPath = env["STOCKFLOW_SIGNING_PROPERTIES"]?.takeIf { it.isNotBlank() }
        ?: System.getProperty("user.home") + File.separator + ".android" + File.separator +
        "stockflow-signing.properties"
    val props = Properties()
    val propsFile = File(propsPath)
    if (propsFile.isFile) {
        propsFile.inputStream().use { props.load(it) }
    }
    fun pick(prop: String, envKey: String): String? =
        env[envKey]?.takeIf { it.isNotBlank() } ?: props.getProperty(prop)?.takeIf { it.isNotBlank() }

    val store = pick("storeFile", "STOCKFLOW_UPLOAD_STORE_FILE")
    val storePassword = pick("storePassword", "STOCKFLOW_UPLOAD_STORE_PASSWORD")
    val keyAlias = pick("keyAlias", "STOCKFLOW_UPLOAD_KEY_ALIAS")
    val keyPassword = pick("keyPassword", "STOCKFLOW_UPLOAD_KEY_PASSWORD")
    if (store == null || storePassword == null || keyAlias == null || keyPassword == null) {
        return null
    }
    val storeFile = File(store)
    if (!storeFile.isFile) return null
    return UploadSigning(storeFile, storePassword, keyAlias, keyPassword)
}

val uploadSigning = loadUploadSigning()

android {
    namespace = "com.odirilemasemola.stockflow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.odirilemasemola.stockflow"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    if (uploadSigning != null) {
        signingConfigs {
            create("release") {
                storeFile = uploadSigning.storeFile
                storePassword = uploadSigning.storePassword
                keyAlias = uploadSigning.keyAlias
                keyPassword = uploadSigning.keyPassword
            }
        }
    }

    buildTypes {
        release {
            if (uploadSigning != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

if (uploadSigning == null) {
    val releasePackaging = Regex("^(package|bundle|assemble).*Release.*")
    tasks.configureEach {
        if (releasePackaging.matches(name)) {
            doFirst {
                throw GradleException(
                    "Release signing is not configured, so this release task is refused " +
                        "(debug signing is not used for release). Create the upload keystore, then " +
                        "put storeFile, storePassword, keyAlias and keyPassword in " +
                        "<user home>/.android/stockflow-signing.properties (or the file named by " +
                        "STOCKFLOW_SIGNING_PROPERTIES). Or set STOCKFLOW_UPLOAD_STORE_FILE, " +
                        "STOCKFLOW_UPLOAD_STORE_PASSWORD, STOCKFLOW_UPLOAD_KEY_ALIAS and " +
                        "STOCKFLOW_UPLOAD_KEY_PASSWORD. storeFile must point at a keystore that exists."
                )
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation(libs.material)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.coil)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.play.services.location)
    implementation(libs.androidx.security.crypto)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Keep google-services.json local/untracked; apply plugin only when the file is present.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
