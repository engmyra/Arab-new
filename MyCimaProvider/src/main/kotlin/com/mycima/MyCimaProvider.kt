package com.mycima

import com.lagradost.cloudstream3.* import com.lagradost.cloudstream3.LoadResponse.Companion.addActors import com.lagradost.cloudstream3.utils.AppUtils.parseJson import com.lagradost.cloudstream3.utils.ExtractorLink import com.lagradost.cloudstream3.utils.loadExtractor import org.jsoup.Jsoup import org.jsoup.nodes.Element import android.util.Log

class MyCima : MainAPI() { override var lang = "ar" override var mainUrl = "https://vbn3.t4ce4ma.shop"  // Updated URL override var name = "MyCima" override val usesWebView = true  // Force WebView to avoid bot detection override val hasMainPage = false  // Disable main page scraping to prevent blocks override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

private fun String.getImageURL(): String? {
    return this.replace(".*background-image:url\(.*?)\.*".toRegex(), "$1")
}

private fun String.getIntFromText(): Int? {
    return Regex("\\d+").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
}

private fun Element.toSearchResponse(): SearchResponse? {
    val url = select("div.Thumb--GridItem a").attr("href")
    val posterUrl = select("span.BG--GridItem").attr("data-lazy-style").getImageURL()
    val year = select("div.GridItem span.year").text()
    val title = select("div.Thumb--GridItem strong").text()
        .replace("$year", "")
        .replace("مشاهدة|فيلم|مسلسل|مترجم".toRegex(), "")
        .replace("( نسخة مدبلجة )", " ( نسخة مدبلجة ) ")

    return MovieSearchResponse(
        title,
        url,
        this@MyCima.name,
        if (url.contains("فيلم")) TvType.Movie else TvType.TvSeries,
        posterUrl,
        year.getIntFromText(),
        null,
    )
}

override suspend fun search(query: String): List<SearchResponse> {
    val q = query.replace(" ", "%20")
    val result = mutableListOf<SearchResponse>()
    listOf(
        "$mainUrl/search/$q",
        "$mainUrl/search/$q/list/series/",
        "$mainUrl/search/$q/list/anime/"
    ).apmap { url ->
        try {
            val doc = app.get(url, headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
                "Referer" to mainUrl,
                "Accept-Language" to "ar,en;q=0.9"
            )).document
            doc.select("div.Grid--WecimaPosts div.GridItem").mapNotNull {
                if (it.text().contains("اعلان")) return@mapNotNull null
                it.toSearchResponse()?.let { it1 -> result.add(it1) }
            }
        } catch (e: Exception) {
            Log.e("CloudStream", "Search request failed", e)
        }
    }
    return result.distinct().sortedBy { it.name }
}

}

