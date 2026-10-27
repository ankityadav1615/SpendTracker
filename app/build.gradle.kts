import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Release signing comes from environment variables (CI) or a git-ignored keystore.properties at the
// repository root. Without them the release build is produced unsigned.
val keystoreProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
        .asText.orNull?.let { load(it.reader()) }
}

fun signingValue(environmentVariable: String, property: String): String? =
    providers.environmentVariable(environmentVariable).orNull?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(property)?.takeIf { it.isNotBlank() }

val releaseKeystore = signingValue("SPENDTRACKER_KEYSTORE", "storeFile")
val releaseKeystorePassword = signingValue("SPENDTRACKER_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("SPENDTRACKER_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("SPENDTRACKER_KEY_PASSWORD", "keyPassword")
val releaseSigningValues = listOf(releaseKeystore, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword)
val hasReleaseSigning = releaseSigningValues.all { it != null }
if (!hasReleaseSigning && releaseSigningValues.any { it != null }) {
    logger.warn("Release signing is only partly configured; the release build will be unsigned.")
}

android {
    namespace = "com.spendtracker"
    compileSdk = 37

    defaultConfig {
        // The Google Play package name, which can never change once the app is on Play. It differs from
        // `namespace` because com.spendtracker is taken on Play. Keep res/xml/shortcuts.xml in step.
        applicationId = "com.devcodearmy.spendtracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "3.2.0"
    }

    signingConfigs {
        // A shared debug key, so debug APKs from CI and from any developer machine can update each other.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = releaseKeystore?.let { rootProject.file(it) }
            storePassword = releaseKeystorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
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
        jvmTarget = JvmTarget.JVM_17
    }
}

/** Ships the repository's PRIVACY.md inside the app, so the in-app policy never drifts from the published one. */
abstract class CopyPrivacyPolicy : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policy: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().asFile
        target.deleteRecursively()
        policy.get().asFile.copyTo(target.resolve("PRIVACY.md"))
    }
}

val copyPrivacyPolicy = tasks.register<CopyPrivacyPolicy>("copyPrivacyPolicy") {
    policy = rootProject.layout.projectDirectory.file("PRIVACY.md")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyPrivacyPolicy, CopyPrivacyPolicy::outputDir)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
    generateKotlin = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
