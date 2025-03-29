package com.arabseed

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import android.util.Log
import kotlin.coroutines.resume

class ArabSeed : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://m15.asd.rest"
    override var name = "ArabSeed"
    override val usesWebView = true  // Enable WebView for Cloudflare bypass
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    // Extracting data with WebView to bypass Cloudflare
    private suspend fun loadPageWithWebView(url: String): String = suspendCancellableCoroutine { cont ->
        val webView = WebView(app)
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                webView.evaluateJavascript("document.documentElement.outerHTML") { html ->
                    cont.resume(html)
                }
            }
        }
        webView.loadUrl(url)
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

    override val mainPage = mainPageOf(
        "$mainUrl/latest1/?offset=" to "Latest",  // New "Latest" section
        "$mainUrl/category/%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D8%B1%D9%85%D8%B6%D8%A7%D9%86/ramadan-series-2025/?offset=" to "Ramadan 2025",
        "$mainUrl/movies/?offset=" to "Movies",
        "$mainUrl/series/?offset=" to "Series"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        Log.d("ArabSeed", "Fetching Page: ${request.data + page}")
        val html = loadPageWithWebView(request.data + page)
        val document = Jsoup.parse(html)
        val home = document.select("ul.Blocks-UL > div").mapNotNull { it.toSearchResponse() }

        Log.d("ArabSeed", "Loaded ${home.size} items for ${request.name}")
        return newHomePageResponse(request.name, home)
    }
}
