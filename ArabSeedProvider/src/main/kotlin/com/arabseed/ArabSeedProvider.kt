package com.arabseed

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Log
import kotlin.coroutines.resume

class ArabSeed : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://m15.asd.rest"
    override var name = "ArabSeed"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    // Function to extract HTML content
    private suspend fun fetchHtml(url: String): String {
        return try {
            app.get(url, timeout = 120).text
        } catch (e: Exception) {
            Log.e("ArabSeed", "Failed to load: $url", e)
            ""
        }
    }

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

    override val mainPage = listOf(
        HomePageList("Latest", "$mainUrl/latest1/"),
        HomePageList("Movies", "$mainUrl/movies/"),
        HomePageList("Series", "$mainUrl/series/")
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        Log.d("ArabSeed", "Fetching Page: ${request.data}")

        val html = fetchHtml(request.data)
        val document = Jsoup.parse(html)

        val selector = if (request.name == "Latest") "ul.Blocks-UL > div, div.latest-item" else "ul.Blocks-UL > div"
        val home = document.select(selector).mapNotNull { it.toSearchResponse() }

        Log.d("ArabSeed", "Loaded ${home.size} items for ${request.name}")
        return newHomePageResponse(request.name, home)
    }
}
