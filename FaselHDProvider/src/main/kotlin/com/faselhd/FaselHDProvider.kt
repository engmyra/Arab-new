package com.faselhd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.nodes.Element

class FaselHD : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://w1.faselhdxwatch.top"
    private val alternativeUrl = "https://www.faselhd.club"
    override var name = "FaselHD"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.AsianDrama, TvType.Anime)
    private val cfKiller = CloudflareKiller()

    // Helper function to extract numbers from text
    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.value?.toIntOrNull()
    }

    // Convert an Element into a SearchResponse
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
            quality = getQualityFromString(quality),
            posterHeaders = cfKiller.getCookieHeaders(alternativeUrl).toMap()
        )
    }

    // Define the main page categories
    override val mainPage = mainPageOf(
        "$mainUrl/all-movies/page/0" to "جميع الافلام",
        "$mainUrl/movies_top_views/page/0" to "الأفلام الأعلى مشاهدة",
        "$mainUrl/dubbed-movies/page/0" to "الأفلام المدبلجة",
        "$mainUrl/movies_top_imdb/page/0" to "الأفلام الأعلى تقييما IMDB",
        "$mainUrl/series/page/0" to "المسلسلات",
        "$mainUrl/recent_series/page/0" to "المضاف حديثا",
        "$mainUrl/anime/page/0" to "الأنمي"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var doc = app.get(request.data + page).document
        if (doc.select("title").text() == "Just a moment...") {
            doc = app.get(request.data.replace(mainUrl, alternativeUrl) + page, interceptor = cfKiller, timeout = 120).document
        }
        val list = doc.select("div[id=\"postList\"] div[class=\"col-xl-2 col-lg-2 col-md-3 col-sm-3\"]")
            .mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "+")
        var doc = app.get("$mainUrl/?s=$q").document
        if (doc.select("title").text() == "Just a moment...") {
            doc = app.get("$alternativeUrl/?s=$q", interceptor = cfKiller, timeout = 120).document
        }
        return doc.select("div[id=\"postList\"] div[class=\"col-xl-2 col-lg-2 col-md-3 col-sm-3\"]")
            .mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        var doc = app.get(url).document
        if (doc.select("title").text() == "Just a moment...") {
            doc = app.get(url, interceptor = cfKiller, timeout = 120).document
        }

        val isMovie = doc.select("div.epAll").isEmpty()
        val posterUrl = doc.select("div.posterImg img").attr("src").ifEmpty {
            doc.select("div.seasonDiv.active img").attr("data-src")
        }
        val year = doc.select("div[id=\"singleList\"] div:contains(سنة|موعد)").text().getIntFromText()
        val title = doc.select("h1").text()
        val description = doc.select("p.story").text()

        val videoIframeUrl = doc.select("iframe[src*=\"faselhd\"]").attr("src")
        if (videoIframeUrl.isEmpty()) throw ErrorLoadingException("No video iframe found!")

        val sources = extractVideoLinks(videoIframeUrl)

        return MovieLoadResponse(
            title,
            url,
            this.name,
            TvType.Movie,
            sources,
            posterUrl,
            year,
            description
        )
    }

    // Function to extract video links from the iframe URL
    private suspend fun extractVideoLinks(iframeUrl: String): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        val iframeDoc = app.get(iframeUrl).document
        val m3u8Link = iframeDoc.select("source[type=\"application/x-mpegURL\"]").attr("src")

        if (m3u8Link.isNotEmpty()) {
            sources.add(
                ExtractorLink(
                    name = this.name,
                    source = this.name,
                    url = m3u8Link,
                    referer = iframeUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = true
                )
            )
        }
        return sources
    }
}
