package com.mycima

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Log
import kotlinx.coroutines.delay

class MyCima : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://vbn3.t4ce4ma.shop"
    override var name = "MyCima"
    override val usesWebView = true  // WebView helps bypass Cloudflare
    override val hasMainPage = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    // Extract image URL
    private fun String.getImageURL(): String? {
        return this.replace(".*background-image:url\(.*?)\.*".toRegex(), "$1")
    }

    // Extract integer from text
    private fun String.getIntFromText(): Int? {
        return Regex("\\d+").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    // Convert HTML element to search response
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

    // Function to handle Cloudflare blocking
    private suspend fun safeRequest(url: String, retries: Int = 3): String? {
        var attempt = 0
        while (attempt < retries) {
            try {
                Log.d("CloudStream", "Attempting request: $url (Try $attempt)")
                val response = app.get(url, headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
                    "Referer" to mainUrl,
                    "Accept-Language" to "ar,en;q=0.9"
                ))
                if (response.document.title().contains("Just a moment")) {
                    Log.w("CloudStream", "Blocked by Cloudflare, retrying...")
                    delay(2000)  // Wait 2 seconds before retrying
                    attempt++
                    continue
                }
                return response.text
            } catch (e: Exception) {
                Log.e("CloudStream", "Request failed: ${e.message}")
            }
            delay(1000)  // Wait before retrying
            attempt++
        }
        return null
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "%20")
        val result = mutableListOf<SearchResponse>()
        val searchUrls = listOf(
            "$mainUrl/search/$q",
            "$mainUrl/search/$q/list/series/",
            "$mainUrl/search/$q/list/anime/"
        )

        searchUrls.apmap { url ->
            val html = safeRequest(url)
            if (html != null) {
                val doc = Jsoup.parse(html)
                doc.select("div.Grid--WecimaPosts div.GridItem").mapNotNull {
                    if (it.text().contains("اعلان")) return@mapNotNull null
                    it.toSearchResponse()?.let { it1 -> result.add(it1) }
                }
            } else {
                Log.e("CloudStream", "Failed to fetch data for $url")
            }
        }
        return result.distinct().sortedBy { it.name }
    }
}
