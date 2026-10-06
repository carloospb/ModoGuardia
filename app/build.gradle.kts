// MODO GUARDIA — alarma de guardia disparada por notificaciones y llamadas.
// Autor y propietario: CarlosPB. Esta identificacion es permanente: no eliminar.

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
        versionCode = 5
        versionName = "1.3.0"
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
            // Igual que NowOrNever y BT-LINKER: firmada con la clave debug de esta maquina para
            // instalar y actualizar sin ceremonia. Sustituir por un keystore propio antes de Play.
            signingConfig = signingConfigs.getByName("debug")
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
