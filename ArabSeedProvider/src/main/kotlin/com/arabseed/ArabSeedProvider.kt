package com.arabseed

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import android.util.Log

class ArabSeed : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://m15.asd.rest"
    override var name = "ArabSeed"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private fun Element.toSearchResponse(): SearchResponse? {
        val title = select("h4, .Title").text()
        val posterUrl = select("img.imgOptimzer, .Thumbnail img").attr("data-image").ifEmpty {
            select("div.Poster img, .Cover img").attr("data-src")
        }
        val link = select("a").attr("href").ifEmpty { select(".Entry a").attr("href") }
        val tvType = if (select("span.category, .GenreTag").text().contains("مسلسلات")) TvType.TvSeries else TvType.Movie

        return if (title.isNotBlank() && link.isNotBlank()) {
            MovieSearchResponse(title, link, this@ArabSeed.name, tvType, posterUrl)
        } else null
    }

    override val mainPage = mainPageOf(
        "$mainUrl/latest1/" to "Latest",
        "$mainUrl/movies/" to "Movies",
        "$mainUrl/series/" to "Series"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val url = "${request.data}${if (page > 1) "page/$page/" else ""}"
            Log.d("ArabSeed", "Fetching Page: $url")
            val document = app.get(url, timeout = 120).document
            val selector = when (request.name) {
                "Latest" -> "div.latest-item, .latest-post, .item"
                else -> "ul.Blocks-UL > div, .content-item, .block"
            }
            val home = document.select(selector).mapNotNull { it.toSearchResponse() }
            Log.d("ArabSeed", "Loaded ${home.size} items for ${request.name}")
            return newHomePageResponse(request.name, home)
        } catch (e: Exception) {
            Log.e("ArabSeed", "Error fetching ${request.name}: ${e.message}")
            return newHomePageResponse(request.name, emptyList())
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, timeout = 120).document
        val title = document.selectFirst("h1, .Title, .post-title")?.text() ?: "No Title"
        val poster = document.selectFirst("img.Poster, .Cover img, .post-image")?.attr("data-src") ?: ""
        val description = document.selectFirst(".Description p, .post-content p")?.text()

        val isSeries = url.contains("مسلسل") && url.contains("الحلقة")
        return if (isSeries) {
            val episode = Episode(url, "Episode ${url.split('-').last { it.matches(Regex("\\d+")) }}")
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, listOf(episode)) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(data, timeout = 120).document
        document.select("a[href*='gamehub.cam'], iframe[src]").forEach { element ->
            val link = element.attr("href").ifEmpty { element.attr("src") }
            if (link.isNotBlank()) {
                if (link.contains("gamehub.cam")) {
                    val gamehubDoc = app.get(link, timeout = 120).document
                    val videoLink = gamehubDoc.selectFirst("iframe[src], source[src]")?.attr("src") ?: return@forEach
                    loadExtractor(videoLink, mainUrl, subtitleCallback, callback)
                } else {
                    loadExtractor(link, mainUrl, subtitleCallback, callback)
                }
            }
        }
    }
}
