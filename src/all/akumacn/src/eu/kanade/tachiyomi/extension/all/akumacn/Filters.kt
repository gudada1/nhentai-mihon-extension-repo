package eu.kanade.tachiyomi.extension.all.akumacn

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    Filter.Header("多个标签用英文逗号 (,) 分隔"),
    Filter.Header("前面加减号 (-) 表示排除"),
    ChineseOnlyFilter(),
    AnimatedFilter(),
    TextFilter("女性标签", "female"),
    TextFilter("男性标签", "male"),
    TextFilter("其他标签", "other"),
    CategoryFilter(),
    TextFilter("社团", "group"),
    TextFilter("画师", "artist"),
    TextFilter("原作", "parody"),
    TextFilter("角色", "character"),
    Filter.Separator(),
    Filter.Header("需要账号权限的范围筛选：收藏、已读、已评论"),
    OptionFilter(),
)

internal class TextFilter(name: String, val tag: String) : Filter.Text(name)
internal class ChineseOnlyFilter : Filter.CheckBox("只显示中文（脚本规则）", false)
internal class AnimatedFilter :
    Filter.Select<String>(
        "动图/GIF",
        arrayOf("不限", "只显示动图", "排除动图/GIF"),
    ) {
    fun queryTokens(): List<String> = when (state) {
        1 -> listOf("tag:\"animated\"")
        2 -> listOf("-tag:\"animated\"", "-tag:\"gif\"")
        else -> emptyList()
    }
}
internal class OptionFilter(val value: List<Pair<String, String>> = options) : Filter.Select<String>("搜索范围", options.map { it.first }.toTypedArray()) {
    fun getValue() = options[state].second
}

internal open class TagTriState(name: String, val value: String) : Filter.TriState(name)
internal class CategoryFilter : Filter.Group<TagTriState>("分类", categoryList.map { TagTriState(it.first, it.second) })

private val categoryList = listOf(
    "同人志" to "Doujinshi",
    "漫画" to "Manga",
    "图集" to "Image Set",
    "画师 CG" to "Artist CG",
    "游戏 CG" to "Game CG",
    "欧美" to "Western",
    "非 H" to "Non-H",
    "Cosplay" to "Cosplay",
    "其他" to "Misc",
)
private val options = listOf(
    "不使用" to "",
    "只看收藏" to "favorited",
    "只看已读" to "read",
    "只看已评论" to "commented",
)
