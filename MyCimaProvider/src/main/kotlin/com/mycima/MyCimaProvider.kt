package com.mycima

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Log

class MyCima : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://vbn3.t4ce4ma.shop"  // Updated URL
    override var name = "MyCima"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    private fun String.getImageURL(): String? {
        return this.replace("--im(age|g):url\|\;".toRegex(), "")
    }

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("div.Thumb--GridItem a")
        val posterUrl = select("span.BG--GridItem")?.attr("data-lazy-style")?.getImageURL()
        val year = select("div.GridItem span.year")?.text()
        val title = select("div.Thumb--GridItem strong").text()
            .replace("$year", "")
            .replace("مشاهدة|فيلم|مسلسل|مترجم".toRegex(), "")
            .replace("( نسخة مدبلجة )", " ( نسخة مدبلجة ) ")

        return MovieSearchResponse(
            title,
            url.attr("href"),
            this@MyCima.name,
            if (url.attr("title").contains("فيلم")) TvType.Movie else TvType.TvSeries,
            posterUrl,
            year?.getIntFromText(),
            null,
        )
    }

    override val mainPage = mainPageOf(
        "$mainUrl/movies/top/page/0" to "Top Movies",
        "$mainUrl/movies/recent/page/0" to "Recently Added Movies",
        "$mainUrl/seriestv/top/page/0" to "Top Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page).document
        val list = doc.select("div.Grid--WecimaPosts div.GridItem").mapNotNull { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "%20")
        val result = arrayListOf<SearchResponse>()
        listOf(
            "$mainUrl/search/$q",
            "$mainUrl/search/$q/list/series/",
            "$mainUrl/search/$q/list/anime/"
        ).apmap { url ->
            val d = app.get(url).document
            d.select("div.Grid--WecimaPosts div.GridItem").mapNotNull {
                if (it.text().contains("اعلان")) return@mapNotNull null
                it.toSearchResponse()?.let { it1 -> result.add(it1) }
            }
        }
        return result.distinct().sortedBy { it.name }
    }

    data class MoreEPS(
        val output: String
    )

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val isMovie = doc.select("ol li:nth-child(3)").text().contains("افلام")
        val posterUrl =
            doc.select("wecima.separated--top")?.attr("data-lazy-style")?.getImageURL()
                ?.ifEmpty { doc.select("meta[itemprop=\"thumbnailUrl\"]")?.attr("content") }
                ?.ifEmpty { doc.select("wecima.separated--top")?.attr("style")?.getImageURL() }
        val year = doc.select("div.Title--Content--Single-begin h1 a.unline")?.text()?.getIntFromText()
        val title = doc.select("div.Title--Content--Single-begin h1").text()
            .replace("($year)", "")
            .replace("مشاهدة|فيلم|مسلسل|مترجم|انمي".toRegex(), "")

        val duration =
            doc.select("ul.Terms--Content--Single-begin li").firstOrNull {
                it.text().contains("المدة")
            }?.text()?.getIntFromText()

        val synopsis = doc.select("div.StoryMovieContent").text()
            .ifEmpty { doc.select("div.PostItemContent").text() }

        val tags = doc.select("li:nth-child(3) > p > a").map { it.text() }

        val actors = doc.select("div.List--Teamwork > ul.Inner--List--Teamwork > li")?.mapNotNull {
            val name = it?.selectFirst("a > div.ActorName > span")?.text() ?: return@mapNotNull null
            val image = it.attr("style")?.getImageURL() ?: return@mapNotNull null
            Actor(name, image)
        }

        val recommendations =
            doc.select("div.Grid--WecimaPosts div.GridItem")?.mapNotNull { element ->
                element.toSearchResponse()
            }

        return if (isMovie) {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = synopsis
                this.tags = tags
                this.duration = duration
                this.recommendations = recommendations
                addActors(actors)
            }
        } else {
            newTvSeriesLoadResponse(
                title,
                url,
                TvType.TvSeries,
                url,
                episodes = emptyList()
            ) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = synopsis
                this.tags = tags
                this.recommendations = recommendations
                addActors(actors)
            }
        }
    }
}
