package eu.kanade.tachiyomi.extension.all.ehentaicn

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.net.Uri
import android.webkit.CookieManager
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.Filter.CheckBox
import eu.kanade.tachiyomi.source.model.Filter.Select
import eu.kanade.tachiyomi.source.model.Filter.Text
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.CacheControl
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URLEncoder

abstract class EHentai(
    override val lang: String,
    private val ehLang: String,
) : HttpSource(),
    ConfigurableSource {

    override val name = "E-Hentai CN"

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val webViewCookieManager: CookieManager by lazy { CookieManager.getInstance() }
    private val memberId: String by lazy { getMemberIdPref() }
    private val passHash: String by lazy { getPassHashPref() }
    private val igneous: String by lazy { getIgneousPref() }
    private val forceEh: Boolean by lazy { getForceEhPref() }

    override val baseUrl: String
        get() = when {
            System.getenv("CI") == "true" -> "https://e-hentai.org"
            !forceEh && memberId.isNotEmpty() && passHash.isNotEmpty() -> "https://exhentai.org"
            else -> "https://e-hentai.org"
        }

    override val supportsLatest = true

    private var lastMangaId = ""
    private var galleryListLanguageFilter = ""

    // true if lang is a "natural human language"
    private fun isLangNatural(): Boolean = lang !in listOf("none", "other")

    private fun genericMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangaElements = doc.select("table.itg td.glname")
            .let { elements ->
                if (galleryListLanguageFilter.isNotBlank()) {
                    elements.filter { element ->
                        element.select("div[title^=language]").firstOrNull()?.text() == galleryListLanguageFilter
                    }
                } else if (isLangNatural() && getEnforceLanguagePref()) {
                    elements.filter { element ->
                        // only accept elements with a language tag matching ehLang or without a language tag
                        // could make this stricter and not accept elements without a language tag, possibly add a sharedpreference for it
                        element.select("div[title^=language]").firstOrNull()?.let { it.text() == ehLang } ?: true
                    }
                } else {
                    elements
                }
            }
        val parsedMangas: MutableList<SManga> = mutableListOf()
        for (i in mangaElements.indices) {
            val manga = mangaElements[i].let {
                SManga.create().apply {
                    // Get title
                    it.selectFirst("a")?.apply {
                        title = this.select(".glink").text()
                        url = ExGalleryMetadata.normalizeUrl(attr("href"))
                        if (i == mangaElements.lastIndex) {
                            lastMangaId = ExGalleryMetadata.galleryId(attr("href"))
                        }
                    }
                    // Get image
                    it.parent()?.select(".glthumb img")?.first().apply {
                        thumbnail_url = this?.attr("data-src")?.nullIfBlank()
                            ?: this?.attr("src")
                    }
                }
            }
            parsedMangas.add(manga)
        }

        // Add to page if required
        val hasNextPage = doc.select("a#unext[href], table.ptt a[href*=p=]").hasText()

        return MangasPage(parsedMangas, hasNextPage)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.just(
        listOf(
            SChapter.create().apply {
                url = manga.url
                name = "Chapter"
                chapter_number = 1f
            },
        ),
    )

    override fun fetchPageList(chapter: SChapter) = fetchChapterPage(chapter, "$baseUrl/${chapter.url}").map {
        it.mapIndexed { i, s ->
            Page(i, s)
        }
    }!!

    /**
     * Recursively fetch chapter pages
     */
    private fun fetchChapterPage(
        chapter: SChapter,
        np: String,
        pastUrls: List<String> = emptyList(),
    ): Observable<List<String>> {
        val urls = ArrayList(pastUrls)
        return chapterPageCall(np).flatMap {
            val jsoup = it.asJsoup()
            urls += parseChapterPage(jsoup)
            nextPageUrl(jsoup)?.let { string ->
                fetchChapterPage(chapter, string, urls)
            } ?: Observable.just(urls)
        }
    }

    private fun parseChapterPage(response: Element) = with(response) {
        select("#gdt a").map {
            it.attr("href")
        }
    }

    private fun chapterPageCall(np: String) = client.newCall(chapterPageRequest(np)).asObservableSuccess()
    private fun chapterPageRequest(np: String) = exGet(np, null, headers)

    private fun nextPageUrl(element: Element) = element.select("a[onclick=return false]").last()?.let {
        if (it.text() == ">") it.attr("href") else null
    }

    private fun languageTag(enforceLanguageFilter: Boolean = false): String = if (isLangNatural() && (enforceLanguageFilter || getEnforceLanguagePref())) "language:$ehLang" else ""

    override fun popularMangaRequest(page: Int): Request {
        galleryListLanguageFilter = ""
        rememberDisplayPage(page)
        return if (isLangNatural()) {
            exGet("$baseUrl/?f_search=${languageTag()}&f_srdd=5&f_sr=on", page)
        } else {
            latestUpdatesRequest(page)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        rememberDisplayPage(page, filterList)

        val selectedLanguage = filterList.filterIsInstance<SearchLanguageFilter>()
            .firstOrNull()
            ?.selectedLanguage()
            .orEmpty()
        val enforceLanguageFilter = filterList.find { it is EnforceLanguageFilter }?.state == true
        val languageQuery = when {
            selectedLanguage.isNotBlank() -> "language:$selectedLanguage"
            enforceLanguageFilter -> languageTag(enforceLanguageFilter = true)
            else -> ""
        }
        rankingMangaRequest(page, filterList, selectedLanguage)?.let { return it }

        galleryListLanguageFilter = ""
        val uri = Uri.parse("$baseUrl$QUERY_PREFIX").buildUpon()
        var modifiedQuery = when {
            languageQuery.isBlank() -> query
            query.isBlank() -> languageQuery
            else -> "$query,$languageQuery"
        }
        filterList.filterIsInstance<TextFilter>().forEach { filter ->
            if (filter.state.isNotEmpty()) {
                val splitted = filter.state.split(",").filter(String::isNotBlank)
                if (splitted.size < 2 && filter.type != "tags") {
                    modifiedQuery = modifiedQuery.withSearchToken(formatTagSearch(filter.type, filter.state))
                } else {
                    splitted.forEach { tag ->
                        val trimmed = tag.trim().lowercase()
                        modifiedQuery = if (trimmed.startsWith('-')) {
                            modifiedQuery.withSearchToken(formatTagSearch(filter.type, trimmed.removePrefix("-"), true))
                        } else {
                            modifiedQuery.withSearchToken(formatTagSearch(filter.type, trimmed))
                        }
                    }
                }
            }
        }
        if (filterList.filterIsInstance<ExcludeAiFilter>().firstOrNull()?.state == true) {
            modifiedQuery = modifiedQuery
                .withSearchToken(formatTagSearch("tag", "ai generated", true))
                .withSearchToken(formatTagSearch("tag", "ai assisted", true))
        }
        filterList.filterIsInstance<AnimatedFilter>().firstOrNull()?.queryTokens()?.forEach { token ->
            modifiedQuery = modifiedQuery.withSearchToken(token)
        }
        uri.appendQueryParameter("f_search", modifiedQuery.trim())
        // when attempting to search with no genres selected, will auto select all genres
        filterList.filterIsInstance<GenreGroup>().firstOrNull()?.state?.let {
            // variable to to check is any genres are selected
            val check = it.any { option -> option.state } // or it.any(GenreOption::state)
            // if no genres are selected by the user set all genres to on
            if (!check) {
                for (i in it) {
                    i.state = true
                }
            }
        }

        filterList.forEach {
            if (it is UriFilter) it.addToUri(uri)
        }

        if (uri.toString().contains("f_spf") || uri.toString().contains("f_spt")) {
            if (page > 1) uri.appendQueryParameter("from", lastMangaId)
        }

        return exGet(uri.toString(), page)
    }

    private fun rankingMangaRequest(page: Int, filters: FilterList, selectedLanguage: String): Request? {
        val rankingType = filters.filterIsInstance<RankingTypeFilter>()
            .firstOrNull()
            ?.selectedType()
            .orEmpty()
        if (rankingType.isBlank()) return null

        galleryListLanguageFilter = selectedLanguage

        val period = filters.filterIsInstance<RankingPeriodFilter>()
            .firstOrNull()
            ?.selectedPeriod()
            .orEmpty()
        val toplistCode = RANKING_TOPLIST_CODES[period] ?: RANKING_TOPLIST_CODES.getValue(RANKING_PERIOD_ALL)
        val url = "$baseUrl/toplist.php".toHttpUrl().newBuilder()
            .addQueryParameter("tl", toplistCode)
            .apply {
                if (page > 1) {
                    addQueryParameter("p", (page - 1).toString())
                }
            }
            .build()

        return GET(url.toString(), headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        galleryListLanguageFilter = ""
        rememberDisplayPage(page)
        return exGet(baseUrl, page)
    }

    override fun popularMangaParse(response: Response) = genericMangaParse(response)
    override fun searchMangaParse(response: Response) = genericMangaParse(response)
    override fun latestUpdatesParse(response: Response) = genericMangaParse(response)

    private fun exGet(url: String, page: Int? = null, additionalHeaders: Headers? = null, cache: Boolean = true): Request {
        // pages no longer exist, if app attempts to go to the first page after a request, do not include the page append
        val pageIndex = if (page == 1) null else page
        return GET(
            pageIndex?.let {
                addParam(url, "next", lastMangaId)
            } ?: url,
            additionalHeaders?.let { header ->
                val headers = headers.newBuilder()
                header.toMultimap().forEach { (t, u) ->
                    u.forEach {
                        headers.add(t, it)
                    }
                }
                headers.build()
            } ?: headers,

        ).let {
            if (!cache) {
                it.newBuilder().cacheControl(CacheControl.FORCE_NETWORK).build()
            } else {
                it
            }
        }
    }

    private fun rememberDisplayPage(page: Int, filters: FilterList? = null) {
        val typedPage = filters
            ?.filterIsInstance<StartPageFilter>()
            ?.firstOrNull()
            ?.state
            ?.trim()
            ?: getStartPagePref()
        val presetState = filters
            ?.filterIsInstance<StartPagePresetFilter>()
            ?.firstOrNull()
            ?.state
            ?: getStartPagePresetPref()
        val presetPage = START_PAGE_PRESET_OPTIONS
            .getOrNull(presetState)
            ?.second
            ?.toIntOrNull()
        val startPage = (presetPage ?: typedPage.toIntOrNull())?.coerceAtLeast(1)
        val displayPage = ((startPage ?: 1) + page - 1).coerceAtLeast(1)

        preferences.edit().apply {
            if (filters != null) {
                putString("${START_PAGE_PREF_KEY}_$lang", typedPage)
                putInt(
                    "${START_PAGE_PRESET_PREF_KEY}_$lang",
                    presetState.coerceIn(0, START_PAGE_PRESET_OPTIONS.lastIndex),
                )
            }
            putInt("${LAST_DISPLAY_PAGE_PREF_KEY}_$lang", displayPage)
        }.apply()
    }

    private fun formatTagSearch(type: String, value: String, exclude: Boolean = false): String {
        val prefix = if (exclude) "-" else ""
        return "$prefix$type:\"${value.trim().replace(" ", "+")}\""
    }

    private fun String.withSearchToken(token: String): String = if (isBlank()) token else "$this $token"

    /**
     * Parse gallery page to metadata model
     */
    @SuppressLint("DefaultLocale")
    override fun mangaDetailsParse(response: Response) = with(response.asJsoup()) {
        with(ExGalleryMetadata()) {
            url = response.request.url.encodedPath
            title = select("#gn").text().nullIfBlank()?.trim()

            altTitle = select("#gj").text().nullIfBlank()?.trim()

            // Thumbnail is set as background of element in style attribute
            thumbnailUrl = select("#gd1 div").attr("style").nullIfBlank()?.let {
                it.substring(it.indexOf('(') + 1 until it.lastIndexOf(')'))
            }
            genre = select("#gdc div").text().nullIfBlank()?.trim()?.lowercase()

            uploader = select("#gdn").text().nullIfBlank()?.trim()

            // Parse the table
            select("#gdd tr").forEach {
                it.select(".gdt1")
                    .text()
                    .nullIfBlank()
                    ?.trim()
                    ?.let { left ->
                        it.select(".gdt2")
                            .text()
                            .nullIfBlank()
                            ?.trim()
                            ?.let { right ->
                                ignore {
                                    when (
                                        left.removeSuffix(":")
                                            .lowercase()
                                    ) {
                                        "posted" -> datePosted = EX_DATE_FORMAT.parse(right)?.time ?: 0

                                        "visible" -> visible = right.nullIfBlank()

                                        "language" -> {
                                            language = right.removeSuffix(TR_SUFFIX).trim().nullIfBlank()
                                            translated = right.endsWith(TR_SUFFIX, true)
                                        }

                                        "file size" -> size = parseHumanReadableByteCount(right)?.toLong()

                                        "length" -> length = right.removeSuffix("pages").trim().nullIfBlank()?.toInt()

                                        "favorited" -> favorites = right.removeSuffix("times").trim().nullIfBlank()?.toInt()
                                    }
                                }
                            }
                    }
            }

            // Parse ratings
            ignore {
                averageRating = select("#rating_label")
                    .text()
                    .removePrefix("Average:")
                    .trim()
                    .nullIfBlank()
                    ?.toDouble()
                ratingCount = select("#rating_count")
                    .text()
                    .trim()
                    .nullIfBlank()
                    ?.toInt()
            }

            // Parse tags
            tags.clear()
            select("#taglist tr").forEach {
                val namespace = it.select(".tc").text().removeSuffix(":")
                val currentTags = it.select("div").map { element ->
                    Tag(
                        element.text().trim(),
                        element.hasClass("gtl"),
                    )
                }
                tags[namespace] = currentTags
            }

            // Copy metadata to manga
            SManga.create().apply {
                copyTo(this)
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
        }
    }

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/g/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/g/$id/"
        return MangasPage(listOf(details), false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith("https://")) {
        val url = query.toHttpUrl()
        if (url.pathSegments.size < 3) {
            throw Exception("Unsupported url")
        }
        val id = url.pathSegments[1]
        val key = url.pathSegments[2]
        fetchSearchManga(page, "${PREFIX_ID_SEARCH}$id/$key", filters)
    } else if (query.startsWith(PREFIX_ID_SEARCH)) {
        val id = query.removePrefix(PREFIX_ID_SEARCH)
        client.newCall(searchMangaByIdRequest(id))
            .asObservableSuccess()
            .map { response -> searchMangaByIdParse(response, id) }
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    override fun pageListParse(response: Response) = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = imageUrlParse(response, true)

    private fun imageUrlParse(response: Response, isGetBakImageUrl: Boolean): String {
        val doc = response.asJsoup()
        val imgUrl = doc.select("#img").attr("abs:src")
        // from https://github.com/Miuzarte/EHentai-go/blob/dd9a24adb13300c028c35f53b9eff31b51966def/query.go#L695
        val nlValue = Regex("nl\\('(.+?)'\\)").find(doc.selectFirst("#loadfail")?.attr("onclick").orEmpty())?.groupValues?.get(1)

        // from https://github.com/ccloli/E-Hentai-Downloader/blob/c51e1118def7541b5fbb224f7e512e170f4b9d5e/src/main.js#L2444
        if (getOriginalImagePref()) {
            val originalUrl = doc.selectFirst("a[href*=/fullimg/]")?.attr("abs:href")
            if (!originalUrl.isNullOrEmpty()) {
                return originalUrl.toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("nl", nlValue)
                    .build()
                    .toString()
            }
        }

        if (!isGetBakImageUrl) {
            return imgUrl
        }

        if (nlValue.isNullOrEmpty()) return imgUrl
        val bakUrl = response.request.url.newBuilder()
            .addQueryParameter("nl", nlValue)
            .toString()
        return "$imgUrl#$bakUrl"
    }

    private val cookiesHeader by lazy {
        val cookies = mutableMapOf<String, String>()

        // Setup settings
        val settings = mutableListOf<String>()

        // Do not show popular right now pane as we can't parse it
        settings += "prn_n"

        cookies["uconfig"] = buildSettings(settings)

        // Bypass "Offensive For Everyone" content warning
        cookies["nw"] = "1"

        cookies["ipb_member_id"] = memberId

        cookies["ipb_pass_hash"] = passHash

        cookies["igneous"] = igneous

        buildCookies(cookies)
    }

    // Headers
    override fun headersBuilder() = super.headersBuilder().add("Cookie", cookiesHeader)

    private fun buildSettings(settings: List<String?>) = settings.filterNotNull().joinToString(separator = "-")

    private fun buildCookies(cookies: Map<String, String>) = cookies.entries.joinToString(separator = "; ", postfix = ";") {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }

    @Suppress("SameParameterValue")
    private fun addParam(url: String, param: String, value: String) = Uri.parse(url)
        .buildUpon()
        .appendQueryParameter(param, value)
        .toString()

    override val client = network.cloudflareClient.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .addInterceptor { chain ->
            val request = chain.request()
            val result = runCatching { chain.proceed(request) }
            val bakUrl = request.url.fragment
                ?: return@addInterceptor result.getOrThrow()

            if (result.isFailure || result.getOrNull()?.isSuccessful != true) {
                result.getOrNull()?.close()
                val newRequest = GET(bakUrl, headers)
                val newImageUrl = imageUrlParse(chain.proceed(newRequest), false)
                val newImageRequest = request.newBuilder()
                    .url(newImageUrl)
                    .build()

                chain.proceed(newImageRequest)
            } else {
                result.getOrThrow()
            }
        }
        .addInterceptor { chain ->
            val newReq = chain
                .request()
                .newBuilder()
                .removeHeader("Cookie")
                .addHeader("Cookie", cookiesHeader)
                .build()

            chain.proceed(newReq)
        }.build()

    // Filters
    override fun getFilterList() = FilterList(
        SearchLanguageFilter(),
        EnforceLanguageFilter(getEnforceLanguagePref()),
        RankingTypeFilter(),
        RankingPeriodFilter(),
        Filter.Header("排行榜使用 E-Hentai 官方 Toplist；启用后普通关键词和标签筛选会被忽略"),
        Favorites(),
        Watched(),
        Filter.Separator(),
        Filter.Header("当前浏览页：第 ${getLastDisplayPagePref()} 页（上次请求）"),
        StartPagePresetFilter(getStartPagePresetPref()),
        StartPageFilter(getStartPagePref()),
        Filter.Header("起始页用于保存和显示浏览进度；E-Hentai 使用游标翻页，不能直接跳到远处页。"),
        Filter.Separator(),
        ExcludeAiFilter(),
        AnimatedFilter(),
        GenreGroup(),
        Filter.Header("多个标签用英文逗号 (,) 分隔"),
        Filter.Header("前面加减号 (-) 表示排除"),
        Filter.Header("女性/男性标签请用专用栏；普通标签会搜索所有分类。"),
        TextFilter("普通标签", "tag"),
        TextFilter("女性标签", "female"),
        TextFilter("男性标签", "male"),
        AdvancedGroup(),
    )

    internal open class TextFilter(name: String, val type: String, val specific: String = "") : Text(name)

    private class StartPageFilter(default: String = "") : Text("起始页（手动输入）", default)

    private class StartPagePresetFilter(default: Int) :
        Select<String>(
            "快捷页数（优先于手动输入）",
            START_PAGE_PRESET_OPTIONS.map { it.first }.toTypedArray(),
            default,
        )

    private class ExcludeAiFilter : CheckBox("排除 AI 图", false)

    private class AnimatedFilter :
        Select<String>(
            "动图",
            ANIMATED_OPTIONS.map { it.first }.toTypedArray(),
        ) {
        fun queryTokens(): List<String> = when (ANIMATED_OPTIONS.getOrNull(state)?.second) {
            ANIMATED_INCLUDE -> listOf("tag:\"animated\"")
            ANIMATED_EXCLUDE -> listOf(
                "-tag:\"animated\"",
                "-tag:\"gif\"",
            )
            else -> emptyList()
        }
    }

    private class SearchLanguageFilter :
        Select<String>(
            "搜索语言",
            SEARCH_LANGUAGE_OPTIONS.map { it.first }.toTypedArray(),
        ) {
        fun selectedLanguage() = SEARCH_LANGUAGE_OPTIONS[state].second
    }

    private class RankingTypeFilter :
        Select<String>(
            "排行类型",
            RANKING_TYPE_OPTIONS.map { it.first }.toTypedArray(),
        ) {
        fun selectedType() = RANKING_TYPE_OPTIONS[state].second
    }

    private class RankingPeriodFilter :
        Select<String>(
            "排行时间",
            RANKING_PERIOD_OPTIONS.map { it.first }.toTypedArray(),
        ) {
        fun selectedPeriod() = RANKING_PERIOD_OPTIONS[state].second
    }

    class Watched :
        CheckBox("已关注列表"),
        UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if (state) {
                builder.appendPath("watched")
            }
        }
    }

    class Favorites :
        CheckBox("收藏"),
        UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if (state) {
                builder.appendPath("favorites.php")
            }
        }
    }

    class GenreOption(name: String, private val genreId: String) :
        CheckBox(name, false),
        UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            builder.appendQueryParameter("f_$genreId", if (state) "1" else "0")
        }
    }

    class GenreGroup :
        UriGroup<GenreOption>(
            "分类",
            listOf(
                GenreOption("同人志", "doujinshi"),
                GenreOption("漫画", "manga"),
                GenreOption("画师 CG", "artistcg"),
                GenreOption("游戏 CG", "gamecg"),
                GenreOption("欧美", "western"),
                GenreOption("非 H", "non-h"),
                GenreOption("图集", "imageset"),
                GenreOption("Cosplay", "cosplay"),
                GenreOption("亚洲成人视频", "asianporn"),
                GenreOption("其他", "misc"),
            ),
        )

    class AdvancedOption(name: String, private val param: String, defValue: Boolean = false) :
        CheckBox(name, defValue),
        UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if (state) {
                builder.appendQueryParameter(param, "on")
            }
        }
    }

    open class PageOption(name: String, private val queryKey: String) :
        Text(name),
        UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if (state.isNotBlank()) {
                if (builder.build().getQueryParameters("f_sp").isEmpty()) {
                    builder.appendQueryParameter("f_sp", "on")
                }

                builder.appendQueryParameter(queryKey, state.trim())
            }
        }
    }

    class MinPagesOption : PageOption("最少页数", "f_spf")
    class MaxPagesOption : PageOption("最多页数", "f_spt")

    class RatingOption :
        Select<String>(
            "最低评分",
            arrayOf(
                "不限",
                "2 星以上",
                "3 星以上",
                "4 星以上",
                "5 星",
            ),
        ),
        UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if (state > 0) {
                builder.appendQueryParameter("f_srdd", (state + 1).toString())
                builder.appendQueryParameter("f_sr", "on")
            }
        }
    }

    // Explicit type arg for listOf() to workaround this: KT-16570
    class AdvancedGroup :
        UriGroup<Filter<*>>(
            "高级选项",
            listOf(
                AdvancedOption("搜索画廊名称", "f_sname", true),
                AdvancedOption("搜索画廊标签", "f_stags", true),
                AdvancedOption("搜索画廊描述", "f_sdesc"),
                AdvancedOption("搜索种子文件名", "f_storr"),
                AdvancedOption("只显示有种子的画廊", "f_sto"),
                AdvancedOption("搜索低权重标签", "f_sdt1"),
                AdvancedOption("搜索被踩标签", "f_sdt2"),
                AdvancedOption("显示已删除画廊", "f_sh"),
                RatingOption(),
                MinPagesOption(),
                MaxPagesOption(),
            ),
        )

    private class EnforceLanguageFilter(default: Boolean) : CheckBox("强制匹配当前源语言", default)

    // map languages to their internal ids
    private val languageMappings = listOf(
        Pair("japanese", listOf("0", "1024", "2048")),
        Pair("english", listOf("1", "1025", "2049")),
        Pair("chinese", listOf("10", "1034", "2058")),
        Pair("dutch", listOf("20", "1044", "2068")),
        Pair("french", listOf("30", "1054", "2078")),
        Pair("german", listOf("40", "1064", "2088")),
        Pair("hungarian", listOf("50", "1074", "2098")),
        Pair("italian", listOf("60", "1084", "2108")),
        Pair("korean", listOf("70", "1094", "2118")),
        Pair("polish", listOf("80", "1104", "2128")),
        Pair("portuguese", listOf("90", "1114", "2138")),
        Pair("russian", listOf("100", "1124", "2148")),
        Pair("spanish", listOf("110", "1134", "2158")),
        Pair("thai", listOf("120", "1144", "2168")),
        Pair("vietnamese", listOf("130", "1154", "2178")),
        Pair("n/a", listOf("254", "1278", "2302")),
        Pair("other", listOf("255", "1279", "2303")),
    )

    companion object {
        const val QUERY_PREFIX = "?f_apply=Apply+Filter"
        const val PREFIX_ID_SEARCH = "id:"
        const val TR_SUFFIX = "TR"

        // Preferences vals
        private const val START_PAGE_PREF_KEY = "START_PAGE"
        private const val START_PAGE_PRESET_PREF_KEY = "START_PAGE_PRESET"
        private const val LAST_DISPLAY_PAGE_PREF_KEY = "LAST_DISPLAY_PAGE"

        private const val ENFORCE_LANGUAGE_PREF_KEY = "ENFORCE_LANGUAGE"
        private const val ENFORCE_LANGUAGE_PREF_TITLE = "强制匹配当前源语言"
        private const val ENFORCE_LANGUAGE_PREF_SUMMARY = "勾选后浏览时只显示匹配当前语言标签的作品"
        private const val ENFORCE_LANGUAGE_PREF_DEFAULT_VALUE = false

        private val SEARCH_LANGUAGE_OPTIONS = arrayOf(
            "全部语言" to "",
            "中文" to "chinese",
            "英文" to "english",
            "日文" to "japanese",
            "韩文" to "korean",
            "西班牙文" to "spanish",
            "法文" to "french",
            "德文" to "german",
            "俄文" to "russian",
            "其他" to "other",
            "无语言" to "n/a",
        )

        private const val RANKING_TYPE_NONE = ""
        private const val RANKING_TYPE_VIEWS = "views"
        private const val RANKING_TYPE_FAVORITES = "favorites"
        private val RANKING_TYPE_OPTIONS = arrayOf(
            "不使用排行榜" to RANKING_TYPE_NONE,
            "浏览量排行" to RANKING_TYPE_VIEWS,
            "最多收藏排行（近似）" to RANKING_TYPE_FAVORITES,
        )

        private const val RANKING_PERIOD_ALL = "all"
        private const val RANKING_PERIOD_MONTH = "month"
        private const val RANKING_PERIOD_WEEK = "week"
        private const val RANKING_PERIOD_DAY = "day"
        private val RANKING_PERIOD_OPTIONS = arrayOf(
            "全部时间" to RANKING_PERIOD_ALL,
            "当月" to RANKING_PERIOD_MONTH,
            "当周（站点无周榜，使用当月）" to RANKING_PERIOD_WEEK,
            "当日（站点使用昨日榜）" to RANKING_PERIOD_DAY,
        )

        private val RANKING_TOPLIST_CODES = mapOf(
            RANKING_PERIOD_ALL to "11",
            RANKING_PERIOD_MONTH to "13",
            RANKING_PERIOD_WEEK to "13",
            RANKING_PERIOD_DAY to "15",
        )

        private const val ORIGINAL_IMAGE_PREF_KEY = "ORIGINAL_IMAGE"
        private const val ORIGINAL_IMAGE_PREF_TITLE = "使用原图"
        private const val ORIGINAL_IMAGE_PREF_SUMMARY = "勾选后如果账号有权限，会优先使用原图；加载速度会更慢"
        private const val ORIGINAL_IMAGE_PREF_DEFAULT_VALUE = false

        private const val MEMBER_ID_PREF_KEY = "MEMBER_ID"
        private const val MEMBER_ID_PREF_TITLE = "ipb_member_id"
        private const val MEMBER_ID_PREF_SUMMARY = "ipb_member_id 的值"
        private const val MEMBER_ID_PREF_DEFAULT_VALUE = ""

        private const val PASS_HASH_PREF_KEY = "PASS_HASH"
        private const val PASS_HASH_PREF_TITLE = "ipb_pass_hash"
        private const val PASS_HASH_PREF_SUMMARY = "ipb_pass_hash 的值"
        private const val PASS_HASH_PREF_DEFAULT_VALUE = ""

        private const val IGNEOUS_PREF_KEY = "IGNEOUS"
        private const val IGNEOUS_PREF_TITLE = "igneous"
        private const val IGNEOUS_PREF_SUMMARY = "手动覆盖 igneous 的值"
        private const val IGNEOUS_PREF_DEFAULT_VALUE = ""

        private const val FORCE_EH = "FORCE_EH"
        private const val FORCE_EH_TITLE = "强制使用 E-Hentai"
        private const val FORCE_EH_SUMMARY = "强制使用 e-hentai.org，避免进入 exhentai.org 内容"
        private const val FORCE_EH_DEFAULT_VALUE = true

        private val START_PAGE_PRESET_OPTIONS = arrayOf(
            Pair("手动输入", ""),
            Pair("第 1 页", "1"),
            Pair("第 25 页", "25"),
            Pair("第 50 页", "50"),
            Pair("第 100 页", "100"),
            Pair("第 150 页", "150"),
            Pair("第 200 页", "200"),
            Pair("第 250 页", "250"),
            Pair("第 300 页", "300"),
            Pair("第 350 页", "350"),
            Pair("第 400 页", "400"),
            Pair("第 450 页", "450"),
            Pair("第 500 页", "500"),
            Pair("第 550 页", "550"),
            Pair("第 600 页", "600"),
            Pair("第 650 页", "650"),
            Pair("第 700 页", "700"),
        )

        private const val ANIMATED_INCLUDE = "include"
        private const val ANIMATED_EXCLUDE = "exclude"
        private val ANIMATED_OPTIONS = arrayOf(
            Pair("不限", ""),
            Pair("只显示动图", ANIMATED_INCLUDE),
            Pair("排除动图", ANIMATED_EXCLUDE),
        )
    }

    // Preferences

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val forceEhPref = CheckBoxPreference(screen.context).apply {
            key = FORCE_EH
            title = FORCE_EH_TITLE
            summary = FORCE_EH_SUMMARY
            setDefaultValue(FORCE_EH_DEFAULT_VALUE)
        }

        val enforceLanguagePref = CheckBoxPreference(screen.context).apply {
            key = "${ENFORCE_LANGUAGE_PREF_KEY}_$lang"
            title = ENFORCE_LANGUAGE_PREF_TITLE
            summary = ENFORCE_LANGUAGE_PREF_SUMMARY
            setDefaultValue(ENFORCE_LANGUAGE_PREF_DEFAULT_VALUE)
        }

        val originalImagePref = CheckBoxPreference(screen.context).apply {
            key = "${ORIGINAL_IMAGE_PREF_KEY}_$lang"
            title = ORIGINAL_IMAGE_PREF_TITLE
            summary = ORIGINAL_IMAGE_PREF_SUMMARY
            setDefaultValue(ORIGINAL_IMAGE_PREF_DEFAULT_VALUE)
        }

        val memberIdPref = EditTextPreference(screen.context).apply {
            key = MEMBER_ID_PREF_KEY
            title = MEMBER_ID_PREF_TITLE
            summary = MEMBER_ID_PREF_SUMMARY

            setDefaultValue(MEMBER_ID_PREF_DEFAULT_VALUE)
        }

        val passHashPref = EditTextPreference(screen.context).apply {
            key = PASS_HASH_PREF_KEY
            title = PASS_HASH_PREF_TITLE
            summary = PASS_HASH_PREF_SUMMARY

            setDefaultValue(PASS_HASH_PREF_DEFAULT_VALUE)
        }

        val igneousPref = EditTextPreference(screen.context).apply {
            key = IGNEOUS_PREF_KEY
            title = IGNEOUS_PREF_TITLE
            summary = IGNEOUS_PREF_SUMMARY

            setDefaultValue(IGNEOUS_PREF_DEFAULT_VALUE)
        }

        screen.addPreference(forceEhPref)
        screen.addPreference(memberIdPref)
        screen.addPreference(passHashPref)
        screen.addPreference(igneousPref)
        screen.addPreference(originalImagePref)
        screen.addPreference(enforceLanguagePref)
    }

    private fun getEnforceLanguagePref(): Boolean = preferences.getBoolean("${ENFORCE_LANGUAGE_PREF_KEY}_$lang", ENFORCE_LANGUAGE_PREF_DEFAULT_VALUE)

    private fun getOriginalImagePref(): Boolean = preferences.getBoolean("${ORIGINAL_IMAGE_PREF_KEY}_$lang", ORIGINAL_IMAGE_PREF_DEFAULT_VALUE)

    private fun getStartPagePref(): String = preferences.getString("${START_PAGE_PREF_KEY}_$lang", "").orEmpty()

    private fun getStartPagePresetPref(): Int = preferences
        .getInt("${START_PAGE_PRESET_PREF_KEY}_$lang", 0)
        .coerceIn(0, START_PAGE_PRESET_OPTIONS.lastIndex)

    private fun getLastDisplayPagePref(): Int = preferences
        .getInt("${LAST_DISPLAY_PAGE_PREF_KEY}_$lang", 1)
        .coerceAtLeast(1)

    private fun getCookieValue(cookieTitle: String, defaultValue: String, prefKey: String): String {
        val cookies = webViewCookieManager.getCookie("https://forums.e-hentai.org")
        var value: String? = null

        if (cookies != null) {
            val cookieArray = cookies.split("; ")
            for (cookie in cookieArray) {
                if (cookie.startsWith("$cookieTitle=")) {
                    value = cookie.split("=")[1]

                    break
                }
            }
        }

        if (value == null) {
            value = preferences.getString(prefKey, defaultValue) ?: defaultValue
        }

        return value
    }

    private fun getPassHashPref(): String = getCookieValue(PASS_HASH_PREF_TITLE, PASS_HASH_PREF_DEFAULT_VALUE, PASS_HASH_PREF_KEY)

    private fun getMemberIdPref(): String = getCookieValue(MEMBER_ID_PREF_TITLE, MEMBER_ID_PREF_DEFAULT_VALUE, MEMBER_ID_PREF_KEY)

    private fun getIgneousPref(): String = getCookieValue(IGNEOUS_PREF_TITLE, IGNEOUS_PREF_DEFAULT_VALUE, IGNEOUS_PREF_KEY)

    private fun getForceEhPref(): Boolean = preferences.getBoolean(FORCE_EH, FORCE_EH_DEFAULT_VALUE)
}
