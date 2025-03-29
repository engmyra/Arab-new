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
    override val usesWebView = false  
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

    private fun getHeaders(): Headers {
        return Headers.Builder()
            .add("User-Agent", getRandomUserAgent())
            .add("Referer", mainUrl)
            .add("Accept-Language", "ar,en;q=0.9")
            .add("Accept-Encoding", "gzip, deflate, br")
            .add("Connection", "keep-alive")
            .add("Cache-Control", "max-age=0")
            .add("Upgrade-Insecure-Requests", "1")
            .build()
    }

    private suspend fun safeRequest(url: String, retries: Int = 3): String? {
        var attempt = 0
        while (attempt < retries) {
            try {
                Log.d("CloudStream", "Attempting request: $url (Try $attempt)")
                val request = Request.Builder()
                    .url(url)
                    .headers(getHeaders())
                    .build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body?.contains("Just a moment") == true || body.contains("Checking your browser")) {
                        Log.w("CloudStream", "Blocked by Cloudflare, retrying...")
                        delay(3000) 
                        attempt++
                        continue
                    }
                    return body
                }
            } catch (e: Exception) {
                Log.e("CloudStream", "Request failed: ${e.message}")
            }
            delay(2000)
            attempt++
        }
        return null
    }

    suspend fun getHomePage(): String? {
        return safeRequest(mainUrl)
    }

    private fun getRandomUserAgent(): String {
        val userAgents = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 15_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Mobile/15E148 Safari/604.1"
        )
        return userAgents.random()
    }
}
