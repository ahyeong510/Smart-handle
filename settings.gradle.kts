pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // ✅ Mapbox SDK 저장소 (토큰 인증 포함)
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            credentials {
                username = "mapbox"
                password = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orNull
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}

rootProject.name = "SMART-HANDLE"
include(":app")
