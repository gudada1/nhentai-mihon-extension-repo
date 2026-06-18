package eu.kanade.tachiyomi.extension.all.nhentaicn

internal object NHSearchAliases {
    private data class Alias(
        val field: String?,
        val value: String,
        val aliases: List<String>,
    ) {
        fun toQueryPart(): String {
            val valuePart = value.toQueryValue()
            return if (field == null) valuePart else "$field:$valuePart"
        }
    }

    private val aliases = listOf(
        Alias("language", "chinese", listOf("中文", "汉化", "漢化", "中译", "中譯")),
        Alias("language", "english", listOf("英文", "英语", "英語")),
        Alias("language", "japanese", listOf("日文", "日语", "日語", "日本语", "日本語")),

        Alias("category", "doujinshi", listOf("同人", "同人志")),
        Alias("category", "artistcg", listOf("画集", "画册", "cg集", "CG集")),
        Alias("category", "gamecg", listOf("游戏cg", "游戏CG")),
        Alias("category", "imageset", listOf("图集", "图片集")),

        Alias("tag", "full color", listOf("全彩", "彩色")),
        Alias("tag", "original", listOf("原创", "原創")),
        Alias("tag", "sole female", listOf("单女主", "单一女主", "单女")),
        Alias("tag", "sole male", listOf("单男主", "单一男主", "单男")),
        Alias("tag", "group", listOf("多人")),
        Alias("tag", "anthology", listOf("合集", "短篇集")),
        Alias("tag", "webtoon", listOf("条漫", "长条漫")),
        Alias("tag", "big breasts", listOf("巨乳", "大胸")),
        Alias("tag", "small breasts", listOf("贫乳", "小胸")),
        Alias("tag", "maid", listOf("女仆", "女僕")),
        Alias("tag", "bunny girl", listOf("兔女郎")),
        Alias("tag", "cat ears", listOf("猫耳", "貓耳")),
        Alias("tag", "animal ears", listOf("兽耳", "獸耳")),
        Alias("tag", "glasses", listOf("眼镜", "眼鏡")),
        Alias("tag", "swimsuit", listOf("泳装", "泳裝", "泳衣")),
        Alias("tag", "schoolgirl uniform", listOf("校服", "水手服")),
        Alias("tag", "teacher", listOf("老师", "教師", "教师")),
        Alias("tag", "nurse", listOf("护士", "護士")),

        Alias("parody", "genshin impact", listOf("原神")),
        Alias("parody", "honkai star rail", listOf("崩坏星穹铁道", "崩壞星穹鐵道", "星穹铁道", "星穹鐵道", "崩铁", "崩鐵")),
        Alias("parody", "honkai impact 3rd", listOf("崩坏3", "崩壞3")),
        Alias("parody", "zenless zone zero", listOf("绝区零", "絕區零")),
        Alias("parody", "blue archive", listOf("蔚蓝档案", "蔚藍檔案", "碧蓝档案", "碧藍檔案")),
        Alias("parody", "azur lane", listOf("碧蓝航线", "碧藍航線")),
        Alias("parody", "arknights", listOf("明日方舟")),
        Alias("parody", "girls frontline", listOf("少女前线", "少女前線")),
        Alias("parody", "fate grand order", listOf("命运冠位指定", "命運冠位指定", "fgo", "FGO")),
        Alias("parody", "touhou project", listOf("东方project", "東方project", "东方", "東方")),
        Alias("parody", "kantai collection", listOf("舰队collection", "艦隊collection", "舰娘", "艦娘")),
        Alias("parody", "idolmaster", listOf("偶像大师", "偶像大師")),
        Alias("parody", "vocaloid", listOf("初音未来", "初音未來", "初音")),
        Alias("parody", "hololive", listOf("hololive", "holo")),
        Alias("parody", "nijisanji", listOf("彩虹社")),
        Alias("parody", "pokemon", listOf("宝可梦", "寶可夢", "口袋妖怪")),
        Alias("parody", "one piece", listOf("海贼王", "海賊王", "航海王")),
        Alias("parody", "naruto", listOf("火影", "火影忍者")),
        Alias("parody", "bleach", listOf("死神")),
        Alias("parody", "dragon ball", listOf("龙珠", "龍珠")),
        Alias("parody", "kimetsu no yaiba", listOf("鬼灭之刃", "鬼滅之刃")),
        Alias("parody", "chainsaw man", listOf("链锯人", "鏈鋸人", "电锯人", "電鋸人")),
        Alias("parody", "boku no hero academia", listOf("我的英雄学院", "我的英雄學院")),
        Alias("parody", "shingeki no kyojin", listOf("进击的巨人", "進擊的巨人")),
        Alias("parody", "neon genesis evangelion", listOf("新世纪福音战士", "新世紀福音戰士", "eva", "EVA")),
        Alias("parody", "spy x family", listOf("间谍过家家", "間諜過家家")),
        Alias("parody", "uma musume pretty derby", listOf("赛马娘", "賽馬娘")),

        Alias("character", "ganyu", listOf("甘雨")),
        Alias("character", "keqing", listOf("刻晴")),
        Alias("character", "raiden shogun", listOf("雷电将军", "雷電將軍")),
        Alias("character", "nahida", listOf("纳西妲", "納西妲")),
        Alias("character", "furina", listOf("芙宁娜", "芙寧娜")),
        Alias("character", "hutao", listOf("胡桃")),
        Alias("character", "nilou", listOf("妮露")),
        Alias("character", "yae miko", listOf("八重神子")),
        Alias("character", "march 7th", listOf("三月七")),
        Alias("character", "kafka", listOf("卡芙卡")),
        Alias("character", "silver wolf", listOf("银狼", "銀狼")),
        Alias("character", "firefly", listOf("流萤", "流螢")),
        Alias("character", "shiroko", listOf("白子")),
        Alias("character", "hoshino", listOf("星野")),
        Alias("character", "yuuka", listOf("优香", "優香")),
        Alias("character", "mash kyrielight", listOf("玛修", "瑪修")),
        Alias("character", "artoria pendragon", listOf("阿尔托莉雅", "阿爾托莉雅")),
        Alias("character", "hatsune miku", listOf("初音未来", "初音未來", "初音ミク")),
    )

    private val lookup = aliases
        .flatMap { alias -> alias.aliases.map { it to alias } }
        .sortedByDescending { it.first.length }

    private val fieldQueryRegex = Regex("""(^|\s)-?[A-Za-z_]+:""")
    private val cjkRegex = Regex("""[\u3400-\u9FFF\uF900-\uFAFF\u3040-\u30FF]+""")
    private val separatorRegex = Regex("""[，、；;]+""")
    private val spacesRegex = Regex("""\s+""")

    fun normalizeQuery(query: String): String {
        val normalized = query.normalizeInput()
        if (normalized.isBlank() || fieldQueryRegex.containsMatchIn(normalized)) return normalized

        var result = normalized
        var replaced = false
        lookup.forEach { (aliasText, alias) ->
            val regex = Regex(Regex.escape(aliasText), RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(result)) {
                result = regex.replace(result, " ${alias.toQueryPart()} ")
                replaced = true
            }
        }

        if (replaced) {
            result = result.replace(cjkRegex, " ")
        }

        return result.normalizeInput().ifBlank { normalized }
    }

    fun normalizeFilterValue(value: String, queryName: String): String {
        val normalized = value.normalizeInput()
        val alias = aliases.firstOrNull { candidate ->
            (candidate.field == queryName || candidate.field == null) &&
                candidate.aliases.any { it.equals(normalized, ignoreCase = true) }
        } ?: return normalized

        return alias.value
    }

    private fun String.normalizeInput(): String = trim()
        .replace(separatorRegex, " ")
        .replace(spacesRegex, " ")
        .trim()

    private fun String.toQueryValue(): String {
        val cleaned = replace("\"", "").trim()
        return if (cleaned.matches(Regex("""[A-Za-z0-9_-]+"""))) cleaned else "\"$cleaned\""
    }
}
