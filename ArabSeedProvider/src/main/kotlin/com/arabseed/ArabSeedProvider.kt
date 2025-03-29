package com.arabseed

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
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
        } else {
            null
        }
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
            "Latest" -> "div.latest-item"  // Adjust this selector if needed
            else -> "ul.Blocks-UL > div"
        }

        val home = document.select(selector).mapNotNull { it.toSearchResponse() }
        Log.d("ArabSeed", "Loaded ${home.size} items for ${request.name}")

        return newHomePageResponse(request.name, home)
    }
}
