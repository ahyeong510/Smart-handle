pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        // (선택) 카카오맵 SDK 저장소가 필요할 때 대비
        maven(url = "https://devrepo.kakao.com/nexus/repository/kakaomap-releases/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://devrepo.kakao.com/nexus/repository/kakaomap-releases/")
    }
}

rootProject.name = "Smart-handle"
include(":app")
