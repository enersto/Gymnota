pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven("${System.getenv("FLUTTER_STORAGE_BASE_URL") ?: "https://storage.googleapis.com"}/download.flutter.io")
    }
}

rootProject.name = "myfit"
include(":app")

// Generate this wrapper with `flutter pub get` in flutter_library.
apply(from = File(settingsDir, "flutter_library/.android/include_flutter.groovy"))
