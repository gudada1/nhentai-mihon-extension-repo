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
include(":lib:cntagtranslator")
include(":lib:i18n")
include(":lib:randomua")
include(":lib-multisrc:galleryadults")
include(":src:all:akumacn")
include(":src:all:asmhentaicn")
include(":src:all:ehentaicn")
include(":src:all:hdoujincn")
include(":src:all:hentaifoxcn")
include(":src:all:hitomicn")
include(":src:all:mangadexcn")
include(":src:all:nhentaicn")
include(":src:all:pixivcn")
include(":src:zh:mycomiccn")
