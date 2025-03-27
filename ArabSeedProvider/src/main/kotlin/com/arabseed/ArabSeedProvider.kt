package com.arabseed

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import android.util.Log

class ArabSeed : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://m15.asd.rest"  // Updated Base URL
    override var name = "ArabSeed"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val title = select("h4, .Title").text()  // Support different structures
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
        "$mainUrl/category/%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D8%B1%D9%85%D8%B6%D8%A7%D9%86/ramadan-series-2025/" to "Ramadan 2025",
        "$mainUrl/movies/?offset=" to "Movies",
        "$mainUrl/series/?offset=" to "Series"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        Log.d("ArabSeed", "Fetching Page: ${request.data + page}")  // Debug URL being accessed

        val document = app.get(request.data + page, timeout = 120).document
        val home = document.select("ul.Blocks-UL > div").mapNotNull { it.toSearchResponse() }

        Log.d("ArabSeed", "Loaded ${home.size} items for ${request.name}")  // Log number of items loaded
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val list = arrayListOf<SearchResponse>()
        arrayListOf(
            mainUrl to "series",
            mainUrl to "movies"
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

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 5000).document
        val title = doc.title()
        val isMovie = title.contains("فيلم")

        val posterUrl = doc.select("div.Poster > img").let { it.attr("data-src").ifEmpty { it.attr("src") } }
        val rating = doc.select("div.RatingImdb em").text().getIntFromText()
        val synopsis = doc.select("p.descrip").last()?.text()
        val year = doc.select("li:contains(السنه) a").text().getIntFromText()
        val tags = doc.select("li:contains(النوع) > a, li:contains(التصنيف) > a")?.map { it.text() }

        val actors = doc.select("div.WorkTeamIteM").mapNotNull {
            val name = it.selectFirst("h4 > em")?.text() ?: return@mapNotNull null
            val image = it.selectFirst("div.Icon img")?.attr("src") ?: return@mapNotNull null
            val roleString = it.select("h4 > span").text()
            val mainActor = Actor(name, image)
            ActorData(actor = mainActor, roleString = roleString)
        }

        val recommendations = doc.select("ul.Blocks-UL > div").mapNotNull { it.toSearchResponse() }

        return if (isMovie) {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = posterUrl
                this.recommendations = recommendations
                this.plot = synopsis
                this.tags = tags
                this.actors = actors
                this.rating = rating
                this.year = year
            }
        } else {
            val seasonList = doc.select("div.SeasonsListHolder ul > li")
            val episodes = arrayListOf<Episode>()
            if (seasonList.isNotEmpty()) {
                seasonList.apmap { season ->
                    app.post(
                        "$mainUrl/wp-content/themes/Elshaikh2021/Ajaxat/Single/Episodes.php",
                        data = mapOf("season" to season.attr("data-season"), "post_id" to season.attr("data-id"))
                    ).document.select("a").apmap {
                        episodes.add(
                            Episode(
                                it.attr("href"),
                                it.text(),
                                season.attr("data-season")[0].toString().toIntOrNull(),
                                it.text().getIntFromText()
                            )
                        )
                    }
                }
            } else {
                doc.select("div.ContainerEpisodesList > a").apmap {
                    episodes.add(
                        Episode(
                            it.attr("href"),
                            it.text(),
                            0,
                            it.text().getIntFromText()
                        )
                    )
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinct().sortedBy { it.data }) {
                this.posterUrl = posterUrl
                this.tags = tags
                this.plot = synopsis
                this.actors = actors
                this.recommendations = recommendations
                this.rating = rating
                this.year = year
            }
        }
    }
}
