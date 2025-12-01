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
    plugins {
        // ... other plugins like com.android.application, org.jetbrains.kotlin.android ...
        id("com.google.devtools.ksp") version "2.0.21-1.0.26" apply false // Check for latest KSP version compatible with your Kotlin
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MCQ App"
include(":app")
 