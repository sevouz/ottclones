package com.cncverse

import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class MegaPlayExtractor : ExtractorApi() {
    override val name = "MegaPlay"
    override val mainUrl = "https://megaplay.buzz"
    override val requiresReferer = false

    companion object {
        private const val TAG = "MegaPlayExtractor"
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val mainheaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.5",
            "Origin" to "https://rapid-cloud.co",
            "Referer" to "https://rapid-cloud.co/",
        )

        try {
            // Must send referer to avoid 410 error
            val embedHeaders = mapOf(
                "Referer" to (referer ?: "https://anikoto.cz/"),
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
            )
            val pageResponse = app.get(url, headers = embedHeaders)
            val document = pageResponse.document

            val id = document.selectFirst("#megaplay-player")?.attr("data-id")

            if (id.isNullOrBlank()) {
                Log.e(TAG, "Could not find megaplay-player data-id from: $url")
                fallbackWebView(url, mainheaders, subtitleCallback, callback)
                return
            }

            Log.d(TAG, "Found data-id: $id")

            val apiUrl = "$mainUrl/stream/getSources?id=$id&id=$id"
            val headers = mapOf(
                "Accept" to "*/*",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to mainUrl
            )
            val gson = Gson()
            val response = try {
                val json = app.get(apiUrl, headers = headers).text
                Log.d(TAG, "getSources response: $json")
                gson.fromJson(json, MegaPlayResponse::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse getSources response: ${e.message}")
                null
            }

            val m3u8 = response?.sources?.file
            if (m3u8.isNullOrBlank()) {
                Log.e(TAG, "No sources found in response, trying fallback")
                fallbackWebView(url, mainheaders, subtitleCallback, callback)
                return
            }

            Log.d(TAG, "Got m3u8: $m3u8")

            // Provide direct M3U8 link - works on both stable and pre-release
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    m3u8,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = "https://rapid-cloud.co/"
                    this.quality = Qualities.Unknown.value
                    this.headers = mainheaders
                }
            )

            response.tracks?.forEach { track ->
                if (track.kind == "captions" || track.kind == "subtitles") {
                    subtitleCallback(newSubtitleFile(track.label, track.file))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Primary method failed: ${e.message}")
            fallbackWebView(url, mainheaders, subtitleCallback, callback)
        }
    }

    private suspend fun fallbackWebView(
        url: String,
        mainheaders: Map<String, String>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d(TAG, "Using WebView fallback for: $url")

        val jsToClickPlay = """
            (() => {
                const btn = document.querySelector('.jw-icon-display.jw-button-color.jw-reset');
                if (btn) { btn.click(); return "clicked"; }
                return "button not found";
            })();
        """.trimIndent()

        val m3u8Resolver = WebViewResolver(
            interceptUrl = Regex("""master\.m3u8|index\.m3u8|playlist\.m3u8|\.m3u8"""),
            additionalUrls = listOf(Regex("""\.m3u8""")),
            script = jsToClickPlay,
            scriptCallback = { result -> Log.d(TAG, "JS Result: $result") },
            useOkhttp = false,
            timeout = 15_000L
        )

        try {
            val fallbackM3u8 = app.get(url = url, referer = mainUrl, interceptor = m3u8Resolver).url

            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    fallbackM3u8,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                    this.headers = mainheaders
                }
            )
        } catch (ex: Exception) {
            Log.e(TAG, "Fallback also failed: ${ex.message}")
        }
    }

    data class MegaPlayResponse(
        val sources: Sources?,
        val tracks: List<Track>?,
        val t: Long?,
        val intro: TimeRange?,
        val outro: TimeRange?,
        val server: Long?,
    )

    data class Sources(
        val file: String?,
    )

    data class Track(
        val file: String,
        val label: String,
        val kind: String,
        val default: Boolean?,
    )

    data class TimeRange(
        val start: Long?,
        val end: Long?,
    )
}
