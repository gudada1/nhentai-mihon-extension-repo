package eu.kanade.tachiyomi.extension.all.nhentaicn

import keiyoushi.lib.cntagtranslator.CnTagTranslator

object NHUtils {
    fun getArtists(data: Hentai): String {
        val artists = data.tags.filter { it.type == "artist" }
        return artists.joinToString { it.name }
    }

    fun getGroups(data: Hentai): String? {
        val groups = data.tags.filter { it.type == "group" }
        return groups.joinToString { it.name }.takeIf { it.isNotBlank() }
    }

    fun getTagDescription(data: Hentai): String {
        val tags = data.tags.groupBy { it.type }
        return buildString {
            tags["category"]?.joinToString { it.name }?.let {
                append("分类：", CnTagTranslator.tags(it) ?: it, "\n")
            }
            tags["parody"]?.joinToString { it.name }?.let {
                append("原作：", it, "\n")
            }
            tags["character"]?.joinToString { it.name }?.let {
                append("角色：", it, "\n")
            }
            append("\n")
        }
    }

    fun getTags(data: Hentai): String {
        val tags = data.tags.filter { it.type == "tag" }
        return tags.map { CnTagTranslator.tag(it.name) }.sorted().joinToString()
    }
}
