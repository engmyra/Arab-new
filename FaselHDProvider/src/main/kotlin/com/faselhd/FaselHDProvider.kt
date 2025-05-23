// Cloudstream extractor for faselhd.cafe (anime-movies)
// Disclaimer: This code is provided for educational purposes only.
// Use it responsibly and respect copyright laws.

package com.example.faselhd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import android.util.Log

class FaselhdProvider : MainAPI() {
    override var mainUrl = "https://web184.faselhd.cafe"
    override var name = "FaselHD (Anime Movies)"
    override val hasMainPage = false
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.AnimeMovie)
    override var sequentialMainPage = false

    override suspend fun search(query: String): List<SearchResponse> {
        val urlEncodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/?s=$urlEncodedQuery"
        val document = app.get(searchUrl).document
        val searchResults = document.select("div.Movies--Grid > div.GridItem")
        return searchResults.mapNotNull { element ->
            val title = element.selectFirst("a.Poster > div.Title")?.text() ?: return@mapNotNull null
            val href = element.selectFirst("a.Poster")?.attr("href") ?: return@mapNotNull null
            val posterUrl = element.selectFirst("a.Poster > div.Image > img")?.attr("src")
            TvType.AnimeMovie.toSearchResponse(title, href, posterUrl, null, null)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.Title")?.text() ?: "Unknown Title"
        val poster = document.selectFirst("div.Poster > img")?.attr("src")
        val synopsis = document.selectFirst("div.Description")?.text()
        val episodes = mutableListOf<Episode>()

        // Extracting video links from iframe or direct links
        val iframeSrc = document.selectFirst("iframe")?.attr("src")
        var videoUrls: List<String> = listOf()

        if (!iframeSrc.isNullOrEmpty()) {
            // Handle iframes
            try {
                val iframeDoc = app.get(iframeSrc).document
                videoUrls = iframeDoc.select("source[src]").mapNotNull { it.attr("src").takeIf { it.isNotBlank() } }
            } catch (e: Exception) {
                Log.e("FaselHD", "Error loading iframe: ${e.message}")
            }
        } else {
            // Handle direct links
            try {
                videoUrls = document.select("source[src]").mapNotNull { it.attr("src").takeIf { it.isNotBlank() } }
            } catch (e: Exception) {
                Log.e("FaselHD", "Error loading direct links: ${e.message}")
            }
        }

        if (videoUrls.isNotEmpty()) {
            videoUrls.forEach { videoUrl ->
                episodes.add(Episode(videoUrl))
            }
        } else {
            Log.e("FaselHD", "No video links found.")
        }

        return newMovieLoadResponse(title, url, TvType.AnimeMovie, episodes) {
            this.posterUrl = poster
            this.plot = synopsis
        }
    }
}
