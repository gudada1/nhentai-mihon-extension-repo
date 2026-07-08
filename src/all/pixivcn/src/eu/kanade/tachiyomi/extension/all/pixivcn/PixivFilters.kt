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

private val BOOKMARK_VALUES = arrayOf(
    "不使用热度门槛",
    "50 users入り",
    "100 users入り",
    "300 users入り",
    "500 users入り",
    "1000 users入り",
    "5000 users入り",
    "10000 users入り",
    "20000 users入り",
    "30000 users入り",
    "50000 users入り",
)
private val BOOKMARK_PARAMS = arrayOf(
    null,
    "50users入り",
    "100users入り",
    "300users入り",
    "500users入り",
    "1000users入り",
    "5000users入り",
    "10000users入り",
    "20000users入り",
    "30000users入り",
    "50000users入り",
)

private val RANKING_TYPE_VALUES = arrayOf("不使用排行榜", "漫画排行", "插画排行")
private val RANKING_TYPE_PARAMS = arrayOf(null, "manga", "illust")

private val RANKING_PERIOD_VALUES = arrayOf("当日", "当周", "当月")
private val RANKING_PERIOD_PARAMS = arrayOf("daily", "weekly", "monthly")

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

    private val bookmarkFilter = object : Filter.Select<String>("热度门槛（users入り）", BOOKMARK_VALUES, 0) {}.also(::add)
    private val rankingTypeFilter = object : Filter.Select<String>("排行类型", RANKING_TYPE_VALUES, 0) {}.also(::add)
    private val rankingPeriodFilter = object : Filter.Select<String>("排行时间", RANKING_PERIOD_VALUES, 0) {}.also(::add)

    init {
        add(Filter.Header("热度门槛会追加 Pixiv 的 users入り 标签；启用排行榜时会忽略关键词、标签、日期和热度门槛"))
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

    private val bookmarkTag: String? get() = BOOKMARK_PARAMS[bookmarkFilter.state]
    fun withBookmarkTag(word: String): String = listOfNotNull(word.ifBlank { null }, bookmarkTag).joinToString(" ")

    val rankingType: String? get() = RANKING_TYPE_PARAMS[rankingTypeFilter.state]
    val rankingPeriod: String get() = RANKING_PERIOD_PARAMS[rankingPeriodFilter.state]

    val order: String? get() = orderFilter.state?.ascending?.let { "date" }

    val dateBefore: String by dateBeforeFilter::state
    val dateAfter: String by dateAfterFilter::state
}
