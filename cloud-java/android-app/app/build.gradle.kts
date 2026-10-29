import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val useMockRepository = providers.gradleProperty("yingge.useMockRepository")
    .map(String::toBoolean)
    .orElse(false)

val releaseSigningPropertiesFile = providers.gradleProperty("yingge.signingProperties")
    .orNull
    ?.let(rootProject::file)
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile?.isFile == true) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}
fun signingSecret(valueKey: String, fileKey: String): String? {
    releaseSigningProperties.getProperty(valueKey)?.takeIf(String::isNotBlank)?.let { return it }
    val secretFile = releaseSigningProperties.getProperty(fileKey)
        ?.takeIf(String::isNotBlank)
        ?.let(rootProject::file)
    return secretFile?.takeIf { it.isFile }?.readText()?.trim()?.takeIf(String::isNotBlank)
}
val releaseStorePassword = signingSecret("storePassword", "storePasswordFile")
val releaseKeyPassword = signingSecret("keyPassword", "keyPasswordFile")
val releaseSigningConfigured = releaseSigningPropertiesFile?.isFile == true &&
    !releaseSigningProperties.getProperty("storeFile").isNullOrBlank() &&
    !releaseSigningProperties.getProperty("keyAlias").isNullOrBlank() &&
    releaseStorePassword != null && releaseKeyPassword != null

android {
    namespace = "com.eagleslab.yingge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eagleslab.yingge"
        minSdk = 28
        targetSdk = 35
        versionCode = 8
        versionName = "0.2.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseStorePassword
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Preview data is opt-in: ./gradlew assembleDebug -Pyingge.useMockRepository=true
            buildConfigField("boolean", "USE_MOCK_REPOSITORY", useMockRepository.get().toString())
        }
        release {
            // A release artifact must never be able to select the preview repository.
            buildConfigField("boolean", "USE_MOCK_REPOSITORY", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

val verifyReleaseSigningConfigured = tasks.register("verifyReleaseSigningConfigured") {
    group = "verification"
    description = "Fails release builds unless an external signing properties file is complete."
    doLast {
        check(releaseSigningConfigured) {
            "Release signing is required. Pass -Pyingge.signingProperties=/absolute/path/to/keystore.properties"
        }
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }
    .configureEach { dependsOn(verifyReleaseSigningConfigured) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("verifyReleaseRoleBounded") {
    group = "verification"
    description = "Fails if release contains preview identities or an unbounded dynamic HTTP proxy."
    dependsOn("assembleRelease")
    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        check(apk.isFile) { "Release APK was not produced" }
        val forbidden = listOf("preview-user", "PREVIEW_READ_ONLY", "yingge.useMockRepository=true")
        val dexPayload = zipTree(apk).matching { include("classes*.dex") }.files
            .joinToString(separator = "") { it.readBytes().toString(Charsets.ISO_8859_1) }
        forbidden.forEach { marker -> check(!dexPayload.contains(marker)) { "Forbidden release marker found: $marker" } }
    }
}

tasks.register("verifyReleaseWebViewBoundaries") {
    group = "verification"
    description = "Allows android.webkit.WebView only in the QQ workbench screen."
    doLast {
        val sourceRoot = project.file("src/main/java")
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "QqWebViewScreen.kt" }
            .filter { file ->
                val body = file.readText()
                body.contains("android.webkit.WebView") || body.contains("androidx.compose.ui.viewinterop.AndroidView")
            }
            .map { it.relativeTo(project.projectDir).path }
            .toList()
        check(offenders.isEmpty()) { "Only QQ may use WebView. Offenders=$offenders" }
    }
}

val verifyReleaseJsonModelsNotObfuscated = tasks.register("verifyReleaseJsonModelsNotObfuscated") {
    group = "verification"
    description = "Fails when R8 renames a Gson wire model used by the release APK."
    dependsOn("assembleRelease")
    doLast {
        val mapping = layout.buildDirectory.file("outputs/mapping/release/mapping.txt").get().asFile
        check(mapping.isFile) { "Release R8 mapping was not produced" }
        val content = mapping.readText()
        val wireModels = listOf(
            "com.eagleslab.yingge.data.contract.MobileLoginRequest",
            "com.eagleslab.yingge.data.contract.MobileRegisterRequest",
            "com.eagleslab.yingge.data.contract.MobileTokenResponse",
            "com.eagleslab.yingge.data.contract.ApiErrorDto",
            "com.eagleslab.yingge.data.CustomerDto",
            "com.eagleslab.yingge.data.CustomerUpsertDto",
            "com.eagleslab.yingge.data.InteractionCreateDto",
            "com.eagleslab.yingge.data.DailyUpsertDto",
            "com.eagleslab.yingge.data.ConsultationCreateDto",
            "com.eagleslab.yingge.data.ContentPostCreateDto",
        )
        wireModels.forEach { model ->
            check(content.contains("$model -> $model:")) {
                "Release JSON model was renamed or removed by R8: $model"
            }
        }
    }
}

tasks.named("verifyReleaseRoleBounded") { dependsOn(verifyReleaseJsonModelsNotObfuscated, "verifyReleaseWebViewBoundaries") }

tasks.register("verifyMobileContractCatalog") {
    group = "verification"
    description = "Bidirectionally compares Android METHOD|path operations with contracts/mobile-api.yaml."
    doLast {
        val contract = rootProject.file("../contracts/mobile-api.yaml")
        val catalog = project.file("src/main/java/com/eagleslab/yingge/data/contract/EndpointCatalog.kt")
        check(contract.isFile) { "Missing shared mobile contract: $contract" }
        val expected = Regex("\\\"ANDROID(?:,IOS)?\\|([A-Z]+)\\|([^\\\"]+)\\\"")
            .findAll(contract.readText()).map { "${it.groupValues[1]}|${it.groupValues[2]}" }.toSet()
        val actual = Regex("\\\"(GET|POST|PUT|PATCH|DELETE)\\|(/api/v1/[^\\\"]+)\\\"")
            .findAll(catalog.readText()).map { "${it.groupValues[1]}|${it.groupValues[2]}" }.toSet()
        check(expected == actual) {
            "Android contract catalog drift. Missing=${expected - actual}; extra=${actual - expected}"
        }
    }
}

tasks.named("check") { dependsOn("verifyMobileContractCatalog", "verifyReleaseWebViewBoundaries") }
