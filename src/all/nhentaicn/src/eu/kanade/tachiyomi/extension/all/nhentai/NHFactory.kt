package eu.kanade.tachiyomi.extension.all.nhentaicn

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NHFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NHentai("zh", "chinese", NHentai.SOURCE_ID_ZH, "NHentai 中文/收藏"),
    )
}
