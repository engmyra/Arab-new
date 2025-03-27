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
            "$mainUrl/latest1/" to "Latest",  // Now correctly set as "Latest"
            "$mainUrl/movies/?offset=" to "Movies",
            "$mainUrl/series/?offset=" to "Series"
    )

    override suspend fun getMainPage(
            page: Int,
            request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page, timeout = 120).document
        val home = document.select("ul.Blocks-UL > div").mapNotNull {
            it.toSearchResponse()
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val list = arrayListOf<SearchResponse>()
        arrayListOf(
                "$mainUrl/series/?offset=" to "series",
                "$mainUrl/movies/?offset=" to "movies",
                "$mainUrl/latest1/" to "latest"  // Ensure "Latest" is part of the search
        ).apmap { (url, type) ->
            val doc = app.post(
                    "$url/wp-content/themes/Elshaikh2021/Ajaxat/SearchingTwo.php",
                    data = mapOf("search" to query, "type" to type),
                    referer = mainUrl
            ).document
            doc.select("ul.Blocks-UL > div").mapNotNull {
                it.toSearchResponse()?.let { it1 -> list.add(it1) }
            }
        }
        return list
    }
}
