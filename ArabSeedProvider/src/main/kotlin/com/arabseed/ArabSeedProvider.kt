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

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val title = select("h4").text()
        val posterUrl = select("img.imgOptimzer").attr("data-image").ifEmpty { select("div.Poster img").attr("data-src") }
        val tvType = if (select("span.category").text().contains("مسلسلات")) TvType.TvSeries else TvType.Movie
        return MovieSearchResponse(
                title,
                select("a").attr("href"),
                this@ArabSeed.name,
                tvType,
                posterUrl,
        )
    }

    override val mainPage = mainPageOf(
            "$mainUrl/category/مسلسلات-رمضان/ramadan-series-2025/" to "Ramadan 2025",  // Updated Latest Section
            "$mainUrl/movies/?offset=" to "Movies",
            "$mainUrl/series/?offset=" to "Series"
    )

    override suspend fun getMainPage(
            page: Int,
            request: MainPageRequest
    ): HomePageResponse {
        Log.d("ArabSeed", "Fetching: ${request.data + page}")  // Debugging
        val document = app.get(request.data + page, timeout = 120).document
        val home = document.select("ul.Blocks-UL > div").mapNotNull {
            it.toSearchResponse()
        }
        
        if (home.isEmpty()) {
            Log.e("ArabSeed", "No data found for: ${request.name}")
        }

        return newHomePageResponse(request.name, home)
    }
}
