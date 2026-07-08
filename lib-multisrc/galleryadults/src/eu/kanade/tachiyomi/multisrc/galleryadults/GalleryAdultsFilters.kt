package eu.kanade.tachiyomi.multisrc.galleryadults

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val uri: String) : Filter.CheckBox(name)
class GenresFilter(genres: Map<String, String>) :
    Filter.Group<Genre>(
        "标签",
        genres.map { Genre(it.key, it.value) },
    )

class SortOrderFilter(sortOrderURIs: List<Pair<String, String>>) : Filter.Select<String>("排序", sortOrderURIs.map { it.first }.toTypedArray())

class ChineseOnlyFilter(state: Boolean = false) : Filter.CheckBox("只显示中文（脚本规则）", state)

class AnimatedFilter :
    Filter.Select<String>(
        "动图/GIF",
        arrayOf("不限", "只显示动图", "排除动图/GIF"),
    ) {
    fun queryTerms(): List<String> = when (state) {
        1 -> listOf("animated")
        2 -> listOf("-animated", "-gif")
        else -> emptyList()
    }

    fun advancedTerms(): List<String> = when (state) {
        1 -> listOf("%2Btag:\"animated\"")
        2 -> listOf("-tag:\"animated\"", "-tag:\"gif\"")
        else -> emptyList()
    }
}

class FavoriteFilter : Filter.CheckBox("只显示收藏（需要 WebView 登录）", false)

class RandomEntryFilter : Filter.CheckBox("随机漫画", false)

// Speechless
class SpeechlessFilter : Filter.CheckBox("只显示无文字作品", false)

// Intermediate search
class SearchFlagFilter(name: String, val uri: String, state: Boolean = true) : Filter.CheckBox(name, state)
class CategoryFilters(flags: List<SearchFlagFilter>) : Filter.Group<SearchFlagFilter>("分类", flags)

// Advance search
abstract class AdvancedTextFilter(name: String) : Filter.Text(name)
class TagsFilter : AdvancedTextFilter("标签")
class ParodiesFilter : AdvancedTextFilter("原作")
class ArtistsFilter : AdvancedTextFilter("画师")
class CharactersFilter : AdvancedTextFilter("角色")
class GroupsFilter : AdvancedTextFilter("社团")
