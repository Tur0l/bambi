plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
}

import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// APK para publicar en GitHub: si existe .public_build con "1", se compila SIN ninguna clave ni dato privado
// (ni las claves iniciales ni el secreto de Trakt), pase lo que pase en local.properties.
val publicBuild: Boolean = rootProject.file(".public_build").let { it.exists() && it.readText().trim() == "1" }
val seedKeys: Boolean = !publicBuild && localProps.getProperty("seedKeys")?.trim()?.lowercase() == "true"
// Fork táctil: APK con claves para UN aparato concreto (p.ej. la tele de los padres), nunca para publicar:
//   bash ./gradlew :app:assembleRelease -PseedFile=/ruta/claves.properties
// Las claves se guardan una vez al primer arranque y sobreviven a las actualizaciones normales (que van sin claves).
val seedFileProps: Properties? = (findProperty("seedFile") as String?)?.let { path ->
    Properties().apply { file(path).inputStream().use { load(it) } }
}
fun seed(name: String): String =
    seedFileProps?.getProperty(name)?.trim() ?: if (seedKeys) localProps.getProperty(name) ?: "" else ""
fun privateProp(name: String): String = if (publicBuild) "" else localProps.getProperty(name)?.trim() ?: ""
val rdToken: String = seed("rdToken")
val subdlApiKey: String = seed("subdlApiKey")
val tmdbApiKey: String = seed("tmdbApiKey")
val allDebridKey: String = seed("allDebridKey")
val seedDebrid: String = seed("seedDebrid")
val traktClientId: String = localProps.getProperty("traktClientId")?.trim() ?: ""
val traktClientSecret: String = privateProp("traktClientSecret")
val traktRedirectUri: String = localProps.getProperty("traktRedirectUri")?.trim() ?: "urn:ietf:wg:oauth:2.0:oob"

android {
    namespace = "com.shieldstream.tv"
    compileSdk = 37

    defaultConfig {
        // Fork táctil: identificador propio para convivir con el Bambi original (firmado con otra clave).
        applicationId = "com.shieldstream.tv.tactil"
        minSdk = 21
        targetSdk = 35
        // Versión de upstream + revisión del fork: 1.0.8.1 = 1.0.8 de grodri99 con nuestra revisión 1.
        // versionCode = el de upstream ×100 + revisión, para que siempre suba al traer una versión nueva de upstream.
        versionCode = 10802
        versionName = "1.0.8.2"
        manifestPlaceholders["appLabel"] = "@string/app_name"

        buildConfigField("String", "RD_TOKEN", "\"$rdToken\"")
        buildConfigField("String", "SUBDL_API_KEY", "\"$subdlApiKey\"")
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "ALLDEBRID_KEY", "\"$allDebridKey\"")
        buildConfigField("String", "SEED_DEBRID", "\"$seedDebrid\"")
        buildConfigField("String", "TRAKT_CLIENT_ID", "\"$traktClientId\"")
        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"$traktClientSecret\"")
        buildConfigField("String", "TRAKT_REDIRECT_URI", "\"$traktRedirectUri\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(localProps.getProperty("releaseStoreFile") ?: "keystore/butaca.keystore")
            storePassword = localProps.getProperty("releaseStorePassword") ?: "android"
            keyAlias = localProps.getProperty("releaseKeyAlias") ?: "androiddebugkey"
            keyPassword = localProps.getProperty("releaseKeyPassword") ?: "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-pruebas"
            manifestPlaceholders["appLabel"] = "Bambi (pruebas)"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
        // Release-like performance testing without touching the stable app or enabling self-updates.
        // Shares the debug package/data, but is non-debuggable so AGP packages the baseline profile.
        create("benchmark") {
            initWith(getByName("release"))
            isDebuggable = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-benchmark"
            manifestPlaceholders["appLabel"] = "Bambi (benchmark)"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:strings"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:media"))
    implementation(project(":core:ui"))
    implementation(project(":feature:home"))
    implementation(project(":feature:details"))
    implementation(project(":feature:player"))
    implementation(project(":feature:settings"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.tvprovider)
    implementation(libs.coil)
    implementation(files("libs/media3-decoder-av1-1.6.0.aar"))
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
