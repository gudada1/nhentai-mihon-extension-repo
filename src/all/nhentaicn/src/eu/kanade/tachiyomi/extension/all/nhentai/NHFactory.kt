package eu.kanade.tachiyomi.extension.all.nhentaicn

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NHFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NHentai("en", "english", NHentai.SOURCE_ID_EN),
        NHentai("ja", "japanese", NHentai.SOURCE_ID_JA),
        NHentai("zh", "chinese", NHentai.SOURCE_ID_ZH),
        NHentai("all", "", NHentai.SOURCE_ID_ALL),
        NHentaiFavorites("all", "", NHentai.SOURCE_ID_FAVORITES),
    )
}
