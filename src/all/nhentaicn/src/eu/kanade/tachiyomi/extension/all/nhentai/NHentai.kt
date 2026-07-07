package eu.kanade.tachiyomi.extension.all.nhentaicn

import android.app.Application
import android.content.SharedPreferences
import android.webkit.CookieManager
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.all.nhentaicn.NHUtils.getArtists
import eu.kanade.tachiyomi.extension.all.nhentaicn.NHUtils.getGroups
import eu.kanade.tachiyomi.extension.all.nhentaicn.NHUtils.getTagDescription
import eu.kanade.tachiyomi.extension.all.nhentaicn.NHUtils.getTags
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.utils.applicationContext
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

open class NHentai(
    override val lang: String,
    private val nhLang: String,
    private val sourceId: Long,
    private val sourceName: String = "NHentai 中文版",
) : HttpSource(),
    ConfigurableSource {

    final override val baseUrl = "https://nhentai.net"

    val apiUrl = "$baseUrl/api/v2"

    override val id: Long = sourceId

    override val name = sourceName

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by lazy { sharedPreferencesWithMigration() }

    private val webViewCookieManager: CookieManager by lazy { CookieManager.getInstance() }

    override val client: OkHttpClient by lazy {
        val (permits, period) = preferences.parseRateLimit()

        val app = Injekt.get<Application>()
        val cacheParent = app.cacheDir
            .takeIf { it.exists() || it.mkdirs() }
            ?: app.externalCacheDir
            ?: app.filesDir
        val cacheDirectory = File(cacheParent, "nhentai_api_cache_$id")

        network.cloudflareClient.newBuilder()
            .rateLimitHost(baseUrl.toHttpUrl(), permits, period, TimeUnit.SECONDS)
            .cache(
                Cache(
                    directory = cacheDirectory,
                    maxSize = 5L * 1024 * 1024, // 5 MiB which should be enough
                ),
            )
            .addInterceptor(NhApiRetryInterceptor())
            .addNetworkInterceptor(NhGalleryCacheInterceptor())
            .addNetworkInterceptor(NhAuthorizationInterceptor())
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .setRandomUserAgent(
            filterInclude = listOf("chrome"),
        )

    // Authentication

    val apiKey
        get() = readAuthPreference(API_KEY).normalizeApiKey()
    private val manualAccessToken
        get() = readAuthPreference(ACCESS_TOKEN).normalizeAccessToken()
    val cookieToken
        get() = manualAccessToken.takeUnless { it.isNullOrBlank() }
            ?: webViewCookieManager.getCookie(baseUrl)
                ?.split("; ")
                ?.firstOrNull { it.startsWith("access_token=") }
                ?.replace("access_token=", "") ?: ""

    // Cdns

    val nhConfig: NHConfig by lazy {
        try {
            client.newCall(GET("$apiUrl/config", headers)).execute().parseAs<NHConfig>(json)
        } catch (_: Exception) {
            NHConfig(
                (1..4).map { n -> "https://i$n.nhentai.net" }.toList(),
                (1..4).map { n -> "https://t$n.nhentai.net" }.toList(),
            )
        }
    }

    val imageServer
        get() = nhConfig.imageServers.random()

    val thumbServer
        get() = nhConfig.thumbServers.random()

    // Preferences

    private var displayFullTitle: Boolean = when (preferences.getString(TITLE_PREF, "full")) {
        "full" -> true
        else -> false
    }

    private val shortenTitleRegex = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")
    private fun String.shortenTitle() = this.replace(shortenTitleRegex, "").trim()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = TITLE_PREF
            title = TITLE_PREF
            entries = arrayOf("完整标题", "短标题")
            entryValues = arrayOf("full", "short")
            summary = "%s"
            setDefaultValue("full")

            setOnPreferenceChangeListener { _, newValue ->
                displayFullTitle = when (newValue) {
                    "full" -> true
                    else -> false
                }
                true
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = SORT_PREF
            title = SORT_PREF
            entries = SORT_OPTIONS.map { it.first }.toTypedArray()
            entryValues = SORT_OPTIONS.map { it.second }.toTypedArray()
            summary = "%s"
            setDefaultValue("popular")
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = API_KEY
            title = "API key（推荐）"
            summary = "推荐用于绕开 WebView 登录和 CAPTCHA。登录网页后到 https://nhentai.net/user/settings#apikeys 创建并填入。"
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(API_KEY, (newValue as String).normalizeApiKey()).apply()
                true
            }
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = ACCESS_TOKEN
            title = "Access token（备用）"
            summary = "找不到 API key 时使用。只填写你自己的 nHentai access_token Cookie，不要分享给别人。"
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(ACCESS_TOKEN, (newValue as String).normalizeAccessToken()).apply()
                true
            }
        }.let(screen::addPreference)

        screen.addRandomUAPreference()

        ListPreference(screen.context).apply {
            key = RATE_LIMIT_PREF
            title = "网络限速"
            summary = "%s"
            entries = RATE_LIMIT_OPTIONS.map { it.first }.toTypedArray()
            entryValues = RATE_LIMIT_OPTIONS.map { it.second }.toTypedArray()
            setDefaultValue(RATE_LIMIT_DEFAULT)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "重启应用后生效", Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        rememberLastPage(page)
        val url = if (nhLang.isBlank()) {
            "$apiUrl/galleries".toHttpUrl().newBuilder()
        } else {
            "$apiUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("query", "language:$nhLang")
        }
        url.addQueryParameter("page", page.toString())
        return GET(url.build(), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        rememberLastPage(page)
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", if (nhLang.isBlank()) "\"\"" else "language:$nhLang")
            .addQueryParameter("sort", "popular")
            .addQueryParameter("page", page.toString())
        return GET(url.build(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // Search

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = when {
        query.startsWith("https://") -> {
            val url = query.toHttpUrl()
            if (url.host != baseUrl.toHttpUrl().host) {
                throw Exception("不支持的链接")
            }
            if (url.pathSegments.size < 2) {
                throw Exception("不支持的链接")
            }
            getSearchManga(page, "$PREFIX_ID_SEARCH${url.pathSegments[1]}", filters)
        }
        query.startsWith(PREFIX_ID_SEARCH) -> {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(searchMangaByIdRequest(id)).await().use { searchMangaByIdParse(it) }
        }

        query.toIntOrNull() != null -> {
            client.newCall(searchMangaByIdRequest(query)).await().use { searchMangaByIdParse(it) }
        }

        else -> super.getSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val normalizedQuery = NHSearchAliases.normalizeQuery(query)
        val languageQuery = resolveSearchLanguageQuery(filterList, normalizedQuery)
        val advQuery = combineQuery(filterList)
        val favoriteFilter = filterList.firstInstanceOrNull<FavoriteFilter>()
        val useStartPage = favoriteFilter?.state == true || (normalizedQuery.isBlank() && advQuery.isBlank())
        val requestPage = resolveRequestPage(page, filterList, useStartPage)

        if (favoriteFilter?.state == true) {
            return favoritesMangaRequest(requestPage, listOf(normalizedQuery, languageQuery, advQuery).toNhQuery())
        } else {
            val url = "$apiUrl/search".toHttpUrl().newBuilder()
                // Blank query (Multi + sort by popular month/week/day) shows a 404 page
                // Searching for `""` is a hacky way to return everything without any filtering
                .addQueryParameter("query", listOf(normalizedQuery, languageQuery, advQuery).toNhQuery().ifBlank { "\"\"" })
                .addQueryParameter("page", requestPage.toString())

            filterList.firstInstanceOrNull<SortFilter>()?.let { f ->
                url.addQueryParameter("sort", f.toUriPart())
            }
            return GET(url.build(), headers)
        }
    }

    protected fun resolveRequestPage(page: Int, filters: FilterList, useConfiguredStartPage: Boolean = true): Int {
        val typedPage = filters.firstInstanceOrNull<StartPageFilter>()?.state?.trim().orEmpty()
        val presetFilter = filters.firstInstanceOrNull<StartPagePresetFilter>()
        val presetPage = presetFilter?.toUriPart()?.toIntOrNull()

        val requestPage = if (!useConfiguredStartPage) {
            page
        } else {
            (presetPage ?: typedPage.toIntOrNull())
                ?.coerceAtLeast(1)
                ?.let { it + page - 1 }
                ?: page
        }

        preferences.edit()
            .putString(START_PAGE_PREF, typedPage)
            .putInt(START_PAGE_PRESET_PREF, presetFilter?.state ?: 0)
            .putInt(LAST_PAGE_PREF, requestPage)
            .apply()

        return requestPage
    }

    protected fun combineQuery(filters: FilterList): String = buildString {
        filters.filterIsInstance<AdvSearchEntryFilter>().forEach { filter ->
            filter.state.split(",")
                .map(String::trim)
                .filterNot(String::isBlank)
                .forEach { tag ->
                    val normalizedTag = NHSearchAliases.normalizeFilterValue(tag.removePrefix("-"), filter.queryName)
                    val shouldQuoteValue = !(filter.queryName == "pages" || filter.queryName == "uploaded")
                    if (tag.startsWith("-")) append("-")
                    append(filter.queryName, ':')
                    if (shouldQuoteValue) append('"')
                    append(normalizedTag)
                    if (shouldQuoteValue) append('"')
                    append(" ")
                }
        }
    }

    private fun resolveSearchLanguageQuery(filters: FilterList, query: String): String {
        if (LANGUAGE_QUERY_REGEX.containsMatchIn(query)) return ""

        return when (val selected = filters.firstInstanceOrNull<SearchLanguageFilter>()?.toUriPart().orEmpty()) {
            SEARCH_LANGUAGE_SOURCE -> if (nhLang.isBlank()) "" else "language:$nhLang"
            "" -> ""
            else -> "language:$selected"
        }
    }

    private fun List<String>.toNhQuery(): String = filter(String::isNotBlank).joinToString(" ")

    protected fun favoritesMangaRequest(page: Int, query: String = ""): Request {
        val url = "$apiUrl/favorites".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
        return GET(url.build(), headers)
    }

    private fun searchMangaByIdRequest(id: String) = GET("$apiUrl/galleries/$id", headers)

    private fun searchMangaByIdParse(response: Response): MangasPage {
        val details = mangaDetailsParse(response)
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val res = response.parseAs<PaginatedResponse<GalleryItem>>(json)
        val mangas = res.result.mapNotNull { runCatching { parseSearchData(it) }.getOrNull() }
        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasNextPage =
            (res.numPages != null && res.numPages > page) || (res.numPages == null && res.total != null && res.total > page * res.perPage)
        return MangasPage(mangas, hasNextPage)
    }

    fun parseSearchData(data: GalleryItem): SManga = SManga.create().apply {
        url = "/g/${data.id}/"
        title = (data.englishTitle ?: data.japaneseTitle)!!.let {
            if (displayFullTitle) it else it.shortenTitle()
        }
        thumbnail_url = "$thumbServer/${data.thumbnail}"
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    // Manga

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = searchMangaByIdRequest(manga.url.removeSurrounding("/g/", "/"))

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<Hentai>(json)

        return SManga.create().apply {
            url = "/g/${data.id}/"
            title = if (displayFullTitle) {
                data.title.english ?: data.title.japanese ?: data.title.pretty!!
            } else {
                data.title.pretty ?: (data.title.english ?: data.title.japanese)!!.shortenTitle()
            }
            thumbnail_url = "$thumbServer/${data.thumbnail.path}"
            status = SManga.COMPLETED
            artist = getArtists(data)
            author = getGroups(data) ?: getArtists(data)
            // Some people want these additional details in description
            description = "完整英文/日文标题：\n"
                .plus("${data.title.english ?: data.title.japanese ?: data.title.pretty ?: ""}\n")
                .plus(data.title.japanese ?: "")
                .plus("\n\n")
                .plus("页数：${data.numPages}\n")
                .plus("收藏数：${data.numFavorites}\n")
                .plus(getTagDescription(data))
            genre = getTags(data)
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            initialized = true
        }
    }

    // Chapter List

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga).newBuilder()
        .cacheControl(CacheControl.Builder().maxStale(2, TimeUnit.HOURS).build())
        .build()

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<Hentai>(json)
        return listOf(data.toSChapter())
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.removeSurrounding("/g/", "/")
        return GET("$apiUrl/galleries/$id", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<Hentai>(json)
        return data.pages.mapIndexed { i, page ->
            Page(i, imageUrl = "$imageServer/${page.path}")
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("当前浏览页：第 ${getLastPagePref()} 页（上次请求）"),
        Filter.Header("关键词支持部分中文别名，例如作品名、角色名、常见标签会自动转为英文搜索语法。"),
        Filter.Header("多个条件用英文逗号 (,) 分隔"),
        Filter.Header("前面加减号 (-) 表示排除"),
        TagFilter(),
        CategoryFilter(),
        GroupFilter(),
        ArtistFilter(),
        ParodyFilter(),
        CharactersFilter(),
        Filter.Header("上传时间单位：h 小时、d 天、w 周、m 月、y 年。"),
        Filter.Header("示例：>20d"),
        UploadedFilter(),
        Filter.Header("按页数筛选，示例：>20"),
        PagesFilter(),

        Filter.Separator(),
        SearchLanguageFilter(),
        SortFilter(
            SORT_OPTIONS.indexOfFirst { it.second == preferences.getString(SORT_PREF, "popular") }
                .coerceAtLeast(0),
        ),
        StartPagePresetFilter(
            preferences.getInt(START_PAGE_PRESET_PREF, 0)
                .coerceIn(0, START_PAGE_PRESET_OPTIONS.lastIndex),
        ),
        StartPageFilter(preferences.getString(START_PAGE_PREF, "").orEmpty()),
        Filter.Header("起始页用于收藏/空搜索浏览；普通关键词搜索会自动从第 1 页开始。"),
        Filter.Header("起始页为空时从第 1 页开始；输入 700 时第一页就是第 700 页，下一页是 701。"),
        Filter.Header("勾选后显示账号收藏，可继续配合上面的条件搜索收藏。"),
        Filter.Header("只显示收藏时会忽略排序"),
        FavoriteFilter(),
    )

    class TagFilter : AdvSearchEntryFilter("标签", "tag")
    class CategoryFilter : AdvSearchEntryFilter("分类", "category")
    class GroupFilter : AdvSearchEntryFilter("社团", "group")
    class ArtistFilter : AdvSearchEntryFilter("画师", "artist")
    class ParodyFilter : AdvSearchEntryFilter("原作", "parody")
    class CharactersFilter : AdvSearchEntryFilter("角色", "character")
    class UploadedFilter : AdvSearchEntryFilter("上传时间", "uploaded")
    class PagesFilter : AdvSearchEntryFilter("页数", "pages")
    open class AdvSearchEntryFilter(name: String, val queryName: String) : Filter.Text(name)

    class StartPageFilter(default: String = "") : Filter.Text("起始页（手动输入）", default)

    class StartPagePresetFilter(default: Int) : UriPartFilter("快捷跳页（优先于手动输入）", START_PAGE_PRESET_OPTIONS, default)

    private class FavoriteFilter : Filter.CheckBox("只显示我的收藏", false)

    private class SearchLanguageFilter : UriPartFilter("搜索语言", SEARCH_LANGUAGE_OPTIONS, 0)

    private class SortFilter(default: Int) : UriPartFilter("排序", SORT_OPTIONS, default)

    open class UriPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
        state: Int,
    ) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun toUriPart() = vals[state].second
    }

    companion object {
        const val API_KEY = "api_key"
        private const val ACCESS_TOKEN = "access_token"
        const val PREFIX_ID_SEARCH = "id:"
        const val SOURCE_ID_EN = 6316214103987364001L
        const val SOURCE_ID_JA = 6316214103987364002L
        const val SOURCE_ID_ZH = 6316214103987364003L
        const val SOURCE_ID_ALL = 6316214103987364004L
        const val SOURCE_ID_FAVORITES = 6316214103987364005L
        private const val SHARED_PREFS_NAME = "source_nhentaicn_shared"
        private const val SHARED_PREFS_MIGRATED = "shared_prefs_migrated_v1"
        private const val NHENTAI_HOST = "nhentai.net"
        private val GALLERY_PATH_REGEX = Regex("^/api/v2/galleries/\\d+/?$")
        private val API_PATH_REGEX = Regex("^/api/v2/.*$")
        private const val BACKOFF_RETRY_HEADER = "X-NHentai-Backoff-Retry"
        private const val GALLERY_CACHE_MAX_AGE_SECONDS = 7200
        private const val RATE_LIMIT_PREF = "rate_limit_pref"
        private const val START_PAGE_PREF = "start_page_pref"
        private const val START_PAGE_PRESET_PREF = "start_page_preset_pref"
        private const val LAST_PAGE_PREF = "last_page_pref"
        private const val RATE_LIMIT_DEFAULT = "1/1"
        private const val RATE_LIMIT_MIN_PERMITS = 1
        private const val RATE_LIMIT_MAX_PERMITS = 10
        private const val RATE_LIMIT_MIN_PERIOD_SECONDS = 1L
        private const val RATE_LIMIT_MAX_PERIOD_SECONDS = 60L
        private const val SEARCH_LANGUAGE_SOURCE = "__source__"
        private val LANGUAGE_QUERY_REGEX = Regex("""(^|\s)-?language:""")
        private val RATE_LIMIT_OPTIONS = arrayOf(
            Pair("0.25 次/秒", "1/4"),
            Pair("0.5 次/秒", "1/2"),
            Pair("1 次/秒（默认）", "1/1"),
            Pair("2 次/秒", "2/1"),
            Pair("4 次/秒（建议配合 API key）", "4/1"),
        )
        private const val TITLE_PREF = "标题显示"

        private val SEARCH_LANGUAGE_OPTIONS = arrayOf(
            Pair("全部语言（关键词默认）", ""),
            Pair("中文", "chinese"),
            Pair("英文", "english"),
            Pair("日文", "japanese"),
            Pair("跟随首页语言", SEARCH_LANGUAGE_SOURCE),
        )

        private val SORT_OPTIONS = arrayOf(
            Pair("热门：全部时间", "popular"),
            Pair("热门：本月", "popular-month"),
            Pair("热门：本周", "popular-week"),
            Pair("热门：今天", "popular-today"),
            Pair("最近更新", "date"),
        )

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

        private const val SORT_PREF = "搜索默认排序"
    }

    protected fun getLastPagePref(): Int = preferences.getInt(LAST_PAGE_PREF, 1).coerceAtLeast(1)

    protected fun rememberLastPage(page: Int) {
        preferences.edit().putInt(LAST_PAGE_PREF, page.coerceAtLeast(1)).apply()
    }

    private fun sharedPreferencesWithMigration(): SharedPreferences {
        val shared = applicationContext.getSharedPreferences(SHARED_PREFS_NAME, 0x0000)
        if (shared.getBoolean(SHARED_PREFS_MIGRATED, false)) return shared

        val editor = shared.edit()
        listOf(SOURCE_ID_EN, SOURCE_ID_JA, SOURCE_ID_ZH, SOURCE_ID_ALL, SOURCE_ID_FAVORITES)
            .forEach { oldSourceId ->
                val oldPrefs = applicationContext.getSharedPreferences("source_$oldSourceId", 0x0000)
                oldPrefs.all.forEach { (key, value) ->
                    if (shared.contains(key)) return@forEach
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> ->
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(key, value as Set<String>)
                    }
                }
            }

        editor.putBoolean(SHARED_PREFS_MIGRATED, true).apply()
        return shared
    }

    private fun readAuthPreference(key: String): String {
        val sourcePrefs = applicationContext.getSharedPreferences("source_$id", 0x0000)
        val legacyPrefs = listOf(SOURCE_ID_EN, SOURCE_ID_JA, SOURCE_ID_ZH, SOURCE_ID_ALL, SOURCE_ID_FAVORITES)
            .filterNot { it == id }
            .map { applicationContext.getSharedPreferences("source_$it", 0x0000) }

        return (listOf(sourcePrefs, preferences) + legacyPrefs)
            .firstNotNullOfOrNull { prefs -> prefs.getString(key, null)?.takeIf(String::isNotBlank) }
            .orEmpty()
    }

    private fun String?.normalizeApiKey(): String = orEmpty()
        .trim()
        .replace(Regex("(?i)^authorization:\\s*"), "")
        .replace(Regex("(?i)^key\\s+"), "")
        .trim()

    private fun String?.normalizeAccessToken(): String = orEmpty()
        .trim()
        .replace(Regex("(?i)^authorization:\\s*"), "")
        .replace(Regex("(?i)^user\\s+"), "")
        .let { raw ->
            if (raw.contains("access_token=")) {
                raw.substringAfter("access_token=").substringBefore(";")
            } else {
                raw
            }
        }
        .trim()

    private fun SharedPreferences.parseRateLimit(): Pair<Int, Long> {
        val raw = getString(RATE_LIMIT_PREF, RATE_LIMIT_DEFAULT).orEmpty()
        return parseRateLimitString(raw) ?: defaultRateLimit()
    }

    private fun defaultRateLimit(): Pair<Int, Long> = requireNotNull(parseRateLimitString(RATE_LIMIT_DEFAULT))

    private fun parseRateLimitString(raw: String): Pair<Int, Long>? {
        val parts = raw.split("/", limit = 2)
        if (parts.size != 2) return null

        val permits = parts[0].toIntOrNull()
        val period = parts[1].toLongOrNull()
        if (permits == null || period == null) return null

        return permits.coerceIn(RATE_LIMIT_MIN_PERMITS, RATE_LIMIT_MAX_PERMITS) to
            period.coerceIn(RATE_LIMIT_MIN_PERIOD_SECONDS, RATE_LIMIT_MAX_PERIOD_SECONDS)
    }

    private class NhGalleryCacheInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            if (!GALLERY_PATH_REGEX.matches(response.request.url.encodedPath)) return response

            // Tell HttpClient to cache the gallery API JSON response for 2 hours
            return response.newBuilder()
                .removeHeader("Cache-Control")
                .removeHeader("Expires")
                .removeHeader("Pragma")
                .header("Cache-Control", "max-age=$GALLERY_CACHE_MAX_AGE_SECONDS")
                .build()
        }
    }

    private class NhApiRetryInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url

            // If the request is not to the NHentai API, do not retry
            if (url.host != NHENTAI_HOST || !API_PATH_REGEX.matches(url.encodedPath)) {
                return chain.proceed(request)
            }

            val response = chain.proceed(request)
            // The request returned normally or this is already a retry request and still got a 429
            if (response.code != 429 || request.header(BACKOFF_RETRY_HEADER) != null) {
                return response
            }

            // Do not block OkHttp threads; only immediate one-shot retry
            val retryAfter = response.header("Retry-After")?.trim()
            // Server asks us to wait for a certain amount of time before retrying
            if (!retryAfter.isNullOrEmpty() && retryAfter.toLongOrNull() != 0L) return response

            response.close()
            val retryRequest = request.newBuilder()
                .header(BACKOFF_RETRY_HEADER, "1")
                .build()
            return chain.proceed(retryRequest)
        }
    }

    private inner class NhAuthorizationInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            val url = request.url

            // If the request is not to the NHentai API, do not add authorization headers
            if (url.host != NHENTAI_HOST || !API_PATH_REGEX.matches(url.encodedPath)) {
                return chain.proceed(request)
            }

            if (!apiKey.isNullOrBlank()) {
                request = request.newBuilder().addHeader("Authorization", "Key $apiKey").build()
                val response = chain.proceed(request)
                if (response.code == 401) {
                    response.close()
                    throw IOException("API key 无效")
                }
                return response
            }

            if (url.encodedPath.contains("/favorites")) {
                val accessToken = cookieToken
                if (accessToken.isNotBlank()) {
                    request = request.newBuilder().addHeader("Authorization", "User $accessToken").build()
                }
                val response = chain.proceed(request)
                if (response.code == 401) {
                    response.close()
                    throw IOException("请先通过 WebView 登录，或在源设置里填写 API key 后再查看收藏")
                }
                return response
            }

            return chain.proceed(request)
        }
    }
}

class NHentaiFavorites(
    lang: String,
    nhLang: String,
    sourceId: Long,
) : NHentai(lang, nhLang, sourceId, "NHentai 我的收藏") {

    override fun popularMangaRequest(page: Int): Request {
        rememberLastPage(page)
        return favoritesMangaRequest(page)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        rememberLastPage(page)
        return favoritesMangaRequest(page)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val normalizedQuery = NHSearchAliases.normalizeQuery(query)
        val advQuery = combineQuery(filterList)
        val requestPage = resolveRequestPage(page, filterList)

        return favoritesMangaRequest(requestPage, listOf(normalizedQuery, advQuery).filter(String::isNotBlank).joinToString(" "))
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("当前浏览页：第 ${getLastPagePref()} 页（上次请求）"),
        Filter.Header("在我的收藏中搜索；多个条件用英文逗号 (,) 分隔"),
        Filter.Header("前面加减号 (-) 表示排除"),
        TagFilter(),
        CategoryFilter(),
        GroupFilter(),
        ArtistFilter(),
        ParodyFilter(),
        CharactersFilter(),
        Filter.Header("上传时间单位：h 小时、d 天、w 周、m 月、y 年。"),
        Filter.Header("示例：>20d"),
        UploadedFilter(),
        Filter.Header("按页数筛选，示例：>20"),
        PagesFilter(),
        Filter.Separator(),
        StartPagePresetFilter(0),
        StartPageFilter(),
        Filter.Header("起始页为空时从第 1 页开始；输入 700 时第一页就是第 700 页，下一页是 701。"),
    )
}
