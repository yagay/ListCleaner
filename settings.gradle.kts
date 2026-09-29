pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // libxposed/service is not reliably published to Maven Central. CI builds the matching
        // upstream sources into Maven Local first; developers can do the same when the artifact is
        // absent from their local Gradle cache.
        mavenLocal {
            content { includeGroup("io.github.libxposed") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "ListCleaner"
include(":app")
