// MODO GUARDIA — alarma de guardia disparada por notificaciones y llamadas.
// Autor y propietario: CarlosPB. Esta identificacion es permanente: no eliminar.

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "es.carlospb.modoguardia"
    compileSdk = 35

    defaultConfig {
        applicationId = "es.carlospb.modoguardia"
        // Android 10. Por debajo no hay setShowWhenLocked/turnScreenOn ni canales maduros.
        minSdk = 29
        targetSdk = 35
        versionCode = 7
        versionName = "1.3.2"
        buildConfigField("String", "AUTHOR", "\"CarlosPB\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sin firma aqui: la firma la tarea signReleaseApk (abajo) con la clave propia y la
            // prueba de rotacion desde la clave debug, que AGP no sabe aplicar.
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
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric necesita los recursos de la app (tema, fuente, cadenas).
        unitTests.isIncludeAndroidResources = true
        // Cada clase de prueba en su propia JVM: las pruebas de interfaz con Robolectric comparten
        // estado estático (DataStore, Room, Dispatchers.Main) si van en la misma, y fallaban a ratos.
        // En el teléfono solo hay una Application por proceso, así que la app no tiene ese problema.
        unitTests.all { it.forkEvery = 1 }
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
}

/*
 * Firma de publicacion con rotacion de clave (APK Signature Scheme v3).
 *
 * Las versiones 1.0.0-1.3.0 se firmaron con la clave debug de este PC (CN=Android Debug). Desde la
 * 1.3.1 se firman con la clave propia de CarlosPB, y la APK lleva la prueba, firmada por la clave
 * antigua, de que la nueva la sustituye: Android (9+) instala la actualizacion encima conservando
 * reglas e historial. Clave, contrasena y prueba viven FUERA del repositorio, en
 * ~/.modoguardia/signing.properties (ver docs/FIRMA.md). Sin ese archivo la release no se firma
 * y la compilacion falla: una release firmada con la clave debug ya no se instalaria encima.
 */
val signing = Properties().apply {
    val f = file("${System.getProperty("user.home")}/.modoguardia/signing.properties")
    if (f.exists()) f.inputStream().use(::load)
}

val signReleaseApk by tasks.registering {
    description = "Firma la APK release con la clave propia y la rotacion desde la clave debug."
    val outDir = layout.buildDirectory.dir("outputs/apk/release")
    doLast {
        if (signing.getProperty("storeFile") == null) {
            throw GradleException("Falta ~/.modoguardia/signing.properties: no se puede firmar la release (ver docs/FIRMA.md).")
        }
        val dir = outDir.get().asFile
        val unsigned = File(dir, "app-release-unsigned.apk")
        val signed = File(dir, "app-release.apk")
        val apksigner = android.sdkDirectory.resolve("build-tools/${android.buildToolsVersion}/lib/apksigner.jar")
        val java = File(System.getProperty("java.home"), "bin/java").path
        project.exec {
            environment("MG_KS_PASS", signing.getProperty("storePassword"))
            environment("MG_KEY_PASS", signing.getProperty("keyPassword"))
            commandLine(
                java, "-jar", apksigner.path, "sign",
                "--ks", signing.getProperty("storeFile"),
                "--ks-key-alias", signing.getProperty("keyAlias"),
                "--ks-pass", "env:MG_KS_PASS",
                "--key-pass", "env:MG_KEY_PASS",
                "--lineage", signing.getProperty("lineageFile"),
                // La rotacion vale desde Android 9; la app pide Android 10 o superior.
                "--rotation-min-sdk-version", "28",
                "--min-sdk-version", "29",
                // Solo v3: Android 10+ la verifica primero, y asi la APK no lleva el certificado debug
                // en ninguna firma (con v2 habria que firmar tambien con la clave antigua).
                "--v1-signing-enabled", "false",
                "--v2-signing-enabled", "false",
                "--out", signed.path,
                unsigned.path,
            )
        }
        project.exec {
            commandLine(java, "-jar", apksigner.path, "verify", "--min-sdk-version", "29", signed.path)
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(signReleaseApk) }
