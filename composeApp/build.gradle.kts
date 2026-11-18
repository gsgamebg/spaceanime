import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val releaseSigningVariables = listOf(
    "SPACEANIME_KEYSTORE_FILE",
    "SPACEANIME_KEYSTORE_PASSWORD",
    "SPACEANIME_KEY_ALIAS",
    "SPACEANIME_KEY_PASSWORD",
)
val releaseVersion = System.getenv("RELEASE_VERSION") ?: "0.7.19"
val releaseCode = System.getenv("RELEASE_CODE")?.let { raw ->
    raw.toIntOrNull() ?: error("RELEASE_CODE must be a positive integer.")
} ?: 35
require(releaseCode > 0) { "RELEASE_CODE must be a positive integer." }
require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(releaseVersion)) {
    "RELEASE_VERSION must use the format major.minor.patch."
}
val updateSource = file("src/commonMain/kotlin/app/anim/update/Updates.kt").readText()
val sourceVersion = Regex("""const val APP_VERSION = "([0-9.]+)"""")
    .find(updateSource)?.groupValues?.get(1) ?: error("APP_VERSION is missing from Updates.kt.")
val sourceCode = Regex("""const val APP_VERSION_CODE = ([0-9]+)""")
    .find(updateSource)?.groupValues?.get(1)?.toInt() ?: error("APP_VERSION_CODE is missing from Updates.kt.")
require(releaseVersion == sourceVersion && releaseCode == sourceCode) {
    "RELEASE_VERSION/RELEASE_CODE must match APP_VERSION/APP_VERSION_CODE in Updates.kt."
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("io.coil-kt.coil3:coil-compose:3.3.0")
            implementation(libs.coroutines.core)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.okhttp)
                implementation(libs.jsoup)
                implementation(libs.json)
                implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
            }
        }
        androidMain.get().dependsOn(jvmSharedMain)
        getByName("desktopMain").dependsOn(jvmSharedMain)
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.firebase.messaging)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.hls)
            implementation(libs.media3.dash)
            implementation(libs.media3.ui)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.vlcj)
                implementation(libs.coroutines.swing)
            }
        }
    }
}

dependencies {
    add("androidMainImplementation", platform(libs.firebase.bom))
}

tasks.register<Zip>("packageReleaseWindowsZip") {
    dependsOn("createReleaseDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main-release/app/SpaceAnime"))
    archiveFileName.set("SpaceAnime-$releaseVersion-windows.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    doLast {
        val missing = releaseSigningVariables.filter { System.getenv(it).isNullOrBlank() }
        check(missing.isEmpty()) {
            "Release signing is not configured. Set: ${missing.joinToString(", ")}."
        }
    }
}

tasks.matching { it.name == "validateSigningRelease" }.configureEach {
    dependsOn(verifyReleaseSigning)
}
tasks.matching { it.name == "packageRelease" || it.name == "packageReleaseBundle" }.configureEach {
    dependsOn(verifyReleaseSigning)
}

compose.resources { packageOfResClass = "app.anim.resources" }

android {
    namespace = "app.anim"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.anim"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseCode
        versionName = releaseVersion
    }
    signingConfigs {
        create("release") {
            storeFile = System.getenv("SPACEANIME_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let(::file)
            storePassword = System.getenv("SPACEANIME_KEYSTORE_PASSWORD").orEmpty()
            keyAlias = System.getenv("SPACEANIME_KEY_ALIAS").orEmpty()
            keyPassword = System.getenv("SPACEANIME_KEY_PASSWORD").orEmpty()
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "app.anim.MainKt"
        buildTypes.release.proguard {
            configurationFiles.from(project.file("desktop-proguard-rules.pro"))
        }
        nativeDistributions {
            includeAllModules = true
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "SpaceAnime"
            packageVersion = releaseVersion
            windows { iconFile.set(rootProject.file("assets/branding/spaceanime.ico")) }
        }
    }
}

tasks.withType<Test>().configureEach {
    inputs.property("nativePlaybackSmoke", providers.environmentVariable("ANIM_PLAYER_SMOKE").orElse("0"))
    inputs.property("liveSourceSmoke", providers.environmentVariable("ANIM_SOURCE_SMOKE").orElse("0"))
    inputs.property("bloggerSmoke", providers.environmentVariable("ANIM_BLOGGER_SMOKE").orElse("0"))
}









