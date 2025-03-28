package com.faselhd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class FaselHD : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://w1.faselhdxwatch.top"
    override var name = "FaselHD"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.AsianDrama, TvType.Anime)
    private val cfKiller = CloudflareKiller()

    override val mainPage = mainPageOf(
        "$mainUrl/all-movies/page/0" to "جميع الافلام",
        "$mainUrl/movies_top_views/page/0" to "الأفلام الأعلى مشاهدة",
        "$mainUrl/dubbed-movies/page/0" to "الأفلام المدبلجة",
        "$mainUrl/movies_top_imdb/page/0" to "الأفلام الأعلى تقييمًا IMDB",
        "$mainUrl/series/page/0" to "مسلسلات",
        "$mainUrl/recent_series/page/" to "المضاف حديثًا",
        "$mainUrl/anime/page/0" to "الأنمي"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page, interceptor = cfKiller, timeout = 120).document
        val list = doc.select("div[id=\"postList\"] div[class*=\"col-\"]")
            .mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=" + query.replace(" ", "+")
        val doc = app.get(searchUrl, interceptor = cfKiller, timeout = 120).document
        return doc.select("div[id=\"postList\"] div[class*=\"col-\"]").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, interceptor = cfKiller, timeout = 120).document
        val isMovie = doc.select("div.epAll").isEmpty()
        val posterUrl = doc.select("div.posterImg img").attr("src")
            .ifEmpty { doc.select("div.seasonDiv.active img").attr("data-src") }

        val year = doc.select("div[id=\"singleList\"] div[class*=\"col-\"]")
            .firstOrNull { it.text().contains("سنة|موعد".toRegex()) }?.text()?.getIntFromText()

        val title = doc.select("title").text().replace(" - فاصل إعلاني", "")
            .replace("الموسم الأول|برنامج|فيلم|مترجم|اون لاين|مسلسل|مشاهدة|انمي|أنمي|$year".toRegex(), "")

        val iframeUrl = doc.select("iframe[src*=\"embed\"]").attr("src")
        println("Extracted iframe URL: $iframeUrl")

        val sources = extractVideoLinks(iframeUrl)

        return if (isMovie) {
            MovieLoadResponse(
                title,
                url,
                this.name,
                sources,
                posterUrl,
                year
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

        if (iframeUrl.isNotEmpty()) {
            val iframeDoc = app.get(iframeUrl, interceptor = cfKiller, timeout = 120).document

            // Try extracting .m3u8 links directly
            val videoUrl = iframeDoc.select("source[src*=\".m3u8\"]").attr("src")
            if (videoUrl.isNotEmpty()) {
                sources.add(
                    ExtractorLink(
                        name = "FaselHD",
                        source = this.name,
                        url = videoUrl,
                        referer = mainUrl,
                        quality = Qualities.Unknown.value
                    )
                )
            } else {
                // Alternative method: Extract from JavaScript
                val scriptTag = iframeDoc.select("script:containsData(var sources)").html()
                val extractedVideoUrl = Regex("""
