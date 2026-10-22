plugins {
    id("com.chaquo.python")
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.dovecoteescapee.byedpi"
    compileSdk = 34
    if (!project.hasProperty("veyraPrebuiltNative")) ndkVersion = "28.2.13676358"
    packaging { jniLibs { useLegacyPackaging = true } }
    if (project.hasProperty("veyraPrebuiltNative")) sourceSets["main"].jniLibs.setSrcDirs(listOf("prebuilt-native/jniLibs"))

    defaultConfig {
        applicationId = if (project.hasProperty("veyraWifiTest")) "io.github.nosmokee.veyra.next.wifitest" else if (project.hasProperty("veyraIsolatedTest"))
            "io.github.nosmokee.veyra.next.unifiedtest" else "io.github.nosmokee.veyra.next"
        manifestPlaceholders["veyraAppLabel"] = if (project.hasProperty("veyraWifiTest")) "Veyra" else if (project.hasProperty("veyraIsolatedTest"))
            "Veyra" else "@string/app_name"
        minSdk = 24
        targetSdk = 34
        versionCode = 23
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86_64")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // Optional existing test keystore, kept outside the source archive.
    providers.gradleProperty("veyraSigningStore").orNull?.let { path ->
        signingConfigs.getByName("debug").storeFile = file(path)
    }
    buildTypes {
        release {
            if(project.hasProperty("veyraSigningStore")) signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "VERSION_NAME",  "\"${defaultConfig.versionName}\"")

            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            buildConfigField("String", "VERSION_NAME",  "\"${defaultConfig.versionName}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    if (!project.hasProperty("veyraPrebuiltNative")) {
        externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildFeatures {
        viewBinding = true
    }

    // https://android.izzysoft.de/articles/named/iod-scan-apkchecks?lang=en#blobs
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

dependencies {
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.takisoft.preferencex:preferencex:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

if (!project.hasProperty("veyraPrebuiltNative")) {
tasks.register<Exec>("runNdkBuild") {
    group = "build"

    val ndkDir = android.ndkDirectory
    executable = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        "$ndkDir\\ndk-build.cmd"
    } else {
        "$ndkDir/ndk-build"
    }
    setArgs(listOf(
        "NDK_PROJECT_PATH=build/intermediates/ndkBuild",
        "NDK_LIBS_OUT=src/main/jniLibs",
        "APP_BUILD_SCRIPT=src/main/jni/Android.mk",
        "NDK_APPLICATION_MK=src/main/jni/Application.mk"
    ))

    println("Command: $commandLine")
}

tasks.preBuild {
    dependsOn("runNdkBuild")
}
}

// Optional account-free protocol test, never part of normal builds.
tasks.register<JavaExec>("verifyTelegramLive") {
    dependsOn("compileDebugUnitTestKotlin")
    classpath = configurations.getByName("debugUnitTestRuntimeClasspath") + files(
        layout.buildDirectory.dir("tmp/kotlin-classes/debugUnitTest"),
        layout.buildDirectory.dir("tmp/kotlin-classes/debug"),
        layout.buildDirectory.dir("intermediates/javac/debugUnitTest/classes"))
    mainClass.set("io.github.dovecoteescapee.byedpi.core.TelegramLive")
    if (project.hasProperty("veyraSocksPort")) args(project.property("veyraSocksPort").toString())
}

// Explicit optional WSS timing smoke test through a caller-owned local SOCKS.
tasks.register<JavaExec>("verifyConnectionPing") {
    dependsOn("compileDebugUnitTestKotlin")
    classpath = configurations.getByName("debugUnitTestRuntimeClasspath") + files(
        layout.buildDirectory.dir("tmp/kotlin-classes/debugUnitTest"),
        layout.buildDirectory.dir("tmp/kotlin-classes/debug"),
        layout.buildDirectory.dir("intermediates/javac/debugUnitTest/classes"))
    mainClass.set("io.github.dovecoteescapee.byedpi.core.ConnectionPingLive")
    if (project.hasProperty("veyraSocksPort")) args(project.property("veyraSocksPort").toString())
}

chaquopy {
    sourceSets { getByName("main") { srcDir("../../telegram-engine"); exclude("python-libs/**", "__pycache__/**", "**/__pycache__/**", "*.pyc") } }
    defaultConfig {
        version = "3.13"
        buildPython("python3.13")
        pip { if (project.hasProperty("veyraOfflineWheels")) options("--no-index", "--find-links", project.property("veyraOfflineWheels").toString()); install("httpx[http2]==0.28.1"); install("-r", "../../telegram-engine/requirements.txt") }
    }
}
