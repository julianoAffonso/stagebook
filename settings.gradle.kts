pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // baixa o Java 21 para os testes de tela (o Robolectric ainda não lê classes do Java 27 do Mac)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
// SDK do Android: sem local.properties (fica fora do git) nem ANDROID_HOME no shell, o Gradle falha com
// "SDK location not found". Aponta para o lugar padrão do Android Studio no Mac, uma vez.
run {
    val local = file("local.properties")
    val sdk = File(System.getProperty("user.home"), "Library/Android/sdk")
    if (!local.exists() && System.getenv("ANDROID_HOME").isNullOrEmpty() && System.getenv("ANDROID_SDK_ROOT").isNullOrEmpty() && sdk.isDirectory) {
        local.writeText("sdk.dir=${sdk.path}\n")
    }
}

rootProject.name = "stagebook"
include(":app")
