package com.faselhd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class FaselHD : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://w1.faselhdxwatch.top" // Update to latest working domain
    override var name = "FaselHD"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.AsianDrama, TvType.Anime)
    private val cfKiller = CloudflareKiller()

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("div.postDiv a").attr("href") ?: return null
        val posterUrl = select("div.postDiv a div img").attr("data-src") ?: select("div.postDiv a div img").attr("src")
        val title = select("div.postDiv a div img").attr("alt")
        val quality = select(".quality").first()?.text()?.replace("1080p |-".toRegex(), "")
        val type = if (title.contains("فيلم")) TvType.Movie else TvType.TvSeries

        return MovieSearchResponse(
            title.replace("الموسم الأول|برنامج|فيلم|مترجم|اون لاين|مسلسل|مشاهدة|انمي|أنمي".toRegex(), ""),
            url,
            this@FaselHD.name,
            type,
            posterUrl,
            null,
            null,
            quality = getQualityFromString(quality)
        )
    }

    override val mainPage = mainPageOf(
        "$mainUrl/all-movies/page/0" to "جميع الافلام",
        "$mainUrl/movies_top_views/page/0" to "الافلام الاعلي مشاهدة",
        "$mainUrl/dubbed-movies/page/0" to "الأفلام المدبلجة",
        "$mainUrl/movies_top_imdb/page/0" to "الافلام الاعلي تقييما IMDB",
        "$mainUrl/series/page/0" to "مسلسلات",
        "$mainUrl/recent_series/page/" to "المضاف حديثا",
        "$mainUrl/anime/page/0" to "الأنمي"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var doc = app.get(request.data + page, interceptor = cfKiller, timeout = 120).document
        val list = doc.select("div[id=\"postList\"] div[class=\"col-xl-2 col-lg-2 col-md-3 col-sm-3\"]")
            .mapNotNull { element ->
                element.toSearchResponse()
            }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "+")
        val d = app.get("$mainUrl/?s=$q", interceptor = cfKiller, timeout = 120).document
        return d.select("div[id=\"postList\"] div[class=\"col-xl-2 col-lg-2 col-md-3 col-sm-3\"]")
            .mapNotNull {
                it.toSearchResponse()
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, interceptor = cfKiller, timeout = 120).document
        val isMovie = doc.select("div.epAll").isEmpty()
        val posterUrl = doc.select("div.posterImg img").attr("src")
            .ifEmpty { doc.select("div.seasonDiv.active img").attr("data-src") }

        val year = doc.select("div[id=\"singleList\"] div[class=\"col-xl-6 col-lg-6 col-md-6 col-sm-6\"]")
            .firstOrNull { it.text().contains("سنة|موعد".toRegex()) }?.text()?.getIntFromText()

        val title = doc.select("title").text().replace(" - فاصل إعلاني", "")
            .replace("الموسم الأول|برنامج|فيلم|مترجم|اون لاين|مسلسل|مشاهدة|انمي|أنمي|$year".toRegex(), "")
        val duration = doc.select("div[id=\"singleList\"] div[class=\"col-xl-6 col-lg-6 col-md-6 col-sm-6\"]")
            .firstOrNull { it.text().contains("مدة|توقيت".toRegex()) }?.text()?.getIntFromText()

        val iframeUrl = doc.select("iframe[src*=\"embed\"]").attr("src")
        val sources = extractVideoLinks(iframeUrl)

        return if (isMovie) {
            MovieLoadResponse(
                title,
                url,
                this.name,
                sources,
                posterUrl,
                year,
                duration
            )
        } else {
            TvSeriesLoadResponse(
                title,
                url,
                this.name,
                posterUrl,
                year,
                episodes = listOf()
            )
        }
    }

    private suspend fun extractVideoLinks(iframeUrl: String): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        
        val iframeDoc = app.get(iframeUrl, interceptor = cfKiller, timeout = 120).document
        
        val videoUrl = iframeDoc.select("source[src*=\".m3u8\"]").attr("src")
        
        if (videoUrl.isNotEmpty()) {
            sources.add(
                ExtractorLink(
                    name = "FaselHD",
                    source = this.name,
                    url = videoUrl,
                    referer = iframeUrl,
                    quality = Qualities.Unknown,
                    isM3u8 = true
                )
            )
        } else {
            throw ErrorLoadingException("No .m3u8 links found!")
        }
        
        return sources
    }
}
