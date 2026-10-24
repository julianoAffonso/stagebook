import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

// Versão automática pelo git: <base>.<nº de commits> (commit); "+" = build com mudanças não commitadas.
// O nº de commits só cresce, então cada APK novo instala por cima do anterior.
fun git(vararg args: String) = providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim()
val commits = git("rev-list", "--count", "HEAD").toInt()
val commit = git("rev-parse", "--short", "HEAD") + if (git("status", "--porcelain").isNotEmpty()) "+" else ""

// Chave de assinatura das versões publicadas: fica FORA do repositório (o código é público). Sem ela, o
// release não assina e não pode ser publicado. Perder a chave = os aparelhos não aceitam mais atualizações.
val releaseKey = Properties()
val releaseKeyFile = file(System.getProperty("user.home") + "/.android/stagebook-release.properties")
if (releaseKeyFile.exists()) releaseKeyFile.inputStream().use { releaseKey.load(it) }

android {
    namespace = "com.juliano.stagebook"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.juliano.stagebook"
        minSdk = 28          // Android 9: Fire HD 10 (2019) e tablets mais simples
        targetSdk = 37
        versionCode = commits
        versionName = "0.1.$commits ($commit)"
    }

    signingConfigs {
        if (releaseKey.isNotEmpty()) create("release") {
            storeFile = file(releaseKey.getProperty("storeFile"))
            storePassword = releaseKey.getProperty("storePassword")
            keyAlias = releaseKey.getProperty("keyAlias")
            keyPassword = releaseKey.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    // capturas de tela das telas (Robolectric + Roborazzi): ./gradlew recordRoborazziDebug
    testOptions { unitTests.isIncludeAndroidResources = true }

    // motor de áudio (player): vem pronto do build_native.sh, o mesmo comando do motor validado
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("native").get().asFile)
    // mesmo efeito do android:extractNativeLibs="true" da versão validada
    packaging { jniLibs { useLegacyPackaging = true } }
}

// arm64 = TCL e celulares; armeabi-v7a = Fire HD 10 (Fire OS 32 bits); x86_64 = emulador
val buildNative by tasks.registering(Exec::class) {
    val native = layout.buildDirectory.dir("native").get().asFile
    inputs.files("src/main/cpp/engine.c", "src/main/cpp/third_party/stb_vorbis.c", "src/main/cpp/third_party/dr_mp3.h", "src/main/cpp/third_party/dr_wav.h", "build_native.sh")
    outputs.dir(native)
    commandLine("./build_native.sh", native.absolutePath)
}
tasks.named("preBuild") { dependsOn(buildNative) }

tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}

// Nomes de teste são frases entre crases, e o Kotlin/JVM recusa . ; : / [ ] < > \ neles ("Name contains
// illegal characters"), só depois de uma compilação inteira. Esta checagem roda antes e diz onde
// trocar (o separador do projeto é " - ").
val checkTestNames by tasks.registering {
    val sources = fileTree("src/test") { include("**/*.kt") }
    inputs.files(sources)
    doLast {
        val name = Regex("""fun\s+`([^`]*)`""")
        val bad = sources.files.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                name.find(line)?.groupValues?.get(1)?.takeIf { n -> n.any { it in ".;:/[]<>\\" } }
                    ?.let { "${f.relativeTo(projectDir)}:${i + 1}: `$it`" }
            }
        }
        if (bad.isNotEmpty()) {
            throw GradleException("Nome de teste com caractere que o Kotlin recusa (. ; : / [ ] < > \\), troque por \" - \":\n" + bad.joinToString("\n"))
        }
    }
}
tasks.matching { it.name.startsWith("compile") && it.name.endsWith("UnitTestKotlin") }.configureEach { dependsOn(checkTestNames) }

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.reorderable)
    implementation(libs.zxing.core)
    // leitor de QR do setlist (Letras ou Stagebook): CameraX + ZXing, sem serviço do Google
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
