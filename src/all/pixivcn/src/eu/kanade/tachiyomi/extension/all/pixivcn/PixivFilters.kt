package eu.kanade.tachiyomi.extension.all.pixivcn

import eu.kanade.tachiyomi.source.model.Filter

private val TYPE_VALUES = arrayOf("全部", "插画", "漫画")
private val TYPE_PARAMS = arrayOf(null, "illust", "manga")

private val TAGS_MODE_VALUES = arrayOf("部分匹配", "完全匹配")
private val TAGS_MODE_PARAMS = arrayOf("s_tag", "s_tag_full")

private val RATING_VALUES = arrayOf("全部", "全年龄", "R-18")
private val RATING_PARAMS = arrayOf(null, "all", "r18")

private val RATING_PREDICATES: Array<((PixivIllust) -> Boolean)?> =
    arrayOf(null, { it.x_restrict == "0" }, { it.x_restrict == "1" })

internal class PixivFilters : MutableList<Filter<*>> by mutableListOf() {
    init {
        add(Filter.Header("搜索支持 Pixiv 链接、aid:123、user:123、sid:123"))
    }

    private val typeFilter = object : Filter.Select<String>("类型", TYPE_VALUES, 2) {}.also(::add)
    private val tagsFilter = object : Filter.Text("标签") {}.also(::add)
    private val tagsModeFilter = object : Filter.Select<String>("标签匹配方式", TAGS_MODE_VALUES, 0) {}.also(::add)
    private val usersFilter = object : Filter.Text("用户") {}.also(::add)
    init {
        add(Filter.Header("搜索词为空时，用户筛选会按用户查找；需要先通过 WebView 登录"))
    }

    private val ratingFilter = object : Filter.Select<String>("分级", RATING_VALUES, 0) {}.also(::add)

    init {
        add(Filter.Header("使用用户筛选时，下面这些条件会被忽略"))
    }

    private val orderFilter = object : Filter.Sort("排序", arrayOf("投稿日期")) {}.also(::add)
    private val dateBeforeFilter = object : Filter.Text("投稿早于（YYYY-MM-DD）") {}.also(::add)
    private val dateAfterFilter = object : Filter.Text("投稿晚于（YYYY-MM-DD）") {}.also(::add)

    val type: String? get() = TYPE_PARAMS[typeFilter.state]

    val tags: String by tagsFilter::state
    val searchMode: String get() = TAGS_MODE_PARAMS[tagsModeFilter.state]

    fun makeTagsPredicate(): ((PixivIllust) -> Boolean)? {
        val tags = tags.ifBlank { return null }.split(' ')

        if (tagsModeFilter.state == 0) {
            val regex = Regex(tags.joinToString("|") { Regex.escape(it) })
            return { it.tags?.any(regex::containsMatchIn) == true }
        } else {
            return { it.tags?.containsAll(tags) == true }
        }
    }

    val users: String by usersFilter::state

    fun makeUsersPredicate(): ((PixivIllust) -> Boolean)? {
        val users = users.ifBlank { return null }
        val regex = Regex(users.split(' ').joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE)

        return { it.author_details?.user_name?.contains(regex) == true }
    }

    val rating: String? get() = RATING_PARAMS[ratingFilter.state]
    fun makeRatingPredicate() = RATING_PREDICATES[ratingFilter.state]

    val order: String? get() = orderFilter.state?.ascending?.let { "date" }

    val dateBefore: String by dateBeforeFilter::state
    val dateAfter: String by dateAfterFilter::state
}
