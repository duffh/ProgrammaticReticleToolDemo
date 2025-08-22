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
    @Suppress("UnstableApiUsage")
    repositories {
        gradlePluginPortal()

        google()
        mavenCentral()
        maven { url = java.net.URI("https://esri.jfrog.io/artifactory/arcgis") }
    }
}

rootProject.name = "ProgrammaticReticleToolDemo"
include(":app")
 