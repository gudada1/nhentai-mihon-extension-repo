package eu.kanade.tachiyomi.extension.all.mangadex

object MangaDexIntl {
    const val BRAZILIAN_PORTUGUESE = "pt-BR"
    const val CHINESE = "zh"
    const val CHINESE_SIMPLIFIED = "zh-Hans"
    const val CHINESE_TRADITIONAL = "zh-Hant"
    const val ENGLISH = "en"
    const val JAPANESE = "ja"
    const val KOREAN = "ko"
    const val PORTUGUESE = "pt"
    const val SPANISH_LATAM = "es-419"
    const val SPANISH = "es"
    const val RUSSIAN = "ru"

    val AVAILABLE_LANGS = setOf(
        ENGLISH,
        BRAZILIAN_PORTUGUESE,
        PORTUGUESE,
        SPANISH,
        SPANISH_LATAM,
        RUSSIAN,
        CHINESE_SIMPLIFIED,
        CHINESE_TRADITIONAL,
    )

    const val MANGADEX_NAME = "MangaDex"
}
