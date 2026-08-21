pluginManagement {
    repositories {
        maven { url = uri("https://chaquo.com/maven") }
                // ... your existing repos
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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Local FFmpegKitNext AAR produced by the cloned repo at D:\github\backend\ffmpeg-kit-next
        maven { url = uri("file:///D:/github/backend/ffmpeg-kit-next/prebuilt/bundle-android-aar-24-maven") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "VideoToMp3"
include(":app")
 