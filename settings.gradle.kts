@file:Suppress("ktlint:standard:kdoc")

pluginManagement {
    includeBuild("gradle/build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://www.jitpack.io")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("kei") {
            from(files("gradle/kei.versions.toml"))
        }
    }

    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Nhentai-Mihon-Extension"

include(":core")
include(":lib:i18n")
include(":lib:randomua")
include(":lib-multisrc:galleryadults")
include(":src:all:akuma")
include(":src:all:asmhentai")
include(":src:all:ehentai")
include(":src:all:hdoujin")
include(":src:all:hentaifox")
include(":src:all:hitomi")
include(":src:all:mangadex")
include(":src:all:nhentaicn")
include(":src:all:pixiv")
include(":src:zh:mycomic")
