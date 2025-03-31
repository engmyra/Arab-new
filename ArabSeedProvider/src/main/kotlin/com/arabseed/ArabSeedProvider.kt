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

        // Log HTML snippet for debugging
        Log.d("ArabSeed", "Load HTML snippet: ${document.html().substring(0, minOf(500, document.html().length))}...")

        val title = document.selectFirst("h1, .Title, .post-title")?.text() ?: "No Title"
        val poster = document.selectFirst("img.Poster, .Cover img, .post-image")?.attr("data-src") ?: ""
        val description = document.selectFirst(".Description p, .post-content p")?.text() ?: ""

        // Extract server list to include in description
        val serverItems = document.select(".containerServers ul li")
        Log.d("ArabSeed", "Found ${serverItems.size} servers during load in .containerServers")

        val serverListText = if (serverItems.isNotEmpty()) {
            val servers = serverItems.mapIndexed { index, item ->
                val link = item.attr("data-link").ifEmpty { item.attr("href") }
                val serverName = item.selectFirst("span")?.text() ?: "Server ${index + 1}"
                val quality = when {
                    item.parent()?.previousElementSibling()?.text()?.contains("720") == true -> "720p"
                    item.parent()?.previousElementSibling()?.text()?.contains("480") == true -> "480p"
                    else -> "Unknown"
                }
                Log.d("ArabSeed", "Server $index in load: $serverName ($quality) - $link")
                "$serverName ($quality): $link"
            }.joinToString("\n")
            "\n\nAvailable Servers:\n$servers"
        } else {
            Log.w("ArabSeed", "No servers found in .containerServers during load, trying fallback")
            val fallbackServers = document.select(
                "a[href*='gamehub.cam'], a[href*='vidmoly.to'], a[href*='bigwarp.io'], " +
                "a[href*='filemoon.sx'], a[href*='voe.sx'], a.server-link, iframe[src], .watch-server a"
            )
            Log.d("ArabSeed", "Found ${fallbackServers.size} fallback servers during load")
            if (fallbackServers.isNotEmpty()) {
                val servers = fallbackServers.mapIndexed { index, element ->
                    val link = element.attr("href").ifEmpty { element.attr("src") }
                    val serverName = element.text().ifEmpty { "Server ${index + 1}" }
                    val quality = if (link.contains("720")) "720p" else if (link.contains("480")) "480p" else "Unknown"
                    Log.d("ArabSeed", "Fallback server $index in load: $serverName ($quality) - $link")
                    "$serverName ($quality): $link"
                }.joinToString("\n")
                "\n\nAvailable Servers (Fallback):\n$servers"
            } else {
                "\n\nNo servers found."
            }
        }

        Log.d("ArabSeed", "Final description: $description$serverListText")

        val isSeries = url.contains("مسلسل") && url.contains("الحلقة")
        return if (isSeries) {
            val episodeNumber = url.split('-').last { it.matches(Regex("\\d+")) }
            val episode = Episode(url, "Episode $episodeNumber")
            Log.d("ArabSeed", "Detected series with episode: ${episode.name}")
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, listOf(episode)) {
                this.posterUrl = poster
                this.plot = description + serverListText
            }
        } else {
            Log.d("ArabSeed", "Detected movie")
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description + serverListText
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

        // Log a snippet of HTML for debugging
        Log.d("ArabSeed", "Page HTML snippet: ${document.html().substring(0, minOf(500, document.html().length))}...")

        // Primary extraction from containerServers
        val serverItems = document.select(".containerServers ul li")
        Log.d("ArabSeed", "Found ${serverItems.size} server items in .containerServers")

        if (serverItems.isNotEmpty()) {
            serverItems.forEachIndexed { index, item ->
                val link = item.attr("data-link").ifEmpty { item.attr("href") }
                Log.d("ArabSeed", "Server $index raw link: '$link'")

                if (link.isBlank()) {
                    Log.w("ArabSeed", "Empty or missing link for server item $index: ${item.outerHtml()}")
                    return@forEachIndexed
                }

                val serverName = item.selectFirst("span")?.text() ?: "Server ${index + 1}"
                val quality = when {
                    item.parent()?.previousElementSibling()?.text()?.contains("720") == true -> "720p"
                    item.parent()?.previousElementSibling()?.text()?.contains("480") == true -> "480p"
                    else -> "Unknown"
                }

                Log.d("ArabSeed", "Processing server $index: $serverName ($quality) - $link")

                try {
                    loadExtractor(link, mainUrl, subtitleCallback) { extractorLink ->
                        val finalLink = extractorLink.copy(
                            name = "$serverName ($quality) - Play",
                            quality = when (quality) {
                                "720p" -> 720
                                "480p" -> 480
                                else -> ExtractorLink.QUALITY_UNKNOWN
                            }
                        )
                        Log.d("ArabSeed", "Adding extractor link: ${finalLink.name} -> ${finalLink.url}")
                        callback(finalLink)
                    }
                } catch (e: Exception) {
                    Log.e("ArabSeed", "Error processing server link $link: ${e.message}", e)
                }
            }
        } else {
            Log.w("ArabSeed", "No server links found in .containerServers, trying fallback for: $data")
            // Fallback to broader selectors
            val fallbackLinks = document.select(
                "a[href*='gamehub.cam'], a[href*='vidmoly.to'], a[href*='bigwarp.io'], " +
                "a[href*='filemoon.sx'], a[href*='voe.sx'], a.server-link, iframe[src], .watch-server a"
            )
            Log.d("ArabSeed", "Found ${fallbackLinks.size} fallback links")

            fallbackLinks.forEachIndexed { index, element ->
                val link = element.attr("href").ifEmpty { element.attr("src") }
                if (link.isNotBlank()) {
                    Log.d("ArabSeed", "Processing fallback link $index: $link")
                    try {
                        loadExtractor(link, mainUrl, subtitleCallback) { extractorLink ->
                            val finalLink = extractorLink.copy(
                                name = "Server ${index + 1} (${
                                    if (link.contains("720")) "720p" else if (link.contains("480")) "480p" else "Unknown"
                                }) - Play"
                            )
                            Log.d("ArabSeed", "Adding fallback extractor link: ${finalLink.name} -> ${finalLink.url}")
                            callback(finalLink)
                        }
                    } catch (e: Exception) {
                        Log.e("ArabSeed", "Error processing fallback link $link: ${e.message}", e)
                    }
                }
            }

            if (fallbackLinks.isEmpty()) {
                Log.e("ArabSeed", "No links found at all for: $data")
            }
        }
    }
}
