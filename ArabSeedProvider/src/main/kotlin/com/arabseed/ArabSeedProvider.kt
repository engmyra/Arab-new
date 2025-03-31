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
        Log.d("ArabSeed", "Fetching Page: ${request.data}")
        val document = app.get(request.data, timeout = 120).document
        val selector = when (request.name) {
            "Latest" -> "div.latest-item"
            else -> "ul.Blocks-UL > div"
        }
        val home = document.select(selector).mapNotNull { it.toSearchResponse() }
        Log.d("ArabSeed", "Loaded ${home.size} items for ${request.name}")
        return newHomePageResponse(request.name, home)
    }

    override suspend fun load(url: String): LoadResponse {
        Log.d("ArabSeed", "Loading URL: $url")
        val document = app.get(url, timeout = 120).document
        val title = document.selectFirst("h1, .Title, .post-title")?.text() ?: "No Title"
        val poster = document.selectFirst("img.Poster, .Cover img, .post-image")?.attr("data-src") ?: ""
        val description = document.selectFirst(".Description p, .post-content p")?.text()

        // Check if it's a series (URL contains "مسلسل" and "الحلقة")
        val isSeries = url.contains("مسلسل") && url.contains("الحلقة")
        return if (isSeries) {
            // For series, treat the URL as a single episode for now
            val episodeNumber = url.split('-').last { it.matches(Regex("\\d+")) }
            val episode = Episode(url, "Episode $episodeNumber")
            Log.d("ArabSeed", "Detected series with episode: ${episode.name}")
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, listOf(episode)) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            Log.d("ArabSeed", "Detected movie")
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
        Log.d("ArabSeed", "Fetching links for: $data")
        val document = app.get(data, timeout = 120).document

        // Look for all server links (e.g., "Watch Now" buttons or iframes)
        val serverLinks = document.select("a[href*='gamehub.cam'], a.server-link, iframe[src], .watch-server a")
        Log.d("ArabSeed", "Found ${serverLinks.size} potential server links")

        serverLinks.forEachIndexed { index, element ->
            val link = element.attr("href").ifEmpty { element.attr("src") }
            if (link.isNotBlank()) {
                Log.d("ArabSeed", "Processing server link $index: $link")
                try {
                    if (link.contains("gamehub.cam")) {
                        // Follow the gamehub link to get the embedded video
                        val gamehubDoc = app.get(link, timeout = 120).document
                        val videoLink = gamehubDoc.selectFirst("iframe[src], source[src], video[src]")?.attr("src")
                        if (videoLink.isNullOrBlank()) {
                            Log.w("ArabSeed", "No video link found in gamehub page for: $link")
                            return@forEachIndexed
                        }
                        Log.d("ArabSeed", "Extracted video link from gamehub: $videoLink")
                        loadExtractor(videoLink, mainUrl, subtitleCallback) { extractorLink ->
                            callback(extractorLink.copy(name = "Server ${index + 1} - ${extractorLink.name}"))
                        }
                    } else {
                        // Direct link or iframe
                        loadExtractor(link, mainUrl, subtitleCallback) { extractorLink ->
                            callback(extractorLink.copy(name = "Server ${index + 1} - ${extractorLink.name}"))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ArabSeed", "Error processing link $link: ${e.message}")
                }
            }
        }

        if (serverLinks.isEmpty()) {
            Log.w("ArabSeed", "No server links found on page: $data")
        }
    }
}
