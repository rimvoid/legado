package io.legado.app.ui.book.read

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.getPrefStringSet
import io.legado.app.utils.putPrefString
import java.net.URLEncoder

object ContentSelectConfig {

    const val ACTION_WEB_SEARCH = "web_search"
    const val ACTION_REPLACE = "replace"
    const val ACTION_COPY = "copy"
    const val ACTION_BOOKMARK = "bookmark"
    const val ACTION_ALOUD = "aloud"
    const val ACTION_DICT = "dict"
    const val ACTION_ASK_AI = "ask_ai"
    const val ACTION_GENERATE_IMAGE = "generate_image"
    const val ACTION_SHARE_IMAGE = "share_image"

    /**
     * 取词模式开关, 常驻在长按文字菜单中, 不参与可配置菜单项
     */
    const val ACTION_WORD_LOOKUP = "word_lookup"

    private val legacyDefaultActions = setOf(
        ACTION_REPLACE,
        ACTION_COPY,
        ACTION_BOOKMARK,
        ACTION_ALOUD,
        ACTION_DICT,
        ACTION_ASK_AI,
        ACTION_GENERATE_IMAGE
    )

    private val defaultActionsBeforeShare = setOf(
        ACTION_WEB_SEARCH,
        ACTION_REPLACE,
        ACTION_COPY,
        ACTION_BOOKMARK,
        ACTION_ALOUD,
        ACTION_DICT,
        ACTION_ASK_AI,
        ACTION_GENERATE_IMAGE
    )

    val defaultActions = setOf(
        ACTION_WEB_SEARCH,
        ACTION_REPLACE,
        ACTION_COPY,
        ACTION_BOOKMARK,
        ACTION_ALOUD,
        ACTION_DICT,
        ACTION_ASK_AI,
        ACTION_GENERATE_IMAGE,
        ACTION_SHARE_IMAGE
    )

    val defaultOpenValues = listOf("", ACTION_WEB_SEARCH, ACTION_DICT, ACTION_ASK_AI)
    private val removedActionIds = emptySet<String>()

    data class SearchEngine(
        val id: String = "",
        val name: String = "",
        val url: String = "",
        val hideCss: String? = null
    )

    private val defaultBingHideCss = """
        header#b_header,
        #b_header,
        #sb_form,
        #b_searchboxForm,
        .b_searchboxForm,
        .b_searchbox,
        .b_scopebar,
        #est_switch {
            display: none !important;
        }
    """.trimIndent()

    private val defaultBaiduHideCss = """
        .se-page-hd,
        .se-page-hd-fixed,
        .se-results-top,
        .se-head-tablink,
        .se-head-logo,
        .se-box,
        .se-form,
        .se-searchbox,
        .sfc-dom,
        #head,
        #s_tab,
        #u,
        .s_form {
            display: none !important;
        }
    """.trimIndent()

    private val defaultBaiduBaikeHideCss = """
        header,
        .navbar-wrapper,
        .lemmaWgt-searchHeader,
        .search-box,
        .header-wrapper,
        .baike-header,
        .wgt-searchbar {
            display: none !important;
        }
    """.trimIndent()

    private val defaultGoogleHideCss = """
        header,
        #searchform,
        .sfbg,
        .minidiv,
        [jsname="RNNXgb"],
        form[role="search"],
        div[role="search"] {
            display: none !important;
        }
    """.trimIndent()

    private val legacyDefaultSearchEngineIds = listOf("bing", "baidu", "google")

    val defaultSearchEngines = listOf(
        SearchEngine(
            "baidu_baike",
            "百度百科",
            "https://baike.baidu.com/item/{queryPath}",
            defaultBaiduBaikeHideCss
        ),
        SearchEngine("bing", "Bing", "https://www.bing.com/search?q={query}", defaultBingHideCss),
        SearchEngine("baidu", "Baidu", "https://www.baidu.com/s?wd={query}", defaultBaiduHideCss),
        SearchEngine("google", "Google", "https://www.google.com/search?q={query}", defaultGoogleHideCss)
    )

    fun selectedActionIds(context: Context): Set<String> {
        val saved = context.getPrefStringSet(PreferKey.contentSelectActions, null)
            ?.filterNot { it in removedActionIds }
            ?.toSet()
            ?: return defaultActions
        return if (saved == legacyDefaultActions || saved == defaultActionsBeforeShare) {
            defaultActions
        } else {
            saved
        }
    }

    fun searchEngines(context: Context): List<SearchEngine> {
        val raw = context.getPrefString(PreferKey.contentSelectSearchEngines).orEmpty()
        val parsed = raw.takeIf { it.isNotBlank() }?.let {
            runCatching {
                GSON.fromJson(it, Array<SearchEngine>::class.java).toList()
            }.getOrNull()
        }
        val sanitized = sanitizeSearchEngines(parsed.orEmpty())
        if (isLegacyDefaultSearchEngines(sanitized)) {
            saveDefaultSearchEngines(context)
            return defaultSearchEngines
        }
        return sanitized.ifEmpty { defaultSearchEngines }
    }

    fun saveSearchEngines(context: Context, engines: List<SearchEngine>) {
        val normalized = sanitizeSearchEngines(engines).ifEmpty { defaultSearchEngines }
        context.putPrefString(PreferKey.contentSelectSearchEngines, GSON.toJson(normalized))
        val currentId = context.getPrefString(PreferKey.contentSelectSearchEngineId).orEmpty()
        if (normalized.none { it.id == currentId }) {
            context.putPrefString(PreferKey.contentSelectSearchEngineId, normalized.first().id)
        }
    }

    fun resetSearchEngines(context: Context) {
        saveDefaultSearchEngines(context)
    }

    fun currentSearchEngine(context: Context): SearchEngine {
        val engines = searchEngines(context)
        val currentId = context.getPrefString(PreferKey.contentSelectSearchEngineId).orEmpty()
        return engines.firstOrNull { it.id == currentId } ?: engines.first()
    }

    fun selectSearchEngine(context: Context, id: String) {
        if (searchEngines(context).any { it.id == id }) {
            context.putPrefString(PreferKey.contentSelectSearchEngineId, id)
        }
    }

    fun buildSearchUrl(engine: SearchEngine, query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val pathEncoded = encoded.replace("+", "%20")
        val template = engine.url.trim()
        if (template.contains("{queryPath}")) {
            return template.replace("{queryPath}", pathEncoded)
        }
        if (template.contains("{query}")) {
            return template.replace("{query}", encoded)
        }
        val separator = when {
            template.endsWith("?") || template.endsWith("&") -> ""
            template.contains("?") -> "&"
            else -> "?"
        }
        return "$template${separator}q=$encoded"
    }

    private fun saveDefaultSearchEngines(context: Context) {
        context.putPrefString(PreferKey.contentSelectSearchEngines, GSON.toJson(defaultSearchEngines))
        context.putPrefString(PreferKey.contentSelectSearchEngineId, defaultSearchEngines.first().id)
    }

    private fun isLegacyDefaultSearchEngines(engines: List<SearchEngine>): Boolean {
        return engines.map { it.id } == legacyDefaultSearchEngineIds
    }

    private fun sanitizeSearchEngines(engines: List<SearchEngine>): List<SearchEngine> {
        return engines.mapNotNull { engine ->
            val id = engine.id.trim()
            val name = engine.name.trim()
            val url = engine.url.trim()
            if (id.isBlank() || name.isBlank() || !url.startsWith("http", ignoreCase = true)) {
                null
            } else {
                val fallbackHideCss = engine.hideCss?.trim()
                    ?: defaultSearchEngines.firstOrNull { it.id == id }?.hideCss
                SearchEngine(
                    id = id,
                    name = name,
                    url = url,
                    hideCss = fallbackHideCss
                )
            }
        }.distinctBy { it.id }
    }
}
