package com.mycima

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.*

class MyCima : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://vbn3.t4ce4ma.shop"
    override var name = "MyCima"
    override val usesWebView = false  // Keep WebView disabled
    override val hasMainPage = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : CookieJar {
            private val cookieStore = mutableMapOf<HttpUrl, List<Cookie>>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url] = cookies
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url] ?: emptyList()
            }
        })
        .build()

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

    private suspend fun safeRequest(url: String, retries: Int = 3): String? {
        var attempt = 0
        while (attempt < retries) {
            try {
                Log.d("CloudStream", "Attempting request: $url (Try $attempt)")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", getRandomUserAgent())
                    .header("Referer", mainUrl)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Accept-Language", "ar,en;q=0.9")
                    .header("Connection", "keep-alive")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.code == 403 || responseBody?.contains("Just a moment") == true) {
                    Log.w("CloudStream", "Blocked by Cloudflare, retrying...")
                    delay(3000)  // Wait before retrying
                    attempt++
                    continue
                }

                return responseBody
            } catch (e: Exception) {
                Log.e("CloudStream", "Request failed: ${e.message}")
            }
            delay(2000)  // Wait before next attempt
            attempt++
        }
        return null
    }

    private fun getRandomUserAgent(): String {
        val userAgents = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/537.36",
            "Mozilla/5.0 (iPad; CPU OS 14_0 like Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/537.36",
            "Mozilla/5.0 (Android 10; Mobile; rv:89.0) Gecko/89.0 Firefox/89.0"
        )
        return userAgents.random()
    }
}
