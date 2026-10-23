plugins {
    id("com.android.application")
}

val workspaceManifest = rootProject.file("../../Cargo.toml")
val workspacePackage = workspaceManifest.readText().substringAfter("[workspace.package]")
val versionLine = workspacePackage.lineSequence().firstOrNull { it.trim().startsWith("version =") }
    ?: throw GradleException("Could not find [workspace.package].version in $workspaceManifest")
val workspaceVersionName = versionLine.substringAfter('"').substringBefore('"')
val versionParts = workspaceVersionName.substringBefore('-').split('.').map {
    it.toIntOrNull() ?: throw GradleException("Invalid VectorCraft version: $workspaceVersionName")
}
if (versionParts.size != 3) {
    throw GradleException("Expected a three-part VectorCraft version, got: $workspaceVersionName")
}
val androidVersionCode = versionParts[0] * 1_000_000 + versionParts[1] * 1_000 + versionParts[2]

val releaseSigningValues = listOf(
    providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull.orEmpty(),
    providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull.orEmpty(),
    providers.environmentVariable("ANDROID_KEY_ALIAS").orNull.orEmpty(),
    providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull.orEmpty(),
)
val hasReleaseSigning = releaseSigningValues.all { it.isNotBlank() }

val generatedIconResources = layout.buildDirectory.dir("generated/vectorcraft-icon-res")
val copyLauncherIcon = tasks.register<Copy>("copyVectorCraftLauncherIcon") {
    from(rootProject.file("../../assets/app-icon/hicolor/512x512/apps/ai.storyteller.vectorcraft.png"))
    into(generatedIconResources.map { it.dir("mipmap-xxxhdpi") })
    rename { "ic_launcher.png" }
}

val webAssets = rootProject.file("../../dist/web")
val verifyWebAssets = tasks.register("verifyVectorCraftWebAssets") {
    doLast {
        val hasWasm = webAssets.listFiles()?.any { it.extension.equals("wasm", ignoreCase = true) } == true
        val hasJs = webAssets.listFiles()?.any { it.extension.equals("js", ignoreCase = true) } == true
        if (!webAssets.resolve("index.html").isFile || !hasWasm || !hasJs) {
            throw GradleException("VectorCraft web assets are missing in ${webAssets.absolutePath}; run packaging/android/build.sh first")
        }
    }
}

android {
    namespace = "ai.storyteller.vectorcraft"
    compileSdk = 36

    defaultConfig {
        applicationId = "ai.storyteller.vectorcraft"
        minSdk = 23
        targetSdk = 36
        versionCode = androidVersionCode
        versionName = workspaceVersionName
    }

    sourceSets.getByName("main").assets.srcDir(webAssets)
    sourceSets.getByName("main").res.srcDir(generatedIconResources)

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("playStore") {
                storeFile = file(releaseSigningValues[0])
                storePassword = releaseSigningValues[1]
                keyAlias = releaseSigningValues[2]
                keyPassword = releaseSigningValues[3]
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("playStore")
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(copyLauncherIcon, verifyWebAssets)
}

tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
    doFirst {
        if (!hasReleaseSigning) {
            throw GradleException(
                "Play Store builds require ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD"
            )
        }
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.13.0")
}
