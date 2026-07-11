package eu.kanade.tachiyomi.extension.all.hitomicn

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    ChineseOnlyFilter(),
    SelectFilter("排序", getSortsList),
    TypeFilter("类型"),
    AnimatedFilter(),
    Filter.Separator(),
    Filter.Header("多个标签用英文逗号 (,) 分隔"),
    Filter.Header("前面加减号 (-) 表示排除"),
    TextFilter("社团", "group"),
    TextFilter("画师", "artist"),
    TextFilter("系列/原作", "series"),
    TextFilter("角色", "character"),
    TextFilter("男性标签", "male"),
    TextFilter("女性标签", "female"),
    Filter.Header("女性/男性标签请填到上面两个专用栏，这里的普通标签不支持 female/male。"),
    TextFilter("普通标签", "tag"),
)

internal open class TextFilter(name: String, val type: String) : Filter.Text(name)
internal class ChineseOnlyFilter : Filter.CheckBox("只显示中文（脚本规则）", false)
internal open class SelectFilter(name: String, val vals: List<Triple<String, String?, String>>, state: Int = 0) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    fun getArea() = vals[state].second
    fun getValue() = vals[state].third
    fun isRandom() = vals[state].first == "随机"
}
internal class TypeFilter(name: String) :
    Filter.Group<CheckBoxFilter>(
        name,
        listOf(
            Pair("动画", "anime"),
            Pair("画师 CG", "artistcg"),
            Pair("同人志", "doujinshi"),
            Pair("游戏 CG", "gamecg"),
            Pair("图集", "imageset"),
            Pair("漫画", "manga"),
        ).map { CheckBoxFilter(it.first, it.second, true) },
    )
internal open class CheckBoxFilter(name: String, val value: String, state: Boolean) : Filter.CheckBox(name, state)
internal class AnimatedFilter :
    Filter.Select<String>(
        "动图/动画",
        arrayOf("不限", "只显示动画", "排除动画"),
    ) {
    fun queryTokens(): List<String> = when (state) {
        1 -> listOf("type:anime")
        2 -> listOf("-type:anime")
        else -> emptyList()
    }
}

private val getSortsList: List<Triple<String, String?, String>> = listOf(
    Triple("添加日期", null, "index"),
    Triple("发布日期", "date", "published"),
    Triple("热门：今天", "popular", "today"),
    Triple("热门：本周", "popular", "week"),
    Triple("热门：本月", "popular", "month"),
    Triple("热门：本年", "popular", "year"),
    Triple("随机", "popular", "year"),
)
