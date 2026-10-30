pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // downloads Java 21 for the screen tests (Robolectric can't read the Mac's Java 27 classes yet)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
// Android SDK: without local.properties (kept out of git) or ANDROID_HOME in the shell, Gradle fails with "SDK location
// not found". Points to Android Studio's default location on the Mac, once.
run {
    val local = file("local.properties")
    val sdk = File(System.getProperty("user.home"), "Library/Android/sdk")
    if (!local.exists() && System.getenv("ANDROID_HOME").isNullOrEmpty() && System.getenv("ANDROID_SDK_ROOT").isNullOrEmpty() && sdk.isDirectory) {
        local.writeText("sdk.dir=${sdk.path}\n")
    }
}

rootProject.name = "stagebook"
include(":app")
