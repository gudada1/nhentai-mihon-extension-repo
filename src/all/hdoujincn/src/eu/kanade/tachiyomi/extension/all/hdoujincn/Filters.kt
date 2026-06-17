
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    SelectFilter("排序", getSortsList),
    CategoryFilter("分类"),
    Filter.Separator(),
    TagType("包含标签匹配方式", "i"),
    TagType("排除标签匹配方式", "e"),
    Filter.Separator(),
    Filter.Header("多个标签用英文逗号 (,) 分隔"),
    Filter.Header("前面加减号 (-) 表示排除"),
    TextFilter("标签", "tag"),
    TextFilter("男性标签", "male"),
    TextFilter("女性标签", "female"),
    TextFilter("混合标签", "mixed"),
    TextFilter("其他标签", "other"),
    Filter.Separator(),
    TextFilter("画师", "artist"),
    TextFilter("原作", "parody"),
    TextFilter("角色", "character"),
    Filter.Separator(),
    TextFilter("上传者", "reason"),
    TextFilter("社团/圈子", "circle"),
    TextFilter("语言", "language"),
    Filter.Separator(),
    Filter.Header("按页数筛选，例如：>20"),
    TextFilter("页数", "pages"),
)

class CheckBoxFilter(name: String, val value: Int, state: Boolean) : Filter.CheckBox(name, state)

internal class CategoryFilter(name: String) :
    Filter.Group<CheckBoxFilter>(
        name,
        listOf(
            Pair("漫画", 2),
            Pair("同人志", 4),
            Pair("插画", 8),
        ).map { CheckBoxFilter(it.first, it.second, true) },
    )

internal class TagType(title: String, val type: String) :
    Filter.Select<String>(
        title,
        arrayOf("全部匹配 (AND)", "任一匹配 (OR)"),
    )

internal open class TextFilter(name: String, val type: String) : Filter.Text(name)

internal open class SelectFilter(name: String, val vals: List<Pair<String, String>>, state: Int = 2) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    val selected get() = vals[state].second.takeIf { it.isNotEmpty() }
}

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("标题", "2"),
    Pair("页数", "3"),
    Pair("日期", ""),
    Pair("浏览量", "8"),
    Pair("收藏数", "9"),
    Pair("本周热门", "popular"),
)
